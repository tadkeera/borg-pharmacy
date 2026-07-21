-- Repair Borg Pharmacy visit distribution after legacy tenant/sync transition.
-- Run once in Supabase SQL Editor.
-- هدفه حذف الزيارات اليتيمة والمكررة من السحابة حتى لا ترجع للتطبيق مرة أخرى.

begin;

-- 1) توحيد أعمدة الحذف الناعم القديمة والجديدة.
update public.companies
set is_deleted = true,
    sync_status = 'SYNCED'
where deleted_at is not null
  and coalesce(is_deleted, false) = false;

update public.representatives
set is_deleted = true,
    sync_status = 'SYNCED'
where deleted_at is not null
  and coalesce(is_deleted, false) = false;

update public.visits
set is_deleted = true,
    sync_status = 'SYNCED'
where deleted_at is not null
  and coalesce(is_deleted, false) = false;

-- 2) حذف زيارات الشركات غير النشطة أو غير الموجودة.
update public.visits v
set is_deleted = true,
    deleted_at = coalesce(v.deleted_at, (extract(epoch from now()) * 1000)::bigint),
    updated_at = (extract(epoch from now()) * 1000)::bigint,
    sync_status = 'SYNCED'
where coalesce(v.is_deleted, false) = false
  and not exists (
    select 1
    from public.companies c
    where c.id = v.company_id
      and c.deleted_at is null
      and coalesce(c.is_deleted, false) = false
  );

-- 3) حذف مندوبي الشركات غير النشطة أو غير الموجودة.
update public.representatives r
set is_deleted = true,
    deleted_at = coalesce(r.deleted_at, (extract(epoch from now()) * 1000)::bigint),
    updated_at = (extract(epoch from now()) * 1000)::bigint,
    sync_status = 'SYNCED'
where coalesce(r.is_deleted, false) = false
  and not exists (
    select 1
    from public.companies c
    where c.id = r.company_id
      and c.deleted_at is null
      and coalesce(c.is_deleted, false) = false
  );

-- 4) داخل كل شركة وكل أسبوع، احتفظ بزيارة واحدة فقط واحذف التكرارات.
-- التطبيق v1.0.44 سيعيد بناء الزيارة الصحيحة Stable ID إن احتاج.
with ranked as (
  select
    v.id,
    row_number() over (
      partition by v.company_id, v.cycle_start_epoch_day, v.week_of_cycle
      order by v.updated_at desc, v.created_at desc, v.id
    ) as rn
  from public.visits v
  join public.companies c on c.id = v.company_id
  where v.deleted_at is null
    and coalesce(v.is_deleted, false) = false
    and c.deleted_at is null
    and coalesce(c.is_deleted, false) = false
)
update public.visits v
set is_deleted = true,
    deleted_at = coalesce(v.deleted_at, (extract(epoch from now()) * 1000)::bigint),
    updated_at = (extract(epoch from now()) * 1000)::bigint,
    sync_status = 'SYNCED'
from ranked r
where v.id = r.id
  and r.rn > 1;

-- 5) تقرير سريع بعد الإصلاح.
select
  (select count(*) from public.companies where deleted_at is null and coalesce(is_deleted, false) = false) as active_companies,
  (select count(*) from public.visits v join public.companies c on c.id = v.company_id where v.deleted_at is null and coalesce(v.is_deleted, false) = false and c.deleted_at is null and coalesce(c.is_deleted, false) = false) as active_valid_visits,
  (select count(*) from public.visits where deleted_at is null and coalesce(is_deleted, false) = false) as active_total_visits;

commit;
