-- Open capacity scheduling migration.
-- Run in Supabase SQL editor to remove the old per-shift slot_index <= 10 constraint.
-- The app keeps slot_index only as a legacy display/order hint; capacity is no longer limited.

alter table public.visits drop constraint if exists visits_slot_index_check;
