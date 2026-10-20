package com.aamnnr.duitku.ui

import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

val LocaleID: Locale = Locale.forLanguageTag("id-ID")

private val numberFormat = NumberFormat.getIntegerInstance(LocaleID)

/** 1250000 -> "Rp 1.250.000", -5000 -> "-Rp 5.000" */
fun formatRupiah(amount: Long): String {
    val abs = numberFormat.format(kotlin.math.abs(amount))
    return if (amount < 0) "-Rp $abs" else "Rp $abs"
}

fun formatThousands(amount: Long): String = numberFormat.format(amount)

/** Versi ringkas untuk kartu kecil: 1680000 -> "Rp 1,68 jt", 612000 -> "Rp 612rb". */
fun formatCompact(amount: Long): String {
    val abs = kotlin.math.abs(amount)
    val sign = if (amount < 0) "-" else ""
    fun dec(v: Double) = String.format(LocaleID, "%.2f", v).trimEnd('0').trimEnd(',')
    val body = when {
        abs >= 1_000_000_000 -> "${dec(abs / 1e9)} M"
        abs >= 1_000_000 -> "${dec(abs / 1e6)} jt"
        abs >= 1_000 -> "${abs / 1_000}rb"
        else -> abs.toString()
    }
    return "${sign}Rp $body"
}

/** "1.250.000" -> 1250000; teks kosong -> 0 */
fun parseAmount(text: String): Long = text.filter(Char::isDigit).take(15).toLongOrNull() ?: 0L

private val zone: ZoneId get() = ZoneId.systemDefault()

fun Long.toLocalDate(): LocalDate = Instant.ofEpochMilli(this).atZone(zone).toLocalDate()

/** Rentang epoch millis [awal, akhir] untuk satu bulan. */
fun YearMonth.millisRange(): Pair<Long, Long> {
    val start = atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
    val end = plusMonths(1).atDay(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1
    return start to end
}

val YearMonth.startDate: LocalDate get() = atDay(1)
val YearMonth.endDate: LocalDate get() = atEndOfMonth()

fun LocalDate.startMillis(): Long = atStartOfDay(zone).toInstant().toEpochMilli()
fun LocalDate.endMillis(): Long = plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1

private val dayMonthFormat = DateTimeFormatter.ofPattern("d MMM", LocaleID)
fun LocalDate.dayMonth(): String = format(dayMonthFormat)

fun YearMonth.shortLabel(): String = "${month.getDisplayName(TextStyle.SHORT, LocaleID)} $year"

fun YearMonth.label(): String =
    "${month.getDisplayName(TextStyle.FULL, LocaleID)} $year"

private val dayHeaderFormat = DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", LocaleID)
private val shortDateFormat = DateTimeFormatter.ofPattern("d MMM yyyy", LocaleID)

fun LocalDate.dayHeader(): String {
    val today = LocalDate.now()
    return when (this) {
        today -> "Hari ini"
        today.minusDays(1) -> "Kemarin"
        else -> format(dayHeaderFormat)
    }
}

fun LocalDate.shortLabel(): String = format(shortDateFormat)

/** Menggabungkan tanggal yang dipilih dengan jam sekarang, agar urutan transaksi di hari yang sama tetap wajar. */
fun LocalDate.withCurrentTimeMillis(): Long =
    atTime(java.time.LocalTime.now()).atZone(zone).toInstant().toEpochMilli()
