-- WhatsApp bot cloud tables for Borg Pharmacy.
-- Run in Supabase SQL Editor before using the bot screen from Android/Termux.

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

alter table public.bot_config enable row level security;
alter table public.bot_logs enable row level security;

do $$ begin
  create policy "borg_bot_config_read" on public.bot_config for select using (true);
exception when duplicate_object then null; end $$;
do $$ begin
  create policy "borg_bot_config_write" on public.bot_config for all using (true) with check (true);
exception when duplicate_object then null; end $$;
do $$ begin
  create policy "borg_bot_logs_read" on public.bot_logs for select using (true);
exception when duplicate_object then null; end $$;
do $$ begin
  create policy "borg_bot_logs_write" on public.bot_logs for all using (true) with check (true);
exception when duplicate_object then null; end $$;

insert into public.bot_config (id, phone_number, is_active)
values ('primary_bot', '967', false)
on conflict (id) do nothing;
