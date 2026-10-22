package com.aamnnr.duitku.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalSize
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.aamnnr.duitku.DuitkuApp
import com.aamnnr.duitku.MainActivity
import com.aamnnr.duitku.domain.TransactionType
import com.aamnnr.duitku.domain.computeStats
import com.aamnnr.duitku.domain.isCashflow
import com.aamnnr.duitku.notif.Notifications
import com.aamnnr.duitku.ui.endMillis
import com.aamnnr.duitku.ui.formatCompact
import com.aamnnr.duitku.ui.formatRupiah
import com.aamnnr.duitku.ui.startMillis
import com.aamnnr.duitku.ui.toLocalDate
import kotlinx.coroutines.flow.first
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

private enum class Period(val chip: String, val label: String) {
    DAY("Hari", "Pengeluaran hari ini"),
    WEEK("Minggu", "Pengeluaran minggu ini"),
    MONTH("Bulan", "Pengeluaran bulan ini"),
}

private data class PeriodTotal(val expense: Long, val count: Int, val avgPerDay: Long)

private data class SpendingData(
    val totals: Map<Period, PeriodTotal>,
    val monthlyLimit: Long,
    val hidden: Boolean,
)

private val PeriodKey = intPreferencesKey("period")

/**
 * Widget pengeluaran: hari ini / minggu ini (Senin–Minggu) / bulan ini.
 * Ketuk chip untuk ganti periode; pilihan disimpan per widget. Di ukuran kecil, ketuk widget memutar periode.
 */
class SpendingWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(setOf(SMALL, WIDE))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = load(context)
        provideContent {
            val period = Period.entries.getOrElse(currentState<Preferences>()[PeriodKey] ?: 0) { Period.DAY }
            Content(context, data, period)
        }
    }

    private suspend fun load(context: Context): SpendingData {
        val c = (context.applicationContext as DuitkuApp).container
        val pid = c.profiles.activeProfileId.first()
        val today = LocalDate.now()
        val starts = mapOf(
            Period.DAY to today,
            Period.WEEK to today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)),
            Period.MONTH to today.withDayOfMonth(1),
        )
        // Satu kueri untuk rentang terlebar (awal minggu bisa jatuh di bulan sebelumnya).
        val from = starts.values.min()
        val items = c.transactions.observeItems(pid, from.startMillis(), today.endMillis()).first()
        val totals = starts.mapValues { (_, start) ->
            val inRange = items.filter { it.date.toLocalDate() >= start }
            val s = computeStats(inRange, start, today, today)
            PeriodTotal(s.expense, inRange.count { it.isCashflow && it.type == TransactionType.EXPENSE }, s.avgExpensePerDay)
        }
        return SpendingData(totals, c.settings.monthlyLimit(pid).first(), c.settings.hideBalance.first())
    }

    @Composable
    private fun Content(context: Context, d: SpendingData, period: Period) {
        val ink = ColorProvider(Color(0xFFF8F6F3))
        val muted = ColorProvider(Color(0xFFF8F6F3).copy(alpha = 0.65f))
        val small = LocalSize.current.width < WIDE.width
        val total = d.totals.getValue(period)
        fun money(v: Long, compact: Boolean = false) =
            if (d.hidden) "Rp ••••" else if (compact) formatCompact(v) else formatRupiah(v)
        val open = actionStartActivity(
            Intent(context, MainActivity::class.java).putExtra(Notifications.EXTRA_OPEN, "home")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        )
        val next = Period.entries[(period.ordinal + 1) % Period.entries.size]

        Column(
            GlanceModifier.fillMaxSize().cornerRadius(24.dp).background(ColorProvider(Color(0xFF2A2421)))
                .padding(if (small) 12.dp else 16.dp)
                .clickable(if (small) selectAction(next) else open),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (small) {
                Text(period.label.removePrefix("Pengeluaran ").replaceFirstChar { it.uppercase() }, style = TextStyle(color = muted, fontSize = 12.sp))
                Text(money(total.expense, compact = true), style = TextStyle(color = ink, fontSize = 20.sp, fontWeight = FontWeight.Bold))
                Text("Ketuk: ${next.chip.lowercase()} ini", style = TextStyle(color = muted, fontSize = 10.sp))
                return@Column
            }
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Duitku", style = TextStyle(color = muted, fontSize = 12.sp), modifier = GlanceModifier.defaultWeight())
                Period.entries.forEach { p ->
                    Spacer(GlanceModifier.width(4.dp))
                    Chip(p.chip, selected = p == period, action = selectAction(p))
                }
            }
            Spacer(GlanceModifier.height(10.dp))
            Text(period.label, style = TextStyle(color = muted, fontSize = 12.sp))
            Text(money(total.expense), style = TextStyle(color = ink, fontSize = 24.sp, fontWeight = FontWeight.Bold))
            Text(caption(period, total, d, ::money), style = TextStyle(color = muted, fontSize = 12.sp), maxLines = 1)
            Spacer(GlanceModifier.height(8.dp))
            // Dua periode lain sebagai pembanding cepat.
            Row(GlanceModifier.fillMaxWidth()) {
                Period.entries.filter { it != period }.forEachIndexed { i, p ->
                    if (i > 0) Spacer(GlanceModifier.width(8.dp))
                    Text(
                        "${p.chip}: ${money(d.totals.getValue(p).expense, compact = true)}",
                        style = TextStyle(color = ink, fontSize = 12.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center),
                        modifier = GlanceModifier.defaultWeight().cornerRadius(12.dp)
                            .background(ColorProvider(Color(0x1FFFFFFF))).padding(vertical = 6.dp)
                            .clickable(selectAction(p)),
                    )
                }
            }
        }
    }

    private fun caption(period: Period, t: PeriodTotal, d: SpendingData, money: (Long, Boolean) -> String): String = when {
        period == Period.MONTH && d.monthlyLimit > 0 -> {
            val pct = t.expense * 100 / d.monthlyLimit
            "$pct% dari batas ${money(d.monthlyLimit, true)}"
        }
        period == Period.DAY -> if (t.count == 0) "Belum ada pengeluaran" else "${t.count} transaksi"
        else -> "Rata-rata ${money(t.avgPerDay, true)}/hari"
    }

    @Composable
    private fun Chip(label: String, selected: Boolean, action: androidx.glance.action.Action) {
        Text(
            label,
            style = TextStyle(
                color = ColorProvider(if (selected) Color(0xFF2A2421) else Color(0xFFF8F6F3)),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
            ),
            modifier = GlanceModifier.cornerRadius(50.dp)
                .background(ColorProvider(if (selected) Color(0xFFC08A5B) else Color(0x22FFFFFF)))
                .padding(horizontal = 10.dp, vertical = 5.dp)
                .clickable(action),
        )
    }

    private fun selectAction(p: Period) =
        actionRunCallback<SelectPeriodAction>(actionParametersOf(SelectPeriodAction.PERIOD to p.ordinal))

    companion object {
        private val SMALL = DpSize(110.dp, 40.dp)
        private val WIDE = DpSize(250.dp, 110.dp)

        suspend fun refresh(context: Context) = runCatching { SpendingWidget().updateAll(context) }
    }
}

class SelectPeriodAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val period = parameters[PERIOD] ?: return
        updateAppWidgetState(context, glanceId) { it[PeriodKey] = period }
        SpendingWidget().update(context, glanceId)
    }

    companion object {
        val PERIOD = ActionParameters.Key<Int>("period")
    }
}

class SpendingWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = SpendingWidget()
}
