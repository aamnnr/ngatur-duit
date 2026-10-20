-- Fase 2: anggaran dan target tabungan, disinkronkan dari aplikasi (pola sama dengan tabel keuangan lain).

create table public.budgets (
  id            text primary key,
  user_id       uuid not null default auth.uid() references auth.users (id) on delete cascade,
  profile_id    text not null references public.fin_profiles (id) on delete cascade,
  name          text not null,
  category_ids  text not null default '',
  amount        bigint not null check (amount > 0),
  period        text not null default 'MONTHLY' check (period in ('MONTHLY', 'WEEKLY')),
  created_at    bigint not null,
  is_deleted    boolean not null default false,
  updated_at    timestamptz not null default now()
);

create table public.saving_goals (
  id             text primary key,
  user_id        uuid not null default auth.uid() references auth.users (id) on delete cascade,
  profile_id     text not null references public.fin_profiles (id) on delete cascade,
  name           text not null,
  target_amount  bigint not null check (target_amount > 0),
  target_date    bigint,
  icon           text not null,
  color          bigint,
  status         text not null default 'ACTIVE' check (status in ('ACTIVE', 'DONE', 'ARCHIVED')),
  created_at     bigint not null,
  is_deleted     boolean not null default false,
  updated_at     timestamptz not null default now()
);

create table public.saving_logs (
  id          text primary key,
  user_id     uuid not null default auth.uid() references auth.users (id) on delete cascade,
  goal_id     text not null references public.saving_goals (id) on delete cascade,
  profile_id  text not null references public.fin_profiles (id) on delete cascade,
  amount      bigint not null check (amount > 0),
  type        text not null check (type in ('DEPOSIT', 'WITHDRAW')),
  note        text,
  log_date    bigint not null,
  created_at  bigint not null,
  is_deleted  boolean not null default false,
  updated_at  timestamptz not null default now()
);

create index budgets_sync_idx      on public.budgets (user_id, updated_at);
create index saving_goals_sync_idx on public.saving_goals (user_id, updated_at);
create index saving_logs_sync_idx  on public.saving_logs (user_id, updated_at);

create trigger budgets_touch      before update on public.budgets      for each row execute function public.touch_updated_at();
create trigger saving_goals_touch before update on public.saving_goals for each row execute function public.touch_updated_at();
create trigger saving_logs_touch  before update on public.saving_logs  for each row execute function public.touch_updated_at();

alter table public.budgets      enable row level security;
alter table public.saving_goals enable row level security;
alter table public.saving_logs  enable row level security;

create policy "own rows" on public.budgets for all to authenticated
  using (user_id = (select auth.uid())) with check (user_id = (select auth.uid()));
create policy "own rows" on public.saving_goals for all to authenticated
  using (user_id = (select auth.uid())) with check (user_id = (select auth.uid()));
create policy "own rows" on public.saving_logs for all to authenticated
  using (user_id = (select auth.uid())) with check (user_id = (select auth.uid()));
