package com.aamnnr.duitku.notif

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.aamnnr.duitku.MainActivity
import com.aamnnr.duitku.R
import com.aamnnr.duitku.data.local.NotificationDraftEntity
import com.aamnnr.duitku.ui.formatRupiah

/** Notifikasi milik Duitku: draf baru dari auto-import, pengingat harian, peringatan anggaran. */
object Notifications {
    const val EXTRA_OPEN = "open"
    const val OPEN_INBOX = "inbox"
    const val OPEN_ADD = "add"
    const val OPEN_PLAN = "plan"

    private const val CH_DRAFTS = "drafts"
    private const val CH_REMINDER = "reminder"
    private const val CH_BUDGET = "budget"

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannels(
            listOf(
                NotificationChannel(CH_DRAFTS, "Draf dari notifikasi bank", NotificationManager.IMPORTANCE_DEFAULT)
                    .apply { description = "Transaksi yang terbaca otomatis dan menunggu disetujui" },
                NotificationChannel(CH_REMINDER, "Pengingat mencatat", NotificationManager.IMPORTANCE_DEFAULT),
                NotificationChannel(CH_BUDGET, "Peringatan anggaran", NotificationManager.IMPORTANCE_HIGH),
            ),
        )
    }

    fun canPost(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun open(context: Context, target: String, code: Int): PendingIntent =
        PendingIntent.getActivity(
            context, code,
            Intent(context, MainActivity::class.java).putExtra(EXTRA_OPEN, target)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    @Suppress("MissingPermission") // dicek lewat canPost()
    private fun post(context: Context, id: Int, builder: NotificationCompat.Builder) {
        if (!canPost(context)) return
        NotificationManagerCompat.from(context).notify(id, builder.build())
    }

    private fun base(context: Context, channel: String) = NotificationCompat.Builder(context, channel)
        .setSmallIcon(R.drawable.ic_launcher_monochrome)
        .setColor(0xFF2A2421.toInt())
        .setAutoCancel(true)

    fun draftReady(context: Context, draft: NotificationDraftEntity) {
        val sign = if (draft.type == "INCOME") "+" else "-"
        post(
            context, draft.fingerprint.hashCode(),
            base(context, CH_DRAFTS)
                .setContentTitle("Draf baru dari ${draft.sourceName}")
                .setContentText("$sign${formatRupiah(draft.amount)}${draft.note?.let { " · $it" } ?: ""} — ketuk untuk menyetujui")
                .setContentIntent(open(context, OPEN_INBOX, 1)),
        )
    }

    fun dailyReminder(context: Context, text: String) {
        post(
            context, 2001,
            base(context, CH_REMINDER)
                .setContentTitle("Sudah catat pengeluaran hari ini?")
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setContentIntent(open(context, OPEN_ADD, 2)),
        )
    }

    fun budgetAlert(context: Context, id: String, title: String, text: String) {
        post(
            context, 3000 + (id.hashCode() and 0xFFFF),
            base(context, CH_BUDGET)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setContentIntent(open(context, OPEN_PLAN, 3)),
        )
    }
}
