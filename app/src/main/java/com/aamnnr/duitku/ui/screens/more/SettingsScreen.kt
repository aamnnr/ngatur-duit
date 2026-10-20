package com.aamnnr.duitku.ui.screens.more

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.aamnnr.duitku.AppContainer
import com.aamnnr.duitku.BuildConfig
import com.aamnnr.duitku.data.local.ProfileEntity
import com.aamnnr.duitku.data.repository.ThemeMode
import com.aamnnr.duitku.domain.ProfileType
import com.aamnnr.duitku.ui.appViewModel
import com.aamnnr.duitku.ui.components.SoonBadge
import com.aamnnr.duitku.ui.components.StatusBarIcons
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.VisibilityOff
import com.aamnnr.duitku.ui.components.NeuCircleButton
import com.aamnnr.duitku.ui.components.neuSurface
import com.aamnnr.duitku.ui.formatCompact
import com.aamnnr.duitku.ui.screens.home.LimitDialog
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class MoreState(
    val profiles: List<ProfileEntity> = emptyList(),
    val activeId: String? = null,
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val accountEmail: String? = null,
    val displayName: String = "",
    val hideBalance: Boolean = false,
    val monthlyLimit: Long = 0,
) {
    val active: ProfileEntity? get() = profiles.firstOrNull { it.id == activeId }
}

@OptIn(ExperimentalCoroutinesApi::class)
class MoreViewModel(private val c: AppContainer) : ViewModel() {
    private val base = combine(c.profiles.profiles, c.profiles.activeProfileId, c.settings.themeMode, c.cloud.account) { p, a, t, acc ->
        MoreState(p, a, t, acc?.email)
    }
    private val limit = c.profiles.activeProfileId.flatMapLatest { c.settings.monthlyLimit(it) }

    val state = combine(base, c.settings.displayName, c.settings.hideBalance, limit) { b, n, h, l ->
        b.copy(displayName = n, hideBalance = h, monthlyLimit = l)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MoreState())

    fun setName(name: String) = viewModelScope.launch { c.settings.setDisplayName(name) }
    fun setHide(hide: Boolean) = viewModelScope.launch { c.settings.setHideBalance(hide) }
    fun setLimit(amount: Long) = viewModelScope.launch { c.settings.setMonthlyLimit(c.profiles.activeProfileId.first(), amount) }

    fun switchTo(id: String) = viewModelScope.launch { c.profiles.switchTo(id) }
    fun create(name: String, type: ProfileType) = viewModelScope.launch { c.profiles.createProfile(name, type) }
    fun rename(profile: ProfileEntity, name: String) = viewModelScope.launch { c.profiles.rename(profile, name) }
    fun setTheme(mode: ThemeMode) = viewModelScope.launch { c.settings.setThemeMode(mode) }
}

private fun ProfileType.label() = if (this == ProfileType.BUSINESS) "Usaha" else "Pribadi"

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenInsight: () -> Unit,
    onOpenWallets: () -> Unit,
    onOpenCategories: () -> Unit,
    onOpenAccount: () -> Unit,
    onOpenTelegram: () -> Unit,
) {
    val vm = appViewModel { MoreViewModel(it) }
    val s by vm.state.collectAsStateWithLifecycle()
    var profileDialog by remember { mutableStateOf(false) }
    var creating by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<ProfileEntity?>(null) }
    var themeDialog by remember { mutableStateOf(false) }
    var nameDialog by remember { mutableStateOf(false) }
    var limitDialog by remember { mutableStateOf(false) }
    StatusBarIcons(lightIcons = false)

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            NeuCircleButton(Icons.AutoMirrored.Filled.ArrowBack, "Kembali", onBack)
            Spacer(Modifier.width(14.dp))
            Text("Profil & Pengaturan", style = MaterialTheme.typography.headlineSmall)
        }
        Spacer(Modifier.height(20.dp))

        // Kartu pengguna: nama tampil untuk sapaan dan kartu dompet.
        Row(
            Modifier
                .fillMaxWidth()
                .neuSurface()
                .clickable { nameDialog = true }
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(52.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    s.displayName.split(" ").filter { it.isNotBlank() }.take(2).joinToString("") { it.take(1).uppercase() }.ifEmpty { "D" },
                    color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.titleMedium,
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(s.displayName.ifBlank { "Atur nama kamu" }, style = MaterialTheme.typography.titleMedium)
                Text(s.accountEmail ?: "Belum masuk akun", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Outlined.Edit, "Ubah nama", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(12.dp))
        // Profil keuangan aktif
        Row(
            Modifier
                .fillMaxWidth()
                .neuSurface()
                .clickable { profileDialog = true }
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.SwapHoriz, null, tint = MaterialTheme.colorScheme.secondary)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Profil keuangan: ${s.active?.name.orEmpty()}", style = MaterialTheme.typography.titleSmall)
                Text("${s.active?.type?.label().orEmpty()} · ketuk untuk ganti atau buat profil",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Group("Akun") {
            SettingRow(
                Icons.Outlined.CloudSync,
                "Akun dan sinkron",
                value = s.accountEmail ?: "Belum masuk",
                onClick = onOpenAccount,
            )
            SettingRow(Icons.AutoMirrored.Outlined.Send, "Bot Telegram", onClick = onOpenTelegram)
        }
        Group("Keuangan") {
            SettingRow(Icons.Outlined.AccountBalanceWallet, "Dompet", onClick = onOpenWallets)
            SettingRow(Icons.Outlined.Category, "Kategori", onClick = onOpenCategories)
            SettingRow(
                Icons.Outlined.Speed,
                "Batas pengeluaran bulanan",
                value = if (s.monthlyLimit > 0) formatCompact(s.monthlyLimit) else "Belum diatur",
                onClick = { limitDialog = true },
            )
        }
        AutomationAndSecurity(onOpenInsight)
        Group("Tampilan") {
            SettingRow(Icons.Outlined.DarkMode, "Tema", value = s.theme.label, onClick = { themeDialog = true })
            SettingRow(
                Icons.Outlined.VisibilityOff,
                "Sembunyikan saldo",
                value = if (s.hideBalance) "Aktif" else "Mati",
                onClick = { vm.setHide(!s.hideBalance) },
            )
        }
        Group("Tentang") {
            SettingRow(Icons.Outlined.Info, "Duitku ${BuildConfig.VERSION_NAME}", value = "Data tersimpan di perangkat ini")
        }
        Spacer(Modifier.height(16.dp))
    }

    if (profileDialog) {
        AlertDialog(
            onDismissRequest = { profileDialog = false },
            title = { Text("Profil keuangan") },
            text = {
                Column {
                    s.profiles.forEach { p ->
                        Row(
                            Modifier.fillMaxWidth().clickable { vm.switchTo(p.id); profileDialog = false },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = p.id == s.activeId, onClick = { vm.switchTo(p.id); profileDialog = false })
                            Column {
                                Text(p.name, style = MaterialTheme.typography.bodyLarge)
                                Text(p.type.label(), style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { profileDialog = false; creating = true }) { Text("Profil baru") } },
            dismissButton = {
                TextButton(onClick = { profileDialog = false; renaming = s.active }) { Text("Ganti nama") }
            },
        )
    }
    if (creating) {
        ProfileDialog("Profil baru", "", showType = true, onDismiss = { creating = false }) { name, type ->
            vm.create(name, type)
            creating = false
        }
    }
    renaming?.let { p ->
        ProfileDialog("Ganti nama profil", p.name, showType = false, onDismiss = { renaming = null }) { name, _ ->
            vm.rename(p, name)
            renaming = null
        }
    }
    if (nameDialog) {
        var name by remember { mutableStateOf(s.displayName) }
        AlertDialog(
            onDismissRequest = { nameDialog = false },
            title = { Text("Nama kamu") },
            text = {
                OutlinedTextField(name, { name = it.take(40) }, label = { Text("Dipakai untuk sapaan dan kartu dompet") }, singleLine = true)
            },
            confirmButton = { TextButton(onClick = { vm.setName(name); nameDialog = false }) { Text("Simpan") } },
            dismissButton = { TextButton(onClick = { nameDialog = false }) { Text("Batal") } },
        )
    }
    if (limitDialog) {
        LimitDialog(current = s.monthlyLimit, onDismiss = { limitDialog = false }) {
            vm.setLimit(it)
            limitDialog = false
        }
    }
    if (themeDialog) {
        AlertDialog(
            onDismissRequest = { themeDialog = false },
            title = { Text("Tema") },
            text = {
                Column {
                    ThemeMode.entries.forEach { m ->
                        Row(
                            Modifier.fillMaxWidth().clickable { vm.setTheme(m); themeDialog = false },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = s.theme == m, onClick = { vm.setTheme(m); themeDialog = false })
                            Text(m.label)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { themeDialog = false }) { Text("Tutup") } },
        )
    }
}

@Composable
internal fun Group(title: String, content: @Composable ColumnScope.() -> Unit) {
    Text(
        title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp, top = 20.dp, bottom = 6.dp),
    )
    Column(
        Modifier.fillMaxWidth().neuSurface(),
        content = content,
    )
}

@Composable
internal fun SettingRow(
    icon: ImageVector,
    title: String,
    value: String? = null,
    soon: Boolean = false,
    onClick: (() -> Unit)? = null,
    /** Bila diisi, baris menampilkan sakelar. */
    checked: Boolean? = null,
) {
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(enabled = onClick != null) { onClick?.invoke() }
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
            Text(title, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            when {
                soon -> SoonBadge()
                value != null -> Text(value, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (checked != null) {
                androidx.compose.material3.Switch(checked = checked, onCheckedChange = { onClick?.invoke() })
            } else if (onClick != null) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        HorizontalDivider(Modifier.padding(start = 50.dp), color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
private fun ProfileDialog(
    title: String,
    initialName: String,
    showType: Boolean,
    onDismiss: () -> Unit,
    onSave: (String, ProfileType) -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    var type by remember { mutableStateOf(ProfileType.PERSONAL) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Nama profil") }, singleLine = true)
                if (showType) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ProfileType.entries.forEach { t ->
                            FilterChip(selected = type == t, onClick = { type = t }, label = { Text(t.label()) })
                        }
                    }
                    Text("Profil baru mendapat dompet utama dan kategori bawaan sesuai jenisnya.",
                        style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(name.trim(), type) }, enabled = name.isNotBlank()) { Text("Simpan") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } },
    )
}
