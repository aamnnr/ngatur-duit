package com.aamnnr.duitku.notif

import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.app.Notification
import com.aamnnr.duitku.AppContainer
import com.aamnnr.duitku.DuitkuApp
import com.aamnnr.duitku.data.local.NotificationDraftEntity
import com.aamnnr.duitku.data.repository.Flags
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.security.MessageDigest

object AutoImport {
    /** Apakah pengguna sudah memberi izin akses notifikasi ke Duitku. */
    fun hasPermission(context: Context): Boolean {
        val enabled = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners") ?: return false
        val me = ComponentName(context, DuitkuNotificationListener::class.java).flattenToString()
        return enabled.split(":").any { it == me }
    }

    /**
     * Ubah satu notifikasi menjadi draf di Inbox. Mengembalikan draf bila baru (bukan duplikat).
     * Dompet dicocokkan dari nama sumber (mis. "GoPay"), kategori dari kata kunci merchant.
     */
    suspend fun handle(c: AppContainer, pkg: String, title: String?, text: String?, postedAt: Long): NotificationDraftEntity? {
        val parsed = NotificationParser.parse(pkg, title, text) ?: return null
        val raw = listOfNotNull(title, text).joinToString(" — ").take(500)
        val pid = c.profiles.activeProfileId.first()
        val wallets = c.wallets.observeWithBalance(pid).first()
        val wallet = wallets.firstOrNull { w ->
            w.name.contains(parsed.sourceName, ignoreCase = true) || w.institution?.contains(parsed.sourceName, ignoreCase = true) == true
        }
        val categories = c.categories.observeAll(pid).first().filter { it.type.name == parsed.type }
        val category = NotificationParser.suggestCategory(raw, categories.map { it.id to it.name })
        // Satu notifikasi bisa diperbarui berkali-kali; sidik jari per menit mencegah draf ganda.
        val fingerprint = sha1("$pkg|${parsed.amount}|${parsed.type}|${parsed.counterpart}|${postedAt / 60_000}")
        val draft = NotificationDraftEntity(
            fingerprint = fingerprint,
            sourcePackage = pkg,
            sourceName = parsed.sourceName,
            rawText = raw,
            type = parsed.type,
            amount = parsed.amount,
            note = parsed.counterpart,
            walletId = wallet?.id,
            categoryId = category,
            confidence = if (category == null) parsed.confidence.coerceAtMost(0.55) else parsed.confidence,
            receivedAt = postedAt,
        )
        return if (c.notificationDrafts.add(draft)) draft else null
    }

    private fun sha1(s: String): String =
        MessageDigest.getInstance("SHA-1").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }
}

/** Membaca notifikasi dari aplikasi bank & e-wallet yang dikenal (lihat [NotificationParser.SOURCES]). */
class DuitkuNotificationListener : NotificationListenerService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName !in NotificationParser.SOURCES) return
        val extras = sbn.notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
        val text = (extras.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: extras.getCharSequence(Notification.EXTRA_TEXT))?.toString()
        val container = (application as DuitkuApp).container
        scope.launch {
            if (!container.settings.flagNow(Flags.AUTO_IMPORT)) return@launch
            val draft = AutoImport.handle(container, sbn.packageName, title, text, sbn.postTime) ?: return@launch
            Notifications.draftReady(this@DuitkuNotificationListener, draft)
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
