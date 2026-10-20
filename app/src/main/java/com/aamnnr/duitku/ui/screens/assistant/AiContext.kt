package com.aamnnr.duitku.ui.screens.assistant

import com.aamnnr.duitku.AppContainer
import com.aamnnr.duitku.data.remote.CategoryRef
import com.aamnnr.duitku.data.remote.LedgerContext
import com.aamnnr.duitku.data.remote.WalletRef
import com.aamnnr.duitku.domain.TransactionType
import com.aamnnr.duitku.domain.computeStats
import com.aamnnr.duitku.ui.endDate
import com.aamnnr.duitku.ui.label
import com.aamnnr.duitku.ui.millisRange
import com.aamnnr.duitku.ui.startDate
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.addJsonObject
import java.time.LocalDate
import java.time.YearMonth

/** Daftar kategori & dompet profil aktif agar AI memakai ID yang benar. Hanya nama, tanpa saldo. */
suspend fun buildLedgerContext(c: AppContainer): LedgerContext {
    val pid = c.profiles.activeProfileId.first()
    val categories = c.categories.observeAll(pid).first().map { CategoryRef(it.id, it.name, it.type.name) }
    val wallets = c.wallets.observeWithBalance(pid).first().map { WalletRef(it.id, it.name) }
    return LedgerContext(categories, wallets, LocalDate.now().toString())
}

/**
 * Ringkasan bulan ini (dan bulan lalu) untuk pertanyaan analisa.
 * Sengaja hanya total dan per kategori, tanpa catatan transaksi atau saldo dompet.
 */
suspend fun buildSummary(c: AppContainer): JsonObject {
    val pid = c.profiles.activeProfileId.first()
    suspend fun month(m: YearMonth): JsonObject {
        val (from, to) = m.millisRange()
        val items = c.transactions.observeItems(pid, from, to).first()
        val stats = computeStats(items, m.startDate, m.endDate)
        val top = c.transactions.observeCategoryTotals(pid, TransactionType.EXPENSE, from, to).first().take(8)
        return buildJsonObject {
            put("period", m.label())
            put("income", stats.income)
            put("expense", stats.expense)
            put("transaction_count", stats.count)
            put("avg_expense_per_day", stats.avgExpensePerDay)
            putJsonArray("expense_by_category") {
                top.forEach { t -> addJsonObject { put("name", t.name); put("total", t.total) } }
            }
        }
    }
    val now = YearMonth.now()
    return buildJsonObject {
        put("today", LocalDate.now().toString())
        put("this_month", month(now))
        put("last_month", month(now.minusMonths(1)))
    }
}
