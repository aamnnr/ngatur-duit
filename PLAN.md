# Duitku — Rencana Pembuatan Ulang

Referensi: `Catat-in.apk` (package `com.umkm.app`). Dianalisa dari isi APK (manifest, string DEX, skema Room) tanpa menjalankan aplikasinya.

> Prinsip: kita **meniru fitur dan alur**, bukan menyalin kode, aset, nama, atau backend mereka. Semua kode ditulis baru, memakai backend milik sendiri (atau tanpa backend sama sekali).

---

## 1. Hasil Analisa Aplikasi Referensi

### 1.1 Teknologi yang dipakai
| Lapisan | Teknologi |
|---|---|
| Bahasa / UI | Kotlin, Jetpack Compose (Android native) |
| Arsitektur | MVVM + Clean Architecture (`data / domain / ui`), Hilt (DI), sebagian kode KMP (`shared/`) |
| Database lokal | Room (SQLite), DataStore Preferences |
| Backend | Supabase (Auth, Postgres REST, RPC, Edge Functions) via `supabase-kt` + Ktor |
| Background | WorkManager (sync, ringkasan harian, pengingat) |
| Lainnya | Glance (widget), ML Kit OCR + barcode, Credential Manager (Google Sign-In), Play Billing, AdMob, Firebase Analytics/Crashlytics, In-App Review & Update |

### 1.2 Daftar fitur
**Inti (pencatatan)**
1. **Transaksi** — pemasukan/pengeluaran, kategori, catatan, foto struk, tanggal; riwayat dengan filter; gabungkan transaksi.
2. **Dompet** — banyak dompet/bank, "Dompet Utama" default, **transfer antar dompet** (2 transaksi berpasangan), **penyesuaian saldo**. (Versi gratis dibatasi 3 dompet.)
3. **Kategori** — kategori bawaan per tipe profil + kategori kustom (ikon & warna).
4. **Anggaran (Budget)** — per satu/beberapa kategori, periode bulanan/kustom, peringatan bila melebihi.
5. **Target Tabungan** — target nominal & tanggal, log setor/tarik.
6. **Laporan** — per periode, tren harian, rasio pengeluaran vs pemasukan, ekspor CSV/PDF.
7. **Profil (disebut "business")** — tipe *Usaha (UMKM)* atau *Rumah tangga/pribadi*; bisa lebih dari satu dan berpindah-pindah; ada *invite code* untuk berbagi profil antar perangkat.

**Otomatisasi**
8. **Auto-import dari notifikasi bank/e-wallet** — `NotificationListenerService` membaca notifikasi lalu parser membuat draf transaksi ke **Inbox** untuk direview. Sumber yang dikenali: BCA blu, BRImo, Livin' Mandiri, wondr BNI, BSI, GoPay, ShopeePay, DANA, LinkAja, Jenius, Neo Bank, Allo Bank, Krom. Ada dedupe (fingerprint), skor *confidence*, penolakan pola tertentu, dan pemetaan otomatis ke kategori/dompet.
9. **Scan struk** — OCR di perangkat + Edge Function AI (`scan-receipt`) untuk mengisi form transaksi.
10. **Asisten AI (chat)** — ketik bahasa sehari-hari → jadi draf transaksi (maks 10 per pesan) atau jawaban analisa keuangan (Edge Function `finance-assistant`), dengan layar persetujuan privasi.
11. **Input suara** — ucapkan transaksi, dicocokkan ke kategori.
12. **Bot Telegram** — catat lewat chat/foto struk ke bot (pairing code).
13. **Web companion** — pairing perangkat ke dashboard web (linked devices).

**Engagement**
14. **Coach / Insight** — analisa otomatis (mis. "transaksi kecil < Rp20rb totalnya…", aturan 50/30/20, pengeluaran malam hari).
15. **Reality Check** — perbandingan lucu (gaji per detik, dll.).
16. **Notifikasi pengingat** dengan copywriting jenaka, ringkasan harian, streak 7 hari.
17. **Widget layar utama** untuk catat cepat.
18. **Keamanan** — PIN 6 digit, login Google, backup/sync cloud, hapus akun.

**Ditemukan pada pindaian ulang**
19. **Biometrik** — buka aplikasi dengan sidik jari/wajah (setelah PIN dibuat).
20. **Deteksi tagihan rutin** — mengenali pengeluaran yang berulang di tanggal yang sama tiap bulan.
21. **Proyeksi** — perkiraan pengeluaran sampai akhir bulan dan setahun.
22. **Kalkulator Realita** — 5 alat: Realita Gaji (gaji per jam/detik), Realita Umur (berapa persen hidup sudah terpakai), Hutang Tidur, Biaya Rokok, Jebakan Langganan; hasilnya bisa dibagikan ("Tantang Temanmu").
23. **Laporan per hari/minggu/bulan** yang bisa digeser ke periode mana pun, plus **Cetak / Simpan PDF** dan **ekspor Excel**.
24. **Widget** dengan mode ringkasan 4x2 (saldo per dompet + tombol catat cepat).
25. **Kode undangan** untuk menghubungkan profil ke versi web.

**Monetisasi (tidak ditiru)**: langganan Pro (Play Billing + `verify-purchase`), iklan AdMob, promo dan pengumuman dari server, paket bundle dengan aplikasi saudaranya "Tagih-in" (aplikasi invoice terpisah, tidak ada di APK ini).

### 1.2a Fitur yang di aplikasi aslinya dikunci Pro → di versi kita semua terbuka
| Fitur | Catat-in gratis | Catat-in Pro | Versi kita |
|---|---|---|---|
| Jumlah dompet | 3 | tak terbatas | **tak terbatas** |
| Riwayat transaksi | terbatas waktu | tanpa batas | **tanpa batas** |
| Geser laporan ke periode lama | hanya periode sekarang | semua periode | **semua periode** |
| Ekspor PDF & Excel | ✗ | ✓ | **✓** |
| Scan struk | kuota harian kecil | 10×/hari | **tanpa kuota** (OCR di HP); AI opsional pakai API key sendiri |
| Asisten AI | trial 10 jawaban | 20×/hari | **pakai API key sendiri**, batasnya kamu yang atur |
| Widget lengkap | terbatas | ✓ | **✓** |
| Iklan | ada | tidak ada | **tidak ada** |

Kita tidak membobol aplikasi aslinya. Fitur-fitur ini terbuka karena kita menulis sendiri seluruh kodenya, tanpa sistem langganan. Satu-satunya biaya yang mungkin muncul adalah layanan AI pihak ketiga (scan struk dengan AI dan asisten chat). Biaya itu dibayar ke penyedia AI, bukan ke Catat-in.

### 1.3 Model data (dari skema Room)
```
profiles(id, name, type[BUSINESS|PERSONAL], invite_code, created_at)
wallets(id, profile_id, name, icon, color, is_default, created_at, is_deleted)
categories(id, profile_id, name, type[INCOME|EXPENSE], is_default, icon, color)
transactions(id, profile_id, type, amount, category_id, wallet_id, note,
             receipt_image_path, transaction_date, created_at,
             is_adjustment, linked_transfer_id, transfer_type, is_deleted)
budgets(id, profile_id, category_ids, name, amount, period, start_date, end_date, is_deleted)
saving_goals(id, profile_id, name, target_amount, target_date, note, icon, color, status, ...)
saving_logs(id, goal_id, amount, type[DEPOSIT|WITHDRAW], note, log_date, ...)
parsed_notifications(fingerprint PK, raw_text, parsed_json, status, received_at, transaction_id, source)
settings(key, value)
```
Catatan desain yang bagus untuk ditiru: ID berupa UUID (aman untuk sync), nominal disimpan sebagai `INTEGER` rupiah, *soft delete* (`is_deleted`) + `synced_at` untuk sinkronisasi offline-first.

---

## 2. Keputusan yang Perlu Diambil

| Pilihan | Rekomendasi | Alasan |
|---|---|---|
| Platform | **Android native: Kotlin + Jetpack Compose** | Fitur andalan (auto-import notifikasi, widget) hanya bisa di Android; sama dengan referensi. Alternatif: Flutter bila ingin iOS juga (auto-import tetap Android-only). |
| Penyimpanan | **Offline-first dengan Room**, cloud menyusul | Untuk pemakaian pribadi tidak wajib server. |
| Backup | Fase awal: ekspor/impor file JSON/CSV. Fase lanjut: Supabase milik sendiri (gratis) | |
| AI (struk & asisten) | Opsional, pakai API key sendiri (mis. Claude API) lewat backend sendiri | Jangan menaruh API key di dalam APK. |
| Iklan & Pro | **Dihilangkan** | Aplikasi untuk diri sendiri, semua fitur terbuka. |

---

## 3. Arsitektur Target

```
app/
 ├─ data/
 │   ├─ local/      Room DB, DAO, entity, DataStore
 │   ├─ repository/ implementasi repository
 │   └─ notif/      parser notifikasi bank/e-wallet (unit-testable, Kotlin murni)
 ├─ domain/         model & interface repository, use case (hitung saldo, laporan, insight)
 ├─ ui/
 │   ├─ theme/      warna, tipografi (desain sendiri)
 │   ├─ navigation/
 │   └─ screens/    dashboard, transaction, wallet, category, budget, savings, report, inbox, settings
 ├─ service/        NotificationListenerService
 ├─ worker/         pengingat & ringkasan harian (WorkManager)
 └─ widget/         Glance widget
```
Stack: Kotlin, Compose + Material 3, Navigation Compose, Hilt, Room, DataStore, WorkManager, Vico (grafik), CameraX + ML Kit (OCR).

---

## 4. Tahapan Pengerjaan

### Fase 0 — Persiapan (½ hari)
- Setup Android Studio, project baru (minSdk 26), Git.

### Fase 0.5 — Identitas & desain UI/UX (sebelum lanjut coding)
- **Nama aplikasi**: ✅ **Duitku** (package `com.aamnnr.duitku`). Catatan: nama ini juga dipakai payment gateway duitku.com — aman untuk pemakaian pribadi, perlu ditinjau ulang bila ingin dipublikasikan.
- ✅ **Ikon & logo** ("Uang dan koin": uang kertas + koin Rp, karamel di atas espresso), ✅ **palet Kopi**, **tipografi** (font kustom, mis. dari Google Fonts).
- **Gaya visual** dan **nada bahasa** (formal, santai, atau jenaka seperti aslinya).
- **Alur & tata letak**: navigasi bawah, letak tombol catat, onboarding pertama kali.
- Hasil: panduan desain singkat + mockup layar utama. Setelah disetujui, kode Fase 1 disesuaikan.

### Fase 1 — MVP pencatatan offline (≈1–2 minggu)
- Room DB + entitas: profile, wallet, category, transaction, settings.
- Seed: profil default, "Dompet Utama", kategori bawaan (pribadi & usaha).
- Layar: Dashboard (saldo total, saldo per dompet, ringkasan bulan ini, transaksi terbaru), Form transaksi, Riwayat + filter, Kelola dompet, Kelola kategori.
- Transfer antar dompet & penyesuaian saldo.
- ✅ Selesai bila: bisa mencatat harian sepenuhnya offline dan saldo selalu benar.

### Fase 2 — Perencanaan & laporan (≈1 minggu)
- Anggaran (per kategori, periode) + indikator progres & peringatan.
- Target tabungan + log setor/tarik.
- Laporan per periode, grafik tren & per kategori.
- Ekspor CSV, backup/restore JSON.

### Fase 3 — Auto-import notifikasi (≈1–2 minggu) ⭐
- `NotificationListenerService` + layar izin.
- Parser modular per sumber (BCA, BRI, Mandiri, BNI, BSI, GoPay, DANA, ShopeePay, OVO, dll.) — ditulis ulang sendiri dengan **unit test** berbasis contoh teks notifikasi asli dari HP kita.
- Inbox draf: setujui / edit / buang, dedupe fingerprint, pemetaan dompet & kategori otomatis.

### Fase 4 — Kenyamanan (≈1 minggu)
- PIN / biometrik lock.
- Widget catat cepat + mode ringkasan 4x2, pengingat harian & ringkasan (WorkManager).
- Insight sederhana (aturan 50/30/20, transaksi kecil, pengeluaran malam), proyeksi bulanan/tahunan, deteksi tagihan rutin.
- Kalkulator Realita (gaji, umur, hutang tidur, rokok, langganan) dengan kartu hasil yang bisa dibagikan.
- Laporan harian/mingguan/bulanan ke periode mana pun, ekspor PDF & Excel.
- Multi-profil (pribadi vs usaha) dan berpindah profil.

### Fase 5 — AI & online (free tier) — detail di [ONLINE.md](ONLINE.md)
Keputusan: **Supabase free** (auth, database, Edge Functions) + **Gemini API free tier** + **bot Telegram**. Semua kunci rahasia hanya disimpan sebagai secrets Supabase, tidak di APK.
- Backend `supabase/`: skema SQL + RLS, Edge Functions `ai-assistant`, `scan-receipt`, `telegram-webhook`.
- Login email OTP (Google menyusul), sinkron Room ↔ Supabase, Inbox draf.
- Asisten chat AI, scan struk (OCR di HP + Gemini), input suara.
- Bot Telegram: pairing kode, pesan/foto → draf di Inbox.
- Dashboard web: opsional, belakangan.

---

## 5. Hal yang Sengaja Tidak Ditiru
- Kode, nama "Catat-in", logo, ilustrasi, dan copywriting mereka (kita buat sendiri).
- Backend Supabase mereka (`yshmxkdtjrfvkjaazmvl.supabase.co`) — **tidak boleh dipakai**.
- Iklan, billing Pro, license check (pairip), analytics pihak ketiga.

## 6. Langkah Berikutnya
1. Konfirmasi pilihan platform (Kotlin native vs Flutter) dan cakupan MVP.
2. Kumpulkan beberapa contoh teks notifikasi bank/e-wallet yang kamu pakai (untuk Fase 3).
3. Mulai Fase 0 & 1.
