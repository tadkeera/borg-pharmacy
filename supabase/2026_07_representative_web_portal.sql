-- Representative Web Portal for Borg Pharmacy
-- صفحة استعلام مندوبي الأدوية: إعداد عرض schedules وصلاحيات قراءة فقط للويب.
--
-- طريقة الاستخدام:
-- 1) نفّذ هذا الملف من Supabase SQL Editor.
-- 2) بعدها يمكن لصفحة index.html العامة استخدام anon key للقراءة فقط.
--
-- تنبيه مهم:
-- إذا كان تطبيق Android الحالي يكتب إلى Supabase باستخدام anon key، فإن أوامر REVOKE/DROP للكتابة أدناه ستمنع هذه الكتابة.
-- هذا هو السلوك الأمني المطلوب لصفحة عامة. في الإنتاج الأفضل نقل عمليات الكتابة إلى Supabase Auth أو Backend آمن/service role.

begin;

-- تفعيل RLS على الجداول التي تحتاجها الصفحة.
alter table if exists public.companies enable row level security;
alter table if exists public.visits enable row level security;
alter table if exists public.representatives enable row level security;

-- لأن مفتاح anon ظاهر داخل صفحة ويب عامة، يجب أيضاً إغلاق أي سياسات كتابة عامة موجودة على جداول البوت إن كانت منشأة.
do $$
begin
  if to_regclass('public.bot_config') is not null then
    execute 'alter table public.bot_config enable row level security';
  end if;
  if to_regclass('public.bot_logs') is not null then
    execute 'alter table public.bot_logs enable row level security';
  end if;
end $$;

-- إنشاء View باسم schedules بنفس أسماء الأعمدة المطلوبة في صفحة الويب.
-- يعتمد على جدول visits الحالي في تطبيق Borg Pharmacy ويحوّل البيانات إلى شكل مناسب للعرض.
create or replace view public.schedules
with (security_invoker = true)
as
select
  v.company_id,
  c.name as company_name,
  v.week_of_cycle as week_number,
  case extract(isodow from (date '1970-01-01' + v.date_epoch_day::integer))::integer
    when 1 then 'الإثنين'
    when 2 then 'الثلاثاء'
    when 3 then 'الأربعاء'
    when 4 then 'الخميس'
    when 5 then 'الجمعة'
    when 6 then 'السبت'
    when 7 then 'الأحد'
    else 'غير محدد'
  end as day_name,
  (date '1970-01-01' + v.date_epoch_day::integer)::text as date,
  case v.shift
    when 'MORNING' then 'الفترة الصباحية'
    when 'EVENING' then 'الفترة المسائية'
    else v.shift
  end as shift_time,
  v.slot_index
from public.visits v
join public.companies c on c.id = v.company_id
where v.deleted_at is null
  and c.deleted_at is null;

-- صلاحيات قراءة فقط للـ anon/public web client.
grant usage on schema public to anon;
grant select on table public.companies to anon;
grant select on table public.visits to anon;
grant select on table public.schedules to anon;

-- منع insert/update/delete من anon على جداول بيانات التطبيق الأساسية المستخدمة في صفحة الويب.
revoke insert, update, delete on table public.companies from anon;
revoke insert, update, delete on table public.visits from anon;
revoke insert, update, delete on table public.representatives from anon;
revoke insert, update, delete on table public.schedules from anon;

-- إغلاق كتابة anon على جداول البوت إن وجدت، لأن نفس مفتاح anon المنشور في الويب يستطيع الوصول لكل صلاحيات anon بالمشروع.
do $$
begin
  if to_regclass('public.bot_config') is not null then
    execute 'revoke insert, update, delete on table public.bot_config from anon';
  end if;
  if to_regclass('public.bot_logs') is not null then
    execute 'revoke insert, update, delete on table public.bot_logs from anon';
  end if;
end $$;

-- إزالة سياسات الكتابة العامة القديمة إن كانت موجودة، لأنها تسمح لأي حامل anon key بالتعديل.
drop policy if exists "borg_companies_write" on public.companies;
drop policy if exists "borg_visits_write" on public.visits;
drop policy if exists "borg_reps_write" on public.representatives;

do $$
begin
  if to_regclass('public.bot_config') is not null then
    execute 'drop policy if exists "borg_bot_config_write" on public.bot_config';
  end if;
  if to_regclass('public.bot_logs') is not null then
    execute 'drop policy if exists "borg_bot_logs_write" on public.bot_logs';
  end if;
end $$;

-- سياسات القراءة للصفحة العامة.
drop policy if exists "web_companies_select_readonly" on public.companies;
create policy "web_companies_select_readonly"
on public.companies
for select
to anon
using (deleted_at is null);

drop policy if exists "web_visits_select_readonly" on public.visits;
create policy "web_visits_select_readonly"
on public.visits
for select
to anon
using (deleted_at is null);

-- إذا تم تفعيل Supabase Auth لاحقاً لتطبيق الإدارة، يمكن للمستخدمين authenticated الكتابة.
-- هذه السياسات لا تعطي صلاحية للزوار anon، بل فقط لحسابات authenticated.
drop policy if exists "borg_companies_authenticated_write" on public.companies;
create policy "borg_companies_authenticated_write"
on public.companies
for all
to authenticated
using (true)
with check (true);

drop policy if exists "borg_visits_authenticated_write" on public.visits;
create policy "borg_visits_authenticated_write"
on public.visits
for all
to authenticated
using (true)
with check (true);

drop policy if exists "borg_reps_authenticated_write" on public.representatives;
create policy "borg_reps_authenticated_write"
on public.representatives
for all
to authenticated
using (true)
with check (true);

commit;
