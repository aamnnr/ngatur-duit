# DESIGN.md — Sistem Desain & Panduan UI/UX Duitku

**Diperbarui:** 20 Oktober 2026 · **Versi:** 2.0 (menggantikan v1 "header espresso")
**Aplikasi:** Duitku (Android, offline-first) · package `com.aamnnr.duitku`
**Gaya UI:** Soft Neumorphism Clean / Minimalist Financial App
**Referensi:** `design_md.md` dari pemilik produk + layar Beranda di Google Stitch (project 13383866570321283954, screen ae62ecfd…).

---

## 1. Prinsip

1. **Catat dalam 3 ketukan.** Tombol `+` di tengah navigasi, area sentuh paling menonjol.
2. **Jelas sekali lihat.** Total saldo, pengeluaran bulan ini, dan saldo dompet terbaca tanpa scroll.
3. **Soft-neumorphic & taktil.** Kartu putih terpisah dengan bayangan lembut + garis tepi tipis, sudut 16–24 dp.
4. **Hierarki tegas.** Nominal besar dan tebal, teks pendukung kecil abu-abu, warna konsisten: pemasukan hijau, pengeluaran terracotta, transfer biru slate.

---

## 2. Warna

### Mode terang (utama)
| Token | Hex | Pakai untuk |
|---|---|---|
| `background` | `#F8F6F3` | Latar layar (krem lembut) |
| `surface` | `#FFFFFF` | Kartu (dengan soft shadow) |
| `primary` | `#2A2421` | Espresso/charcoal: tombol utama, kartu dompet gelap, ikon aktif |
| `onPrimary` | `#FFFFFF` | |
| `accent` | `#C08A5B` | Karamel: link "Lainnya/Lihat Semua", badge AI, sorotan |
| `textPrimary` | `#1C1C1E` | |
| `textSecondary` | `#8E8E93` | Caption, label |
| `border` | `#E5E5EA` | Garis tepi kartu, pemisah |
| `income` | `#3F7D58` | Pemasukan, status "Sinkron", progres aman |
| `expense` | `#B4533A` | Pengeluaran, peringatan |
| `transfer` | `#4E6E8E` | Transfer |
| `shadow` | `#2A2421` @ 6–10% | Soft shadow kartu |

### Mode gelap
| Token | Hex |
|---|---|
| `background` | `#121212` |
| `surface` | `#1E1E1E` |
| `primary` (aksi) | `#C08A5B` karamel, teks di atasnya `#1C1C1E` |
| `walletCard` | `#2A2421` → tetap gelap, garis tepi `#3A3330` |
| `textPrimary` | `#F8F6F3` |
| `textSecondary` | `#A1A1A1` |
| `border` | `#2C2C2E` |
| `income` / `expense` / `transfer` | `#7FBF95` / `#E38B72` / `#8FAACB` |

Ikon kategori duduk di lingkaran berwarna lembut (warna kategori @ 12%).

---

## 3. Tipografi
Font **Plus Jakarta Sans** (variable font, dibundel di `res/font`, lisensi OFL).

| Gaya | Ukuran / berat | Contoh |
|---|---|---|
| Display saldo | 30 sp / Bold | `Rp 15.245.000` |
| Judul layar & kartu | 18–20 sp / SemiBold | "Riwayat Transaksi" |
| Body / item | 14–16 sp / Medium | Nama transaksi |
| Caption | 11–12 sp / Regular | "Terakhir disinkron 1 menit lalu" |
| Label kapital | 12 sp / SemiBold, spasi 1 | "TOTAL SALDO AKTIF" |

---

## 4. Navigasi

Bottom bar **floating pill** (melayang 16 dp dari tepi, sudut penuh, soft shadow) dengan tombol `+` bulat espresso menonjol di tengah.

| Posisi | Tab | Isi |
|---|---|---|
| 1 | **Beranda** | Sapaan, total saldo (bisa disembunyikan), kartu dompet, progres pengeluaran bulan ini, aksi cepat, riwayat |
| 2 | **Dompet** | Slider kartu dompet, 4 aksi bulat (Sesuaikan, Anggaran, Detail, Transfer), progres bulanan, transaksi per dompet, tambah dompet |
| 3 | **+** | Ketuk: lembar opsi (Manual, Scan Struk, Asisten AI, Suara). Tahan: langsung form pengeluaran |
| 4 | **Insight** | Tren, rincian kategori, catatan otomatis, riwayat lengkap, ekspor |
| 5 | **Rencana** | Anggaran, tabungan, tagihan, kalkulator realita |

**Profil & Pengaturan** (akun & sinkron, bot Telegram, kategori, profil keuangan, nama, batas bulanan, tema, otomatisasi, keamanan) dibuka dari **avatar** di kiri atas Beranda. Inbox dibuka dari **lonceng** di kanan atas.

> Keputusan: layar referensi memuat app bar "Beranda" *dan* baris sapaan dengan lonceng kedua. Di Duitku dipakai **satu** header (avatar + sapaan + lonceng) agar tidak ada dua lonceng dan ruang atas tidak terbuang.

---

## 5. Komponen kunci

### Header Beranda
- Avatar inisial (lingkaran, titik hijau = tersinkron) + "Selamat Pagi/Siang/Sore/Malam," + nama pengguna.
- Lonceng (tombol bulat putih) dengan **titik merah** bila ada draf di Inbox.

### Ringkasan saldo
- Label kapital "TOTAL SALDO AKTIF" + chip status sinkron (hijau "Sinkron", abu "Offline", karamel "Menyinkron").
- Nominal 30 sp bold dengan animasi count-up; tombol **mata** menyembunyikan nominal (`Rp ••••••`) di seluruh aplikasi.
- Caption "Terakhir disinkron X menit lalu" (atau "Data tersimpan di HP ini" bila belum masuk akun).

### Kartu dompet (dark card)
- Gradasi espresso lembut `#3B312C → #1F1A18`, sudut 24 dp, chip "OFFLINE READY".
- Isi: nama dompet, "Saldo Tersedia", nominal, `•••• 2038` (4 digit akhir rekening, opsional), "NAMA PEMILIK" + nama pengguna, institusi (mis. "BCA Digital") di kanan bawah.
- Bisa digeser (pager) bila dompet lebih dari satu.

### Kartu progres bulanan
- "Pengeluaran Bulan Ini" + `Rp 8.545.000 / 15jt`, progress bar membulat.
- Status: hijau "Terkontrol rapi" (< 75%), karamel "Mulai waspada" (75–99%), terracotta "Melewati batas" (≥ 100%). Tanpa batas: ajakan "Atur batas bulanan".

### Aksi cepat
Grid 2×2 kartu putih: ikon dalam kotak lembut + judul + subjudul abu. Default: Transfer, Scan Struk (badge AI), Tagihan, Insight.

### Riwayat transaksi
- Dikelompokkan per hari ("Hari ini, 20 Okt 2026") dengan total net hari itu di kanan.
- Tiap item = kartu putih: ikon kategori dalam kotak lembut, nama (catatan atau kategori), "Kategori • 13:00", nominal berwarna, label kecil status di bawah nominal (Diterima / Selesai / Dipindah / Penyesuaian).
- Geser kiri = hapus + snackbar "Transaksi dihapus — Urungkan" (4 detik); geser kanan = ubah.

### Kartu & tombol
- `NeuCard`: surface putih, radius 20 dp, garis tepi 1 dp `border`, bayangan 10 dp @ 8%.
- Tombol bulat 44 dp (lonceng, mata, aksi dompet): surface + bayangan lembut.
- Tombol utama: espresso penuh, radius 16 dp, tinggi 52 dp.

---

## 6. Mikro-interaksi
1. **Haptic** saat menyimpan, menggeser item, dan menekan/menahan `+`.
2. **Count-up** angka saldo & pengeluaran saat layar pertama dimuat dan saat berubah.
3. **Empty state** dengan ilustrasi garis (line-art) + petunjuk ("Belum ada catatan. Tekan + untuk mulai mencatat").
4. **Snackbar** hapus 4 detik dengan Urungkan; tidak ada dialog konfirmasi kecuali hapus dompet/profil dan keluar akun.

---

## 7. Ikon aplikasi
Logo **v3**: dompet krem dengan koin karamel yang menyembul, dikelilingi ayunan karamel dan krem, di atas latar espresso gelap `#2B1D18`. Karamel memakai gradasi `#E8AA62 → #B5713D`, krem `#FBF6EF → #EBDDCD`.

| Berkas | Isi |
|---|---|
| `branding/duitku-logo-v3.webp` | Logo asli dari pemilik produk |
| `branding/duitku-icon.svg` | Master vektor (108×108, area aman lingkaran r=33) |
| `res/drawable/ic_launcher_foreground.xml` | Lapisan depan ikon adaptif (koordinat sama dengan master) |
| `res/drawable/ic_launcher_monochrome.xml` | Versi satu warna untuk ikon bertema Android 13+ (dompet sebagai garis) |
| `branding/telegram-avatar.png` | Foto profil bot (640×640) |
| `branding/archive/` | Logo lama (v1 dompet, v2 uang & koin) |
