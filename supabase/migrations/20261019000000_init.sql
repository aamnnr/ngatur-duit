-- Duitku: skema awal cloud.
-- Tabel keuangan meniru Room di aplikasi (id teks UUID dari HP, nominal bigint rupiah,
-- tanggal epoch millis) supaya sinkron cukup salin baris apa adanya.
-- Setiap baris punya user_id dan dilindungi Row Level Security.

-- ---------------------------------------------------------------------------
-- Utilitas
-- ---------------------------------------------------------------------------
create or replace function public.touch_updated_at()
returns trigger
language plpgsql
as $$
begin
  new.updated_at := now();
  return new;
end;
$$;

-- ---------------------------------------------------------------------------
-- Data keuangan (disinkronkan dari/ke aplikasi)
-- ---------------------------------------------------------------------------
create table public.fin_profiles (
  id          text primary key,
  user_id     uuid not null default auth.uid() references auth.users (id) on delete cascade,
  name        text not null,
  type        text not null check (type in ('PERSONAL', 'BUSINESS')),
  created_at  bigint not null,
  is_deleted  boolean not null default false,
  updated_at  timestamptz not null default now()
);

create table public.wallets (
  id          text primary key,
  user_id     uuid not null default auth.uid() references auth.users (id) on delete cascade,
  profile_id  text not null references public.fin_profiles (id) on delete cascade,
  name        text not null,
  icon        text not null,
  color       bigint,
  is_default  boolean not null default false,
  sort_order  integer not null default 0,
  created_at  bigint not null,
  is_deleted  boolean not null default false,
  updated_at  timestamptz not null default now()
);

create table public.categories (
  id          text primary key,
  user_id     uuid not null default auth.uid() references auth.users (id) on delete cascade,
  profile_id  text not null references public.fin_profiles (id) on delete cascade,
  name        text not null,
  type        text not null check (type in ('INCOME', 'EXPENSE')),
  icon        text not null,
  color       bigint,
  is_default  boolean not null default false,
  is_deleted  boolean not null default false,
  updated_at  timestamptz not null default now()
);

create table public.transactions (
  id                  text primary key,
  user_id             uuid not null default auth.uid() references auth.users (id) on delete cascade,
  profile_id          text not null references public.fin_profiles (id) on delete cascade,
  type                text not null check (type in ('INCOME', 'EXPENSE')),
  amount              bigint not null check (amount > 0),
  category_id         text,
  wallet_id           text,
  note                text,
  transaction_date    bigint not null,
  created_at          bigint not null,
  is_adjustment       boolean not null default false,
  linked_transfer_id  text,
  transfer_type       text check (transfer_type in ('OUT', 'IN')),
  is_deleted          boolean not null default false,
  updated_at          timestamptz not null default now()
);

create index fin_profiles_sync_idx on public.fin_profiles (user_id, updated_at);
create index wallets_sync_idx      on public.wallets (user_id, updated_at);
create index categories_sync_idx   on public.categories (user_id, updated_at);
create index transactions_sync_idx on public.transactions (user_id, updated_at);
create index transactions_date_idx on public.transactions (user_id, profile_id, transaction_date);

create trigger fin_profiles_touch before update on public.fin_profiles for each row execute function public.touch_updated_at();
create trigger wallets_touch      before update on public.wallets      for each row execute function public.touch_updated_at();
create trigger categories_touch   before update on public.categories   for each row execute function public.touch_updated_at();
create trigger transactions_touch before update on public.transactions for each row execute function public.touch_updated_at();

-- ---------------------------------------------------------------------------
-- Inbox draf: transaksi dari bot Telegram (dan nanti sumber lain) yang
-- menunggu disetujui di aplikasi. Hanya Edge Function (service role) yang menulis.
-- ---------------------------------------------------------------------------
create table public.inbox_drafts (
  id           uuid primary key default gen_random_uuid(),
  user_id      uuid not null references auth.users (id) on delete cascade,
  source       text not null check (source in ('telegram', 'notification', 'assistant')),
  raw_text     text,
  -- {type, amount, category_id, wallet_id, note, date, confidence}
  parsed       jsonb not null,
  status       text not null default 'PENDING' check (status in ('PENDING', 'IMPORTED', 'DISCARDED')),
  created_at   timestamptz not null default now(),
  updated_at   timestamptz not null default now()
);

create index inbox_drafts_user_idx on public.inbox_drafts (user_id, status, created_at);
create trigger inbox_drafts_touch before update on public.inbox_drafts for each row execute function public.touch_updated_at();

-- ---------------------------------------------------------------------------
-- Telegram
-- ---------------------------------------------------------------------------
create table public.telegram_links (
  user_id     uuid primary key references auth.users (id) on delete cascade,
  chat_id     bigint not null unique,
  username    text,
  -- Profil keuangan tujuan draf dari bot (null = profil pertama).
  profile_id  text references public.fin_profiles (id) on delete set null,
  linked_at   timestamptz not null default now()
);

create table public.telegram_pairing_codes (
  code        text primary key,
  user_id     uuid not null references auth.users (id) on delete cascade,
  profile_id  text references public.fin_profiles (id) on delete cascade,
  expires_at  timestamptz not null
);

-- ---------------------------------------------------------------------------
-- Pemakaian AI per hari: batas pribadi agar kuota free tier Gemini tidak jebol.
-- ---------------------------------------------------------------------------
create table public.ai_usage (
  user_id  uuid not null references auth.users (id) on delete cascade,
  day      date not null,
  count    integer not null default 0,
  primary key (user_id, day)
);

-- ---------------------------------------------------------------------------
-- Row Level Security
-- ---------------------------------------------------------------------------
alter table public.fin_profiles           enable row level security;
alter table public.wallets                enable row level security;
alter table public.categories             enable row level security;
alter table public.transactions           enable row level security;
alter table public.inbox_drafts           enable row level security;
alter table public.telegram_links         enable row level security;
alter table public.telegram_pairing_codes enable row level security;
alter table public.ai_usage               enable row level security;

-- Data keuangan: pemilik boleh semua operasi atas barisnya sendiri.
create policy "own rows" on public.fin_profiles for all to authenticated
  using (user_id = (select auth.uid())) with check (user_id = (select auth.uid()));
create policy "own rows" on public.wallets for all to authenticated
  using (user_id = (select auth.uid())) with check (user_id = (select auth.uid()));
create policy "own rows" on public.categories for all to authenticated
  using (user_id = (select auth.uid())) with check (user_id = (select auth.uid()));
create policy "own rows" on public.transactions for all to authenticated
  using (user_id = (select auth.uid())) with check (user_id = (select auth.uid()));

-- Inbox: pemilik boleh membaca, menandai (IMPORTED/DISCARDED), dan menghapus; tidak boleh membuat.
create policy "read own drafts" on public.inbox_drafts for select to authenticated
  using (user_id = (select auth.uid()));
create policy "update own drafts" on public.inbox_drafts for update to authenticated
  using (user_id = (select auth.uid())) with check (user_id = (select auth.uid()));
create policy "delete own drafts" on public.inbox_drafts for delete to authenticated
  using (user_id = (select auth.uid()));

-- Telegram: pemilik boleh melihat dan memutus tautannya sendiri.
create policy "read own link" on public.telegram_links for select to authenticated
  using (user_id = (select auth.uid()));
create policy "delete own link" on public.telegram_links for delete to authenticated
  using (user_id = (select auth.uid()));

-- Kode pairing & pemakaian AI: tidak ada akses langsung dari aplikasi (lewat fungsi di bawah).
create policy "read own usage" on public.ai_usage for select to authenticated
  using (user_id = (select auth.uid()));

-- ---------------------------------------------------------------------------
-- Fungsi (RPC)
-- ---------------------------------------------------------------------------

-- Membuat kode pairing Telegram 6 digit yang berlaku 10 menit.
create or replace function public.create_telegram_pairing_code(p_profile_id text default null)
returns text
language plpgsql
security definer
set search_path = public
as $$
declare
  v_user uuid := auth.uid();
  v_code text;
begin
  if v_user is null then
    raise exception 'not authenticated';
  end if;
  delete from telegram_pairing_codes where user_id = v_user or expires_at < now();
  loop
    v_code := lpad((floor(random() * 1000000))::int::text, 6, '0');
    exit when not exists (select 1 from telegram_pairing_codes where code = v_code);
  end loop;
  insert into telegram_pairing_codes (code, user_id, profile_id, expires_at)
  values (v_code, v_user, p_profile_id, now() + interval '10 minutes');
  return v_code;
end;
$$;

revoke all on function public.create_telegram_pairing_code(text) from public, anon;
grant execute on function public.create_telegram_pairing_code(text) to authenticated;

-- Menambah hitungan pemakaian AI hari ini (zona Asia/Jakarta) dan menolak bila melebihi batas.
-- Hanya dipanggil Edge Function dengan service role.
create or replace function public.bump_ai_usage(p_user uuid, p_limit integer)
returns integer
language plpgsql
security definer
set search_path = public
as $$
declare
  v_day date := (now() at time zone 'Asia/Jakarta')::date;
  v_count integer;
begin
  insert into ai_usage (user_id, day, count) values (p_user, v_day, 1)
  on conflict (user_id, day) do update set count = ai_usage.count + 1
  returning count into v_count;
  if v_count > p_limit then
    raise exception 'ai_daily_limit_reached' using errcode = 'P0001';
  end if;
  return v_count;
end;
$$;

revoke all on function public.bump_ai_usage(uuid, integer) from public, anon, authenticated;
grant execute on function public.bump_ai_usage(uuid, integer) to service_role;
