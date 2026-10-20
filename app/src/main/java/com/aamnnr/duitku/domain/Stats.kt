package com.aamnnr.duitku.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Ringkasan arus kas untuk satu rentang tanggal (transfer & penyesuaian tidak dihitung). */
data class PeriodStats(
    val income: Long = 0,
    val expense: Long = 0,
    val count: Int = 0,
    val avgExpensePerDay: Long = 0,
    val largestExpense: TransactionItem? = null,
)

val TransactionItem.isCashflow: Boolean get() = !isTransfer && !isAdjustment

fun computeStats(items: List<TransactionItem>, start: LocalDate, end: LocalDate, today: LocalDate = LocalDate.now()): PeriodStats {
    val flow = items.filter { it.isCashflow }
    val expenses = flow.filter { it.type == TransactionType.EXPENSE }
    val expense = expenses.sumOf { it.amount }
    // Untuk periode yang sedang berjalan, rata-rata dihitung sampai hari ini saja.
    val lastDay = if (today in start..end) today else end
    val days = (lastDay.toEpochDay() - start.toEpochDay() + 1).coerceAtLeast(1)
    return PeriodStats(
        income = flow.filter { it.type == TransactionType.INCOME }.sumOf { it.amount },
        expense = expense,
        count = flow.size,
        avgExpensePerDay = expense / days,
        largestExpense = expenses.maxByOrNull { it.amount },
    )
}

/** Total pengeluaran per hari dari [start] sampai [end] (inklusif). */
fun dailyExpenses(items: List<TransactionItem>, start: LocalDate, end: LocalDate, zone: ZoneId = ZoneId.systemDefault()): List<Long> {
    val days = (end.toEpochDay() - start.toEpochDay() + 1).toInt().coerceAtLeast(1)
    val totals = LongArray(days)
    items.filter { it.isCashflow && it.type == TransactionType.EXPENSE }.forEach {
        val d = Instant.ofEpochMilli(it.date).atZone(zone).toLocalDate()
        val idx = (d.toEpochDay() - start.toEpochDay()).toInt()
        if (idx in 0 until days) totals[idx] += it.amount
    }
    return totals.toList()
}

/** Total pengeluaran per bulan dari bulan [start] sampai bulan [end]. */
fun monthlyExpenses(items: List<TransactionItem>, start: LocalDate, end: LocalDate, zone: ZoneId = ZoneId.systemDefault()): List<Long> {
    val months = ((end.year - start.year) * 12 + end.monthValue - start.monthValue + 1).coerceAtLeast(1)
    val totals = LongArray(months)
    items.filter { it.isCashflow && it.type == TransactionType.EXPENSE }.forEach {
        val d = Instant.ofEpochMilli(it.date).atZone(zone).toLocalDate()
        val idx = (d.year - start.year) * 12 + d.monthValue - start.monthValue
        if (idx in 0 until months) totals[idx] += it.amount
    }
    return totals.toList()
}
