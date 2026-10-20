package com.aamnnr.duitku.ui.screens.telegram

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Send
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aamnnr.duitku.AppContainer
import com.aamnnr.duitku.BuildConfig
import com.aamnnr.duitku.data.remote.RemoteTelegramLink
import com.aamnnr.duitku.ui.appViewModel
import com.aamnnr.duitku.ui.components.ConfirmDialog
import com.aamnnr.duitku.ui.components.EmptyState
import com.aamnnr.duitku.ui.components.IconBadge
import com.aamnnr.duitku.ui.components.KopiCard
import com.aamnnr.duitku.ui.components.StatusBarIcons
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class TelegramViewModel(private val c: AppContainer) : ViewModel() {
    val signedIn get() = c.cloud.isConfigured && c.cloud.currentAccount() != null
    var loading by mutableStateOf(true)
        private set
    var link by mutableStateOf<RemoteTelegramLink?>(null)
        private set
    var code by mutableStateOf<String?>(null)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    init { refresh() }

    fun refresh() = viewModelScope.launch {
        if (!signedIn) { loading = false; return@launch }
        loading = true
        error = null
        try {
            // Bot membaca kategori & dompet dari cloud, jadi pastikan data terbaru sudah terkirim.
            c.sync.syncNow()
            link = c.cloud.telegramLink()
            if (link != null) code = null
        } catch (e: Exception) {
            error = "Gagal memuat status bot. Periksa koneksi internet."
        } finally {
            loading = false
        }
    }

    /** Cek ringan (tanpa sinkron) apakah pairing dari Telegram sudah berhasil. */
    fun pollLink() = viewModelScope.launch {
        runCatching { c.cloud.telegramLink() }.getOrNull()?.let {
            link = it
            code = null
        }
    }

    fun createCode() = viewModelScope.launch {
        error = null
        try {
            code = c.cloud.createPairingCode(c.profiles.activeProfileId.first())
        } catch (e: Exception) {
            error = "Gagal membuat kode. Coba lagi."
        }
    }

    fun clearCode() { code = null }

    fun unlink() = viewModelScope.launch {
        try {
            c.cloud.unlinkTelegram()
            link = null
        } catch (e: Exception) {
            error = "Gagal memutus bot. Coba lagi."
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TelegramScreen(onBack: () -> Unit, onOpenAccount: () -> Unit) {
    val vm = appViewModel { TelegramViewModel(it) }
    val context = LocalContext.current
    var confirmUnlink by remember { mutableStateOf(false) }
    var secondsLeft by remember { mutableIntStateOf(0) }
    val botUser = BuildConfig.TELEGRAM_BOT_USERNAME.removePrefix("@")
    StatusBarIcons(lightIcons = false)

    LaunchedEffect(vm.code) {
        if (vm.code != null) {
            secondsLeft = 600
            while (secondsLeft > 0) {
                delay(1_000)
                secondsLeft--
                // Cek berkala apakah pairing sudah berhasil dari Telegram.
                if (secondsLeft % 5 == 0) vm.pollLink()
            }
            vm.clearCode()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Bot Telegram") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Kembali") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (!vm.signedIn) {
                EmptyState("Masuk dulu untuk memakai bot", "Bot Telegram terhubung ke akun Duitku-mu.")
                Button(onClick = onOpenAccount, modifier = Modifier.fillMaxWidth()) { Text("Masuk ke akun") }
                return@Column
            }
            KopiCard(Modifier.fillMaxWidth()) {
                IconBadge(Icons.AutoMirrored.Outlined.Send, MaterialTheme.colorScheme.primary, 44.dp)
                Spacer(Modifier.height(12.dp))
                Text("Catat lewat chat Telegram", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    "Kirim \"bakso 25rb pakai gopay\" atau foto struk ke bot. Hasilnya masuk ke Inbox untuk kamu setujui.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            when {
                vm.loading && vm.link == null && vm.code == null -> CircularProgressIndicator()
                vm.link != null -> KopiCard(Modifier.fillMaxWidth()) {
                    Text("Terhubung", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary)
                    Text(vm.link?.username?.let { "Akun Telegram @$it" } ?: "Akun Telegram terhubung",
                        style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(8.dp))
                    if (botUser.isNotBlank()) {
                        OutlinedButton(
                            onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/$botUser"))) },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Buka bot") }
                    }
                    TextButton(onClick = { confirmUnlink = true }, modifier = Modifier.fillMaxWidth()) {
                        Text("Putuskan bot", color = MaterialTheme.colorScheme.error)
                    }
                }
                vm.code != null -> KopiCard(Modifier.fillMaxWidth()) {
                    Text("Kode pairing", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        vm.code!!,
                        fontSize = 40.sp,
                        letterSpacing = 8.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp).clip(MaterialTheme.shapes.small)
                            .background(MaterialTheme.colorScheme.primaryContainer).padding(vertical = 12.dp),
                    )
                    Text("Berlaku ${secondsLeft / 60}:${(secondsLeft % 60).toString().padStart(2, '0')} lagi",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(12.dp))
                    if (botUser.isNotBlank()) {
                        // Tautan start=KODE membuat Telegram langsung mengirim "/start KODE" ke bot.
                        Button(
                            onClick = {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/$botUser?start=${vm.code}")))
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Buka Telegram dan hubungkan") }
                    } else {
                        Text("Buka botmu di Telegram, lalu kirim: /start ${vm.code}", style = MaterialTheme.typography.bodyMedium)
                    }
                    OutlinedButton(
                        onClick = {
                            context.getSystemService(ClipboardManager::class.java)
                                ?.setPrimaryClip(ClipData.newPlainText("Duitku", "/start ${vm.code}"))
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Salin perintah /start") }
                }
                else -> Button(onClick = { vm.createCode() }, modifier = Modifier.fillMaxWidth()) { Text("Buat kode pairing") }
            }
            vm.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        }
    }

    if (confirmUnlink) {
        ConfirmDialog(
            title = "Putuskan bot?",
            message = "Bot tidak akan menerima catatan lagi sampai kamu menghubungkannya kembali.",
            confirmLabel = "Putuskan",
            onConfirm = { vm.unlink() },
            onDismiss = { confirmUnlink = false },
        )
    }
}
