-- Orthogonal rotation immutable base-slot migration.
-- Run in Supabase SQL editor if you want cloud-side persistence of Week-1 locked coordinates.
-- The Android app also stores these fields locally in Room and can infer them from visits.

alter table public.companies add column if not exists base_day_index int;
alter table public.companies add column if not exists base_shift text;
