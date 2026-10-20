package com.aamnnr.duitku.domain

import com.aamnnr.duitku.data.local.BudgetEntity
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/** Rentang tanggal periode anggaran yang sedang berjalan. */
fun budgetPeriod(period: String, today: LocalDate = LocalDate.now()): Pair<LocalDate, LocalDate> = when (period) {
    "WEEKLY" -> {
        val start = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        start to start.plusDays(6)
    }
    else -> today.withDayOfMonth(1) to today.withDayOfMonth(today.lengthOfMonth())
}

data class BudgetProgress(
    val budget: BudgetEntity,
    val spent: Long,
    val start: LocalDate,
    val end: LocalDate,
) {
    val ratio: Float get() = if (budget.amount > 0) spent.toFloat() / budget.amount else 0f
    val remaining: Long get() = budget.amount - spent

    /** Sisa yang aman dibelanjakan per hari sampai akhir periode (0 bila sudah habis). */
    fun safePerDay(today: LocalDate = LocalDate.now()): Long {
        val daysLeft = (end.toEpochDay() - today.toEpochDay() + 1).coerceAtLeast(1)
        return (remaining / daysLeft).coerceAtLeast(0)
    }
}

/** Pengeluaran (bukan transfer/penyesuaian) yang masuk ke anggaran ini pada periode berjalan. */
fun budgetProgress(budget: BudgetEntity, items: List<TransactionItem>, today: LocalDate = LocalDate.now()): BudgetProgress {
    val (start, end) = budgetPeriod(budget.period, today)
    val zone = ZoneId.systemDefault()
    val cats = budget.categoryIdList.toSet()
    val spent = items.filter {
        it.isCashflow && it.type == TransactionType.EXPENSE &&
            (cats.isEmpty() || it.categoryId in cats) &&
            Instant.ofEpochMilli(it.date).atZone(zone).toLocalDate().let { d -> d in start..end }
    }.sumOf { it.amount }
    return BudgetProgress(budget, spent, start, end)
}

/** Tagihan yang tampak berulang tiap bulan. */
data class RecurringBill(
    val name: String,
    val categoryId: String?,
    val categoryName: String?,
    val categoryIcon: String?,
    val typicalAmount: Long,
    val dayOfMonth: Int,
    val nextDue: LocalDate,
    val months: Int,
)

/**
 * Deteksi tagihan rutin dari riwayat ±4 bulan: pengeluaran dengan catatan/kategori yang sama,
 * muncul di ≥ 2 bulan berbeda pada tanggal yang mirip (selisih ≤ 4 hari).
 */
fun detectRecurring(items: List<TransactionItem>, today: LocalDate = LocalDate.now()): List<RecurringBill> {
    val zone = ZoneId.systemDefault()
    val expenses = items.filter { it.isCashflow && it.type == TransactionType.EXPENSE }
    return expenses
        .groupBy { (it.note?.trim()?.lowercase()?.takeIf { n -> n.isNotEmpty() } ?: "#${it.categoryId}") to it.categoryId }
        .mapNotNull { (_, list) ->
            val dated = list.map { it to Instant.ofEpochMilli(it.date).atZone(zone).toLocalDate() }
            val byMonth = dated.groupBy { (_, d) -> d.year * 12 + d.monthValue }
            if (byMonth.size < 2) return@mapNotNull null
            val days = byMonth.values.map { entries -> entries.minOf { it.second.dayOfMonth } }
            if (days.max() - days.min() > 4) return@mapNotNull null
            val day = days.sorted()[days.size / 2]
            val last = dated.maxBy { it.second }.first
            val thisMonthDue = today.withDayOfMonth(day.coerceAtMost(today.lengthOfMonth()))
            val paidThisMonth = byMonth.containsKey(today.year * 12 + today.monthValue)
            val next = if (paidThisMonth || thisMonthDue.isBefore(today)) {
                today.plusMonths(1).let { it.withDayOfMonth(day.coerceAtMost(it.lengthOfMonth())) }
            } else thisMonthDue
            RecurringBill(
                name = last.note?.takeIf { it.isNotBlank() } ?: last.categoryName ?: "Tagihan",
                categoryId = last.categoryId,
                categoryName = last.categoryName,
                categoryIcon = last.categoryIcon,
                typicalAmount = list.map { it.amount }.sorted()[list.size / 2],
                dayOfMonth = day,
                nextDue = next,
                months = byMonth.size,
            )
        }
        .sortedBy { it.nextDue }
}

/** Proyeksi pengeluaran akhir bulan dan setahun dari laju harian bulan berjalan. */
data class Projection(val monthEnd: Long, val year: Long)

fun projectSpending(spentThisMonth: Long, today: LocalDate = LocalDate.now()): Projection {
    val perDay = spentThisMonth.toDouble() / today.dayOfMonth
    return Projection((perDay * today.lengthOfMonth()).toLong(), (perDay * 365).toLong())
}
