package com.aamnnr.duitku

import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aamnnr.duitku.data.repository.Flags
import com.aamnnr.duitku.data.repository.ThemeMode
import com.aamnnr.duitku.notif.Notifications
import com.aamnnr.duitku.security.LockScreen
import com.aamnnr.duitku.ui.DuitkuNavHost
import com.aamnnr.duitku.ui.theme.DuitkuTheme

/** FragmentActivity (bukan ComponentActivity) karena BiometricPrompt membutuhkannya. */
class MainActivity : FragmentActivity() {
    /** Tujuan dari notifikasi/widget ("inbox", "add", "plan", "scan"), diproses sekali oleh navigasi. */
    private var openTarget by mutableStateOf<String?>(null)
    private var unlocked by mutableStateOf(false)
    private var stoppedAt = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        openTarget = intent?.getStringExtra(Notifications.EXTRA_OPEN)
        unlocked = savedInstanceState?.getBoolean(KEY_UNLOCKED) ?: false
        val settings = (application as DuitkuApp).container.settings
        setContent {
            val mode by settings.themeMode.collectAsStateWithLifecycle(initialValue = ThemeMode.SYSTEM)
            val lockOn by settings.flag(Flags.APP_LOCK).collectAsStateWithLifecycle(initialValue = null)
            val bioOn by settings.flag(Flags.BIOMETRIC).collectAsStateWithLifecycle(initialValue = false)
            val pinHash by settings.observeText(Flags.PIN_HASH).collectAsStateWithLifecycle(initialValue = null)
            val dark = when (mode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            DuitkuTheme(darkTheme = dark) {
                when {
                    lockOn == null -> Unit // pengaturan belum terbaca: jangan tampilkan data sekejap
                    lockOn == true && pinHash != null && !unlocked -> LockScreen(pinHash, bioOn) { unlocked = true }
                    else -> DuitkuNavHost(openTarget = openTarget, onTargetHandled = { openTarget = null })
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.getStringExtra(Notifications.EXTRA_OPEN)?.let { openTarget = it }
    }

    override fun onStop() {
        super.onStop()
        stoppedAt = SystemClock.elapsedRealtime()
    }

    override fun onStart() {
        super.onStart()
        // Kunci lagi bila aplikasi ditinggal lebih dari 30 detik.
        if (stoppedAt > 0 && SystemClock.elapsedRealtime() - stoppedAt > LOCK_AFTER_MS) unlocked = false
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(KEY_UNLOCKED, unlocked)
    }

    private companion object {
        const val KEY_UNLOCKED = "unlocked"
        const val LOCK_AFTER_MS = 30_000L
    }
}
