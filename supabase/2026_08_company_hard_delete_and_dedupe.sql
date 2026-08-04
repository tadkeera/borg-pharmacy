-- حذف الشركات نهائياً من Supabase + تنظيف التكرارات النشطة حسب الاسم المنظف.
-- الهدف: إذا حُذفت شركة من التطبيق لا تعود للظهور بعد المزامنة، ولا تبقى مكررات نشطة في قائمة الشركات.

begin;

create table if not exists public.companies_backup_before_hard_delete_dedupe_20260804 as table public.companies;
create table if not exists public.representatives_backup_before_hard_delete_dedupe_20260804 as table public.representatives;
create table if not exists public.visits_backup_before_hard_delete_dedupe_20260804 as table public.visits;
create table if not exists public.representative_portal_logs_backup_before_hard_delete_dedupe_20260804 as table public.representative_portal_logs;

create or replace function public.borg_delete_company_forever(
  p_token text,
  p_company_id uuid
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

  delete from public.representative_portal_logs
  where company_id = p_company_id
     or representative_id in (select id from public.representatives where company_id = p_company_id);

  delete from public.representatives where company_id = p_company_id;
  delete from public.visits where company_id = p_company_id;
  delete from public.companies where id = p_company_id;
end;
$$;

grant execute on function public.borg_delete_company_forever(text, uuid) to anon;

-- تنظيف المكررات الحالية مرة واحدة: نحتفظ بالصف الأكثر ارتباطاً بالمندوبين ثم الزيارات ثم الأحدث،
-- وننقل المندوبين والزيارات وسجلات الاستعلام من الصفوف المكررة إلى الصف المعتمد قبل حذفها.
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
           order by (active_rep_count > 0) desc,
                    active_rep_count desc,
                    (active_visit_count > 0) desc,
                    active_visit_count desc,
                    updated_at desc,
                    created_at desc,
                    id
         ) as canonical_id,
         count(*) over (partition by company_key) as duplicate_count
  from active_companies
  where company_key <> ''
), duplicate_map as (
  select id as old_id, canonical_id as new_id
  from ranked
  where duplicate_count > 1 and id <> canonical_id
), moved_logs as (
  update public.representative_portal_logs l
  set company_id = dm.new_id
  from duplicate_map dm
  where l.company_id = dm.old_id
  returning l.id
), moved_representatives as (
  update public.representatives r
  set company_id = dm.new_id,
      tenant_id = '00000000-0000-0000-0000-000000000001'::uuid,
      updated_at = greatest(r.updated_at, (extract(epoch from now()) * 1000)::bigint),
      sync_status = 'SYNCED'
  from duplicate_map dm
  where r.company_id = dm.old_id
  returning r.id
), moved_visits as (
  update public.visits v
  set company_id = dm.new_id,
      tenant_id = '00000000-0000-0000-0000-000000000001'::uuid,
      updated_at = greatest(v.updated_at, (extract(epoch from now()) * 1000)::bigint),
      sync_status = 'SYNCED'
  from duplicate_map dm
  where v.company_id = dm.old_id
  returning v.id
)
delete from public.companies c
using duplicate_map dm
where c.id = dm.old_id;

commit;

select json_build_object(
  'active_companies', (
    select count(*) from public.companies
    where tenant_id = '00000000-0000-0000-0000-000000000001'::uuid
      and deleted_at is null
      and coalesce(is_deleted, false) = false
  ),
  'duplicate_groups', (
    select count(*) from (
      select public.web_normalize_company_name(name) as key
      from public.companies
      where tenant_id = '00000000-0000-0000-0000-000000000001'::uuid
        and deleted_at is null
        and coalesce(is_deleted, false) = false
      group by public.web_normalize_company_name(name)
      having count(*) > 1
    ) d
  )
) as company_delete_dedupe_summary;
