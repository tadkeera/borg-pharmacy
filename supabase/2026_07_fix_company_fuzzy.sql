-- Fix find_company_fuzzy RPC used by older Termux bot scripts.
-- Root cause observed: similarity(text,text) does not exist because pg_trgm was not enabled.
-- This migration enables pg_trgm and creates a robust Arabic-normalized fuzzy search function.

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
  with q as (
    select public.normalize_arabic_company(search_term) as term
  ), c as (
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
    and (
      c.normalized_name % q.term
      or c.normalized_name like '%' || q.term || '%'
      or q.term like '%' || c.normalized_name || '%'
    )
  order by score desc, length(c.normalized_name) asc
  limit 5;
$$;
