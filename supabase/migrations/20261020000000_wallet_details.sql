-- Detail tampilan kartu dompet (desain v2): 4 digit akhir rekening dan nama institusi, keduanya opsional.
alter table public.wallets
  add column if not exists account_suffix text check (account_suffix ~ '^[0-9]{4}$'),
  add column if not exists institution text check (char_length(institution) <= 40);
