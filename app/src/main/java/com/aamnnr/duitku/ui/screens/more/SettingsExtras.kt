package com.aamnnr.duitku.ui.screens.more

import android.Manifest
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.TrackChanges
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.clickable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aamnnr.duitku.data.backup.BackupService
import com.aamnnr.duitku.data.repository.Flags
import com.aamnnr.duitku.notif.AutoImport
import com.aamnnr.duitku.notif.Notifications
import com.aamnnr.duitku.security.PinSetup
import com.aamnnr.duitku.security.biometricAvailable
import com.aamnnr.duitku.ui.LocalUndo
import com.aamnnr.duitku.ui.appContainer
import com.aamnnr.duitku.widget.DuitkuWidgetReceiver
import com.aamnnr.duitku.work.Scheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

/** Bagian Pengaturan untuk otomatisasi (auto-import, pengingat, peringatan, widget) serta keamanan & data. */
@Composable
internal fun AutomationAndSecurity(onOpenInsight: () -> Unit) {
    val context = LocalContext.current
    val c = appContainer()
    val scope = rememberCoroutineScope()
    val undo = LocalUndo.current
    val settings = c.settings

    val autoImport by settings.flag(Flags.AUTO_IMPORT).collectAsStateWithLifecycle(false)
    val reminder by settings.flag(Flags.DAILY_REMINDER).collectAsStateWithLifecycle(false)
    val alerts by settings.flag(Flags.BUDGET_ALERTS).collectAsStateWithLifecycle(false)
    val lock by settings.flag(Flags.APP_LOCK).collectAsStateWithLifecycle(false)
    val bio by settings.flag(Flags.BIOMETRIC).collectAsStateWithLifecycle(false)
    val reminderTime by settings.observeText(Flags.REMINDER_TIME).collectAsStateWithLifecycle(null)

    // Izin akses notifikasi dicek ulang setiap kembali ke layar ini (setelah dari pengaturan sistem).
    var resumeTick by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        resumeTick++
        onPauseOrDispose { }
    }
    val listenerGranted = remember(resumeTick) { AutoImport.hasPermission(context) }

    var pinSetup by remember { mutableStateOf(false) }
    var timeDialog by remember { mutableStateOf(false) }
    var backupDialog by remember { mutableStateOf(false) }

    fun setFlag(key: String, on: Boolean) = scope.launch {
        settings.setFlag(key, on)
        Scheduler.apply(context)
    }

    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    fun ensureNotifPermission() {
        if (Build.VERSION.SDK_INT >= 33 && !Notifications.canPost(context)) {
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) undo.run("Backup tersimpan") {
            withContext(Dispatchers.IO) { BackupService(context, c.database).export(uri) }
        }
    }
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            val result = runCatching { withContext(Dispatchers.IO) { BackupService(context, c.database).restore(uri) } }
            undo.run(
                result.fold(
                    { "Dipulihkan: ${it.transactions} transaksi, ${it.wallets} dompet" },
                    { "Gagal memulihkan: file bukan backup Duitku yang valid" },
                ),
            ) {}
        }
    }

    Group("Otomatis") {
        SettingRow(
            Icons.Outlined.NotificationsActive,
            "Auto-import notifikasi bank",
            value = when {
                !autoImport -> null
                listenerGranted -> "Aktif"
                else -> "Izin belum diberikan"
            },
            checked = autoImport && listenerGranted,
            onClick = {
                if (autoImport && listenerGranted) {
                    setFlag(Flags.AUTO_IMPORT, false)
                } else {
                    setFlag(Flags.AUTO_IMPORT, true)
                    ensureNotifPermission()
                    if (!listenerGranted) {
                        context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }
                }
            },
        )
        SettingRow(
            Icons.Outlined.Alarm,
            "Pengingat harian",
            value = if (reminder) "Jam ${reminderTime ?: "20:00"}" else null,
            checked = reminder,
            onClick = {
                if (!reminder) ensureNotifPermission()
                setFlag(Flags.DAILY_REMINDER, !reminder)
            },
        )
        if (reminder) {
            SettingRow(Icons.Outlined.Alarm, "Ubah jam pengingat", value = reminderTime ?: "20:00", onClick = { timeDialog = true })
        }
        SettingRow(
            Icons.Outlined.TrackChanges,
            "Peringatan anggaran",
            value = if (alerts) "Saat 90% & terlampaui" else null,
            checked = alerts,
            onClick = {
                if (!alerts) ensureNotifPermission()
                setFlag(Flags.BUDGET_ALERTS, !alerts)
            },
        )
        SettingRow(
            Icons.Outlined.Widgets,
            "Tambah widget ke layar utama",
            onClick = {
                val manager = AppWidgetManager.getInstance(context)
                val provider = ComponentName(context, DuitkuWidgetReceiver::class.java)
                val pinned = Build.VERSION.SDK_INT >= 26 && manager.isRequestPinAppWidgetSupported &&
                    manager.requestPinAppWidget(provider, null, null)
                if (!pinned) {
                    undo.run("Tahan layar utama → Widget → pilih Duitku") {}
                }
            },
        )
    }

    Group("Keamanan dan data") {
        SettingRow(
            Icons.Outlined.Lock,
            "Kunci dengan PIN",
            value = if (lock) "Aktif" else null,
            checked = lock,
            onClick = {
                if (lock) {
                    scope.launch {
                        settings.setFlag(Flags.APP_LOCK, false)
                        settings.setFlag(Flags.BIOMETRIC, false)
                    }
                } else {
                    pinSetup = true
                }
            },
        )
        val activity = context as? FragmentActivity
        if (lock && activity != null && biometricAvailable(activity)) {
            SettingRow(Icons.Outlined.Fingerprint, "Buka dengan sidik jari / wajah", checked = bio, onClick = {
                scope.launch { settings.setFlag(Flags.BIOMETRIC, !bio) }
            })
        }
        SettingRow(Icons.Outlined.CloudUpload, "Backup dan restore", value = "File JSON", onClick = { backupDialog = true })
        SettingRow(Icons.Outlined.FileDownload, "Ekspor PDF / Excel", value = "Lewat Insight", onClick = onOpenInsight)
    }

    if (pinSetup) {
        Dialog(onDismissRequest = { pinSetup = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            PinSetup(
                onDone = { hash ->
                    scope.launch {
                        settings.setText(Flags.PIN_HASH, hash)
                        settings.setFlag(Flags.APP_LOCK, true)
                    }
                    pinSetup = false
                    undo.run("PIN aktif. Duitku terkunci saat dibuka.") {}
                },
                onCancel = { pinSetup = false },
            )
        }
    }

    if (timeDialog) {
        val options = listOf("19:00", "20:00", "21:00", "22:00")
        AlertDialog(
            onDismissRequest = { timeDialog = false },
            title = { Text("Jam pengingat") },
            text = {
                Column {
                    options.forEach { t ->
                        Row(
                            Modifier.fillMaxWidth().clickable {
                                scope.launch {
                                    settings.setText(Flags.REMINDER_TIME, t)
                                    Scheduler.apply(context)
                                }
                                timeDialog = false
                            },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = (reminderTime ?: "20:00") == t, onClick = null)
                            Text(t)
                        }
                    }
                    Text("Pengingat hanya muncul bila hari itu belum ada catatan.", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            confirmButton = { TextButton(onClick = { timeDialog = false }) { Text("Tutup") } },
        )
    }

    if (backupDialog) {
        AlertDialog(
            onDismissRequest = { backupDialog = false },
            title = { Text("Backup dan restore") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Backup menyimpan semua profil, dompet, kategori, transaksi, anggaran, dan tabungan ke satu file JSON. Simpan di Drive atau tempat aman.")
                    Text("Restore menggabungkan isi file dengan data di HP ini (berdasarkan id); data yang sudah ada tidak hilang.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    backupDialog = false
                    exportLauncher.launch("duitku-backup-${LocalDate.now()}.json")
                }) { Text("Buat backup") }
            },
            dismissButton = {
                TextButton(onClick = {
                    backupDialog = false
                    restoreLauncher.launch(arrayOf("application/json", "application/octet-stream", "*/*"))
                }) { Text("Restore") }
            },
        )
    }
}
