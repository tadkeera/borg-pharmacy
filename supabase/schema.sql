-- Borg Pharmacy Supabase schema
-- Execute in Supabase SQL editor. The mobile app uses Room first and syncs dirty rows here.

create table if not exists public.companies (
  id uuid primary key,
  name text not null,
  tier text not null default 'UNRATED',
  base_day_index int,
  base_shift text,
  created_at bigint not null,
  updated_at bigint not null,
  deleted_at bigint
);

create table if not exists public.representatives (
  id uuid primary key,
  company_id uuid not null references public.companies(id) on delete cascade,
  name text not null,
  phone text not null default '+967',
  created_at bigint not null,
  updated_at bigint not null,
  deleted_at bigint
);

create table if not exists public.visits (
  id uuid primary key,
  company_id uuid not null references public.companies(id) on delete cascade,
  cycle_start_epoch_day bigint not null,
  day_of_cycle int not null check (day_of_cycle between 1 and 28),
  week_of_cycle int not null check (week_of_cycle between 1 and 4),
  date_epoch_day bigint not null,
  shift text not null check (shift in ('MORNING', 'EVENING')),
  slot_index int not null,
  status text not null default 'SCHEDULED',
  created_at bigint not null,
  updated_at bigint not null,
  deleted_at bigint
);

create index if not exists idx_companies_name on public.companies using btree (lower(name));
create index if not exists idx_representatives_company on public.representatives(company_id);
create index if not exists idx_visits_cycle_date on public.visits(cycle_start_epoch_day, date_epoch_day, shift, slot_index);

alter table public.companies enable row level security;
alter table public.representatives enable row level security;
alter table public.visits enable row level security;

-- For the provided anon key to work, keep policies limited to this application database only.
-- Tighten these when Supabase Auth is added.
do $$ begin
  create policy "borg_companies_read" on public.companies for select using (true);
exception when duplicate_object then null; end $$;
do $$ begin
  create policy "borg_companies_write" on public.companies for all using (true) with check (true);
exception when duplicate_object then null; end $$;
do $$ begin
  create policy "borg_reps_read" on public.representatives for select using (true);
exception when duplicate_object then null; end $$;
do $$ begin
  create policy "borg_reps_write" on public.representatives for all using (true) with check (true);
exception when duplicate_object then null; end $$;
do $$ begin
  create policy "borg_visits_read" on public.visits for select using (true);
exception when duplicate_object then null; end $$;
do $$ begin
  create policy "borg_visits_write" on public.visits for all using (true) with check (true);
exception when duplicate_object then null; end $$;

-- WhatsApp bot tables
create table if not exists public.bot_config (
  id text primary key default 'primary_bot',
  phone_number text not null default '967',
  is_active boolean not null default false,
  updated_at timestamptz not null default now()
);

create table if not exists public.bot_logs (
  id uuid primary key default gen_random_uuid(),
  sender_phone text not null,
  query_text text not null,
  matched_company text not null,
  created_at timestamptz not null default now()
);

-- Fuzzy company search support for WhatsApp bot
create extension if not exists pg_trgm;

create or replace function public.normalize_arabic_company(input text)
returns text
language sql
immutable
as $$
  select trim(regexp_replace(
    replace(replace(replace(replace(replace(replace(replace(replace(lower(coalesce(input,'')), 'أ','ا'), 'إ','ا'), 'آ','ا'), 'ٱ','ا'), 'ى','ي'), 'ئ','ي'), 'ؤ','و'), 'ة','ه'),
    '["''`´‘’“”\(\)\[\]\{\}،,\.:;؛!؟?\-_\/\\|]+',
    ' ',
    'g'
  ));
$$;

create or replace function public.find_company_fuzzy(search_term text)
returns table(id uuid, name text, score real)
language sql
stable
as $$
  with q as (select public.normalize_arabic_company(search_term) as term),
  c as (
    select companies.id, companies.name, public.normalize_arabic_company(companies.name) as normalized_name
    from public.companies
    where companies.deleted_at is null
  )
  select c.id, c.name,
    greatest(
      similarity(c.normalized_name, q.term),
      case when c.normalized_name like '%' || q.term || '%' then 0.95 else 0 end,
      case when q.term like '%' || c.normalized_name || '%' then 0.90 else 0 end
    )::real as score
  from c, q
  where q.term <> ''
    and (c.normalized_name % q.term or c.normalized_name like '%' || q.term || '%' or q.term like '%' || c.normalized_name || '%')
  order by score desc, length(c.normalized_name) asc
  limit 5;
$$;
