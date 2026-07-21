-- Borg Pharmacy Auth Phase: Supabase Auth + tenants + user_profiles.
-- Execute after supabase/2026_07_multi_tenant_offline_sync.sql.
-- This phase prepares Supabase Auth without breaking the current Android login fallback.

begin;

create extension if not exists pgcrypto;

-- Stable first tenant for صيدلية برج الأطباء.
insert into public.tenants (id, name, slug)
values ('00000000-0000-0000-0000-000000000001', 'صيدلية برج الأطباء', 'borg-alatiba')
on conflict (id) do update set
  name = excluded.name,
  slug = excluded.slug,
  updated_at = now(),
  is_deleted = false;

-- Ensure user_profiles exists with strict link to auth.users.
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
create index if not exists idx_user_profiles_active on public.user_profiles(tenant_id, active);

alter table public.tenants enable row level security;
alter table public.user_profiles enable row level security;

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

-- Auto-create profile when an Auth user is created with metadata:
-- raw_user_meta_data: { "tenant_id": "...", "display_name": "...", "role": "ADMIN|PHARMACIST" }
create or replace function public.handle_new_auth_user_profile()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
declare
  v_tenant uuid;
  v_role text;
  v_display text;
begin
  v_tenant := coalesce(
    nullif(new.raw_user_meta_data->>'tenant_id', '')::uuid,
    '00000000-0000-0000-0000-000000000001'::uuid
  );
  v_role := case when new.raw_user_meta_data->>'role' = 'ADMIN' then 'ADMIN' else 'PHARMACIST' end;
  v_display := coalesce(nullif(new.raw_user_meta_data->>'display_name', ''), new.email, 'مستخدم');

  insert into public.user_profiles (user_id, tenant_id, display_name, role, active, must_change_password)
  values (new.id, v_tenant, v_display, v_role, true, coalesce((new.raw_user_meta_data->>'must_change_password')::boolean, false))
  on conflict (user_id) do update set
    tenant_id = excluded.tenant_id,
    display_name = excluded.display_name,
    role = excluded.role,
    active = excluded.active,
    updated_at = now();

  return new;
end;
$$;

drop trigger if exists on_auth_user_created_create_profile on auth.users;
create trigger on_auth_user_created_create_profile
after insert on auth.users
for each row execute function public.handle_new_auth_user_profile();

-- RLS policies.
drop policy if exists tenants_select_own on public.tenants;
create policy tenants_select_own
on public.tenants
for select
to authenticated
using (id = public.current_tenant_id());

drop policy if exists profiles_select_own_tenant on public.user_profiles;
create policy profiles_select_own_tenant
on public.user_profiles
for select
to authenticated
using (tenant_id = public.current_tenant_id());

drop policy if exists profiles_admin_insert_own_tenant on public.user_profiles;
create policy profiles_admin_insert_own_tenant
on public.user_profiles
for insert
to authenticated
with check (tenant_id = public.current_tenant_id() and public.current_app_role() = 'ADMIN');

drop policy if exists profiles_admin_update_own_tenant on public.user_profiles;
create policy profiles_admin_update_own_tenant
on public.user_profiles
for update
to authenticated
using (tenant_id = public.current_tenant_id() and public.current_app_role() = 'ADMIN')
with check (tenant_id = public.current_tenant_id() and public.current_app_role() = 'ADMIN');

-- Tenant RLS for business tables when Android Auth mode is enabled.
do $$
declare
  t text;
begin
  foreach t in array array['companies','representatives','visits'] loop
    if to_regclass('public.' || t) is not null then
      execute format('alter table public.%I enable row level security', t);

      execute format('drop policy if exists %I on public.%I', t || '_auth_select_tenant', t);
      execute format('create policy %I on public.%I for select to authenticated using (tenant_id = public.current_tenant_id() and is_deleted = false)', t || '_auth_select_tenant', t);

      execute format('drop policy if exists %I on public.%I', t || '_auth_admin_insert_tenant', t);
      execute format('create policy %I on public.%I for insert to authenticated with check (tenant_id = public.current_tenant_id() and public.current_app_role() = ''ADMIN'')', t || '_auth_admin_insert_tenant', t);

      execute format('drop policy if exists %I on public.%I', t || '_auth_admin_update_tenant', t);
      execute format('create policy %I on public.%I for update to authenticated using (tenant_id = public.current_tenant_id() and public.current_app_role() = ''ADMIN'') with check (tenant_id = public.current_tenant_id() and public.current_app_role() = ''ADMIN'')', t || '_auth_admin_update_tenant', t);
    end if;
  end loop;
end $$;

-- Repair helper: call after manually creating the first Auth admin user if metadata was missed.
create or replace function public.link_existing_auth_admin_to_first_tenant(p_email text)
returns void
language plpgsql
security definer
set search_path = public
as $$
declare
  v_user_id uuid;
begin
  select id into v_user_id from auth.users where lower(email) = lower(trim(p_email)) limit 1;
  if v_user_id is null then
    raise exception 'auth user not found for email %', p_email;
  end if;

  insert into public.user_profiles (user_id, tenant_id, display_name, role, active, must_change_password)
  values (v_user_id, '00000000-0000-0000-0000-000000000001', p_email, 'ADMIN', true, false)
  on conflict (user_id) do update set
    tenant_id = excluded.tenant_id,
    role = 'ADMIN',
    active = true,
    updated_at = now();
end;
$$;

commit;
