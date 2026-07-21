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

-- دالة تطبيع أسماء الشركات لاستخدامها في إزالة التكرار على مستوى قاعدة البيانات أيضاً.
create or replace function public.web_normalize_company_name(input text)
returns text
language sql
immutable
as $$
  select trim(regexp_replace(
    replace(replace(replace(replace(replace(replace(replace(replace(lower(coalesce(input,'')), 'أ','ا'), 'إ','ا'), 'آ','ا'), 'ٱ','ا'), 'ى','ي'), 'ئ','ي'), 'ؤ','و'), 'ة','ه'),
    '["''`´‘’“”\\(\\)\\[\\]\\{\\}،,\\.:;؛!؟?\\-_\\/\\\\|]+',
    ' ',
    'g'
  ));
$$;

-- View اختياري يعرض الشركات النشطة فقط بدون تكرار حسب الاسم بعد التطبيع.
-- يفيد أي صفحة ويب عامة حتى لا تظهر نفس الشركة أكثر من مرة بسبب سجلات محذوفة/قديمة أو تكرار نشط.
create or replace view public.representative_companies
with (security_invoker = true)
as
select
  public.web_normalize_company_name(c.name) as company_key,
  (array_agg(trim(both ' "' from c.name) order by c.updated_at desc))[1] as company_name,
  array_agg(c.id order by c.updated_at desc) as company_ids,
  count(*)::integer as active_row_count
from public.companies c
where c.deleted_at is null
  and public.web_normalize_company_name(c.name) <> ''
group by public.web_normalize_company_name(c.name);

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
grant select on table public.representative_companies to anon;
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

-- قراءة مزامنة التطبيق: يحتاج تطبيق Android إلى قراءة السجلات النشطة والمحذوفة ناعماً حتى تنتقل عمليات الحذف بين الأجهزة.
-- صفحة الويب ما زالت تعرض النشط فقط لأنها تستخدم deleted_at is null في الاستعلامات والـ views.
drop policy if exists "borg_app_companies_read_sync" on public.companies;
create policy "borg_app_companies_read_sync"
on public.companies
for select
to anon
using (true);

drop policy if exists "borg_app_reps_read_sync" on public.representatives;
create policy "borg_app_reps_read_sync"
on public.representatives
for select
to anon
using (true);

drop policy if exists "borg_app_visits_read_sync" on public.visits;
create policy "borg_app_visits_read_sync"
on public.visits
for select
to anon
using (true);

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


-- =========================================================
-- تسجيل مندوبي صفحة الاستعلامات العامة + سجل عمليات البحث
-- =========================================================
create extension if not exists pgcrypto;

create or replace function public.web_normalize_representative_name(input text)
returns text
language sql
immutable
as $$
  select public.web_normalize_company_name(input);
$$;

create or replace function public.web_normalize_phone(input text)
returns text
language plpgsql
immutable
as $$
declare
  digits text;
begin
  digits := regexp_replace(coalesce(input, ''), '\D', '', 'g');

  -- مفتاح اليمن المعتمد في التطبيق هو +967. إذا بدأ الرقم بـ 967 لا نكرره.
  if digits like '967%' then
    digits := substring(digits from 4);
  end if;

  -- إذا أدخل المستخدم الرقم بصفر محلي، نزيل الصفر ونضيف +967.
  if digits like '0%' then
    digits := substring(digits from 2);
  end if;

  if digits = '' then
    return '+967';
  end if;

  return '+967' || digits;
end;
$$;


-- فهارس تساعد على كشف التكرار بسرعة في صفحة الويب والتطبيق.
create index if not exists idx_representatives_active_phone_normalized
on public.representatives (public.web_normalize_phone(phone))
where deleted_at is null;

create index if not exists idx_representatives_active_name_normalized
on public.representatives (public.web_normalize_representative_name(name))
where deleted_at is null;

create table if not exists public.representative_portal_logs (
  id uuid primary key default gen_random_uuid(),
  representative_id uuid not null references public.representatives(id) on delete cascade,
  company_id uuid not null references public.companies(id) on delete cascade,
  search_text text not null default 'بحث جدول الزيارات',
  created_at timestamptz not null default now()
);

create index if not exists idx_representative_portal_logs_rep_created
on public.representative_portal_logs (representative_id, created_at desc);

create index if not exists idx_representative_portal_logs_company_created
on public.representative_portal_logs (company_id, created_at desc);

alter table public.representative_portal_logs enable row level security;

grant select on table public.representatives to anon;
grant select on table public.representative_portal_logs to anon;

-- لا نعطي anon صلاحية إدخال مباشرة على جدول المندوبين أو السجل؛ الإدخال فقط عبر دوال SECURITY DEFINER الآمنة.
revoke insert, update, delete on table public.representatives from anon;
revoke insert, update, delete on table public.representative_portal_logs from anon;

drop policy if exists "web_reps_select_readonly" on public.representatives;
create policy "web_reps_select_readonly"
on public.representatives
for select
to anon
using (deleted_at is null);

drop policy if exists "web_portal_logs_select_readonly" on public.representative_portal_logs;
create policy "web_portal_logs_select_readonly"
on public.representative_portal_logs
for select
to anon
using (true);

-- قفل الكتابة المباشرة من مفتاح anon على الجداول الأساسية.
-- تطبيق Android يكتب الآن عبر دوال RPC محددة ومحمية بتوكن مزامنة خاص، وليس عبر INSERT/UPDATE مباشر على الجداول.
revoke insert, update, delete on table public.companies from anon;
revoke insert, update, delete on table public.representatives from anon;
revoke insert, update, delete on table public.visits from anon;
revoke insert, update, delete on table public.representative_portal_logs from anon;

drop policy if exists "borg_companies_write" on public.companies;
drop policy if exists "borg_reps_write" on public.representatives;
drop policy if exists "borg_visits_write" on public.visits;

-- تحقق توكن مزامنة تطبيق Android.
-- لا يحتوي ملف SQL على التوكن الخام، بل SHA-256 فقط. التوكن الخام محفوظ كـ GitHub Secret ويُحقن داخل APK وقت الإصدار.
create or replace function public.borg_sync_token_valid(p_token text)
returns boolean
language sql
stable
as $$
  select encode(digest(coalesce(p_token, ''), 'sha256'), 'hex') = '642a1aa19257a2adbe59dc61b12e2c788f0c5f0c625876691822d7b16320bb44';
$$;

revoke execute on function public.borg_sync_token_valid(text) from public;

create or replace function public.borg_sync_companies(p_token text, p_rows jsonb)
returns void
language plpgsql
security definer
set search_path = public
as $$
declare
  item jsonb;
begin
  if not public.borg_sync_token_valid(p_token) then
    raise exception 'unauthorized borg sync token' using errcode = '28000';
  end if;

  if coalesce(jsonb_typeof(p_rows), '') <> 'array' then
    raise exception 'p_rows must be a json array';
  end if;

  for item in select value from jsonb_array_elements(p_rows) loop
    insert into public.companies (
      id, name, tier, base_day_index, base_shift, created_at, updated_at, deleted_at
    ) values (
      (item->>'id')::uuid,
      nullif(trim(item->>'name'), ''),
      coalesce(nullif(item->>'tier', ''), 'UNRATED'),
      case when item ? 'base_day_index' and item->>'base_day_index' is not null then (item->>'base_day_index')::int else null end,
      nullif(item->>'base_shift', ''),
      coalesce((item->>'created_at')::bigint, (extract(epoch from now()) * 1000)::bigint),
      coalesce((item->>'updated_at')::bigint, (extract(epoch from now()) * 1000)::bigint),
      case when item ? 'deleted_at' and item->>'deleted_at' is not null then (item->>'deleted_at')::bigint else null end
    )
    on conflict (id) do update set
      name = excluded.name,
      tier = excluded.tier,
      base_day_index = excluded.base_day_index,
      base_shift = excluded.base_shift,
      updated_at = excluded.updated_at,
      deleted_at = excluded.deleted_at
    where public.companies.updated_at <= excluded.updated_at
       or public.companies.deleted_at is distinct from excluded.deleted_at;
  end loop;
end;
$$;

create or replace function public.borg_sync_representatives(p_token text, p_rows jsonb)
returns void
language plpgsql
security definer
set search_path = public
as $$
declare
  item jsonb;
begin
  if not public.borg_sync_token_valid(p_token) then
    raise exception 'unauthorized borg sync token' using errcode = '28000';
  end if;

  if coalesce(jsonb_typeof(p_rows), '') <> 'array' then
    raise exception 'p_rows must be a json array';
  end if;

  for item in select value from jsonb_array_elements(p_rows) loop
    insert into public.representatives (
      id, company_id, name, phone, created_at, updated_at, deleted_at
    ) values (
      (item->>'id')::uuid,
      (item->>'company_id')::uuid,
      nullif(trim(item->>'name'), ''),
      public.web_normalize_phone(item->>'phone'),
      coalesce((item->>'created_at')::bigint, (extract(epoch from now()) * 1000)::bigint),
      coalesce((item->>'updated_at')::bigint, (extract(epoch from now()) * 1000)::bigint),
      case when item ? 'deleted_at' and item->>'deleted_at' is not null then (item->>'deleted_at')::bigint else null end
    )
    on conflict (id) do update set
      company_id = excluded.company_id,
      name = excluded.name,
      phone = excluded.phone,
      updated_at = excluded.updated_at,
      deleted_at = excluded.deleted_at
    where public.representatives.updated_at <= excluded.updated_at
       or public.representatives.deleted_at is distinct from excluded.deleted_at;
  end loop;
end;
$$;

create or replace function public.borg_sync_visits(p_token text, p_rows jsonb)
returns void
language plpgsql
security definer
set search_path = public
as $$
declare
  item jsonb;
begin
  if not public.borg_sync_token_valid(p_token) then
    raise exception 'unauthorized borg sync token' using errcode = '28000';
  end if;

  if coalesce(jsonb_typeof(p_rows), '') <> 'array' then
    raise exception 'p_rows must be a json array';
  end if;

  for item in select value from jsonb_array_elements(p_rows) loop
    insert into public.visits (
      id, company_id, cycle_start_epoch_day, day_of_cycle, week_of_cycle, date_epoch_day, shift, slot_index, status, created_at, updated_at, deleted_at
    ) values (
      (item->>'id')::uuid,
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
      case when item ? 'deleted_at' and item->>'deleted_at' is not null then (item->>'deleted_at')::bigint else null end
    )
    on conflict (id) do update set
      company_id = excluded.company_id,
      cycle_start_epoch_day = excluded.cycle_start_epoch_day,
      day_of_cycle = excluded.day_of_cycle,
      week_of_cycle = excluded.week_of_cycle,
      date_epoch_day = excluded.date_epoch_day,
      shift = excluded.shift,
      slot_index = excluded.slot_index,
      status = excluded.status,
      updated_at = excluded.updated_at,
      deleted_at = excluded.deleted_at
    where public.visits.updated_at <= excluded.updated_at
       or public.visits.deleted_at is distinct from excluded.deleted_at;
  end loop;
end;
$$;

grant execute on function public.borg_sync_companies(text, jsonb) to anon;
grant execute on function public.borg_sync_representatives(text, jsonb) to anon;
grant execute on function public.borg_sync_visits(text, jsonb) to anon;

create or replace view public.representative_portal_report
with (security_invoker = true)
as
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
where r.deleted_at is null
  and c.deleted_at is null
group by r.id, r.name, r.phone, c.id, c.name
order by max(l.created_at) desc;

grant select on table public.representative_portal_report to anon;

create or replace function public.register_representative_portal(
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

  select c.name into v_company_name
  from public.companies c
  where c.id = p_company_id
    and c.deleted_at is null
  limit 1;

  if v_company_name is null then
    return query select 'company_not_found'::text, null::uuid, v_name, v_phone, p_company_id, null::text, 'الشركة غير موجودة أو محذوفة'::text;
    return;
  end if;

  -- منع تكرار رقم الهاتف نهائياً لأكثر من مندوب نشط.
  select r.id, r.name, r.phone, c.id as c_id, c.name as c_name
  into v_existing
  from public.representatives r
  join public.companies c on c.id = r.company_id
  where r.deleted_at is null
    and c.deleted_at is null
    and public.web_normalize_phone(r.phone) = v_phone
  order by r.updated_at desc
  limit 1;

  if found then
    return query select 'phone_exists'::text, v_existing.id, v_existing.name, public.web_normalize_phone(v_existing.phone), v_existing.c_id, v_existing.c_name, 'رقم الجوال مسجل مسبقاً'::text;
    return;
  end if;

  -- منع تكرار اسم المندوب النشط.
  select r.id, r.name, r.phone, c.id as c_id, c.name as c_name
  into v_existing
  from public.representatives r
  join public.companies c on c.id = r.company_id
  where r.deleted_at is null
    and c.deleted_at is null
    and public.web_normalize_representative_name(r.name) = public.web_normalize_representative_name(v_name)
  order by r.updated_at desc
  limit 1;

  if found then
    return query select 'name_exists'::text, v_existing.id, v_existing.name, public.web_normalize_phone(v_existing.phone), v_existing.c_id, v_existing.c_name, 'اسم المندوب مسجل مسبقاً'::text;
    return;
  end if;

  v_new_id := gen_random_uuid();

  insert into public.representatives (
    id,
    company_id,
    name,
    phone,
    created_at,
    updated_at,
    deleted_at
  ) values (
    v_new_id,
    p_company_id,
    v_name,
    v_phone,
    v_now,
    v_now,
    null
  );

  return query select 'created'::text, v_new_id, v_name, v_phone, p_company_id, v_company_name, 'تم حفظ بيانات المندوب بنجاح'::text;
end;
$$;

create or replace function public.log_representative_portal_search(
  p_representative_id uuid,
  p_company_id uuid
)
returns table (
  search_count integer,
  created_at timestamptz
)
language plpgsql
security definer
set search_path = public
as $$
declare
  v_created_at timestamptz;
  v_count integer;
begin
  if not exists (
    select 1
    from public.representatives r
    where r.id = p_representative_id
      and r.company_id = p_company_id
      and r.deleted_at is null
  ) then
    return query select 0::integer, now();
    return;
  end if;

  insert into public.representative_portal_logs (representative_id, company_id)
  values (p_representative_id, p_company_id)
  returning representative_portal_logs.created_at into v_created_at;

  select count(*)::integer into v_count
  from public.representative_portal_logs
  where representative_id = p_representative_id
    and company_id = p_company_id;

  return query select v_count, v_created_at;
end;
$$;

grant execute on function public.register_representative_portal(text, text, uuid) to anon;
grant execute on function public.log_representative_portal_search(uuid, uuid) to anon;

commit;
