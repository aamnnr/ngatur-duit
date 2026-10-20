package com.aamnnr.duitku.ui.screens.history

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.aamnnr.duitku.AppContainer
import com.aamnnr.duitku.data.local.CategoryEntity
import com.aamnnr.duitku.domain.TransactionItem
import com.aamnnr.duitku.domain.TransactionType
import com.aamnnr.duitku.domain.WalletWithBalance
import com.aamnnr.duitku.domain.isCashflow
import com.aamnnr.duitku.ui.LocalUndo
import com.aamnnr.duitku.ui.appContainer
import com.aamnnr.duitku.ui.appViewModel
import com.aamnnr.duitku.ui.components.EmptyState
import com.aamnnr.duitku.ui.components.PeriodPill
import com.aamnnr.duitku.ui.components.StatusBarIcons
import com.aamnnr.duitku.ui.components.SwipeTransactionRow
import com.aamnnr.duitku.ui.dayHeader
import com.aamnnr.duitku.ui.formatRupiah
import com.aamnnr.duitku.ui.label
import com.aamnnr.duitku.ui.millisRange
import com.aamnnr.duitku.ui.theme.ExpenseRed
import com.aamnnr.duitku.ui.theme.IncomeGreen
import com.aamnnr.duitku.ui.toLocalDate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.time.LocalDate
import java.time.YearMonth

enum class TypeFilter(val label: String) { ALL("Semua"), EXPENSE("Pengeluaran"), INCOME("Pemasukan"), TRANSFER("Transfer") }

data class HistoryFilter(
    val month: YearMonth = YearMonth.now(),
    val type: TypeFilter = TypeFilter.ALL,
    val walletId: String? = null,
    val categoryId: String? = null,
    val query: String = "",
)

data class DayGroup(val date: LocalDate, val items: List<TransactionItem>, val net: Long)

data class HistoryState(
    val filter: HistoryFilter = HistoryFilter(),
    val wallets: List<WalletWithBalance> = emptyList(),
    val categories: List<CategoryEntity> = emptyList(),
    val days: List<DayGroup> = emptyList(),
    val totalIncome: Long = 0,
    val totalExpense: Long = 0,
)

@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModel(c: AppContainer, initial: HistoryFilter) : ViewModel() {
    private val filter = MutableStateFlow(initial)

    val state = c.profiles.activeProfileId.flatMapLatest { pid ->
        filter.flatMapLatest { f ->
            val (from, to) = f.month.millisRange()
            combine(
                c.transactions.observeItems(pid, from, to),
                c.wallets.observeWithBalance(pid),
                c.categories.observeAll(pid),
            ) { items, wallets, categories ->
                val filtered = items.filter { it.matches(f) }
                val flow = filtered.filter { it.isCashflow }
                HistoryState(
                    filter = f,
                    wallets = wallets,
                    categories = categories,
                    days = filtered.groupBy { it.date.toLocalDate() }.map { (date, list) ->
                        val dayFlow = list.filter { it.isCashflow }
                        DayGroup(date, list, dayFlow.sumOf { if (it.type == TransactionType.INCOME) it.amount else -it.amount })
                    },
                    totalIncome = flow.filter { it.type == TransactionType.INCOME }.sumOf { it.amount },
                    totalExpense = flow.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount },
                )
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryState(filter = initial))

    private fun TransactionItem.matches(f: HistoryFilter): Boolean {
        if (f.walletId != null && walletId != f.walletId) return false
        if (f.categoryId != null && categoryId != f.categoryId) return false
        val typeOk = when (f.type) {
            TypeFilter.ALL -> true
            TypeFilter.TRANSFER -> isTransfer
            TypeFilter.EXPENSE -> !isTransfer && type == TransactionType.EXPENSE
            TypeFilter.INCOME -> !isTransfer && type == TransactionType.INCOME
        }
        if (!typeOk) return false
        val q = f.query.trim()
        if (q.isEmpty()) return true
        val digits = q.filter(Char::isDigit)
        return listOfNotNull(note, categoryName, walletName).any { it.contains(q, ignoreCase = true) } ||
            (digits.isNotEmpty() && amount.toString().contains(digits))
    }

    fun shiftMonth(delta: Long) = filter.update { it.copy(month = it.month.plusMonths(delta)) }
    fun setType(t: TypeFilter) = filter.update { it.copy(type = t) }
    fun setWallet(id: String?) = filter.update { it.copy(walletId = id) }
    fun setCategory(id: String?) = filter.update { it.copy(categoryId = id) }
    fun setQuery(q: String) = filter.update { it.copy(query = q) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    initialWalletId: String?,
    initialCategoryId: String?,
    initialMonth: YearMonth?,
    onOpenTransaction: (TransactionItem) -> Unit,
    onBack: () -> Unit,
) {
    val initial = HistoryFilter(
        month = initialMonth ?: YearMonth.now(),
        walletId = initialWalletId,
        categoryId = initialCategoryId,
    )
    val vm = appViewModel(key = "history-$initialWalletId-$initialCategoryId-$initialMonth") { HistoryViewModel(it, initial) }
    val s by vm.state.collectAsStateWithLifecycle()
    val undo = LocalUndo.current
    val container = appContainer()
    var walletMenu by remember { mutableStateOf(false) }
    StatusBarIcons(lightIcons = false)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Riwayat transaksi") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Kembali") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        LazyColumn(Modifier.padding(padding)) {
            item {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    PeriodPill(
                        label = s.filter.month.label(),
                        onPrev = { vm.shiftMonth(-1) },
                        onNext = { vm.shiftMonth(1) },
                        canNext = s.filter.month < YearMonth.now(),
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    )
                }
            }
            item {
                OutlinedTextField(
                    value = s.filter.query,
                    onValueChange = vm::setQuery,
                    placeholder = { Text("Cari catatan, kategori, nominal") },
                    leadingIcon = { Icon(Icons.Outlined.Search, null) },
                    trailingIcon = {
                        if (s.filter.query.isNotEmpty()) IconButton(onClick = { vm.setQuery("") }) { Icon(Icons.Filled.Close, "Hapus") }
                    },
                    singleLine = true,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            item {
                Row(
                    Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    val categoryName = s.categories.firstOrNull { it.id == s.filter.categoryId }?.name
                    if (categoryName != null) {
                        FilterChip(
                            selected = true,
                            onClick = { vm.setCategory(null) },
                            label = { Text(categoryName) },
                            trailingIcon = { Icon(Icons.Filled.Close, "Hapus filter", Modifier.padding(start = 2.dp)) },
                            colors = FilterChipDefaults.filterChipColors(),
                        )
                    }
                    TypeFilter.entries.forEach { t ->
                        FilterChip(selected = s.filter.type == t, onClick = { vm.setType(t) }, label = { Text(t.label) })
                    }
                    Box {
                        val walletName = s.wallets.firstOrNull { it.id == s.filter.walletId }?.name
                        FilterChip(selected = walletName != null, onClick = { walletMenu = true },
                            label = { Text(walletName ?: "Semua dompet") })
                        DropdownMenu(expanded = walletMenu, onDismissRequest = { walletMenu = false }) {
                            DropdownMenuItem(text = { Text("Semua dompet") }, onClick = { vm.setWallet(null); walletMenu = false })
                            s.wallets.forEach { w ->
                                DropdownMenuItem(text = { Text(w.name) }, onClick = { vm.setWallet(w.id); walletMenu = false })
                            }
                        }
                    }
                }
            }
            item {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text("Masuk ${formatRupiah(s.totalIncome)}", color = IncomeGreen, style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f))
                    Text("Keluar ${formatRupiah(s.totalExpense)}", color = ExpenseRed, style = MaterialTheme.typography.bodyMedium)
                }
            }
            if (s.days.isEmpty()) {
                item { EmptyState("Tidak ada transaksi", "Coba pilih bulan lain atau ubah filter.") }
            } else {
                item {
                    Text("Geser ke kiri untuk hapus, ke kanan untuk ubah", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp))
                }
            }
            s.days.forEach { day ->
                item(key = "day-${day.date}") {
                    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp)) {
                        Text(day.date.dayHeader(), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                        if (day.net != 0L) {
                            Text(formatRupiah(day.net), style = MaterialTheme.typography.labelLarge,
                                color = if (day.net >= 0) IncomeGreen else ExpenseRed)
                        }
                    }
                }
                items(day.items, key = { it.id }) { tx ->
                    SwipeTransactionRow(
                        tx,
                        onClick = { onOpenTransaction(tx) },
                        onDelete = {
                            undo.perform(
                                message = "Transaksi dihapus",
                                action = { container.transactions.delete(tx.id) },
                                undo = { container.transactions.restore(tx.id) },
                            )
                        },
                        modifier = Modifier.animateItem(),
                    )
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}
