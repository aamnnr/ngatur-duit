package com.aamnnr.duitku.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.aamnnr.duitku.DuitkuApp
import com.aamnnr.duitku.data.repository.Flags
import com.aamnnr.duitku.domain.budgetProgress
import com.aamnnr.duitku.domain.computeStats
import com.aamnnr.duitku.notif.Notifications
import com.aamnnr.duitku.ui.formatRupiah
import kotlinx.coroutines.flow.first
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/** Menjadwalkan / membatalkan pekerjaan latar sesuai pengaturan. Dipanggil saat aplikasi dibuka dan saat pengaturan berubah. */
object Scheduler {
    private const val REMINDER = "daily_reminder"
    private const val BUDGET = "budget_check"

    suspend fun apply(context: Context) {
        val settings = (context.applicationContext as DuitkuApp).container.settings
        val wm = WorkManager.getInstance(context)
        if (settings.flagNow(Flags.DAILY_REMINDER)) {
            val time = settings.text(Flags.REMINDER_TIME)?.let { runCatching { LocalTime.parse(it) }.getOrNull() } ?: LocalTime.of(20, 0)
            val now = LocalDateTime.now()
            var next = now.toLocalDate().atTime(time)
            if (!next.isAfter(now)) next = next.plusDays(1)
            val request = PeriodicWorkRequestBuilder<ReminderWorker>(1, TimeUnit.DAYS)
                .setInitialDelay(Duration.between(now, next).toMinutes(), TimeUnit.MINUTES)
                .build()
            // UPDATE: jam pengingat baru langsung berlaku.
            wm.enqueueUniquePeriodicWork(REMINDER, ExistingPeriodicWorkPolicy.UPDATE, request)
        } else {
            wm.cancelUniqueWork(REMINDER)
        }
        if (settings.flagNow(Flags.BUDGET_ALERTS)) {
            wm.enqueueUniquePeriodicWork(
                BUDGET, ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<BudgetCheckWorker>(6, TimeUnit.HOURS).build(),
            )
        } else {
            wm.cancelUniqueWork(BUDGET)
        }
    }
}

/** Mengingatkan bila hari ini belum ada transaksi yang dicatat. */
class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    private val lines = listOf(
        "Satu menit sekarang lebih mudah daripada mengingat-ingat besok.",
        "Kopi, parkir, jajan kecil: yang kecil-kecil itu yang sering lupa.",
        "Catat sekarang biar saldo di Duitku sama dengan saldo aslinya.",
        "Tekan + lalu ketik nominalnya. Tiga ketukan saja.",
    )

    override suspend fun doWork(): Result {
        val c = (applicationContext as DuitkuApp).container
        if (!c.settings.flagNow(Flags.DAILY_REMINDER)) return Result.success()
        val pid = c.profiles.activeProfileId.first()
        val zone = ZoneId.systemDefault()
        val start = LocalDate.now().atStartOfDay(zone).toInstant().toEpochMilli()
        val end = LocalDate.now().plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1
        val today = c.transactions.observeItems(pid, start, end).first()
        if (today.isEmpty()) {
            Notifications.dailyReminder(applicationContext, lines[LocalDate.now().dayOfYear % lines.size])
        }
        return Result.success()
    }
}

/** Memberi peringatan saat anggaran atau batas bulanan mencapai 90% dan saat terlampaui (sekali per periode). */
class BudgetCheckWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val c = (applicationContext as DuitkuApp).container
        if (!c.settings.flagNow(Flags.BUDGET_ALERTS)) return Result.success()
        val pid = c.profiles.activeProfileId.first()
        val today = LocalDate.now()
        val zone = ZoneId.systemDefault()
        val from = today.minusDays(40).atStartOfDay(zone).toInstant().toEpochMilli()
        val to = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1
        val items = c.transactions.observeItems(pid, from, to).first()

        suspend fun alertOnce(key: String, title: String, text: String) {
            val k = "alerted_$key"
            if (c.settings.text(k) != null) return
            Notifications.budgetAlert(applicationContext, key, title, text)
            c.settings.setText(k, System.currentTimeMillis().toString())
        }

        c.plans.observeBudgets(pid).first().forEach { b ->
            val p = budgetProgress(b, items, today)
            val level = when { p.ratio >= 1f -> 100; p.ratio >= 0.9f -> 90; else -> return@forEach }
            alertOnce(
                "${b.id}_${p.start}_$level",
                if (level == 100) "Anggaran ${b.name} terlampaui" else "Anggaran ${b.name} hampir habis",
                "Terpakai ${formatRupiah(p.spent)} dari ${formatRupiah(b.amount)}." +
                    if (level == 90) " Aman ${formatRupiah(p.safePerDay(today))} per hari sampai akhir periode." else "",
            )
        }

        val limit = c.settings.monthlyLimit(pid).first()
        if (limit > 0) {
            val monthStart = today.withDayOfMonth(1)
            val spent = computeStats(items.filter {
                java.time.Instant.ofEpochMilli(it.date).atZone(zone).toLocalDate() >= monthStart
            }, monthStart, today).expense
            val ratio = spent.toFloat() / limit
            val level = when { ratio >= 1f -> 100; ratio >= 0.9f -> 90; else -> 0 }
            if (level > 0) {
                alertOnce(
                    "limit_${monthStart}_$level",
                    if (level == 100) "Batas bulanan terlampaui" else "Batas bulanan hampir habis",
                    "Pengeluaran bulan ini ${formatRupiah(spent)} dari batas ${formatRupiah(limit)}.",
                )
            }
        }
        return Result.success()
    }
}
