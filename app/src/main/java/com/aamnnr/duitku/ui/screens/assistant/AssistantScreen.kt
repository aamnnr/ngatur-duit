package com.aamnnr.duitku.ui.screens.assistant

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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.aamnnr.duitku.AppContainer
import com.aamnnr.duitku.data.remote.AssistantRequest
import com.aamnnr.duitku.data.remote.ChatTurn
import com.aamnnr.duitku.data.remote.CloudException
import com.aamnnr.duitku.data.remote.DraftPayload
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
import java.util.UUID

/** Satu gelembung chat. Untuk jawaban berisi transaksi, [drafts] berisi draf yang bisa disimpan. */
data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val fromUser: Boolean,
    val text: String,
    val drafts: List<DraftPayload> = emptyList(),
    val isError: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class)
class AssistantViewModel(private val c: AppContainer) : ViewModel() {
    val messages = mutableStateListOf<ChatMessage>()
    /** Draf yang sudah disimpan (key: id pesan + indeks). */
    val savedDrafts = mutableStateListOf<String>()
    var input by mutableStateOf("")
    var sending by mutableStateOf(false)
        private set

    val consent = c.settings.aiConsent.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val signedIn get() = c.cloud.isConfigured && c.cloud.currentAccount() != null
    val categories = c.profiles.activeProfileId.flatMapLatest { c.categories.observeAll(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun giveConsent() = viewModelScope.launch { c.settings.setAiConsent(true) }

    fun send(text: String = input) {
        val message = text.trim()
        if (message.isEmpty() || sending) return
        input = ""
        val history = messages.filterNot { it.isError }.takeLast(6).map { ChatTurn(if (it.fromUser) "user" else "assistant", it.text) }
        messages += ChatMessage(fromUser = true, text = message)
        sending = true
        viewModelScope.launch {
            try {
                val res = c.cloud.askAssistant(
                    AssistantRequest(message, buildLedgerContext(c), buildSummary(c), history),
                )
                messages += ChatMessage(fromUser = false, text = res.answer, drafts = res.transactions)
            } catch (e: CloudException) {
                messages += ChatMessage(fromUser = false, text = e.message.orEmpty(), isError = true)
            } catch (e: Exception) {
                messages += ChatMessage(fromUser = false, text = "Maaf, terjadi kesalahan. Coba lagi.", isError = true)
            } finally {
                sending = false
            }
        }
    }

    fun isComplete(d: DraftPayload) = c.drafts.isComplete(d)

    fun save(key: String, d: DraftPayload) = viewModelScope.launch {
        if (c.drafts.import(d)) savedDrafts += key
    }
}

private val suggestions = listOf(
    "Kopi 18rb pakai GoPay, parkir 2rb",
    "Pengeluaran terbesarku bulan ini apa?",
    "Bandingkan pengeluaran bulan ini dengan bulan lalu",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssistantScreen(onBack: () -> Unit, onOpenAccount: () -> Unit, onEditDraft: (DraftPayload) -> Unit) {
    val vm = appViewModel { AssistantViewModel(it) }
    val consent by vm.consent.collectAsStateWithLifecycle()
    val categories by vm.categories.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    StatusBarIcons(lightIcons = false)

    LaunchedEffect(vm.messages.size, vm.sending) {
        val count = vm.messages.size + (if (vm.sending) 1 else 0)
        if (count > 0) listState.animateScrollToItem(count - 1)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Asisten Duitku") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Kembali") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        bottomBar = {
            if (vm.signedIn) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                        .navigationBarsPadding()
                        .imePadding()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = vm.input,
                        onValueChange = { vm.input = it.take(1000) },
                        placeholder = { Text("Tulis transaksi atau pertanyaan") },
                        maxLines = 4,
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.size(8.dp))
                    FilledIconButton(onClick = { vm.send() }, enabled = vm.input.isNotBlank() && !vm.sending) {
                        Icon(Icons.AutoMirrored.Filled.Send, "Kirim")
                    }
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (!vm.signedIn) {
                Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    EmptyState("Masuk dulu untuk memakai asisten", "Asisten berjalan di server Duitku, jadi perlu akun.")
                    Button(onClick = onOpenAccount) { Text("Masuk ke akun") }
                }
                return@Box
            }
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                if (vm.messages.isEmpty()) {
                    item { Intro(onPick = { vm.send(it) }) }
                }
                items(vm.messages, key = { it.id }) { m ->
                    Bubble(m)
                    m.drafts.forEachIndexed { i, d ->
                        val key = "${m.id}-$i"
                        DraftRow(
                            d,
                            categoryName = categories.firstOrNull { it.id == d.categoryId }?.name,
                            saved = key in vm.savedDrafts,
                            complete = vm.isComplete(d),
                            onSave = { vm.save(key, d) },
                            onEdit = { onEditDraft(d) },
                        )
                    }
                }
                if (vm.sending) {
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            Text("  Asisten sedang berpikir…", style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }

    if (vm.signedIn && consent == false) {
        AiConsentDialog(onAgree = { vm.giveConsent() }, onCancel = onBack)
    }
}

@Composable
private fun Intro(onPick: (String) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        IconBadge(Icons.Outlined.SmartToy, MaterialTheme.colorScheme.primary, 56.dp)
        Spacer(Modifier.height(12.dp))
        Text("Catat atau tanya apa saja soal keuanganmu", style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold)
        Text("Tulis seperti chat biasa. Transaksi akan jadi draf yang bisa kamu tinjau.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(16.dp))
        suggestions.forEach { s ->
            Text(
                s,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .padding(vertical = 4.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                    .clickable { onPick(s) }
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            )
        }
    }
}

@Composable
private fun Bubble(m: ChatMessage) {
    Box(Modifier.fillMaxWidth(), contentAlignment = if (m.fromUser) Alignment.CenterEnd else Alignment.CenterStart) {
        Text(
            m.text,
            style = MaterialTheme.typography.bodyMedium,
            color = when {
                m.fromUser -> MaterialTheme.colorScheme.onPrimary
                m.isError -> MaterialTheme.colorScheme.error
                else -> MaterialTheme.colorScheme.onSurface
            },
            modifier = Modifier
                .widthIn(max = 300.dp)
                .clip(
                    RoundedCornerShape(
                        topStart = 18.dp, topEnd = 18.dp,
                        bottomStart = if (m.fromUser) 18.dp else 4.dp,
                        bottomEnd = if (m.fromUser) 4.dp else 18.dp,
                    ),
                )
                .background(if (m.fromUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerLowest)
                .padding(horizontal = 14.dp, vertical = 10.dp),
        )
    }
}

@Composable
private fun DraftRow(
    d: DraftPayload,
    categoryName: String?,
    saved: Boolean,
    complete: Boolean,
    onSave: () -> Unit,
    onEdit: () -> Unit,
) {
    val income = d.type == "INCOME"
    KopiCard(Modifier.fillMaxWidth().padding(end = 32.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(d.note ?: "Transaksi", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Text(listOfNotNull(categoryName ?: "Pilih kategori", d.date).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text((if (income) "+" else "-") + formatRupiah(d.amount), fontWeight = FontWeight.Bold,
                color = if (income) IncomeGreen else MaterialTheme.colorScheme.onSurface)
        }
        Spacer(Modifier.height(8.dp))
        if (saved) {
            Text("Tersimpan", color = IncomeGreen, style = MaterialTheme.typography.labelLarge)
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onEdit, modifier = Modifier.weight(1f)) { Text("Ubah") }
                Button(onClick = onSave, enabled = complete, modifier = Modifier.weight(1f)) { Text("Simpan") }
            }
        }
    }
}
