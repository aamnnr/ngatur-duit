# Duitku

Aplikasi pencatat keuangan pribadi/usaha untuk Android, dibuat ulang dari nol dengan referensi fitur dari Catat-in. Kebutuhan produk lengkap ada di [PRD.md](PRD.md), rencana kerja di [PLAN.md](PLAN.md).

## Status
Semua fitur di [PRD.md](PRD.md) sudah dibuat dan terkompilasi; uji menyeluruh di HP masih berjalan.

- Pencatatan offline: transaksi, transfer, dompet (kartu dengan 4 digit akhir & institusi), kategori, multi-profil, riwayat, foto struk
- Analisa: Beranda, Insight, proyeksi, ekspor CSV/PDF
- Rencana: batas bulanan, anggaran per kategori, target tabungan, tagihan rutin, kalkulator realita
- Otomatisasi: auto-import notifikasi bank/e-wallet, scan struk & asisten AI (Gemini), bot Telegram, Inbox, input suara
- Akun & keamanan: login email OTP, sinkron Supabase, PIN & sidik jari, backup/restore JSON
- Kenyamanan: desain v2 soft-neumorphic ([DESIGN.md](DESIGN.md)), widget saldo & widget pengeluaran (hari/minggu/bulan), pengingat harian, peringatan anggaran, sembunyikan saldo

## Setup & build
Butuh JDK 17+ dan Android SDK (platform 36).

1. Buat `local.properties` di root project (file ini diabaikan git, jangan di-commit):
   ```
   sdk.dir=C\:/Android/sdk
   SUPABASE_URL=https://xxxx.supabase.co
   SUPABASE_ANON_KEY=...
   TELEGRAM_BOT_USERNAME=nama_bot_kamu_bot
   ```
   Tiga baris terakhir opsional: tanpa itu aplikasi tetap jalan penuh secara offline, fitur cloud disembunyikan.
2. Build:
   ```bash
   ./gradlew assembleDebug
   ```
   APK: `app/build/outputs/apk/debug/app-debug.apk`.

Backend (Supabase: skema, Edge Functions, bot Telegram) dijelaskan di [supabase/README.md](supabase/README.md). Kunci rahasia (Gemini, token bot) hanya disimpan sebagai secrets Supabase, tidak pernah di repo.

## Struktur
```
app/src/main/java/com/aamnnr/duitku/
  data/local/        Room: entity, DAO, database, data bawaan
  data/repository/   logika saldo, transfer, penyesuaian, profil
  domain/            model
  ui/                tema, komponen, navigasi, layar
```

## Catatan desain
- Nominal disimpan sebagai `Long` rupiah penuh.
- Saldo dompet dihitung dari transaksi (tidak disimpan), jadi selalu konsisten.
- Transfer = dua transaksi berpasangan (`linked_transfer_id`), penyesuaian saldo = `is_adjustment`. Keduanya tidak masuk laporan pemasukan/pengeluaran.
- Hapus memakai soft delete (`is_deleted`) agar siap untuk sinkronisasi cloud nanti.
