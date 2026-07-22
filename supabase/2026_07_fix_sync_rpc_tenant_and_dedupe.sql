-- Fix Borg Pharmacy sync RPCs to be tenant-aware and deduplicate cloud data.
-- Run once in Supabase SQL Editor. Safe and idempotent.

begin;

create extension if not exists pgcrypto;

-- Ensure sync columns exist.
do $$
declare t text;
begin
  foreach t in array array['companies','representatives','visits','users'] loop
    if to_regclass('public.' || t) is not null then
      execute format('alter table public.%I add column if not exists tenant_id uuid', t);
      execute format('alter table public.%I add column if not exists sync_status text not null default ''SYNCED''', t);
      execute format('alter table public.%I add column if not exists is_deleted boolean not null default false', t);
      execute format('update public.%I set tenant_id = ''00000000-0000-0000-0000-000000000001''::uuid where tenant_id is null', t);
    end if;
  end loop;
end $$;

-- Keep old and new soft delete flags consistent.
update public.companies set is_deleted = true where deleted_at is not null and coalesce(is_deleted, false) = false;
update public.representatives set is_deleted = true where deleted_at is not null and coalesce(is_deleted, false) = false;
update public.visits set is_deleted = true where deleted_at is not null and coalesce(is_deleted, false) = false;

-- 1) Tenant-aware sync RPCs.
create or replace function public.borg_sync_companies(p_token text, p_rows jsonb)
returns void
language plpgsql
security definer
set search_path = public
as $$
declare item jsonb; v_deleted boolean; v_tenant uuid;
begin
  if not public.borg_sync_token_valid(p_token) then
    raise exception 'unauthorized borg sync token' using errcode = '28000';
  end if;
  if coalesce(jsonb_typeof(p_rows), '') <> 'array' then
    raise exception 'p_rows must be a json array';
  end if;

  for item in select value from jsonb_array_elements(p_rows) loop
    v_tenant := coalesce(nullif(item->>'tenant_id','')::uuid, '00000000-0000-0000-0000-000000000001'::uuid);
    v_deleted := coalesce((item->>'is_deleted')::boolean, false) or (item ? 'deleted_at' and item->>'deleted_at' is not null);

    insert into public.companies (
      id, tenant_id, name, tier, base_day_index, base_shift, created_at, updated_at, deleted_at, sync_status, is_deleted
    ) values (
      (item->>'id')::uuid,
      v_tenant,
      nullif(trim(item->>'name'), ''),
      coalesce(nullif(item->>'tier', ''), 'UNRATED'),
      case when item ? 'base_day_index' and item->>'base_day_index' is not null then (item->>'base_day_index')::int else null end,
      nullif(item->>'base_shift', ''),
      coalesce((item->>'created_at')::bigint, (extract(epoch from now()) * 1000)::bigint),
      coalesce((item->>'updated_at')::bigint, (extract(epoch from now()) * 1000)::bigint),
      case when item ? 'deleted_at' and item->>'deleted_at' is not null then (item->>'deleted_at')::bigint else null end,
      'SYNCED',
      v_deleted
    )
    on conflict (id) do update set
      tenant_id = excluded.tenant_id,
      name = excluded.name,
      tier = excluded.tier,
      base_day_index = excluded.base_day_index,
      base_shift = excluded.base_shift,
      updated_at = excluded.updated_at,
      deleted_at = excluded.deleted_at,
      sync_status = 'SYNCED',
      is_deleted = excluded.is_deleted
    where public.companies.updated_at <= excluded.updated_at
       or public.companies.deleted_at is distinct from excluded.deleted_at
       or public.companies.is_deleted is distinct from excluded.is_deleted;
  end loop;
end;
$$;

create or replace function public.borg_sync_representatives(p_token text, p_rows jsonb)
returns void
language plpgsql
security definer
set search_path = public
as $$
declare item jsonb; v_deleted boolean; v_tenant uuid;
begin
  if not public.borg_sync_token_valid(p_token) then
    raise exception 'unauthorized borg sync token' using errcode = '28000';
  end if;
  if coalesce(jsonb_typeof(p_rows), '') <> 'array' then
    raise exception 'p_rows must be a json array';
  end if;

  for item in select value from jsonb_array_elements(p_rows) loop
    v_tenant := coalesce(nullif(item->>'tenant_id','')::uuid, '00000000-0000-0000-0000-000000000001'::uuid);
    v_deleted := coalesce((item->>'is_deleted')::boolean, false) or (item ? 'deleted_at' and item->>'deleted_at' is not null);

    insert into public.representatives (
      id, tenant_id, company_id, name, phone, created_at, updated_at, deleted_at, sync_status, is_deleted
    ) values (
      (item->>'id')::uuid,
      v_tenant,
      (item->>'company_id')::uuid,
      nullif(trim(item->>'name'), ''),
      public.web_normalize_phone(item->>'phone'),
      coalesce((item->>'created_at')::bigint, (extract(epoch from now()) * 1000)::bigint),
      coalesce((item->>'updated_at')::bigint, (extract(epoch from now()) * 1000)::bigint),
      case when item ? 'deleted_at' and item->>'deleted_at' is not null then (item->>'deleted_at')::bigint else null end,
      'SYNCED',
      v_deleted
    )
    on conflict (id) do update set
      tenant_id = excluded.tenant_id,
      company_id = excluded.company_id,
      name = excluded.name,
      phone = excluded.phone,
      updated_at = excluded.updated_at,
      deleted_at = excluded.deleted_at,
      sync_status = 'SYNCED',
      is_deleted = excluded.is_deleted
    where public.representatives.updated_at <= excluded.updated_at
       or public.representatives.deleted_at is distinct from excluded.deleted_at
       or public.representatives.is_deleted is distinct from excluded.is_deleted;
  end loop;
end;
$$;

create or replace function public.borg_sync_visits(p_token text, p_rows jsonb)
returns void
language plpgsql
security definer
set search_path = public
as $$
declare item jsonb; v_deleted boolean; v_tenant uuid;
begin
  if not public.borg_sync_token_valid(p_token) then
    raise exception 'unauthorized borg sync token' using errcode = '28000';
  end if;
  if coalesce(jsonb_typeof(p_rows), '') <> 'array' then
    raise exception 'p_rows must be a json array';
  end if;

  for item in select value from jsonb_array_elements(p_rows) loop
    v_tenant := coalesce(nullif(item->>'tenant_id','')::uuid, '00000000-0000-0000-0000-000000000001'::uuid);
    v_deleted := coalesce((item->>'is_deleted')::boolean, false) or (item ? 'deleted_at' and item->>'deleted_at' is not null);

    insert into public.visits (
      id, tenant_id, company_id, cycle_start_epoch_day, day_of_cycle, week_of_cycle, date_epoch_day, shift, slot_index, status, created_at, updated_at, deleted_at, sync_status, is_deleted
    ) values (
      (item->>'id')::uuid,
      v_tenant,
      (item->>'company_id')::uuid,
      (item->>'cycle_start_epoch_day')::bigint,
      (item->>'day_of_cycle')::int,
      (item->>'week_of_cycle')::int,
      (item->>'date_epoch_day')::bigint,
      item->>'shift',
      (item->>'slot_index')::int,
      coalesce(nullif(item->>'status', ''), 'SCHEDULED'),
      coalesce((item->>'created_at')::bigint, (extract(epoch from now()) * 1000)::bigint),
      coalesce((item->>'updated_at')::bigint, (extract(epoch from now()) * 1000)::bigint),
      case when item ? 'deleted_at' and item->>'deleted_at' is not null then (item->>'deleted_at')::bigint else null end,
      'SYNCED',
      v_deleted
    )
    on conflict (id) do update set
      tenant_id = excluded.tenant_id,
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
      sync_status = 'SYNCED',
      is_deleted = excluded.is_deleted
    where public.visits.updated_at <= excluded.updated_at
       or public.visits.deleted_at is distinct from excluded.deleted_at
       or public.visits.is_deleted is distinct from excluded.is_deleted;
  end loop;
end;
$$;

-- 2) Server-side pruning after an explicit catalog replacement in Android.
create or replace function public.borg_prune_tenant_to_companies(p_token text, p_tenant_id uuid, p_company_ids jsonb)
returns void
language plpgsql
security definer
set search_path = public
as $$
declare
  v_now bigint := (extract(epoch from now()) * 1000)::bigint;
begin
  if not public.borg_sync_token_valid(p_token) then
    raise exception 'unauthorized borg sync token' using errcode = '28000';
  end if;
  if coalesce(jsonb_typeof(p_company_ids), '') <> 'array' then
    raise exception 'p_company_ids must be a json array';
  end if;

  with keep(id) as (
    select value::uuid from jsonb_array_elements_text(p_company_ids)
  )
  update public.visits v
  set is_deleted = true, deleted_at = coalesce(v.deleted_at, v_now), updated_at = v_now, sync_status = 'SYNCED'
  where v.tenant_id = p_tenant_id
    and coalesce(v.is_deleted, false) = false
    and not exists (select 1 from keep k where k.id = v.company_id);

  with keep(id) as (
    select value::uuid from jsonb_array_elements_text(p_company_ids)
  )
  update public.representatives r
  set is_deleted = true, deleted_at = coalesce(r.deleted_at, v_now), updated_at = v_now, sync_status = 'SYNCED'
  where r.tenant_id = p_tenant_id
    and coalesce(r.is_deleted, false) = false
    and not exists (select 1 from keep k where k.id = r.company_id);

  with keep(id) as (
    select value::uuid from jsonb_array_elements_text(p_company_ids)
  )
  update public.companies c
  set is_deleted = true, deleted_at = coalesce(c.deleted_at, v_now), updated_at = v_now, sync_status = 'SYNCED'
  where c.tenant_id = p_tenant_id
    and coalesce(c.is_deleted, false) = false
    and not exists (select 1 from keep k where k.id = c.id);
end;
$$;

grant execute on function public.borg_sync_companies(text, jsonb) to anon;
grant execute on function public.borg_sync_representatives(text, jsonb) to anon;
grant execute on function public.borg_sync_visits(text, jsonb) to anon;
grant execute on function public.borg_prune_tenant_to_companies(text, uuid, jsonb) to anon;

-- 3) One-time cloud cleanup for previous bad syncs: dedupe active companies by normalized name, keep newest.
with ranked_companies as (
  select c.id,
         row_number() over (
           partition by coalesce(c.tenant_id, '00000000-0000-0000-0000-000000000001'::uuid), public.web_normalize_company_name(c.name)
           order by c.updated_at desc, c.created_at desc, c.id
         ) rn
  from public.companies c
  where c.deleted_at is null and coalesce(c.is_deleted, false) = false
)
update public.companies c
set is_deleted = true,
    deleted_at = coalesce(c.deleted_at, (extract(epoch from now()) * 1000)::bigint),
    updated_at = (extract(epoch from now()) * 1000)::bigint,
    sync_status = 'SYNCED'
from ranked_companies r
where c.id = r.id and r.rn > 1;

update public.visits v
set is_deleted = true,
    deleted_at = coalesce(v.deleted_at, (extract(epoch from now()) * 1000)::bigint),
    updated_at = (extract(epoch from now()) * 1000)::bigint,
    sync_status = 'SYNCED'
where coalesce(v.is_deleted, false) = false
  and not exists (
    select 1 from public.companies c
    where c.id = v.company_id
      and c.deleted_at is null
      and coalesce(c.is_deleted, false) = false
  );

update public.representatives r
set is_deleted = true,
    deleted_at = coalesce(r.deleted_at, (extract(epoch from now()) * 1000)::bigint),
    updated_at = (extract(epoch from now()) * 1000)::bigint,
    sync_status = 'SYNCED'
where coalesce(r.is_deleted, false) = false
  and not exists (
    select 1 from public.companies c
    where c.id = r.company_id
      and c.deleted_at is null
      and coalesce(c.is_deleted, false) = false
  );

with ranked_visits as (
  select v.id,
         row_number() over (
           partition by v.tenant_id, v.company_id, v.cycle_start_epoch_day, v.week_of_cycle
           order by v.updated_at desc, v.created_at desc, v.id
         ) rn
  from public.visits v
  join public.companies c on c.id = v.company_id
  where v.deleted_at is null and coalesce(v.is_deleted, false) = false
    and c.deleted_at is null and coalesce(c.is_deleted, false) = false
)
update public.visits v
set is_deleted = true,
    deleted_at = coalesce(v.deleted_at, (extract(epoch from now()) * 1000)::bigint),
    updated_at = (extract(epoch from now()) * 1000)::bigint,
    sync_status = 'SYNCED'
from ranked_visits r
where v.id = r.id and r.rn > 1;

select
  (select count(*) from public.companies where deleted_at is null and coalesce(is_deleted, false) = false) active_companies,
  (select count(*) from public.visits v join public.companies c on c.id = v.company_id where v.deleted_at is null and coalesce(v.is_deleted, false) = false and c.deleted_at is null and coalesce(c.is_deleted, false) = false) active_valid_visits;

-- 4) Fix portal representatives: inherit tenant_id from selected company.
update public.representatives r
set tenant_id = c.tenant_id,
    updated_at = greatest(r.updated_at, (extract(epoch from now()) * 1000)::bigint),
    sync_status = 'SYNCED'
from public.companies c
where r.company_id = c.id
  and c.tenant_id is not null
  and (r.tenant_id is null or r.tenant_id is distinct from c.tenant_id);

drop function if exists public.register_representative_portal(text, text, uuid);

create function public.register_representative_portal(
  p_name text,
  p_phone text,
  p_company_id uuid
)
returns table (
  status text,
  representative_id uuid,
  rep_name text,
  phone text,
  company_id uuid,
  company_name text,
  message text
)
language plpgsql
security definer
set search_path = public
as $$
declare
  v_name text;
  v_phone text;
  v_company_name text;
  v_company_tenant_id uuid;
  v_existing record;
  v_new_id uuid;
  v_now bigint;
begin
  v_name := trim(coalesce(p_name, ''));
  v_phone := public.web_normalize_phone(p_phone);
  v_now := (extract(epoch from now()) * 1000)::bigint;

  if length(v_name) < 3 then
    return query select 'invalid_name'::text, null::uuid, v_name, v_phone, p_company_id, null::text, 'اسم المندوب غير مكتمل'::text;
    return;
  end if;

  if length(regexp_replace(v_phone, '\D', '', 'g')) < 10 then
    return query select 'invalid_phone'::text, null::uuid, v_name, v_phone, p_company_id, null::text, 'رقم الجوال غير صحيح'::text;
    return;
  end if;

  select c.name, coalesce(c.tenant_id, '00000000-0000-0000-0000-000000000001'::uuid)
  into v_company_name, v_company_tenant_id
  from public.companies c
  where c.id = p_company_id
    and c.deleted_at is null
    and coalesce(c.is_deleted, false) = false
  limit 1;

  if v_company_name is null then
    return query select 'company_not_found'::text, null::uuid, v_name, v_phone, p_company_id, null::text, 'الشركة غير موجودة أو محذوفة'::text;
    return;
  end if;

  select r.id, r.name, r.phone, c.id as c_id, c.name as c_name
  into v_existing
  from public.representatives r
  join public.companies c on c.id = r.company_id
  where r.deleted_at is null
    and c.deleted_at is null
    and coalesce(r.is_deleted, false) = false
    and coalesce(c.is_deleted, false) = false
    and public.web_normalize_phone(r.phone) = v_phone
  order by r.updated_at desc
  limit 1;

  if found then
    update public.representatives
    set tenant_id = v_company_tenant_id,
        sync_status = 'SYNCED'
    where id = v_existing.id and (tenant_id is null or tenant_id is distinct from v_company_tenant_id);
    return query select 'phone_exists'::text, v_existing.id, v_existing.name, public.web_normalize_phone(v_existing.phone), v_existing.c_id, v_existing.c_name, 'رقم الجوال مسجل مسبقاً'::text;
    return;
  end if;

  select r.id, r.name, r.phone, c.id as c_id, c.name as c_name
  into v_existing
  from public.representatives r
  join public.companies c on c.id = r.company_id
  where r.deleted_at is null
    and c.deleted_at is null
    and coalesce(r.is_deleted, false) = false
    and coalesce(c.is_deleted, false) = false
    and public.web_normalize_representative_name(r.name) = public.web_normalize_representative_name(v_name)
  order by r.updated_at desc
  limit 1;

  if found then
    update public.representatives
    set tenant_id = v_company_tenant_id,
        sync_status = 'SYNCED'
    where id = v_existing.id and (tenant_id is null or tenant_id is distinct from v_company_tenant_id);
    return query select 'name_exists'::text, v_existing.id, v_existing.name, public.web_normalize_phone(v_existing.phone), v_existing.c_id, v_existing.c_name, 'اسم المندوب مسجل مسبقاً'::text;
    return;
  end if;

  v_new_id := gen_random_uuid();

  insert into public.representatives (
    id, tenant_id, company_id, name, phone, created_at, updated_at, deleted_at, sync_status, is_deleted
  ) values (
    v_new_id, v_company_tenant_id, p_company_id, v_name, v_phone, v_now, v_now, null, 'SYNCED', false
  );

  return query select 'created'::text, v_new_id, v_name, v_phone, p_company_id, v_company_name, 'تم حفظ بيانات المندوب بنجاح'::text;
end;
$$;

grant execute on function public.register_representative_portal(text, text, uuid) to anon;

-- 5) Tenant-aware user pull; keeps backward compatibility with older app calls.
drop function if exists public.borg_pull_users(text);
drop function if exists public.borg_pull_users(text, uuid);

create function public.borg_pull_users(p_token text, p_tenant_id uuid default null)
returns table (
  id uuid,
  tenant_id uuid,
  username text,
  display_name text,
  role text,
  passcode_hash text,
  must_change_passcode boolean,
  active boolean,
  created_at bigint,
  updated_at bigint,
  sync_status text,
  is_deleted boolean
)
language plpgsql
security definer
set search_path = public
as $$
declare
  v_tenant uuid := coalesce(p_tenant_id, '00000000-0000-0000-0000-000000000001'::uuid);
begin
  if not public.borg_sync_token_valid(p_token) then
    raise exception 'unauthorized borg sync token' using errcode = '28000';
  end if;

  return query
  select u.id,
         coalesce(u.tenant_id, v_tenant) as tenant_id,
         u.username,
         u.display_name,
         u.role,
         u.passcode_hash,
         u.must_change_passcode,
         u.active,
         u.created_at,
         u.updated_at,
         coalesce(u.sync_status, 'SYNCED') as sync_status,
         coalesce(u.is_deleted, not u.active) as is_deleted
  from public.users u
  where coalesce(u.tenant_id, v_tenant) = v_tenant
  order by u.username;
end;
$$;

grant execute on function public.borg_pull_users(text, uuid) to anon;

commit;
