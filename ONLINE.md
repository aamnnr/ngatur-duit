# Duitku — Fitur AI & Online (Free Tier)

Keputusan: semua layanan memakai **paket gratis**, karena Duitku hanya untuk pemakaian pribadi.

| Layanan | Dipakai untuk | Paket |
|---|---|---|
| **Supabase** | Login, database cloud (sinkron & backup), Edge Functions (perantara AI dan bot) | Free |
| **Gemini API** (Google AI Studio) | Asisten chat, baca struk dari foto, baca pesan bot | Free tier |
| **Telegram Bot** | Catat lewat chat atau foto struk | Gratis |

---

## 1. Arsitektur

```
 HP (Duitku)                       Supabase (milikmu)                    Layanan luar
┌────────────────────┐   sinkron  ┌──────────────────────────┐
│ Room (data utama,  │◄──────────►│ Postgres + RLS           │
│ tetap jalan        │  (Auth JWT)│ (data per pengguna)      │
│ offline)           │            │                          │
│                    │  panggil   │ Edge Functions:          │   API key   ┌──────────┐
│ Asisten / Scan ────┼───────────►│  • ai-assistant ─────────┼────────────►│ Gemini   │
│                    │            │  • scan-receipt ─────────┼────────────►│          │
└────────────────────┘            │  • telegram-webhook ─────┼────────────►└──────────┘
                                  │        ▲                 │
                                  └────────┼─────────────────┘
                                   webhook │
                                  ┌────────┴─────┐
                                  │ Bot Telegram │
                                  └──────────────┘
```

Prinsip:
- **Offline-first tetap**: Room adalah sumber utama. Cloud hanya untuk sinkron, backup, dan fitur yang butuh server. Kalau Supabase sedang mati, aplikasi tetap bisa dipakai.
- **Rahasia tidak pernah masuk APK**: `GEMINI_API_KEY` dan `TELEGRAM_BOT_TOKEN` hanya disimpan sebagai *secrets* di Supabase. APK hanya berisi URL project dan *anon/publishable key*, yang memang aman dibagikan selama Row Level Security (RLS) aktif.
- **Setiap baris data punya `user_id`**, dan RLS memastikan hanya pemiliknya yang bisa membaca atau mengubahnya.

### Alur bot Telegram
1. Di aplikasi: Lainnya → Bot Telegram → "Buat kode" (kode 6 digit, berlaku 10 menit).
2. Di Telegram: kirim `/start KODE` ke bot.
3. `telegram-webhook` mencocokkan kode, lalu menyimpan `chat_id` ↔ `user_id`.
4. Pesan berikutnya ("bakso 25rb gopay") atau foto struk dibaca Gemini, lalu disimpan sebagai **draf** di tabel `inbox_drafts`.
5. Aplikasi menarik draf itu ke Inbox untuk disetujui, sama seperti draf auto-import notifikasi.

### Alur asisten & scan struk
Aplikasi mengirim teks/foto + konteks seperlunya (daftar kategori & dompet, ringkasan bulan ini) ke Edge Function. Edge Function memanggil Gemini dengan **output JSON terstruktur**, memvalidasinya, lalu mengembalikannya ke aplikasi sebagai draf yang bisa ditinjau sebelum disimpan.

---

## 2. Batasan free tier yang perlu diketahui
- **Gemini free tier**: ada batas permintaan per menit dan per hari (cek angka terbaru di AI Studio, karena bisa berubah). Duitku akan menampilkan pesan ramah bila kuota habis; pencatatan manual tetap jalan.
- **Privasi Gemini free tier**: Google dapat memakai data dari free tier untuk meningkatkan produknya. Karena itu Duitku hanya mengirim data seperlunya (tanpa nama lengkap, nomor rekening, atau saldo detail per dompet bila tidak dibutuhkan), dan akan ada layar persetujuan sebelum fitur AI pertama kali dipakai.
- **Supabase free**: project akan **dijeda otomatis bila tidak ada aktivitas sekitar seminggu**. Bot Telegram dan sinkron tidak jalan selama dijeda; aktifkan lagi dari dashboard Supabase. Aplikasi sendiri tetap bisa dipakai offline. Kapasitas database & storage paket gratis lebih dari cukup untuk satu orang.
- **Model Gemini** tidak di-hardcode: dibaca dari secret `GEMINI_MODEL`, sehingga bisa diganti ke model gratis terbaru tanpa mengubah aplikasi.

---

## 3. Yang perlu kamu siapkan

Akun dan kunci rahasia harus dibuat dan dimasukkan **oleh kamu sendiri**. Saya menyiapkan kode, SQL, dan perintahnya.

### a. Supabase
1. Daftar di supabase.com, lalu **New project**. Pilih region **Southeast Asia (Singapore)**, dan simpan *database password* di tempat aman.
2. Buka **Project Settings → API**. Catat **Project URL** dan **anon / publishable key**.
3. Tambahkan keduanya ke `local.properties` di folder project (file ini tidak ikut dibagikan):
   ```
   SUPABASE_URL=https://xxxx.supabase.co
   SUPABASE_ANON_KEY=eyJ...
   TELEGRAM_BOT_USERNAME=nama_bot_kamu_bot
   ```
   `TELEGRAM_BOT_USERNAME` opsional: bila diisi, tombol "Buka Telegram dan hubungkan" langsung mengirim kode pairing ke bot.
4. Install Supabase CLI, lalu jalankan `supabase login` di terminalmu sendiri.

### b. Gemini
1. Buka aistudio.google.com, masuk dengan akun Google, lalu **Get API key → Create API key**.
2. Simpan sebagai secret Supabase (jalankan sendiri di terminal):
   ```
   supabase secrets set GEMINI_API_KEY=... GEMINI_MODEL=gemini-flash-latest
   ```

### c. Telegram
1. Di Telegram, buka **@BotFather**, kirim `/newbot`, lalu ikuti langkahnya. Simpan **token** yang diberikan.
2. Simpan sebagai secret, plus satu kata acak sebagai pengaman webhook:
   ```
   supabase secrets set TELEGRAM_BOT_TOKEN=... TELEGRAM_WEBHOOK_SECRET=<kata-acak-panjang>
   ```
3. Setelah Edge Function di-deploy, daftarkan webhook (perintah lengkapnya disiapkan saat tahap ini).

### d. Login
Tahap awal memakai **login email + kode OTP** dari Supabase Auth: paling sederhana, tanpa konfigurasi Google Cloud. Login Google bisa ditambahkan nanti (butuh OAuth client di Google Cloud Console, juga gratis).

---

## 4. Urutan pengerjaan
1. **Backend dulu** (bisa dikerjakan tanpa kunci rahasia): skema SQL + RLS, Edge Functions `ai-assistant`, `scan-receipt`, `telegram-webhook`, semuanya di folder `supabase/`.
2. **Aplikasi**: login email OTP, layar persetujuan AI, sinkron Room ↔ Supabase, Inbox draf.
3. **Asisten AI** di tombol +, lalu **scan struk** (OCR di HP dulu, AI sebagai penyempurna).
4. **Bot Telegram**: pairing kode + draf ke Inbox.
5. Uji end-to-end setelah kamu mengisi `local.properties` dan secrets.
