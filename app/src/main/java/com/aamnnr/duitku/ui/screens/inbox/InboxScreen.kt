package com.aamnnr.duitku.ui.screens.inbox

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.aamnnr.duitku.AppContainer
import com.aamnnr.duitku.data.local.CategoryEntity
import com.aamnnr.duitku.data.remote.DraftPayload
import com.aamnnr.duitku.data.remote.RemoteDraft
import com.aamnnr.duitku.domain.WalletWithBalance
import com.aamnnr.duitku.ui.LocalUndo
import com.aamnnr.duitku.ui.appContainer
import com.aamnnr.duitku.ui.appViewModel
import com.aamnnr.duitku.ui.components.EmptyState
import com.aamnnr.duitku.ui.components.IconBadge
import com.aamnnr.duitku.ui.components.KopiCard
import com.aamnnr.duitku.ui.components.StatusBarIcons
import com.aamnnr.duitku.ui.formatRupiah
import com.aamnnr.duitku.ui.theme.IncomeGreen
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.map

@OptIn(ExperimentalCoroutinesApi::class)
class InboxViewModel(private val c: AppContainer) : ViewModel() {
    var drafts by mutableStateOf<List<RemoteDraft>>(emptyList())
        private set
    var loading by mutableStateOf(true)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    val signedIn get() = c.cloud.currentAccount() != null

    val categories = c.profiles.activeProfileId.flatMapLatest { c.categories.observeAll(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val wallets = c.profiles.activeProfileId.flatMapLatest { c.wallets.observeWithBalance(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Draf dari notifikasi HP (lokal), dibentuk sama dengan draf cloud agar kartunya seragam. */
    val local = c.notificationDrafts.pending.map { list ->
        list.map { d ->
            RemoteDraft(
                id = LOCAL_PREFIX + d.fingerprint,
                source = "notification",
                rawText = "${d.sourceName}: ${d.rawText}",
                parsed = DraftPayload(
                    type = d.type, amount = d.amount, categoryId = d.categoryId, walletId = d.walletId, note = d.note,
                    date = java.time.Instant.ofEpochMilli(d.receivedAt).atZone(java.time.ZoneId.systemDefault()).toLocalDate().toString(),
                    confidence = d.confidence,
                ),
                status = d.status,
                createdAt = d.receivedAt.toString(),
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init { refresh() }

    suspend fun markImported(d: RemoteDraft) {
        if (d.id.startsWith(LOCAL_PREFIX)) c.notificationDrafts.markImported(d.id.removePrefix(LOCAL_PREFIX))
        else runCatching { c.cloud.markDraft(d.id, "IMPORTED") }
    }

    suspend fun markDiscarded(d: RemoteDraft, discarded: Boolean) {
        val fp = d.id.removePrefix(LOCAL_PREFIX)
        if (d.id.startsWith(LOCAL_PREFIX)) {
            if (discarded) c.notificationDrafts.markDiscarded(fp) else c.notificationDrafts.markPending(fp)
        } else {
            runCatching { c.cloud.markDraft(d.id, if (discarded) "DISCARDED" else "PENDING") }
        }
    }

    fun refresh() = viewModelScope.launch {
        if (!signedIn) {
            loading = false
            return@launch
        }
        loading = true
        error = null
        try {
            drafts = c.cloud.pendingDrafts()
            c.sync.setPendingDrafts(drafts.size)
        } catch (e: Exception) {
            error = "Gagal memuat inbox. Periksa koneksi internet."
        } finally {
            loading = false
        }
    }

    /** Simpan draf yang lengkap. Mengembalikan jumlah yang berhasil. */
    suspend fun importAll(list: List<RemoteDraft>): Int {
        var ok = 0
        for (d in list) {
            if (c.drafts.import(d.parsed)) {
                markImported(d)
                ok++
            }
        }
        removeLocally(list.map { it.id }.toSet())
        return ok
    }

    fun removeLocally(ids: Set<String>) {
        drafts = drafts.filterNot { it.id in ids }
        c.sync.setPendingDrafts(drafts.size)
    }

    fun isComplete(d: RemoteDraft) = c.drafts.isComplete(d.parsed)
    fun isConfident(d: RemoteDraft) = isComplete(d) && d.parsed.confidence >= 0.6
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InboxScreen(onBack: () -> Unit, onEdit: (RemoteDraft) -> Unit) {
    val vm = appViewModel { InboxViewModel(it) }
    val categories by vm.categories.collectAsStateWithLifecycle()
    val wallets by vm.wallets.collectAsStateWithLifecycle()
    val undo = LocalUndo.current
    val container = appContainer()
    val localDrafts by vm.local.collectAsStateWithLifecycle()
    val all = localDrafts + vm.drafts
    val confident = all.filter(vm::isConfident)
    StatusBarIcons(lightIcons = false)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Inbox") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Kembali") } },
                actions = { IconButton(onClick = { vm.refresh() }) { Icon(Icons.Outlined.Refresh, "Muat ulang") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        bottomBar = {
            if (confident.size > 1) {
                Button(
                    onClick = {
                        val list = confident
                        undo.run("${list.size} draf disimpan") { vm.importAll(list) }
                    },
                    modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp).height(52.dp),
                ) { Text("Simpan ${confident.size} draf yang yakin") }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                all.isEmpty() && vm.loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                all.isEmpty() -> EmptyState(
                    "Inbox kosong",
                    "Draf dari notifikasi bank/e-wallet" + (if (vm.signedIn) " dan bot Telegram" else "") +
                        " muncul di sini untuk kamu setujui. Nyalakan auto-import di Profil & Pengaturan.",
                )
                else -> LazyColumn(contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    item {
                        Text("${all.size} draf menunggu", style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    vm.error?.let { e -> item { Text(e, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) } }
                    items(all, key = { it.id }) { d ->
                        DraftCard(
                            draft = d,
                            categories = categories,
                            wallets = wallets,
                            complete = vm.isComplete(d),
                            confident = vm.isConfident(d),
                            onSave = {
                                undo.run("Draf disimpan") {
                                    if (container.drafts.import(d.parsed)) {
                                        vm.markImported(d)
                                        vm.removeLocally(setOf(d.id))
                                    }
                                }
                            },
                            onEdit = { onEdit(d) },
                            onDiscard = {
                                vm.removeLocally(setOf(d.id))
                                undo.perform(
                                    message = "Draf dibuang",
                                    action = { vm.markDiscarded(d, true) },
                                    undo = {
                                        vm.markDiscarded(d, false)
                                        vm.refresh()
                                    },
                                )
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DraftCard(
    draft: RemoteDraft,
    categories: List<CategoryEntity>,
    wallets: List<WalletWithBalance>,
    complete: Boolean,
    confident: Boolean,
    onSave: () -> Unit,
    onEdit: () -> Unit,
    onDiscard: () -> Unit,
) {
    val p: DraftPayload = draft.parsed
    val income = p.type == "INCOME"
    KopiCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (draft.source == "telegram") "TG" else "HP",
                color = Color.White,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.clip(RoundedCornerShape(50)).background(Color(0xFF4E6E8E))
                    .padding(horizontal = 6.dp, vertical = 4.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(draft.rawText ?: p.note.orEmpty(), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text((if (income) "+" else "-") + formatRupiah(p.amount), style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold, color = if (income) IncomeGreen else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f))
            Pill(
                if (confident) "Yakin" else "Perlu cek",
                if (confident) IncomeGreen else MaterialTheme.colorScheme.secondary,
            )
        }
        Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Pill(categories.firstOrNull { it.id == p.categoryId }?.name ?: "Pilih kategori", MaterialTheme.colorScheme.primary)
            Pill(wallets.firstOrNull { it.id == p.walletId }?.name ?: "Dompet terakhir", MaterialTheme.colorScheme.primary)
            p.note?.let { Pill(it, MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onDiscard, modifier = Modifier.weight(1f)) { Text("Buang") }
            OutlinedButton(onClick = onEdit, modifier = Modifier.weight(1f)) { Text("Ubah") }
            Button(onClick = onSave, enabled = complete, modifier = Modifier.weight(1f)) { Text("Simpan") }
        }
    }
}

@Composable
private fun Pill(text: String, color: Color) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = color,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(color.copy(alpha = 0.12f))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

/** Awalan id untuk draf lokal dari notifikasi HP (bukan draf cloud). */
const val LOCAL_PREFIX = "local:"
