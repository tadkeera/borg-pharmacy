-- جعل جدول الزيارات في صفحة الاستعلامات ديناميكياً لكل دورة 28 يوم.
-- المواعيد الأسبوعية/الفترات ثابتة من basedayindex + baseshift، والتاريخ فقط يتغير مع بداية كل دورة.

begin;

create table if not exists public.schedules_view_backup_before_dynamic_cycle_20260725 as
select * from public.schedules;

create or replace view public.schedules as
with params as (
  select
    date '2026-07-04' as baseline_start,
    (now() at time zone 'Asia/Aden')::date as today
), cycle as (
  select
    baseline_start,
    today,
    (baseline_start + ((floor(((today - baseline_start)::numeric) / 28)::int * 28)))::date as cycle_start
  from params
), active_companies as (
  select
    c.id,
    c.name,
    c.basedayindex,
    c.baseshift
  from public.companies c
  where c.tenant_id = '00000000-0000-0000-0000-000000000001'::uuid
    and c.deleted_at is null
    and coalesce(c.is_deleted, false) = false
    and c.basedayindex between 0 and 4
    and c.baseshift in ('MORNING', 'EVENING')
), expanded as (
  select
    c.id as company_id,
    c.name as company_name,
    g.week_number,
    ((c.basedayindex + g.week_number - 1) % 5) as rotated_day_index,
    case
      when g.week_number in (2, 4) and c.baseshift = 'MORNING' then 'EVENING'
      when g.week_number in (2, 4) and c.baseshift = 'EVENING' then 'MORNING'
      else c.baseshift
    end as shift,
    cycle.cycle_start
  from active_companies c
  cross join cycle
  cross join generate_series(1, 4) as g(week_number)
), dated as (
  select
    e.*,
    (e.cycle_start + (((e.week_number - 1) * 7 + e.rotated_day_index))::int)::date as visit_date
  from expanded e
)
select
  d.company_id,
  d.company_name,
  d.week_number,
  case d.rotated_day_index
    when 0 then 'السبت'
    when 1 then 'الأحد'
    when 2 then 'الإثنين'
    when 3 then 'الثلاثاء'
    when 4 then 'الأربعاء'
    else 'غير محدد'
  end as day_name,
  d.visit_date::text as date,
  case d.shift
    when 'MORNING' then 'الفترة الصباحية'
    when 'EVENING' then 'الفترة المسائية'
    else d.shift
  end as shift_time,
  row_number() over (
    partition by d.week_number, d.rotated_day_index, d.shift
    order by d.company_name, d.company_id
  )::integer as slot_index
from dated d;

commit;

select json_build_object(
  'cycle_start', (select (date '2026-07-04' + ((floor((((now() at time zone 'Asia/Aden')::date - date '2026-07-04')::numeric) / 28)::int * 28)))::date),
  'schedule_rows', (select count(*) from public.schedules),
  'companies_in_schedule', (select count(distinct company_id) from public.schedules),
  'weeks', (select json_agg(distinct week_number order by week_number) from public.schedules)
) as dynamic_schedule_summary;
