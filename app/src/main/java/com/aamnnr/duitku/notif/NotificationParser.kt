package com.aamnnr.duitku.notif

/** Hasil baca satu notifikasi transaksi. */
data class ParsedNotification(
    val sourceName: String,
    /** "INCOME" atau "EXPENSE". */
    val type: String,
    val amount: Long,
    /** Merchant / lawan transaksi bila terbaca, mis. "Kopi Kenangan". */
    val counterpart: String?,
    val confidence: Double,
)

/**
 * Membaca notifikasi bank & e-wallet Indonesia menjadi draf transaksi. Kotlin murni (tanpa Android)
 * agar mudah diuji. Hanya aplikasi di [SOURCES] yang dibaca; notifikasi lain diabaikan demi privasi.
 */
object NotificationParser {

    /** Package aplikasi keuangan → nama tampilan (juga dipakai mencocokkan nama dompet). */
    val SOURCES: Map<String, String> = mapOf(
        "com.bca" to "BCA",
        "com.bca.mybca.omni.android" to "BCA",
        "com.bcadigital.blu" to "blu",
        "id.co.bri.brimo" to "BRI",
        "id.bmri.livin" to "Mandiri",
        "id.bni.wondr" to "BNI",
        "src.com.bni" to "BNI",
        "co.id.bankbsi.superapp" to "BSI",
        "id.co.cimbniaga.mobile.android" to "CIMB",
        "com.btpn.dc" to "Jenius",
        "com.bnc.finance" to "Neo",
        "com.alloapp.yump" to "Allo",
        "id.co.bankbkemobile.digitalbank" to "Krom",
        "com.gojek.gopay" to "GoPay",
        "com.gojek.app" to "GoPay",
        "com.gojek.gopaymerchant" to "GoPay",
        "id.dana" to "DANA",
        "com.shopeepay.id" to "ShopeePay",
        "com.shopee.id" to "ShopeePay",
        "ovo.id" to "OVO",
        "com.telkom.mwallet" to "LinkAja",
    )

    private val amountRegex = Regex(
        """(?i)(?:rp\.?|idr)\s?([0-9]{1,3}(?:[.,][0-9]{3})+|[0-9]+)(?:[.,]([0-9]{2}))?(?![0-9])""",
    )

    private val rejectWords = listOf(
        "otp", "kode verifikasi", "kode rahasia", "jangan berikan", "verification code",
        "gagal", "dibatalkan", "ditolak", "tidak berhasil", "pending", "menunggu pembayaran",
        "akan jatuh tempo", "jatuh tempo", "segera bayar", "tagihan anda sebesar",
        "promo", "diskon", "voucher", "cashback hingga", "dapatkan", "hemat hingga", "gratis",
        "limit", "pinjaman", "cicilan 0",
    )

    private val incomeWords = listOf(
        "dana masuk", "uang masuk", "transfer masuk", "masuk ke rekening", "telah menerima", "kamu menerima",
        "anda menerima", "menerima transfer", "diterima dari", "terima uang", "kredit", "top up berhasil",
        "top-up berhasil", "isi saldo berhasil", "refund", "pengembalian dana", "cashback sebesar", "dari ",
    )

    private val expenseWords = listOf(
        "pembayaran", "berhasil dibayar", "bayar", "pembelian", "transaksi di", "transfer ke", "kirim uang",
        "terkirim", "transfer keluar", "dana keluar", "uang keluar", "debit", "tarik tunai", "qris",
        "telah melakukan", "kamu bayar", "pembayaran berhasil", "ke ",
    )

    /** Kata yang menandakan notifikasi berisi transaksi (bukan sekadar info saldo). */
    private val transactionWords = listOf(
        "berhasil", "transfer", "pembayaran", "bayar", "masuk", "keluar", "menerima", "diterima", "terkirim",
        "debit", "kredit", "top up", "top-up", "qris", "pembelian", "tarik tunai", "refund",
    )

    fun parse(packageName: String, title: String?, text: String?): ParsedNotification? {
        val source = SOURCES[packageName] ?: return null
        val full = listOfNotNull(title, text).joinToString(". ").replace('\n', ' ').trim()
        if (full.isEmpty()) return null
        val lower = full.lowercase()

        if (rejectWords.any { it in lower }) return null
        if (transactionWords.none { it in lower }) return null
        val amount = parseAmount(full) ?: return null
        if (amount <= 0 || amount > 10_000_000_000) return null

        val incomeScore = score(lower, incomeWords)
        val expenseScore = score(lower, expenseWords)
        val type = when {
            incomeScore > expenseScore -> "INCOME"
            expenseScore > incomeScore -> "EXPENSE"
            else -> return null
        }
        val counterpart = counterpart(full, type)
        // Kata kunci kuat di kedua arah menurunkan keyakinan.
        var confidence = if (incomeScore == 0 || expenseScore == 0) 0.9 else 0.65
        if (counterpart == null) confidence -= 0.1
        return ParsedNotification(source, type, amount, counterpart, confidence.coerceIn(0.0, 1.0))
    }

    /** Skor = jumlah kata kunci yang cocok; frasa panjang (lebih spesifik) diberi bobot lebih. */
    private fun score(lower: String, words: List<String>) =
        words.sumOf { w -> if (w in lower) (if (w.length > 4) 2 else 1) else 0 }

    /** Nominal pertama yang diawali Rp/IDR. "Rp1.500.000,00" → 1500000, "IDR 50,000.00" → 50000. */
    fun parseAmount(text: String): Long? {
        val m = amountRegex.find(text) ?: return null
        val whole = m.groupValues[1].replace(".", "").replace(",", "")
        return whole.toLongOrNull()
    }

    private val counterpartPatterns = mapOf(
        "EXPENSE" to listOf(
            Regex("""(?i)\b(?:di|pada|merchant)\s+([A-Za-z0-9&'. -]{2,40}?)(?=\s+(?:berhasil|sebesar|senilai|pada|tgl|tanggal|rp|idr)|[.,!]|$)"""),
            Regex("""(?i)\bke\s+([A-Za-z0-9&'. -]{2,40}?)(?=\s+(?:berhasil|sebesar|senilai|pada|tgl|tanggal|rp|idr)|[.,!]|$)"""),
        ),
        "INCOME" to listOf(
            Regex("""(?i)\bdari\s+([A-Za-z0-9&'. -]{2,40}?)(?=\s+(?:berhasil|sebesar|senilai|pada|tgl|tanggal|rp|idr|ke)|[.,!]|$)"""),
        ),
    )

    private fun counterpart(text: String, type: String): String? =
        counterpartPatterns[type].orEmpty().firstNotNullOfOrNull { r ->
            r.find(text)?.groupValues?.get(1)?.trim()?.trimEnd('.', '-')?.takeIf { cp ->
                cp.length >= 2 && !cp.lowercase().startsWith("rp") && cp.any { it.isLetter() }
            }
        }

    /** Kata kunci merchant → kata pada nama kategori pengguna, untuk tebakan kategori. */
    private val categoryHints = listOf(
        listOf("kopi", "coffee", "resto", "food", "makan", "mcd", "kfc", "starbucks", "warung", "bakso", "kenangan", "janji jiwa", "pizza", "burger", "chatime") to listOf("makan", "minum"),
        listOf("gojek", "goride", "gocar", "grab", "maxim", "ojek", "transjakarta", "krl", "mrt", "lrt", "parkir", "pertamina", "shell", "bensin", "tol") to listOf("transport"),
        listOf("indomaret", "alfamart", "alfamidi", "supermarket", "hypermart", "superindo", "tokopedia", "shopee", "lazada", "blibli", "mart") to listOf("belanja"),
        listOf("pln", "listrik", "pdam", "bpjs", "indihome", "tagihan") to listOf("tagihan", "listrik"),
        listOf("pulsa", "paket data", "telkomsel", "xl", "indosat", "smartfren") to listOf("pulsa", "internet"),
        listOf("netflix", "spotify", "youtube", "disney", "vidio", "bioskop", "cgv", "xxi") to listOf("hiburan"),
        listOf("apotek", "klinik", "rumah sakit", "halodoc", "kimia farma") to listOf("kesehatan"),
        listOf("gaji", "salary", "payroll") to listOf("gaji"),
    )

    /** Pilih id kategori yang namanya cocok dengan petunjuk merchant; null bila tidak yakin. */
    fun suggestCategory(text: String, categories: List<Pair<String, String>>): String? {
        val lower = text.lowercase()
        for ((keywords, names) in categoryHints) {
            if (keywords.any { it in lower }) {
                categories.firstOrNull { (_, name) -> names.any { it in name.lowercase() } }?.let { return it.first }
            }
        }
        return null
    }
}
