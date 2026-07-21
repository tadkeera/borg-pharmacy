-- Borg Pharmacy multi-tenant offline-first sync architecture.
-- Execute after the existing schema/portal SQL.
-- This script evolves Borg Pharmacy from one local dataset into tenant-isolated cloud sync.

begin;

create extension if not exists pgcrypto;

-- 1) Tenants and authenticated user profile mapping.
create table if not exists public.tenants (
  id uuid primary key default gen_random_uuid(),
  name text not null,
  slug text unique,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  is_deleted boolean not null default false
);

create table if not exists public.user_profiles (
  user_id uuid primary key references auth.users(id) on delete cascade,
  tenant_id uuid not null references public.tenants(id) on delete cascade,
  display_name text not null default '',
  role text not null check (role in ('ADMIN', 'PHARMACIST')),
  active boolean not null default true,
  must_change_password boolean not null default false,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create index if not exists idx_user_profiles_tenant on public.user_profiles(tenant_id);

create or replace function public.current_tenant_id()
returns uuid
language sql
stable
security definer
set search_path = public
as $$
  select up.tenant_id
  from public.user_profiles up
  where up.user_id = auth.uid()
    and up.active = true
  limit 1;
$$;

create or replace function public.current_app_role()
returns text
language sql
stable
security definer
set search_path = public
as $$
  select up.role
  from public.user_profiles up
  where up.user_id = auth.uid()
    and up.active = true
  limit 1;
$$;

-- 2) Evolve existing data tables. UUID tenant_id is text-compatible with Room String tenantId.
do $$
declare
  t text;
begin
  foreach t in array array['companies','representatives','visits','users','bot_config','bot_logs'] loop
    if to_regclass('public.' || t) is not null then
      execute format('alter table public.%I add column if not exists tenant_id uuid', t);
      execute format('alter table public.%I add column if not exists sync_status text not null default ''SYNCED''', t);
      execute format('alter table public.%I add column if not exists is_deleted boolean not null default false', t);
      execute format('create index if not exists idx_%I_tenant_updated on public.%I(tenant_id, updated_at)', t, t);
      execute format('create index if not exists idx_%I_tenant_active on public.%I(tenant_id) where is_deleted = false', t, t);
    end if;
  end loop;
end $$;

-- visits/companies/representatives currently use bigint updated_at in the Android schema.
-- Existing deleted_at soft-deletes are mirrored into is_deleted.
update public.companies set is_deleted = true where deleted_at is not null;
update public.representatives set is_deleted = true where deleted_at is not null;
update public.visits set is_deleted = true where deleted_at is not null;

-- 3) RLS: tenant isolation for authenticated users.
alter table public.tenants enable row level security;
alter table public.user_profiles enable row level security;
alter table public.companies enable row level security;
alter table public.representatives enable row level security;
alter table public.visits enable row level security;

-- Tenants: users can see their own tenant; admins can update only their tenant metadata.
drop policy if exists tenants_select_own on public.tenants;
create policy tenants_select_own
on public.tenants for select
to authenticated
using (id = public.current_tenant_id());

drop policy if exists tenants_admin_update_own on public.tenants;
create policy tenants_admin_update_own
on public.tenants for update
to authenticated
using (id = public.current_tenant_id() and public.current_app_role() = 'ADMIN')
with check (id = public.current_tenant_id() and public.current_app_role() = 'ADMIN');

-- Profiles: user can read profiles in own tenant; only admin can create/update/deactivate profiles in own tenant.
drop policy if exists profiles_select_own_tenant on public.user_profiles;
create policy profiles_select_own_tenant
on public.user_profiles for select
to authenticated
using (tenant_id = public.current_tenant_id());

drop policy if exists profiles_admin_insert_own_tenant on public.user_profiles;
create policy profiles_admin_insert_own_tenant
on public.user_profiles for insert
to authenticated
with check (tenant_id = public.current_tenant_id() and public.current_app_role() = 'ADMIN');

drop policy if exists profiles_admin_update_own_tenant on public.user_profiles;
create policy profiles_admin_update_own_tenant
on public.user_profiles for update
to authenticated
using (tenant_id = public.current_tenant_id() and public.current_app_role() = 'ADMIN')
with check (tenant_id = public.current_tenant_id() and public.current_app_role() = 'ADMIN');

-- Generic tenant policies for business tables.
-- ADMIN: read/write. PHARMACIST: read, print/share/export in Android; no direct cloud writes except allowed app flows.
do $$
declare
  t text;
begin
  foreach t in array array['companies','representatives','visits'] loop
    execute format('drop policy if exists %I on public.%I', t || '_select_tenant', t);
    execute format('create policy %I on public.%I for select to authenticated using (tenant_id = public.current_tenant_id() and is_deleted = false)', t || '_select_tenant', t);

    execute format('drop policy if exists %I on public.%I', t || '_admin_insert_tenant', t);
    execute format('create policy %I on public.%I for insert to authenticated with check (tenant_id = public.current_tenant_id() and public.current_app_role() = ''ADMIN'')', t || '_admin_insert_tenant', t);

    execute format('drop policy if exists %I on public.%I', t || '_admin_update_tenant', t);
    execute format('create policy %I on public.%I for update to authenticated using (tenant_id = public.current_tenant_id() and public.current_app_role() = ''ADMIN'') with check (tenant_id = public.current_tenant_id() and public.current_app_role() = ''ADMIN'')', t || '_admin_update_tenant', t);
  end loop;
end $$;

-- 4) Offline sync helper RPCs for authenticated sessions.
-- Last-write-wins is enforced by updated_at: old writes cannot overwrite newer rows.
create or replace function public.sync_upsert_companies(rows jsonb)
returns void
language plpgsql
security definer
set search_path = public
as $$
declare item jsonb; v_tenant uuid := public.current_tenant_id();
begin
  if v_tenant is null then raise exception 'tenant not resolved'; end if;
  for item in select value from jsonb_array_elements(rows) loop
    insert into public.companies(id, tenant_id, name, tier, base_day_index, base_shift, created_at, updated_at, deleted_at, sync_status, is_deleted)
    values ((item->>'id')::uuid, v_tenant, item->>'name', coalesce(item->>'tier','UNRATED'), nullif(item->>'base_day_index','')::int, nullif(item->>'base_shift',''), (item->>'created_at')::bigint, (item->>'updated_at')::bigint, nullif(item->>'deleted_at','')::bigint, 'SYNCED', coalesce((item->>'is_deleted')::boolean,false))
    on conflict(id) do update set
      name = excluded.name,
      tier = excluded.tier,
      base_day_index = excluded.base_day_index,
      base_shift = excluded.base_shift,
      updated_at = excluded.updated_at,
      deleted_at = excluded.deleted_at,
      is_deleted = excluded.is_deleted,
      sync_status = 'SYNCED'
    where public.companies.tenant_id = v_tenant
      and public.companies.updated_at <= excluded.updated_at;
  end loop;
end;
$$;

create or replace function public.sync_upsert_representatives(rows jsonb)
returns void
language plpgsql
security definer
set search_path = public
as $$
declare item jsonb; v_tenant uuid := public.current_tenant_id();
begin
  if v_tenant is null then raise exception 'tenant not resolved'; end if;
  for item in select value from jsonb_array_elements(rows) loop
    insert into public.representatives(id, tenant_id, company_id, name, phone, created_at, updated_at, deleted_at, sync_status, is_deleted)
    values ((item->>'id')::uuid, v_tenant, (item->>'company_id')::uuid, item->>'name', item->>'phone', (item->>'created_at')::bigint, (item->>'updated_at')::bigint, nullif(item->>'deleted_at','')::bigint, 'SYNCED', coalesce((item->>'is_deleted')::boolean,false))
    on conflict(id) do update set
      company_id = excluded.company_id,
      name = excluded.name,
      phone = excluded.phone,
      updated_at = excluded.updated_at,
      deleted_at = excluded.deleted_at,
      is_deleted = excluded.is_deleted,
      sync_status = 'SYNCED'
    where public.representatives.tenant_id = v_tenant
      and public.representatives.updated_at <= excluded.updated_at;
  end loop;
end;
$$;

create or replace function public.sync_upsert_visits(rows jsonb)
returns void
language plpgsql
security definer
set search_path = public
as $$
declare item jsonb; v_tenant uuid := public.current_tenant_id();
begin
  if v_tenant is null then raise exception 'tenant not resolved'; end if;
  for item in select value from jsonb_array_elements(rows) loop
    insert into public.visits(id, tenant_id, company_id, cycle_start_epoch_day, day_of_cycle, week_of_cycle, date_epoch_day, shift, slot_index, status, created_at, updated_at, deleted_at, sync_status, is_deleted)
    values ((item->>'id')::uuid, v_tenant, (item->>'company_id')::uuid, (item->>'cycle_start_epoch_day')::bigint, (item->>'day_of_cycle')::int, (item->>'week_of_cycle')::int, (item->>'date_epoch_day')::bigint, item->>'shift', (item->>'slot_index')::int, item->>'status', (item->>'created_at')::bigint, (item->>'updated_at')::bigint, nullif(item->>'deleted_at','')::bigint, 'SYNCED', coalesce((item->>'is_deleted')::boolean,false))
    on conflict(id) do update set
      company_id = excluded.company_id,
      cycle_start_epoch_day = excluded.cycle_start_epoch_day,
      day_of_cycle = excluded.day_of_cycle,
      week_of_cycle = excluded.week_of_cycle,
      date_epoch_day = excluded.date_epoch_day,
      shift = excluded.shift,
      slot_index = excluded.slot_index,
      status = excluded.status,
      updated_at = excluded.updated_at,
      deleted_at = excluded.deleted_at,
      is_deleted = excluded.is_deleted,
      sync_status = 'SYNCED'
    where public.visits.tenant_id = v_tenant
      and public.visits.updated_at <= excluded.updated_at;
  end loop;
end;
$$;

grant execute on function public.sync_upsert_companies(jsonb) to authenticated;
grant execute on function public.sync_upsert_representatives(jsonb) to authenticated;
grant execute on function public.sync_upsert_visits(jsonb) to authenticated;

-- 5) Realtime: required for multi-device updates.
do $$
begin
  begin alter publication supabase_realtime add table public.companies; exception when duplicate_object then null; end;
  begin alter publication supabase_realtime add table public.representatives; exception when duplicate_object then null; end;
  begin alter publication supabase_realtime add table public.visits; exception when duplicate_object then null; end;
end $$;

commit;
