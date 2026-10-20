package com.aamnnr.duitku.domain

/** Hasil baca kalimat suara, mis. "beli kopi 25 ribu pakai gopay". */
data class VoiceDraft(
    val type: TransactionType,
    val amount: Long,
    val note: String?,
)

/**
 * Parser kalimat transaksi sederhana untuk input suara, berjalan offline.
 * Mengenali "25 ribu", "25rb", "25k", "1,5 juta", "Rp25.000", juga angka kata dasar ("dua puluh lima ribu").
 */
object VoiceParser {
    private val incomeWords = listOf("gaji", "terima", "dapat", "masuk", "bonus", "jual", "dibayar", "transferan", "pemasukan")
    private val units = mapOf("ribu" to 1_000L, "rb" to 1_000L, "k" to 1_000L, "juta" to 1_000_000L, "jt" to 1_000_000L)

    private val numericRegex = Regex("""(?i)(?:rp\.?\s?)?(\d+(?:[.,]\d+)?)\s*(ribu|rb|k|juta|jt)?\b""")

    private val digits = mapOf(
        "nol" to 0, "satu" to 1, "dua" to 2, "tiga" to 3, "empat" to 4, "lima" to 5, "enam" to 6, "tujuh" to 7,
        "delapan" to 8, "sembilan" to 9,
    )
    private val fixed = mapOf("sepuluh" to 10, "sebelas" to 11, "seratus" to 100)

    fun parse(text: String): VoiceDraft? {
        val lower = text.lowercase().trim()
        if (lower.isEmpty()) return null
        val (amount, matched) = numericAmount(lower) ?: wordAmount(lower) ?: return null
        if (amount <= 0) return null
        val type = if (incomeWords.any { Regex("""\b$it""").containsMatchIn(lower) }) TransactionType.INCOME else TransactionType.EXPENSE
        val note = lower.replace(matched, " ")
            .replace(Regex("""\b(beli|bayar|buat|untuk|pakai|pake|via|dengan|rupiah|rp)\b"""), " ")
            .replace(Regex("""\s+"""), " ").trim()
            .replaceFirstChar { it.uppercase() }
            .take(40)
            .ifBlank { null }
        return VoiceDraft(type, amount, note)
    }

    private fun numericAmount(lower: String): Pair<Long, String>? {
        // Pilih angka dengan satuan bila ada; kalau tidak, angka terbesar (≥ 100) agar "2 kopi 30 ribu" → 30.000.
        val matches = numericRegex.findAll(lower).toList()
        val candidates = matches.mapNotNull { m ->
            val raw = m.groupValues[1]
            val unit = m.groupValues[2].lowercase()
            val value = if (unit.isNotEmpty()) {
                (raw.replace(',', '.').toDoubleOrNull() ?: return@mapNotNull null) * (units[unit] ?: 1L)
            } else {
                raw.replace(".", "").replace(",", "").toDoubleOrNull() ?: return@mapNotNull null
            }
            Triple(value.toLong(), m.value, unit.isNotEmpty() || m.value.startsWith("rp"))
        }
        val best = candidates.filter { it.third }.maxByOrNull { it.first }
            ?: candidates.filter { it.first >= 100 }.maxByOrNull { it.first }
            ?: return null
        return best.first to best.second
    }

    /** "dua puluh lima ribu" → 25000, "seratus lima puluh ribu" → 150000, "satu setengah juta" → 1500000. */
    private fun wordAmount(lower: String): Pair<Long, String>? {
        val tokens = lower.split(" ")
        var start = -1
        var end = -1
        var total = 0.0
        var current = 0L // nilai di bawah ribuan yang sedang disusun
        var last = 0L    // satu digit terakhir, menunggu "puluh"/"ratus"/"belas"
        var half = false
        for ((i, t) in tokens.withIndex()) {
            val isNumberWord = t in digits || t in fixed || t == "seribu" || t == "sejuta" ||
                (start >= 0 && t in setOf("puluh", "ratus", "belas", "ribu", "juta", "setengah"))
            if (!isNumberWord) {
                if (start >= 0) break else continue
            }
            if (start < 0) start = i
            end = i
            when (t) {
                in digits -> { current += last; last = digits.getValue(t).toLong() }
                in fixed -> { current += last + fixed.getValue(t); last = 0 }
                "puluh" -> { current += last * 10; last = 0 }
                "ratus" -> { current += last * 100; last = 0 }
                "belas" -> { current += last + 10; last = 0 }
                "setengah" -> half = true
                "seribu" -> total += 1_000.0
                "sejuta" -> total += 1_000_000.0
                "ribu", "juta" -> {
                    val unit = if (t == "ribu") 1_000.0 else 1_000_000.0
                    val base = (current + last).let { if (it == 0L) 1L else it }
                    total += (base + if (half) 0.5 else 0.0) * unit
                    current = 0; last = 0; half = false
                }
            }
        }
        if (start < 0) return null
        total += current + last
        if (total < 100) return null
        return total.toLong() to tokens.subList(start, end + 1).joinToString(" ")
    }
}
