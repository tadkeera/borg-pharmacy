-- نقل المندوبين والحذف النهائي من صفحة الاستعلامات/Supabase.
-- يحل جذرياً مشكلتين:
-- 1) نقل ارتباط المندوب إلى شركة نشطة أخرى مع تحديث سجلات بوابة الاستعلامات.
-- 2) حذف المندوب نهائياً بحيث يمكن إعادة تسجيل نفس الرقم لاحقاً دون رسالة "الرقم مسجل مسبقاً".

begin;

create table if not exists public.representatives_backup_before_transfer_delete_20260725 as table public.representatives;
create table if not exists public.representative_portal_logs_backup_before_transfer_delete_20260725 as table public.representative_portal_logs;

create or replace function public.get_representative_portal_profile(p_representative_id uuid)
returns table(
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
set search_path to 'public'
as $$
begin
  return query
  select
    'active'::text as status,
    r.id as representative_id,
    r.name as rep_name,
    public.web_normalize_phone(r.phone) as phone,
    c.id as company_id,
    c.name as company_name,
    'بيانات المندوب نشطة'::text as message
  from public.representatives r
  join public.companies c on c.id = r.company_id
  where r.id = p_representative_id
    and r.tenant_id = '00000000-0000-0000-0000-000000000001'::uuid
    and c.tenant_id = '00000000-0000-0000-0000-000000000001'::uuid
    and r.deleted_at is null
    and c.deleted_at is null
    and coalesce(r.is_deleted, false) = false
    and coalesce(c.is_deleted, false) = false
  limit 1;
end;
$$;

grant execute on function public.get_representative_portal_profile(uuid) to anon, authenticated;

create or replace function public.borg_move_representative(
  p_token text,
  p_representative_id uuid,
  p_target_company_id uuid
)
returns void
language plpgsql
security definer
set search_path to 'public'
as $$
declare
  v_now bigint := (extract(epoch from now()) * 1000)::bigint;
  v_target_company record;
  v_rep record;
begin
  if not public.borg_sync_token_valid(p_token) then
    raise exception 'unauthorized borg sync token' using errcode = '28000';
  end if;

  select * into v_target_company
  from public.companies c
  where c.id = p_target_company_id
    and c.tenant_id = '00000000-0000-0000-0000-000000000001'::uuid
    and c.deleted_at is null
    and coalesce(c.is_deleted, false) = false
  limit 1;

  if v_target_company.id is null then
    raise exception 'target company not found or inactive' using errcode = '23503';
  end if;

  select * into v_rep
  from public.representatives r
  where r.id = p_representative_id
    and r.deleted_at is null
    and coalesce(r.is_deleted, false) = false
  limit 1;

  if v_rep.id is null then
    raise exception 'representative not found or inactive' using errcode = '23503';
  end if;

  update public.representatives r
  set company_id = p_target_company_id,
      tenant_id = '00000000-0000-0000-0000-000000000001'::uuid,
      updated_at = v_now,
      deleted_at = null,
      is_deleted = false,
      sync_status = 'SYNCED'
  where r.id = p_representative_id;

  -- حتى تقارير وصفحة الاستعلامات تعكس الشركة الجديدة فوراً ولا يبقى أثر ارتباط قديم.
  update public.representative_portal_logs l
  set company_id = p_target_company_id
  where l.representative_id = p_representative_id;
end;
$$;

grant execute on function public.borg_move_representative(text, uuid, uuid) to anon;

create or replace function public.borg_delete_representative_forever(
  p_token text,
  p_representative_id uuid
)
returns void
language plpgsql
security definer
set search_path to 'public'
as $$
begin
  if not public.borg_sync_token_valid(p_token) then
    raise exception 'unauthorized borg sync token' using errcode = '28000';
  end if;

  -- حذف سجلات الاستعلام أولاً لضمان اختفاء المندوب من representative_portal_report فوراً.
  delete from public.representative_portal_logs
  where representative_id = p_representative_id;

  delete from public.representatives
  where id = p_representative_id;
end;
$$;

grant execute on function public.borg_delete_representative_forever(text, uuid) to anon;

create or replace function public.log_representative_portal_search(p_representative_id uuid, p_company_id uuid)
returns table(search_count integer, created_at timestamp with time zone)
language plpgsql
security definer
set search_path to 'public'
as $$
declare
  v_created_at timestamptz;
  v_count integer;
  v_current_company_id uuid;
begin
  select r.company_id into v_current_company_id
  from public.representatives r
  join public.companies c on c.id = r.company_id
  where r.id = p_representative_id
    and r.tenant_id = '00000000-0000-0000-0000-000000000001'::uuid
    and c.tenant_id = '00000000-0000-0000-0000-000000000001'::uuid
    and r.deleted_at is null
    and c.deleted_at is null
    and coalesce(r.is_deleted, false) = false
    and coalesce(c.is_deleted, false) = false
  limit 1;

  if v_current_company_id is null then
    raise exception 'representative not found or inactive' using errcode = '23503';
  end if;

  -- لا نثق بأي company_id مخزن قديماً في localStorage؛ نسجل البحث على الشركة الحالية للمندوب.
  insert into public.representative_portal_logs (representative_id, company_id)
  values (p_representative_id, v_current_company_id)
  returning representative_portal_logs.created_at into v_created_at;

  select count(*)::integer into v_count
  from public.representative_portal_logs
  where representative_id = p_representative_id
    and company_id = v_current_company_id;

  return query select v_count, v_created_at;
end;
$$;

grant execute on function public.log_representative_portal_search(uuid, uuid) to anon, authenticated;

create or replace function public.register_representative_portal(p_name text, p_phone text, p_company_id uuid)
returns table(status text, representative_id uuid, rep_name text, phone text, company_id uuid, company_name text, message text)
language plpgsql
security definer
set search_path to 'public'
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

  select c.name, c.tenant_id
  into v_company_name, v_company_tenant_id
  from public.companies c
  where c.id = p_company_id
    and c.tenant_id = '00000000-0000-0000-0000-000000000001'::uuid
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
  where r.tenant_id = v_company_tenant_id
    and c.tenant_id = v_company_tenant_id
    and r.deleted_at is null
    and c.deleted_at is null
    and coalesce(r.is_deleted, false) = false
    and coalesce(c.is_deleted, false) = false
    and public.web_normalize_phone(r.phone) = v_phone
  order by r.updated_at desc
  limit 1;

  if found then
    return query select 'phone_exists'::text, v_existing.id, v_existing.name, public.web_normalize_phone(v_existing.phone), v_existing.c_id, v_existing.c_name, 'رقم الجوال مسجل مسبقاً'::text;
    return;
  end if;

  select r.id, r.name, r.phone, c.id as c_id, c.name as c_name
  into v_existing
  from public.representatives r
  join public.companies c on c.id = r.company_id
  where r.tenant_id = v_company_tenant_id
    and c.tenant_id = v_company_tenant_id
    and r.deleted_at is null
    and c.deleted_at is null
    and coalesce(r.is_deleted, false) = false
    and coalesce(c.is_deleted, false) = false
    and public.web_normalize_representative_name(r.name) = public.web_normalize_representative_name(v_name)
  order by r.updated_at desc
  limit 1;

  if found then
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

grant execute on function public.register_representative_portal(text, text, uuid) to anon, authenticated;

commit;
