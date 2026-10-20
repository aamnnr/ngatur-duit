package com.aamnnr.duitku.ui.screens.transaction

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aamnnr.duitku.AppContainer
import com.aamnnr.duitku.data.local.CategoryEntity
import com.aamnnr.duitku.domain.TransactionType
import com.aamnnr.duitku.domain.TransferType
import com.aamnnr.duitku.domain.WalletWithBalance
import com.aamnnr.duitku.ui.AppIcons
import com.aamnnr.duitku.ui.LocalUndo
import com.aamnnr.duitku.ui.appContainer
import com.aamnnr.duitku.ui.appViewModel
import com.aamnnr.duitku.ui.components.ConfirmDialog
import com.aamnnr.duitku.ui.components.IconBadge
import com.aamnnr.duitku.ui.components.SoonBadge
import com.aamnnr.duitku.ui.components.StatusBarIcons
import com.aamnnr.duitku.ui.components.amountFieldValue
import com.aamnnr.duitku.ui.components.toColor
import com.aamnnr.duitku.ui.formatRupiah
import com.aamnnr.duitku.ui.formatThousands
import com.aamnnr.duitku.ui.parseAmount
import com.aamnnr.duitku.ui.shortLabel
import com.aamnnr.duitku.ui.theme.HeaderBg
import com.aamnnr.duitku.ui.theme.OnHeader
import com.aamnnr.duitku.ui.theme.OnHeaderMuted
import com.aamnnr.duitku.ui.toLocalDate
import com.aamnnr.duitku.ui.withCurrentTimeMillis
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

enum class FormMode(val label: String) { EXPENSE("Pengeluaran"), INCOME("Pemasukan"), TRANSFER("Transfer") }

/** Isian awal form, mis. dari draf bot/asisten/scan struk. [draftId] ditandai IMPORTED setelah disimpan. */
data class FormPrefill(
    val amount: Long? = null,
    val categoryId: String? = null,
    val note: String? = null,
    val date: LocalDate? = null,
    val draftId: String? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
class TransactionFormViewModel(
    private val c: AppContainer,
    private val editId: String?,
    initialMode: FormMode?,
    private val initialWalletId: String?,
    private val prefill: FormPrefill = FormPrefill(),
) : ViewModel() {
    var mode by mutableStateOf(initialMode ?: FormMode.EXPENSE)
    var amount by mutableStateOf(TextFieldValue(""))
    var categoryId by mutableStateOf<String?>(null)
    var walletId by mutableStateOf<String?>(null)
    var toWalletId by mutableStateOf<String?>(null)
    var date by mutableStateOf(LocalDate.now())
    var note by mutableStateOf("")
    var receiptPath by mutableStateOf<String?>(null)
    var error by mutableStateOf<String?>(null)
    var isAdjustment by mutableStateOf(false)
        private set
    private val _saved = MutableStateFlow(false)
    val saved: StateFlow<Boolean> = _saved

    val isEditing = editId != null
    private var linkId: String? = null
    private var originalDateMillis: Long? = null

    /** Kategori aktif, yang paling sering dipakai di depan. */
    val categories = c.profiles.activeProfileId.flatMapLatest { pid ->
        combine(c.categories.observeAll(pid), c.transactions.observeCategoryUsage(pid)) { cats, usage ->
            val uses = usage.associate { it.categoryId to it.uses }
            cats.sortedByDescending { uses[it.id] ?: 0 }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val wallets = c.profiles.activeProfileId.flatMapLatest { c.wallets.observeWithBalance(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        if (editId == null) {
            prefill.amount?.let { amount = amountFieldValue(it) }
            categoryId = prefill.categoryId
            prefill.note?.let { note = it }
            prefill.date?.let { date = it }
        }
        viewModelScope.launch {
            if (editId != null) load(editId)
            if (walletId == null) {
                val list = wallets.first { it.isNotEmpty() }
                val last = c.settings.lastWalletId()
                walletId = listOfNotNull(initialWalletId, last).firstOrNull { id -> list.any { it.id == id } }
                    ?: list.first().id
            }
        }
    }

    private suspend fun load(id: String) {
        val tx = c.transactions.get(id) ?: return
        originalDateMillis = tx.transactionDate
        date = tx.transactionDate.toLocalDate()
        note = tx.note.orEmpty()
        amount = amountFieldValue(tx.amount)
        isAdjustment = tx.isAdjustment
        receiptPath = tx.receiptPath
        if (tx.linkedTransferId != null) {
            linkId = tx.linkedTransferId
            mode = FormMode.TRANSFER
            val pair = c.transactions.getTransferPair(tx.linkedTransferId)
            walletId = pair.firstOrNull { it.transferType == TransferType.OUT }?.walletId
            toWalletId = pair.firstOrNull { it.transferType == TransferType.IN }?.walletId
        } else {
            mode = if (tx.type == TransactionType.INCOME) FormMode.INCOME else FormMode.EXPENSE
            categoryId = tx.categoryId
            walletId = tx.walletId
        }
    }

    fun changeMode(m: FormMode) {
        if (m == mode) return
        mode = m
        categoryId = null
        error = null
    }

    fun onAmountChange(new: TextFieldValue) {
        val digits = parseAmount(new.text)
        val text = if (digits == 0L) "" else formatThousands(digits)
        amount = TextFieldValue(text, TextRange(text.length))
        error = null
    }

    fun save() {
        val value = parseAmount(amount.text)
        val from = walletId
        error = when {
            value <= 0 -> "Masukkan nominal dulu"
            from == null -> "Pilih dompet"
            mode == FormMode.TRANSFER && toWalletId == null -> "Pilih dompet tujuan"
            mode == FormMode.TRANSFER && toWalletId == from -> "Dompet asal dan tujuan harus berbeda"
            mode != FormMode.TRANSFER && categoryId == null -> "Pilih kategori"
            else -> null
        }
        if (error != null || from == null) return
        // Pertahankan jam asli bila tanggal tidak diubah saat edit.
        val millis = originalDateMillis?.takeIf { it.toLocalDate() == date } ?: date.withCurrentTimeMillis()
        val cleanNote = note.trim().ifEmpty { null }
        viewModelScope.launch {
            val pid = c.profiles.activeProfileId.first()
            if (mode == FormMode.TRANSFER) {
                c.transactions.saveTransfer(pid, linkId, from, toWalletId!!, value, cleanNote, millis)
            } else {
                val type = if (mode == FormMode.INCOME) TransactionType.INCOME else TransactionType.EXPENSE
                c.transactions.saveTransaction(
                    pid, editId, type, value, categoryId, from, cleanNote, millis,
                    receiptPath = receiptPath, keepReceipt = false,
                )
                c.settings.setLastWalletId(from)
            }
            prefill.draftId?.let { id ->
                if (id.startsWith(com.aamnnr.duitku.ui.screens.inbox.LOCAL_PREFIX)) {
                    c.notificationDrafts.markImported(id.removePrefix(com.aamnnr.duitku.ui.screens.inbox.LOCAL_PREFIX))
                } else {
                    runCatching { c.cloud.markDraft(id, "IMPORTED") }
                    c.sync.setPendingDrafts((c.sync.state.value.pendingDrafts - 1).coerceAtLeast(0))
                }
            }
            _saved.value = true
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionFormScreen(
    editId: String?,
    initialMode: FormMode?,
    initialWalletId: String?,
    onDone: () -> Unit,
    prefill: FormPrefill = FormPrefill(),
) {
    val vm = appViewModel(key = "tx-$editId-$initialMode-$initialWalletId-$prefill") {
        TransactionFormViewModel(it, editId, initialMode, initialWalletId, prefill)
    }
    val categories by vm.categories.collectAsState()
    val wallets by vm.wallets.collectAsState()
    val saved by vm.saved.collectAsState()
    var showDate by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current
    val undo = LocalUndo.current
    val container = appContainer()
    val amountFocus = remember { FocusRequester() }
    StatusBarIcons(lightIcons = true)

    LaunchedEffect(saved) {
        if (saved) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            onDone()
        }
    }
    LaunchedEffect(Unit) { if (editId == null) amountFocus.requestFocus() }

    val amountValue = parseAmount(vm.amount.text)
    val wallet = wallets.firstOrNull { it.id == vm.walletId }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            Column(Modifier.navigationBarsPadding().imePadding().padding(16.dp)) {
                vm.error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(bottom = 8.dp))
                }
                if (!vm.isAdjustment) {
                    Button(
                        onClick = vm::save,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                    ) {
                        Text(if (amountValue > 0) "Simpan · ${formatRupiah(amountValue)}" else "Simpan",
                            fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            // Header: judul, jenis transaksi, dan nominal besar.
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(HeaderBg)
                    .statusBarsPadding()
                    .padding(start = 8.dp, end = 8.dp, bottom = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDone) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Kembali", tint = OnHeader) }
                    Text(
                        when {
                            vm.isAdjustment -> "Penyesuaian saldo"
                            vm.isEditing -> "Ubah transaksi"
                            else -> "Catat transaksi"
                        },
                        color = OnHeader, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f), textAlign = TextAlign.Center,
                    )
                    if (vm.isEditing) {
                        IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Outlined.Delete, "Hapus", tint = OnHeader) }
                    } else {
                        Spacer(Modifier.width(48.dp))
                    }
                }
                if (!vm.isAdjustment) {
                    ModeChips(vm.mode, enabledFor = { m ->
                        !vm.isEditing || (vm.mode == FormMode.TRANSFER) == (m == FormMode.TRANSFER)
                    }, onSelect = vm::changeMode)
                }
                Spacer(Modifier.height(16.dp))
                Text("Nominal", color = OnHeaderMuted, style = MaterialTheme.typography.bodyMedium)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Rp ", color = OnHeader, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
                    BasicTextField(
                        value = vm.amount,
                        onValueChange = vm::onAmountChange,
                        enabled = !vm.isAdjustment,
                        singleLine = true,
                        textStyle = MaterialTheme.typography.displaySmall.copy(color = OnHeader, fontWeight = FontWeight.Bold),
                        cursorBrush = SolidColor(OnHeader),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        // Lebar mengikuti isi, agar "Rp" tetap menempel di depan angka.
                        modifier = Modifier.width(IntrinsicSize.Min).widthIn(min = 24.dp).focusRequester(amountFocus),
                        decorationBox = { inner ->
                            Box {
                                if (vm.amount.text.isEmpty()) {
                                    Text("0", color = OnHeader.copy(alpha = 0.4f), style = MaterialTheme.typography.displaySmall,
                                        fontWeight = FontWeight.Bold)
                                }
                                inner()
                            }
                        },
                    )
                }
                if (wallet != null && vm.mode != FormMode.TRANSFER) {
                    Text("${wallet.name} · saldo ${formatRupiah(wallet.balance)}", color = OnHeaderMuted,
                        style = MaterialTheme.typography.bodySmall)
                }
            }
            // Lembar isian
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(HeaderBg)
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                        .background(MaterialTheme.colorScheme.background)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (vm.isAdjustment) {
                        Text(
                            "Penyesuaian dibuat dari menu \"Sesuaikan saldo\" dan tidak dihitung sebagai pemasukan atau " +
                                "pengeluaran. Penyesuaian tidak bisa diubah, hanya bisa dihapus.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    } else if (vm.mode == FormMode.TRANSFER) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            WalletField("Dari", wallets, vm.walletId, Modifier.weight(1f)) { vm.walletId = it }
                            WalletField("Ke", wallets, vm.toWalletId, Modifier.weight(1f)) { vm.toWalletId = it }
                        }
                    } else {
                        Text("Kategori", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        val type = if (vm.mode == FormMode.INCOME) TransactionType.INCOME else TransactionType.EXPENSE
                        CategoryGrid(categories.filter { it.type == type }, vm.categoryId) {
                            vm.categoryId = it
                            vm.error = null
                        }
                    }
                    if (!vm.isAdjustment) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FieldBox("Tanggal", vm.date.shortLabel(), Icons.Outlined.CalendarMonth, Modifier.weight(1f)) {
                                showDate = true
                            }
                            if (vm.mode != FormMode.TRANSFER) {
                                WalletField("Dompet", wallets, vm.walletId, Modifier.weight(1f)) { vm.walletId = it }
                            }
                        }
                        NoteField(vm.note) { vm.note = it }
                        if (vm.mode != FormMode.TRANSFER) {
                            ReceiptAttachment(vm.receiptPath, onChange = { vm.receiptPath = it })
                        }
                    }
                }
            }
        }
    }

    if (showDate) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = vm.date.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { showDate = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { vm.date = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
                    showDate = false
                }) { Text("Pilih") }
            },
            dismissButton = { TextButton(onClick = { showDate = false }) { Text("Batal") } },
        ) { DatePicker(state = state) }
    }

    if (confirmDelete && editId != null) {
        ConfirmDialog(
            title = "Hapus transaksi?",
            message = if (vm.mode == FormMode.TRANSFER) "Kedua sisi transfer akan dihapus." else "Transaksi ini akan dihapus.",
            confirmLabel = "Hapus",
            onConfirm = {
                undo.perform(
                    message = "Transaksi dihapus",
                    action = { container.transactions.delete(editId) },
                    undo = { container.transactions.restore(editId) },
                )
                onDone()
            },
            onDismiss = { confirmDelete = false },
        )
    }
}

@Composable
private fun ModeChips(selected: FormMode, enabledFor: (FormMode) -> Boolean, onSelect: (FormMode) -> Unit) {
    Row(
        Modifier
            .padding(horizontal = 24.dp)
            .clip(RoundedCornerShape(50))
            .background(OnHeader.copy(alpha = 0.10f))
            .padding(3.dp),
    ) {
        FormMode.entries.forEach { m ->
            val sel = m == selected
            val enabled = enabledFor(m)
            Text(
                m.label,
                color = when {
                    sel -> HeaderBg
                    enabled -> OnHeader
                    else -> OnHeader.copy(alpha = 0.35f)
                },
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(50))
                    .background(if (sel) OnHeader else OnHeader.copy(alpha = 0f))
                    .clickable(enabled = enabled) { onSelect(m) }
                    .padding(vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun FieldBox(label: String, value: String, icon: ImageVector, modifier: Modifier, onClick: () -> Unit) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 4.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.small)
                .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                .clickable(onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(value, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f))
            Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun WalletField(
    label: String,
    wallets: List<WalletWithBalance>,
    selectedId: String?,
    modifier: Modifier,
    onSelect: (String) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        FieldBox(label, wallets.firstOrNull { it.id == selectedId }?.name ?: "Pilih", Icons.Outlined.AccountBalanceWallet,
            Modifier.fillMaxWidth()) { open = true }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            wallets.forEach { w ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(w.name)
                            Text(formatRupiah(w.balance), style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    },
                    leadingIcon = { IconBadge(AppIcons.get(w.icon), w.color.toColor(MaterialTheme.colorScheme.primary), 28.dp) },
                    onClick = { onSelect(w.id); open = false },
                )
            }
        }
    }
}

@Composable
private fun NoteField(value: String, onChange: (String) -> Unit) {
    Column {
        Text("Catatan", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 4.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.small)
                .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                .padding(horizontal = 12.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicTextField(
                value = value,
                onValueChange = onChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier.weight(1f),
                decorationBox = { inner ->
                    Box {
                        if (value.isEmpty()) {
                            Text("Makan siang tim", style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.outline)
                        }
                        inner()
                    }
                },
            )
            Icon(Icons.Outlined.Edit, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CategoryGrid(categories: List<CategoryEntity>, selectedId: String?, onSelect: (String) -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        maxItemsInEachRow = 3,
    ) {
        categories.forEach { cat ->
            val color = cat.color.toColor(MaterialTheme.colorScheme.primary)
            val sel = cat.id == selectedId
            Column(
                Modifier
                    .weight(1f)
                    .clip(MaterialTheme.shapes.small)
                    .background(if (sel) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLowest)
                    .border(
                        if (sel) 1.5.dp else 0.dp,
                        if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerLowest,
                        MaterialTheme.shapes.small,
                    )
                    .clickable { onSelect(cat.id) }
                    .padding(vertical = 12.dp, horizontal = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                IconBadge(AppIcons.get(cat.icon), color, 32.dp)
                Spacer(Modifier.height(4.dp))
                Text(cat.name, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
            }
        }
        repeat((3 - categories.size % 3) % 3) { Spacer(Modifier.weight(1f)) }
    }
}

/** Lampiran foto struk: ambil dari kamera/galeri, lihat, atau hapus. Foto disimpan di penyimpanan internal. */
@Composable
private fun ReceiptAttachment(path: String?, onChange: (String?) -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    val tempUri = remember {
        val dir = java.io.File(context.cacheDir, "receipts").apply { mkdirs() }
        androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.files", java.io.File(dir, "lampiran.jpg"))
    }
    fun save(uri: android.net.Uri) {
        busy = true
        scope.launch {
            val saved = runCatching {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { com.aamnnr.duitku.ui.ImageUtils.saveReceipt(context, uri) }
            }.getOrNull()
            busy = false
            if (saved != null) onChange(saved)
        }
    }
    val camera = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.TakePicture(),
    ) { ok -> if (ok) save(tempUri) }
    val gallery = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia(),
    ) { uri -> if (uri != null) save(uri) }
    val thumb = remember(path) {
        path?.let { runCatching { android.graphics.BitmapFactory.decodeFile(it, android.graphics.BitmapFactory.Options().apply { inSampleSize = 4 }) }.getOrNull() }
    }

    OutlinedCard(shape = MaterialTheme.shapes.medium, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (thumb != null) {
                androidx.compose.foundation.Image(
                    thumb.asImageBitmap(), "Foto struk",
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    modifier = Modifier.width(56.dp).height(56.dp).clip(MaterialTheme.shapes.small).clickable {
                        val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.files", java.io.File(path!!))
                        context.startActivity(
                            android.content.Intent(android.content.Intent.ACTION_VIEW).setDataAndType(uri, "image/jpeg")
                                .addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION),
                        )
                    },
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Foto struk terlampir", style = MaterialTheme.typography.bodyMedium)
                    Text("Ketuk foto untuk melihat", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton(onClick = { onChange(null) }) { Text("Hapus", color = MaterialTheme.colorScheme.error) }
            } else {
                Icon(Icons.AutoMirrored.Outlined.ReceiptLong, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(if (busy) "  Menyimpan foto…" else "  Lampirkan struk", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                TextButton(onClick = { camera.launch(tempUri) }, enabled = !busy) { Text("Kamera") }
                TextButton(
                    onClick = {
                        gallery.launch(
                            androidx.activity.result.PickVisualMediaRequest(
                                androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly,
                            ),
                        )
                    },
                    enabled = !busy,
                ) { Text("Galeri") }
            }
        }
    }
}


