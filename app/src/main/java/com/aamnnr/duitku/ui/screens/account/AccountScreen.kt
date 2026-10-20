package com.aamnnr.duitku.ui.screens.account

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.MarkEmailRead
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.aamnnr.duitku.AppContainer
import com.aamnnr.duitku.data.remote.Account
import com.aamnnr.duitku.ui.LocaleID
import com.aamnnr.duitku.ui.appViewModel
import com.aamnnr.duitku.ui.components.ConfirmDialog
import com.aamnnr.duitku.ui.components.IconBadge
import com.aamnnr.duitku.ui.components.KopiCard
import com.aamnnr.duitku.ui.components.StatusBarIcons
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class AccountViewModel(private val c: AppContainer) : ViewModel() {
    val configured = c.cloud.isConfigured
    val account = c.cloud.account.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), c.cloud.currentAccount())
    val sync = c.sync.state

    var email by mutableStateOf("")
    var code by mutableStateOf("")
    var codeSent by mutableStateOf(false)
    var busy by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)

    fun sendCode() {
        val e = email.trim()
        if (!e.contains('@') || !e.contains('.')) {
            error = "Masukkan alamat email yang valid."
            return
        }
        launchBusy {
            c.cloud.sendLoginCode(e)
            codeSent = true
            code = ""
        }
    }

    fun verify() {
        if (code.length != 6) {
            error = "Kode terdiri dari 6 digit."
            return
        }
        launchBusy {
            c.cloud.verifyLoginCode(email.trim(), code)
            codeSent = false
        }
    }

    fun syncNow() = viewModelScope.launch { c.sync.syncNow() }

    fun signOut() = launchBusy {
        c.cloud.signOut()
        c.sync.reset()
    }

    private fun launchBusy(block: suspend () -> Unit) = viewModelScope.launch {
        busy = true
        error = null
        try {
            block()
        } catch (e: Exception) {
            error = friendly(e)
        } finally {
            busy = false
        }
    }

    private fun friendly(e: Exception): String {
        val msg = e.message.orEmpty().lowercase()
        return when {
            "expired" in msg || "invalid" in msg || "token" in msg -> "Kode salah atau sudah kedaluwarsa. Minta kode baru."
            "rate" in msg || "429" in msg -> "Terlalu sering meminta kode. Tunggu beberapa menit lalu coba lagi."
            "unable to resolve" in msg || "timeout" in msg || "failed to connect" in msg -> "Tidak bisa terhubung ke server. Periksa koneksi internet."
            else -> "Gagal: ${e.message ?: "kesalahan tidak dikenal"}"
        }
    }
}

private val timeFormat = DateTimeFormatter.ofPattern("d MMM yyyy, HH.mm", LocaleID)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountScreen(onBack: () -> Unit) {
    val vm = appViewModel { AccountViewModel(it) }
    val account by vm.account.collectAsStateWithLifecycle()
    val sync by vm.sync.collectAsStateWithLifecycle()
    StatusBarIcons(lightIcons = false)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Akun dan sinkron") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Kembali") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when {
                !vm.configured -> NotConfigured()
                account != null -> SignedIn(account!!, sync.running, sync.lastSuccessAt, sync.error, vm.busy, vm::syncNow, vm::signOut)
                else -> SignIn(vm)
            }
        }
    }
}

@Composable
private fun NotConfigured() {
    KopiCard(Modifier.fillMaxWidth()) {
        IconBadge(Icons.Outlined.CloudOff, MaterialTheme.colorScheme.secondary, 44.dp)
        Spacer(Modifier.height(12.dp))
        Text("Cloud belum dikonfigurasi", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        Text(
            "Isi SUPABASE_URL dan SUPABASE_ANON_KEY di file local.properties, lalu build ulang aplikasi. " +
                "Panduannya ada di ONLINE.md. Aplikasi tetap bisa dipakai penuh secara offline.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SignIn(vm: AccountViewModel) {
    var resendIn by remember { mutableIntStateOf(0) }
    LaunchedEffect(vm.codeSent) {
        if (vm.codeSent) {
            resendIn = 60
            while (resendIn > 0) {
                delay(1_000)
                resendIn--
            }
        }
    }

    KopiCard(Modifier.fillMaxWidth()) {
        IconBadge(Icons.Outlined.MarkEmailRead, MaterialTheme.colorScheme.primary, 44.dp)
        Spacer(Modifier.height(12.dp))
        Text("Masuk ke Duitku", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(
            "Dengan akun, datamu dicadangkan ke cloud, bisa dipakai di HP lain, dan fitur AI serta bot Telegram aktif.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = vm.email,
            onValueChange = { vm.email = it; vm.error = null },
            label = { Text("Email") },
            singleLine = true,
            enabled = !vm.codeSent && !vm.busy,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier.fillMaxWidth(),
        )
        if (vm.codeSent) {
            Spacer(Modifier.height(12.dp))
            Text("Kode 6 digit sudah dikirim ke ${vm.email.trim()}. Cek juga folder Spam.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = vm.code,
                onValueChange = { v -> vm.code = v.filter(Char::isDigit).take(6); vm.error = null },
                label = { Text("Kode") },
                singleLine = true,
                enabled = !vm.busy,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                textStyle = MaterialTheme.typography.headlineSmall.copy(letterSpacing = MaterialTheme.typography.headlineSmall.fontSize * 0.3f),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        vm.error?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = { if (vm.codeSent) vm.verify() else vm.sendCode() },
            enabled = !vm.busy,
            modifier = Modifier.fillMaxWidth().height(48.dp),
        ) {
            if (vm.busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
            else Text(if (vm.codeSent) "Masuk" else "Kirim kode")
        }
        if (vm.codeSent) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = { vm.codeSent = false; vm.code = "" }) { Text("Ganti email") }
                TextButton(onClick = vm::sendCode, enabled = resendIn == 0 && !vm.busy) {
                    Text(if (resendIn > 0) "Kirim ulang (${resendIn}d)" else "Kirim ulang")
                }
            }
        }
    }
}

@Composable
private fun SignedIn(
    account: Account,
    syncing: Boolean,
    lastSuccessAt: Long,
    syncError: String?,
    busy: Boolean,
    onSync: () -> Unit,
    onSignOut: () -> Unit,
) {
    var confirmSignOut by remember { mutableStateOf(false) }
    KopiCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(Icons.Outlined.CloudDone, MaterialTheme.colorScheme.primary, 44.dp)
            Spacer(Modifier.width(12.dp))
            Column {
                Text("Masuk sebagai", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(account.email ?: "Akun Duitku", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            }
        }
    }
    KopiCard(Modifier.fillMaxWidth()) {
        Text("Sinkron", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        Text(
            when {
                syncing -> "Sedang menyinkronkan…"
                lastSuccessAt > 0 -> "Terakhir: " + Instant.ofEpochMilli(lastSuccessAt).atZone(ZoneId.systemDefault()).format(timeFormat)
                else -> "Belum pernah sinkron"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        syncError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        Text(
            "Perubahan otomatis dikirim beberapa detik setelah kamu mencatat.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = onSync, enabled = !syncing, modifier = Modifier.fillMaxWidth()) {
            Text(if (syncing) "Menyinkronkan…" else "Sinkronkan sekarang")
        }
    }
    TextButton(onClick = { confirmSignOut = true }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
        Text("Keluar dari akun", color = MaterialTheme.colorScheme.error)
    }
    if (confirmSignOut) {
        ConfirmDialog(
            title = "Keluar dari akun?",
            message = "Data di HP ini tetap ada. Sinkron, fitur AI, dan bot Telegram berhenti sampai kamu masuk lagi.",
            confirmLabel = "Keluar",
            onConfirm = onSignOut,
            onDismiss = { confirmSignOut = false },
        )
    }
}
