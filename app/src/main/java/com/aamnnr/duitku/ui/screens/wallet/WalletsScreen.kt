package com.aamnnr.duitku.ui.screens.wallet

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.aamnnr.duitku.AppContainer
import com.aamnnr.duitku.domain.TransactionItem
import com.aamnnr.duitku.domain.TransactionType
import com.aamnnr.duitku.domain.WalletWithBalance
import com.aamnnr.duitku.ui.AppIcons
import com.aamnnr.duitku.ui.LocalUndo
import com.aamnnr.duitku.ui.appContainer
import com.aamnnr.duitku.ui.appViewModel
import com.aamnnr.duitku.ui.components.AmountField
import com.aamnnr.duitku.ui.components.ColorPicker
import com.aamnnr.duitku.ui.components.ConfirmDialog
import com.aamnnr.duitku.ui.components.EmptyState
import com.aamnnr.duitku.ui.components.IconPicker
import com.aamnnr.duitku.ui.components.NeuCircleButton
import com.aamnnr.duitku.ui.components.StatusBarIcons
import com.aamnnr.duitku.ui.components.SwipeTransactionRow
import com.aamnnr.duitku.ui.components.WalletHeroCard
import com.aamnnr.duitku.ui.components.amountFieldValue
import com.aamnnr.duitku.ui.components.money
import com.aamnnr.duitku.ui.components.neuSurface
import com.aamnnr.duitku.ui.components.signedAmount
import com.aamnnr.duitku.ui.dayHeader
import com.aamnnr.duitku.ui.formatRupiah
import com.aamnnr.duitku.ui.label
import com.aamnnr.duitku.ui.millisRange
import com.aamnnr.duitku.ui.theme.ExpenseRed
import com.aamnnr.duitku.ui.theme.IncomeGreen
import com.aamnnr.duitku.ui.toLocalDate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.YearMonth

data class WalletsState(
    val wallets: List<WalletWithBalance> = emptyList(),
    val ownerName: String = "",
    val monthItems: List<TransactionItem> = emptyList(),
)

@OptIn(ExperimentalCoroutinesApi::class)
class WalletsViewModel(private val c: AppContainer) : ViewModel() {
    val selection = c.walletSelection

    val state = c.profiles.activeProfileId.flatMapLatest { pid ->
        val (from, to) = YearMonth.now().millisRange()
        val name = combine(c.profiles.activeProfile, c.settings.displayName) { p, n -> n.ifBlank { p?.name.orEmpty() } }
        combine(c.wallets.observeWithBalance(pid), name, c.transactions.observeItems(pid, from, to)) { w, n, items ->
            WalletsState(w, n, items)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WalletsState())

    fun select(id: String) {
        selection.value = id
    }

    fun save(id: String?, form: WalletForm) = viewModelScope.launch {
        c.wallets.save(
            c.profiles.activeProfileId.first(), id, form.name, form.icon, form.color, form.initialBalance,
            form.accountSuffix, form.institution,
        )
    }

    fun adjust(id: String, actual: Long) = viewModelScope.launch { c.wallets.adjustBalance(id, actual) }

    suspend fun transactionCount(id: String) = c.wallets.transactionCount(id)

    fun delete(id: String) = viewModelScope.launch {
        c.wallets.delete(id)
        selection.value = null
    }
}

/** Dialog yang sedang terbuka di tab Dompet. */
internal sealed interface WalletDialog {
    data class Edit(val wallet: WalletWithBalance?) : WalletDialog
    data class Adjust(val wallet: WalletWithBalance) : WalletDialog
    data class Delete(val wallet: WalletWithBalance, val txCount: Int) : WalletDialog
}

data class WalletForm(
    val name: String,
    val icon: String,
    val color: Long,
    val initialBalance: Long,
    val accountSuffix: String?,
    val institution: String?,
)

@Composable
fun WalletsScreen(
    onOpenTransaction: (TransactionItem) -> Unit,
    onTransfer: (String) -> Unit,
    onHistory: (String) -> Unit,
) {
    val vm = appViewModel { WalletsViewModel(it) }
    val s by vm.state.collectAsStateWithLifecycle()
    val selectedId by vm.selection.collectAsStateWithLifecycle()
    var dialog by remember { mutableStateOf<WalletDialog?>(null) }
    val scope = rememberCoroutineScope()
    val undo = LocalUndo.current
    val container = appContainer()
    StatusBarIcons(lightIcons = false)

    val pager = rememberPagerState { s.wallets.size.coerceAtLeast(1) }
    // Pilihan dari Beranda → geser pager ke dompet itu; geser pager → ubah pilihan.
    LaunchedEffect(selectedId, s.wallets) {
        val index = s.wallets.indexOfFirst { it.id == selectedId }
        if (index >= 0 && index != pager.currentPage) pager.scrollToPage(index)
    }
    LaunchedEffect(pager, s.wallets) {
        snapshotFlow { pager.currentPage }.collect { page -> s.wallets.getOrNull(page)?.let { vm.select(it.id) } }
    }
    val current = s.wallets.getOrNull(pager.currentPage)
    val items = s.monthItems.filter { it.walletId == current?.id }
    val moneyIn = items.filter { it.type == TransactionType.INCOME }.sumOf { it.amount }
    val moneyOut = items.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount }

    LazyColumn(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding(),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 16.dp, top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Dompet Saya", style = MaterialTheme.typography.headlineSmall)
                    Text("${s.wallets.size} dompet · total ${money(s.wallets.sumOf { it.balance })}",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                NeuCircleButton(Icons.Outlined.Add, "Tambah dompet", { dialog = WalletDialog.Edit(null) })
            }
        }
        if (s.wallets.isNotEmpty()) {
            item {
                HorizontalPager(
                    state = pager,
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    pageSpacing = 12.dp,
                    modifier = Modifier.padding(top = 16.dp),
                ) { page -> WalletHeroCard(s.wallets[page], s.ownerName, Modifier.padding(vertical = 10.dp)) }
                if (s.wallets.size > 1) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                        repeat(s.wallets.size) { i ->
                            val active = i == pager.currentPage
                            Box(
                                Modifier.padding(horizontal = 3.dp).size(width = if (active) 18.dp else 6.dp, height = 6.dp)
                                    .clip(CircleShape)
                                    .background(if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline),
                            )
                        }
                    }
                }
            }
        }
        current?.let { w ->
            item {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                    WalletAction(Icons.Outlined.Tune, "Sesuaikan") { dialog = WalletDialog.Adjust(w) }
                    WalletAction(Icons.Outlined.Edit, "Ubah") { dialog = WalletDialog.Edit(w) }
                    WalletAction(Icons.AutoMirrored.Outlined.ReceiptLong, "Riwayat") { onHistory(w.id) }
                    WalletAction(Icons.AutoMirrored.Outlined.Send, "Transfer") { onTransfer(w.id) }
                }
            }
            item {
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).neuSurface().padding(18.dp)) {
                    Text("Arus ${YearMonth.now().label()}", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(12.dp))
                    Row {
                        FlowStat("Masuk", moneyIn, IncomeGreen, Modifier.weight(1f))
                        FlowStat("Keluar", moneyOut, ExpenseRed, Modifier.weight(1f))
                    }
                    val total = (moneyIn + moneyOut).coerceAtLeast(1)
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.fillMaxWidth().height(8.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHighest)) {
                        if (moneyIn > 0) Box(Modifier.weight(moneyIn.toFloat() / total).height(8.dp).background(IncomeGreen))
                        if (moneyOut > 0) Box(Modifier.weight(moneyOut.toFloat() / total).height(8.dp).background(ExpenseRed))
                    }
                }
            }
            item {
                Text("Transaksi dompet ini", style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(start = 20.dp, top = 24.dp, bottom = 6.dp))
            }
            if (items.isEmpty()) {
                item { EmptyState("Belum ada transaksi", "Transaksi bulan ini di ${w.name} akan muncul di sini.") }
            }
            items.groupBy { it.date.toLocalDate() }.forEach { (date, list) ->
                item(key = "d-$date") {
                    Text(date.dayHeader(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 20.dp, top = 10.dp, bottom = 4.dp))
                }
                items(list, key = { it.id }) { tx ->
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
            if (!w.isDefault) {
                item {
                    TextButton(
                        onClick = { scope.launch { dialog = WalletDialog.Delete(w, vm.transactionCount(w.id)) } },
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    ) { Text("Hapus dompet ini", color = MaterialTheme.colorScheme.error) }
                }
            }
        }
    }

    when (val d = dialog) {
        is WalletDialog.Edit -> WalletEditDialog(d.wallet, onDismiss = { dialog = null }) { form ->
            vm.save(d.wallet?.id, form)
            dialog = null
        }
        is WalletDialog.Adjust -> AdjustDialog(d.wallet, onDismiss = { dialog = null }) { actual ->
            vm.adjust(d.wallet.id, actual)
            dialog = null
        }
        is WalletDialog.Delete -> ConfirmDialog(
            title = "Hapus ${d.wallet.name}?",
            message = if (d.txCount > 0) "${d.txCount} transaksi di dompet ini juga akan dihapus." else "Dompet ini belum punya transaksi.",
            confirmLabel = "Hapus",
            onConfirm = { vm.delete(d.wallet.id) },
            onDismiss = { dialog = null },
        )
        null -> Unit
    }
}

@Composable
private fun WalletAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clip(MaterialTheme.shapes.small).clickable(onClick = onClick).padding(4.dp),
    ) {
        NeuCircleButton(icon, label, onClick, size = 52.dp)
        Spacer(Modifier.height(6.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun FlowStat(label: String, amount: Long, color: Color, modifier: Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(money(amount), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = color)
    }
}

@Composable
internal fun WalletEditDialog(
    wallet: WalletWithBalance?,
    onDismiss: () -> Unit,
    onSave: (WalletForm) -> Unit,
) {
    var name by remember { mutableStateOf(wallet?.name.orEmpty()) }
    var icon by remember { mutableStateOf(wallet?.icon ?: "wallet") }
    var color by remember { mutableStateOf(wallet?.color ?: 0xFF2A2421) }
    var initial by remember { mutableStateOf(amountFieldValue(0)) }
    var suffix by remember { mutableStateOf(wallet?.accountSuffix.orEmpty()) }
    var institution by remember { mutableStateOf(wallet?.institution.orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (wallet == null) "Tambah dompet" else "Ubah dompet") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Nama (mis. BCA, GoPay, Tunai)") },
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(institution, { institution = it.take(40) }, label = { Text("Bank/penyedia (opsional)") },
                    placeholder = { Text("BCA Digital") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    suffix, { suffix = it.filter(Char::isDigit).take(4) },
                    label = { Text("4 digit akhir rekening (opsional)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (wallet == null) {
                    AmountField(initial, { initial = it }, label = "Saldo awal")
                }
                Text("Ikon", style = MaterialTheme.typography.labelLarge)
                IconPicker(AppIcons.walletKeys, icon, Color(color)) { icon = it }
                Text("Warna", style = MaterialTheme.typography.labelLarge)
                ColorPicker(color) { color = it }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(
                        WalletForm(
                            name.trim(), icon, color, initial.signedAmount(),
                            suffix.takeIf { it.length == 4 }, institution.trim().ifEmpty { null },
                        ),
                    )
                },
                enabled = name.isNotBlank(),
            ) { Text("Simpan") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } },
    )
}

@Composable
internal fun AdjustDialog(wallet: WalletWithBalance, onDismiss: () -> Unit, onSave: (Long) -> Unit) {
    var actual by remember { mutableStateOf(amountFieldValue(wallet.balance)) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Sesuaikan saldo") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Saldo tercatat: ${formatRupiah(wallet.balance)}. Masukkan saldo sebenarnya; " +
                    "selisihnya dicatat sebagai penyesuaian dan tidak masuk laporan pemasukan/pengeluaran.",
                    style = MaterialTheme.typography.bodyMedium)
                AmountField(actual, { actual = it }, label = "Saldo sebenarnya", allowNegative = true)
            }
        },
        confirmButton = { TextButton(onClick = { onSave(actual.signedAmount()) }) { Text("Simpan") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } },
    )
}
