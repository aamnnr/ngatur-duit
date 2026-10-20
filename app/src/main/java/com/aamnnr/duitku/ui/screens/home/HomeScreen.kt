package com.aamnnr.duitku.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.DocumentScanner
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.aamnnr.duitku.AppContainer
import com.aamnnr.duitku.data.remote.Account
import com.aamnnr.duitku.data.sync.SyncState
import com.aamnnr.duitku.domain.TransactionItem
import com.aamnnr.duitku.domain.WalletWithBalance
import com.aamnnr.duitku.domain.computeStats
import com.aamnnr.duitku.domain.isCashflow
import com.aamnnr.duitku.domain.TransactionType
import com.aamnnr.duitku.ui.LocaleID
import com.aamnnr.duitku.ui.appViewModel
import com.aamnnr.duitku.ui.components.AmountField
import com.aamnnr.duitku.ui.components.AnimatedRupiah
import com.aamnnr.duitku.ui.components.EmptyState
import com.aamnnr.duitku.ui.components.LocalHideBalance
import com.aamnnr.duitku.ui.components.MonthlyProgressCard
import com.aamnnr.duitku.ui.components.NeuCircleButton
import com.aamnnr.duitku.ui.components.OverlineText
import com.aamnnr.duitku.ui.components.QuickActionCard
import com.aamnnr.duitku.ui.components.StatusBarIcons
import com.aamnnr.duitku.ui.components.SyncChip
import com.aamnnr.duitku.ui.components.SyncStatus
import com.aamnnr.duitku.ui.components.TransactionRow
import com.aamnnr.duitku.ui.components.WalletHeroCard
import com.aamnnr.duitku.ui.components.amountFieldValue
import com.aamnnr.duitku.ui.components.money
import com.aamnnr.duitku.ui.components.signedAmount
import com.aamnnr.duitku.ui.endDate
import com.aamnnr.duitku.ui.millisRange
import com.aamnnr.duitku.ui.startDate
import com.aamnnr.duitku.ui.theme.ExpenseRed
import com.aamnnr.duitku.ui.theme.IncomeGreen
import com.aamnnr.duitku.ui.theme.TransferBlue
import com.aamnnr.duitku.ui.toLocalDate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter

data class HomeState(
    val userName: String = "",
    val wallets: List<WalletWithBalance> = emptyList(),
    val monthExpense: Long = 0,
    val monthlyLimit: Long = 0,
    val recent: List<TransactionItem> = emptyList(),
) {
    val totalBalance: Long get() = wallets.sumOf { it.balance }
}

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(private val c: AppContainer) : ViewModel() {
    val state = c.profiles.activeProfileId.flatMapLatest { pid ->
        val (from, to) = YearMonth.now().millisRange()
        val month = YearMonth.now()
        val name = combine(c.profiles.activeProfile, c.settings.displayName) { p, n -> n.ifBlank { p?.name.orEmpty() } }
        combine(
            name,
            c.wallets.observeWithBalance(pid),
            c.transactions.observeItems(pid, from, to),
            c.settings.monthlyLimit(pid),
            c.transactions.observeRecent(pid, 8),
        ) { n, wallets, items, limit, recent ->
            HomeState(n, wallets, computeStats(items, month.startDate, month.endDate).expense, limit, recent)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeState())

    val account = c.cloud.account.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), c.cloud.currentAccount())
    val sync = c.sync.state
    val cloudEnabled = c.cloud.isConfigured
    val localDrafts = c.notificationDrafts.pendingCount.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    fun toggleHide(current: Boolean) = viewModelScope.launch { c.settings.setHideBalance(!current) }

    fun setLimit(amount: Long) = viewModelScope.launch {
        c.settings.setMonthlyLimit(c.profiles.activeProfileId.first(), amount)
    }
}

private val dayFormat = DateTimeFormatter.ofPattern("d MMM yyyy", LocaleID)

private fun greeting(): String = when (LocalTime.now().hour) {
    in 4..10 -> "Selamat Pagi,"
    in 11..14 -> "Selamat Siang,"
    in 15..17 -> "Selamat Sore,"
    else -> "Selamat Malam,"
}

@Composable
fun HomeScreen(
    onOpenTransaction: (TransactionItem) -> Unit,
    onSeeAll: () -> Unit,
    onOpenWallet: (String) -> Unit,
    onOpenProfile: () -> Unit,
    onOpenInbox: () -> Unit,
    onTransfer: () -> Unit,
    onScan: () -> Unit,
    onBills: () -> Unit,
    onInsight: () -> Unit,
    onPlan: () -> Unit,
) {
    val vm = appViewModel { HomeViewModel(it) }
    val s by vm.state.collectAsStateWithLifecycle()
    val account by vm.account.collectAsStateWithLifecycle()
    val sync by vm.sync.collectAsStateWithLifecycle()
    val localDrafts by vm.localDrafts.collectAsStateWithLifecycle()
    val hidden = LocalHideBalance.current
    var editLimit by remember { mutableStateOf(false) }
    StatusBarIcons(lightIcons = false)

    val syncStatus = when {
        !vm.cloudEnabled || account == null -> SyncStatus.LOCAL
        sync.running -> SyncStatus.SYNCING
        sync.error != null -> SyncStatus.OFFLINE
        else -> SyncStatus.SYNCED
    }
    val days = s.recent.groupBy { it.date.toLocalDate() }

    LazyColumn(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding(),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        item {
            Header(
                name = s.userName,
                synced = syncStatus == SyncStatus.SYNCED,
                hasDrafts = sync.pendingDrafts + localDrafts > 0,
                onOpenProfile = onOpenProfile,
                onOpenInbox = onOpenInbox,
            )
        }
        item {
            BalanceBlock(
                total = s.totalBalance,
                hidden = hidden,
                status = syncStatus,
                caption = syncCaption(account, sync),
                onToggleHide = { vm.toggleHide(hidden) },
            )
        }
        if (s.wallets.isNotEmpty()) {
            item {
                val pager = rememberPagerState { s.wallets.size }
                HorizontalPager(
                    state = pager,
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    pageSpacing = 12.dp,
                    modifier = Modifier.padding(top = 16.dp),
                ) { page ->
                    val w = s.wallets[page]
                    WalletHeroCard(w, s.userName, modifier = Modifier.padding(vertical = 10.dp), onClick = { onOpenWallet(w.id) })
                }
                if (s.wallets.size > 1) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                        repeat(s.wallets.size) { i ->
                            val active = i == pager.currentPage
                            Box(
                                Modifier
                                    .padding(horizontal = 3.dp)
                                    .size(width = if (active) 18.dp else 6.dp, height = 6.dp)
                                    .clip(CircleShape)
                                    .background(if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline),
                            )
                        }
                    }
                }
            }
        }
        item {
            MonthlyProgressCard(
                title = "Pengeluaran Bulan Ini",
                spent = s.monthExpense,
                limit = s.monthlyLimit,
                onSetLimit = { editLimit = true },
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp),
            )
        }
        item { SectionHeader("Aksi Cepat", "Lainnya", onPlan) }
        item {
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    QuickActionCard(Icons.AutoMirrored.Outlined.Send, "Transfer", "Pindah saldo antar dompet", TransferBlue, onTransfer, Modifier.weight(1f))
                    QuickActionCard(Icons.Outlined.DocumentScanner, "Scan Struk", "Otomatis dibaca AI", MaterialTheme.colorScheme.secondary, onScan,
                        Modifier.weight(1f), badge = "AI")
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    QuickActionCard(Icons.Outlined.Bolt, "Tagihan", "Listrik, air & pulsa", ExpenseRed, onBills, Modifier.weight(1f))
                    QuickActionCard(Icons.Outlined.BarChart, "Insight", "Tren pengeluaran", IncomeGreen, onInsight, Modifier.weight(1f))
                }
            }
        }
        item { SectionHeader("Riwayat Transaksi", "Lihat Semua", onSeeAll) }
        if (days.isEmpty()) {
            item { EmptyState("Belum ada catatan", "Tekan + di bawah untuk mulai mencatat.") }
        }
        days.forEach { (date, list) ->
            item(key = "day-$date") {
                val net = list.filter { it.isCashflow }.sumOf { if (it.type == TransactionType.INCOME) it.amount else -it.amount }
                Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 6.dp)) {
                    Text(dayLabel(date), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f))
                    if (net != 0L) {
                        Text(money(kotlin.math.abs(net), if (net > 0) "+" else "-") + if (net > 0) " net" else "",
                            style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold,
                            color = if (net > 0) IncomeGreen else ExpenseRed)
                    }
                }
            }
            items(list, key = { "tx-${it.id}" }) { TransactionRow(it, onClick = { onOpenTransaction(it) }) }
        }
    }

    if (editLimit) {
        LimitDialog(current = s.monthlyLimit, onDismiss = { editLimit = false }) {
            vm.setLimit(it)
            editLimit = false
        }
    }
}

private fun dayLabel(date: LocalDate): String {
    val today = LocalDate.now()
    return when (date) {
        today -> "Hari ini, ${date.format(dayFormat)}"
        today.minusDays(1) -> "Kemarin, ${date.format(dayFormat)}"
        else -> date.format(dayFormat)
    }
}

private fun syncCaption(account: Account?, sync: SyncState): String = when {
    account == null -> "Data tersimpan aman di HP ini"
    sync.running -> "Menyinkronkan…"
    sync.error != null -> "Sinkron tertunda, dicoba lagi otomatis"
    sync.lastSuccessAt <= 0 -> "Belum pernah disinkron"
    else -> {
        val minutes = (System.currentTimeMillis() - sync.lastSuccessAt) / 60_000
        when {
            minutes < 1 -> "Terakhir disinkron baru saja"
            minutes < 60 -> "Terakhir disinkron $minutes menit lalu"
            minutes < 24 * 60 -> "Terakhir disinkron ${minutes / 60} jam lalu"
            else -> "Terakhir disinkron ${minutes / (24 * 60)} hari lalu"
        }
    }
}

@Composable
private fun Header(name: String, synced: Boolean, hasDrafts: Boolean, onOpenProfile: () -> Unit, onOpenInbox: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.clip(CircleShape).clickable(onClick = onOpenProfile)) {
            Box(
                Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .border(2.dp, MaterialTheme.colorScheme.surfaceContainerLowest, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(initials(name), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            }
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.background)
                    .padding(2.dp)
                    .clip(CircleShape)
                    .background(if (synced) IncomeGreen else MaterialTheme.colorScheme.outline),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(greeting(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(name.ifBlank { "Duitku" }, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        NeuCircleButton(Icons.Outlined.Notifications, "Inbox", onOpenInbox, badge = hasDrafts)
    }
}

private fun initials(name: String): String =
    name.split(" ").filter { it.isNotBlank() }.take(2).joinToString("") { it.take(1).uppercase() }.ifEmpty { "D" }

@Composable
private fun BalanceBlock(total: Long, hidden: Boolean, status: SyncStatus, caption: String, onToggleHide: () -> Unit) {
    Column(Modifier.padding(start = 20.dp, end = 16.dp, top = 22.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OverlineText("Total saldo aktif")
            Spacer(Modifier.width(8.dp))
            SyncChip(status)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            AnimatedRupiah(total, MaterialTheme.typography.displaySmall, modifier = Modifier.weight(1f))
            NeuCircleButton(
                if (hidden) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                if (hidden) "Tampilkan saldo" else "Sembunyikan saldo",
                onToggleHide,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                when (status) {
                    SyncStatus.LOCAL -> Icons.Outlined.PhoneAndroid
                    SyncStatus.OFFLINE -> Icons.Outlined.CloudOff
                    else -> Icons.Outlined.CloudDone
                },
                null,
                tint = if (status == SyncStatus.SYNCED) IncomeGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(14.dp),
            )
            Text(" $caption", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SectionHeader(title: String, action: String, onAction: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 24.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        TextButton(onClick = onAction) {
            Text(action, color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
fun LimitDialog(current: Long, onDismiss: () -> Unit, onSave: (Long) -> Unit) {
    var value by remember { mutableStateOf(amountFieldValue(current)) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Batas pengeluaran bulanan") },
        text = {
            Column {
                Text("Dipakai untuk progres \"Pengeluaran Bulan Ini\". Isi 0 untuk menonaktifkan.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                AmountField(value, { value = it }, label = "Batas per bulan")
            }
        },
        confirmButton = { TextButton(onClick = { onSave(value.signedAmount().coerceAtLeast(0)) }) { Text("Simpan") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } },
    )
}
