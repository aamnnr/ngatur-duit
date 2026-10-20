package com.aamnnr.duitku.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
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
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.aamnnr.duitku.DuitkuApp
import com.aamnnr.duitku.MainActivity
import com.aamnnr.duitku.domain.computeStats
import com.aamnnr.duitku.notif.Notifications
import com.aamnnr.duitku.ui.endDate
import com.aamnnr.duitku.ui.formatRupiah
import com.aamnnr.duitku.ui.millisRange
import com.aamnnr.duitku.ui.startDate
import kotlinx.coroutines.flow.first
import java.time.YearMonth

private data class WidgetData(val balance: Long, val monthExpense: Long, val hidden: Boolean)

/** Widget 4x2: saldo total, pengeluaran bulan ini, tombol Catat dan Scan. */
class DuitkuWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = load(context)
        provideContent { Content(context, data) }
    }

    private suspend fun load(context: Context): WidgetData {
        val c = (context.applicationContext as DuitkuApp).container
        val pid = c.profiles.activeProfileId.first()
        val month = YearMonth.now()
        val (from, to) = month.millisRange()
        val wallets = c.wallets.observeWithBalance(pid).first()
        val items = c.transactions.observeItems(pid, from, to).first()
        return WidgetData(
            balance = wallets.sumOf { it.balance },
            monthExpense = computeStats(items, month.startDate, month.endDate).expense,
            hidden = c.settings.hideBalance.first(),
        )
    }

    @Composable
    private fun Content(context: Context, d: WidgetData) {
        val ink = ColorProvider(Color(0xFFF8F6F3))
        val muted = ColorProvider(Color(0xFFF8F6F3).copy(alpha = 0.65f))
        fun open(target: String) = actionStartActivity(
            Intent(context, MainActivity::class.java).putExtra(Notifications.EXTRA_OPEN, target)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        )
        Column(
            GlanceModifier.fillMaxSize().cornerRadius(24.dp).background(ColorProvider(Color(0xFF2A2421))).padding(16.dp)
                .clickable(open("home")),
        ) {
            Text("Duitku · Total saldo", style = TextStyle(color = muted, fontSize = 12.sp))
            Text(
                if (d.hidden) "Rp ••••••" else formatRupiah(d.balance),
                style = TextStyle(color = ink, fontSize = 22.sp, fontWeight = FontWeight.Bold),
            )
            Text(
                "Pengeluaran bulan ini " + if (d.hidden) "Rp ••••" else formatRupiah(d.monthExpense),
                style = TextStyle(color = muted, fontSize = 12.sp),
            )
            Spacer(GlanceModifier.height(12.dp))
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Button("+  Catat", open(Notifications.OPEN_ADD), primary = true)
                Spacer(GlanceModifier.width(8.dp))
                Button("Scan struk", open("scan"), primary = false)
            }
        }
    }

    @Composable
    private fun Button(label: String, action: androidx.glance.action.Action, primary: Boolean) {
        Text(
            label,
            style = TextStyle(
                color = ColorProvider(if (primary) Color(0xFF2A2421) else Color(0xFFF8F6F3)),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
            ),
            modifier = GlanceModifier
                .cornerRadius(50.dp)
                .background(ColorProvider(if (primary) Color(0xFFC08A5B) else Color(0x33FFFFFF)))
                .padding(horizontal = 14.dp, vertical = 8.dp)
                .clickable(action),
        )
    }

    companion object {
        /** Muat ulang semua widget (dipanggil setelah data berubah). */
        suspend fun refresh(context: Context) = runCatching { DuitkuWidget().updateAll(context) }
    }
}

class DuitkuWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = DuitkuWidget()
}
