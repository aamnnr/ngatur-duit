# Backend Supabase Duitku

Arsitektur dan alasan desainnya ada di [../ONLINE.md](../ONLINE.md).

```
supabase/
  config.toml                     pengaturan CLI (telegram-webhook tanpa JWT)
  migrations/…_init.sql           tabel, RLS, RPC
  functions/
    _shared/                      helper: HTTP, Supabase, Gemini, draf, struk
    ai-assistant/                 chat → draf transaksi atau jawaban analisa
    scan-receipt/                 foto struk → draf pengeluaran
    telegram-webhook/             bot Telegram → draf di inbox_drafts
```

## Tabel
| Tabel | Isi | Akses dari aplikasi |
|---|---|---|
| `fin_profiles`, `wallets`, `categories`, `transactions` | Salinan data Room untuk sinkron/backup | Baca/tulis milik sendiri (RLS) |
| `inbox_drafts` | Draf dari bot (dan sumber lain) | Baca, tandai, hapus; tidak bisa membuat |
| `telegram_links` | Chat Telegram ↔ akun | Baca & putus milik sendiri |
| `telegram_pairing_codes` | Kode 6 digit, 10 menit | Lewat RPC `create_telegram_pairing_code()` |
| `ai_usage` | Hitungan pemakaian AI per hari | Baca milik sendiri |

## Secrets
| Nama | Wajib | Keterangan |
|---|---|---|
| `GEMINI_API_KEY` | ya | Dari aistudio.google.com |
| `GEMINI_MODEL` | tidak | Bawaan `gemini-flash-latest` |
| `AI_DAILY_LIMIT` | tidak | Batas permintaan AI per hari per akun, bawaan `50` |
| `TELEGRAM_BOT_TOKEN` | untuk bot | Dari @BotFather |
| `TELEGRAM_WEBHOOK_SECRET` | untuk bot | Kata acak panjang (huruf, angka, `_`, `-`) |
| `APP_TIMEZONE` | tidak | Bawaan `Asia/Jakarta` |

`SUPABASE_URL`, `SUPABASE_ANON_KEY`, dan `SUPABASE_SERVICE_ROLE_KEY` sudah disediakan otomatis oleh Supabase di Edge Functions.

## Langkah deploy
Jalankan dari folder project (`ngatur-duit/`). Perintah yang berisi kunci rahasia sebaiknya kamu ketik sendiri.

1. Masuk ke Supabase CLI (membuka browser):
   ```bash
   npx supabase login
   ```
2. Hubungkan ke project. `<ref>` adalah bagian `xxxx` dari `https://xxxx.supabase.co`:
   ```bash
   npx supabase link --project-ref <ref>
   ```
3. Buat tabel dan RLS:
   ```bash
   npx supabase db push
   ```
4. Simpan secrets:
   ```bash
   npx supabase secrets set GEMINI_API_KEY=<kunci-gemini> TELEGRAM_BOT_TOKEN=<token-bot> TELEGRAM_WEBHOOK_SECRET=<kata-acak>
   ```
5. Deploy fungsi:
   ```bash
   npx supabase functions deploy ai-assistant scan-receipt telegram-webhook
   ```
6. Daftarkan webhook bot ke Telegram:
   ```bash
   curl "https://api.telegram.org/bot<token-bot>/setWebhook" -d "url=https://<ref>.supabase.co/functions/v1/telegram-webhook" -d "secret_token=<kata-acak>" -d "allowed_updates=[\"message\"]"
   ```
   Cek dengan `curl "https://api.telegram.org/bot<token-bot>/getWebhookInfo"`.

## Login email OTP
Di dashboard Supabase: **Authentication → Sign In / Providers → Email** (aktif secara bawaan). Lalu di **Authentication → Emails → Magic Link**, pastikan template memuat `{{ .Token }}` agar email berisi kode 6 digit yang diketik di aplikasi. Paket gratis memakai pengirim email bawaan Supabase yang dibatasi beberapa email per jam, cukup untuk satu orang.

## Catatan free tier
- Project dijeda otomatis bila tidak aktif sekitar seminggu; bot dan sinkron berhenti sampai diaktifkan lagi dari dashboard. Aplikasi tetap jalan offline.
- Bila Gemini membalas 429 (kuota habis), pengguna mendapat pesan ramah; `AI_DAILY_LIMIT` membantu menjaga agar kuota tidak habis karena pemakaian berlebihan.

## Pengujian lokal
Tanpa memasang Deno secara global:
```bash
cd supabase/functions
npx -y deno@2 check ai-assistant/index.ts scan-receipt/index.ts telegram-webhook/index.ts
npx -y deno@2 test --allow-env _shared/drafts_test.ts
```
