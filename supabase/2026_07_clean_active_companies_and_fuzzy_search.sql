-- تنظيف الشركات النشطة وتفعيل البحث الذكي في بوابة الاستعلامات.
-- الهدف:
-- 1) تثبيت مصدر واحد للشركات النشطة التابعة لتانت التطبيق فقط.
-- 2) حذف/إزالة الصفوف الخاملة أو المحذوفة بعد إنشاء نسخ احتياطية.
-- 3) دمج الشركات المكررة حسب الاسم العربي المنظّف، مع نقل الزيارات/المندوبين/السجلات إلى الصف المعتمد.
-- 4) توفير RPC للبحث المرن الذي يتجاهل الهمزات، التشكيل، المد، الفواصل والرموز.

begin;

-- نسخ احتياطية قبل أي تنظيف فعلي. تبقى الجداول للرجوع لها عند الحاجة.
create table if not exists public.companies_backup_before_cleanup_20260725 as table public.companies;
create table if not exists public.representatives_backup_before_cleanup_20260725 as table public.representatives;
create table if not exists public.visits_backup_before_cleanup_20260725 as table public.visits;
create table if not exists public.representative_portal_logs_backup_before_cleanup_20260725 as table public.representative_portal_logs;

-- توحيد النص العربي للبحث والمقارنة.
create or replace function public.web_normalize_company_name(input text)
returns text
language sql
immutable
as $$
  select trim(
    regexp_replace(
      regexp_replace(
        regexp_replace(
          replace(
            replace(
              replace(
                replace(
                  replace(
                    replace(
                      replace(
                        replace(
                          lower(coalesce(input, '')),
                          'أ', 'ا'
                        ),
                        'إ', 'ا'
                      ),
                      'آ', 'ا'
                    ),
                    'ٱ', 'ا'
                  ),
                  'ى', 'ي'
                ),
                'ئ', 'ي'
              ),
              'ؤ', 'و'
            ),
            'ة', 'ه'
          ),
          '[ًٌٍَُِّْٰـ]',
          '',
          'g'
        ),
        '["''`´‘’“”\(\)\[\]\{\}،,\.:;؛!؟?\-_\/\\|]+',
        ' ',
        'g'
      ),
      '\s+',
      ' ',
      'g'
    )
  );
$$;

create or replace function public.normalize_arabic_company(input text)
returns text
language sql
immutable
as $$
  select public.web_normalize_company_name(input);
$$;

-- RPC تستخدمه صفحة الويب للبحث المرن داخل الشركات النشطة فقط.
create or replace function public.search_active_companies_portal(
  p_term text,
  p_limit integer default 50
)
returns table(
  id uuid,
  name text,
  deleted_at bigint,
  is_deleted boolean,
  tenant_id uuid,
  updated_at bigint
)
language plpgsql
security definer
set search_path to 'public'
as $$
declare
  v_term text := public.web_normalize_company_name(p_term);
  v_limit integer := least(greatest(coalesce(p_limit, 50), 1), 100);
begin
  if length(coalesce(v_term, '')) < 1 then
    return;
  end if;

  return query
  select c.id, c.name, c.deleted_at, c.is_deleted, c.tenant_id, c.updated_at
  from public.companies c
  where c.tenant_id = '00000000-0000-0000-0000-000000000001'::uuid
    and c.deleted_at is null
    and coalesce(c.is_deleted, false) = false
    and (
      public.web_normalize_company_name(c.name) like '%' || v_term || '%'
      or v_term like '%' || public.web_normalize_company_name(c.name) || '%'
    )
  order by
    case
      when public.web_normalize_company_name(c.name) = v_term then 0
      when public.web_normalize_company_name(c.name) like v_term || '%' then 1
      else 2
    end,
    c.name asc
  limit v_limit;
end;
$$;

grant execute on function public.search_active_companies_portal(text, integer) to anon, authenticated;

-- إصلاح دالة مزامنة الشركات لتطابق أعمدة قاعدة البيانات الحالية: basedayindex / baseshift.
-- تقبل الدالة الصيغتين في JSON لضمان التوافق مع أي APK سابق أو لاحق.
create or replace function public.borg_sync_companies(p_token text, p_rows jsonb)
returns void
language plpgsql
security definer
set search_path to 'public'
as $$
declare
  item jsonb;
  v_deleted boolean;
  v_tenant uuid;
  v_base_day_index integer;
  v_base_shift text;
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
    v_base_day_index := case
      when item ? 'basedayindex' and item->>'basedayindex' is not null then (item->>'basedayindex')::int
      when item ? 'base_day_index' and item->>'base_day_index' is not null then (item->>'base_day_index')::int
      else null
    end;
    v_base_shift := coalesce(nullif(item->>'baseshift', ''), nullif(item->>'base_shift', ''));

    insert into public.companies (
      id, tenant_id, name, tier, basedayindex, baseshift, created_at, updated_at, deleted_at, sync_status, is_deleted
    ) values (
      (item->>'id')::uuid,
      v_tenant,
      nullif(trim(item->>'name'), ''),
      coalesce(nullif(item->>'tier', ''), 'UNRATED'),
      v_base_day_index,
      v_base_shift,
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
      basedayindex = excluded.basedayindex,
      baseshift = excluded.baseshift,
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

grant execute on function public.borg_sync_companies(text, jsonb) to anon;

-- إعادة تعريف الواجهات لتستخدم الشركات/المندوبين/الزيارات النشطة فقط ونفس Tenant التطبيق.
create or replace view public.representative_companies as
select
  public.web_normalize_company_name(c.name) as company_key,
  (array_agg(trim(both ' "' from c.name) order by c.updated_at desc))[1] as company_name,
  array_agg(c.id order by c.updated_at desc) as company_ids,
  count(*)::integer as active_row_count
from public.companies c
where c.tenant_id = '00000000-0000-0000-0000-000000000001'::uuid
  and c.deleted_at is null
  and coalesce(c.is_deleted, false) = false
  and public.web_normalize_company_name(c.name) <> ''
group by public.web_normalize_company_name(c.name);

create or replace view public.representative_portal_report as
select
  r.id as representative_id,
  r.name as representative_name,
  r.phone as representative_phone,
  c.id as company_id,
  c.name as company_name,
  count(l.id)::integer as search_count,
  min(l.created_at) as first_search_at,
  max(l.created_at) as last_search_at
from public.representative_portal_logs l
join public.representatives r on r.id = l.representative_id
join public.companies c on c.id = l.company_id
where r.tenant_id = '00000000-0000-0000-0000-000000000001'::uuid
  and c.tenant_id = '00000000-0000-0000-0000-000000000001'::uuid
  and r.deleted_at is null
  and c.deleted_at is null
  and coalesce(r.is_deleted, false) = false
  and coalesce(c.is_deleted, false) = false
group by r.id, r.name, r.phone, c.id, c.name
order by max(l.created_at) desc;

create or replace view public.schedules as
select
  v.company_id,
  c.name as company_name,
  v.week_of_cycle as week_number,
  case extract(isodow from ('1970-01-01'::date + v.date_epoch_day::integer))::integer
    when 1 then 'الإثنين'
    when 2 then 'الثلاثاء'
    when 3 then 'الأربعاء'
    when 4 then 'الخميس'
    when 5 then 'الجمعة'
    when 6 then 'السبت'
    when 7 then 'الأحد'
    else 'غير محدد'
  end as day_name,
  ('1970-01-01'::date + v.date_epoch_day::integer)::text as date,
  case v.shift
    when 'MORNING' then 'الفترة الصباحية'
    when 'EVENING' then 'الفترة المسائية'
    else v.shift
  end as shift_time,
  v.slot_index
from public.visits v
join public.companies c on c.id = v.company_id
where v.tenant_id = '00000000-0000-0000-0000-000000000001'::uuid
  and c.tenant_id = '00000000-0000-0000-0000-000000000001'::uuid
  and v.deleted_at is null
  and c.deleted_at is null
  and coalesce(v.is_deleted, false) = false
  and coalesce(c.is_deleted, false) = false;

-- اختيار الصف المعتمد لكل مجموعة شركات مكررة:
-- الأولوية: صف عليه مندوب نشط، ثم صف عليه زيارات نشطة، ثم الأحدث تحديثاً.
with active_companies as (
  select
    c.*,
    public.web_normalize_company_name(c.name) as company_key,
    (select count(*) from public.representatives r where r.company_id = c.id and r.deleted_at is null and coalesce(r.is_deleted, false) = false) as active_rep_count,
    (select count(*) from public.visits v where v.company_id = c.id and v.deleted_at is null and coalesce(v.is_deleted, false) = false) as active_visit_count
  from public.companies c
  where c.tenant_id = '00000000-0000-0000-0000-000000000001'::uuid
    and c.deleted_at is null
    and coalesce(c.is_deleted, false) = false
), ranked as (
  select *,
         first_value(id) over (
           partition by company_key
           order by (active_rep_count > 0) desc, active_rep_count desc,
                    (active_visit_count > 0) desc, active_visit_count desc,
                    updated_at desc, created_at desc, id
         ) as canonical_id,
         count(*) over (partition by company_key) as duplicate_count
  from active_companies
  where company_key <> ''
), duplicate_map as (
  select id as old_id, canonical_id as new_id
  from ranked
  where duplicate_count > 1 and id <> canonical_id
), carry_company_fields as (
  update public.companies canonical
  set basedayindex = coalesce(canonical.basedayindex, old_company.basedayindex),
      baseshift = coalesce(canonical.baseshift, old_company.baseshift),
      updated_at = greatest(canonical.updated_at, old_company.updated_at),
      sync_status = 'SYNCED'
  from public.companies old_company
  join duplicate_map dm on dm.old_id = old_company.id
  where canonical.id = dm.new_id
  returning canonical.id
), moved_representatives as (
  update public.representatives r
  set company_id = dm.new_id,
      tenant_id = '00000000-0000-0000-0000-000000000001'::uuid,
      updated_at = (extract(epoch from now()) * 1000)::bigint,
      sync_status = 'SYNCED'
  from duplicate_map dm
  where r.company_id = dm.old_id
  returning r.id
), moved_visits as (
  update public.visits v
  set company_id = dm.new_id,
      tenant_id = '00000000-0000-0000-0000-000000000001'::uuid,
      updated_at = (extract(epoch from now()) * 1000)::bigint,
      sync_status = 'SYNCED'
  from duplicate_map dm
  where v.company_id = dm.old_id
  returning v.id
), moved_logs as (
  update public.representative_portal_logs l
  set company_id = dm.new_id
  from duplicate_map dm
  where l.company_id = dm.old_id
  returning l.id
)
delete from public.companies c
using duplicate_map dm
where c.id = dm.old_id;

-- إزالة الصفوف المحذوفة/الخاملة فعلياً بعد حفظ النسخ الاحتياطية.
delete from public.representatives
where tenant_id = '00000000-0000-0000-0000-000000000001'::uuid
  and (deleted_at is not null or coalesce(is_deleted, false) = true);

delete from public.visits
where tenant_id = '00000000-0000-0000-0000-000000000001'::uuid
  and (deleted_at is not null or coalesce(is_deleted, false) = true);

delete from public.companies
where tenant_id is distinct from '00000000-0000-0000-0000-000000000001'::uuid
   or deleted_at is not null
   or coalesce(is_deleted, false) = true;

-- ضمان أن كل الصفوف الباقية نشطة ومطابقة لتانت التطبيق.
update public.companies
set tenant_id = '00000000-0000-0000-0000-000000000001'::uuid,
    deleted_at = null,
    is_deleted = false,
    sync_status = 'SYNCED'
where tenant_id = '00000000-0000-0000-0000-000000000001'::uuid;

update public.representatives r
set tenant_id = c.tenant_id,
    deleted_at = null,
    is_deleted = false,
    sync_status = 'SYNCED'
from public.companies c
where r.company_id = c.id
  and c.tenant_id = '00000000-0000-0000-0000-000000000001'::uuid;

update public.visits v
set tenant_id = c.tenant_id,
    deleted_at = null,
    is_deleted = false,
    sync_status = 'SYNCED'
from public.companies c
where v.company_id = c.id
  and c.tenant_id = '00000000-0000-0000-0000-000000000001'::uuid;

commit;

-- ملخص تحقق سريع بعد التنفيذ.
select json_build_object(
  'active_companies', (select count(*) from public.companies where tenant_id = '00000000-0000-0000-0000-000000000001'::uuid and deleted_at is null and coalesce(is_deleted, false) = false),
  'active_representatives', (select count(*) from public.representatives where tenant_id = '00000000-0000-0000-0000-000000000001'::uuid and deleted_at is null and coalesce(is_deleted, false) = false),
  'active_visits', (select count(*) from public.visits where tenant_id = '00000000-0000-0000-0000-000000000001'::uuid and deleted_at is null and coalesce(is_deleted, false) = false),
  'duplicate_company_groups', (
    select count(*) from (
      select public.web_normalize_company_name(name) as key
      from public.companies
      where tenant_id = '00000000-0000-0000-0000-000000000001'::uuid and deleted_at is null and coalesce(is_deleted, false) = false
      group by public.web_normalize_company_name(name)
      having count(*) > 1
    ) d
  ),
  'associated_medical_rows', (
    select coalesce(json_agg(json_build_object('id', id, 'name', name)), '[]'::json)
    from public.companies
    where public.web_normalize_company_name(name) like '%اوسشيتيد مديكال%'
       or public.web_normalize_company_name(name) like '%اسوشيتد مديكال%'
  )
) as cleanup_summary;
