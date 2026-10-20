package com.aamnnr.duitku.ui.screens.insight

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.aamnnr.duitku.AppContainer
import com.aamnnr.duitku.domain.CategoryTotal
import com.aamnnr.duitku.domain.PeriodStats
import com.aamnnr.duitku.domain.TransactionItem
import com.aamnnr.duitku.domain.TransactionType
import com.aamnnr.duitku.domain.computeStats
import com.aamnnr.duitku.domain.dailyExpenses
import com.aamnnr.duitku.domain.isCashflow
import com.aamnnr.duitku.domain.monthlyExpenses
import com.aamnnr.duitku.ui.AppIcons
import com.aamnnr.duitku.ui.LocaleID
import com.aamnnr.duitku.ui.appViewModel
import com.aamnnr.duitku.ui.components.AnimatedRupiah
import com.aamnnr.duitku.ui.components.EmptyState
import com.aamnnr.duitku.ui.components.IconBadge
import com.aamnnr.duitku.ui.components.KopiCard
import com.aamnnr.duitku.ui.components.PeriodPill
import com.aamnnr.duitku.ui.components.SoonBadge
import com.aamnnr.duitku.ui.components.StatusBarIcons
import com.aamnnr.duitku.ui.components.TrendChart
import com.aamnnr.duitku.ui.components.toColor
import com.aamnnr.duitku.ui.dayMonth
import com.aamnnr.duitku.ui.endMillis
import com.aamnnr.duitku.ui.formatCompact
import com.aamnnr.duitku.ui.formatRupiah
import com.aamnnr.duitku.ui.label
import com.aamnnr.duitku.ui.startMillis
import com.aamnnr.duitku.ui.theme.ExpenseRed
import com.aamnnr.duitku.ui.theme.IncomeGreen
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.aamnnr.duitku.data.export.ExportService
import com.aamnnr.duitku.domain.projectSpending
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.TextStyle

enum class PeriodType(val label: String) { MONTH("Bulan"), QUARTER("Kuartal"), YEAR("Tahun") }

/** Satu periode laporan, ditandai dengan tanggal awalnya. */
data class Period(val type: PeriodType, val start: LocalDate) {
    val end: LocalDate
        get() = when (type) {
            PeriodType.MONTH -> start.plusMonths(1).minusDays(1)
            PeriodType.QUARTER -> start.plusMonths(3).minusDays(1)
            PeriodType.YEAR -> start.plusYears(1).minusDays(1)
        }

    fun shift(delta: Long) = copy(
        start = when (type) {
            PeriodType.MONTH -> start.plusMonths(delta)
            PeriodType.QUARTER -> start.plusMonths(3 * delta)
            PeriodType.YEAR -> start.plusYears(delta)
        },
    )

    val label: String
        get() = when (type) {
            PeriodType.MONTH -> YearMonth.from(start).label()
            PeriodType.QUARTER -> "Kuartal ${(start.monthValue - 1) / 3 + 1} ${start.year}"
            PeriodType.YEAR -> start.year.toString()
        }

    val isCurrent: Boolean get() = LocalDate.now() in start..end

    companion object {
        fun current(type: PeriodType, today: LocalDate = LocalDate.now()): Period = when (type) {
            PeriodType.MONTH -> Period(type, today.withDayOfMonth(1))
            PeriodType.QUARTER -> Period(type, LocalDate.of(today.year, ((today.monthValue - 1) / 3) * 3 + 1, 1))
            PeriodType.YEAR -> Period(type, LocalDate.of(today.year, 1, 1))
        }
    }
}

data class InsightNote(val icon: String, val text: String)

data class InsightState(
    val period: Period = Period.current(PeriodType.MONTH),
    val stats: PeriodStats = PeriodStats(),
    val previousExpense: Long = 0,
    val series: List<Long> = emptyList(),
    val seriesLabels: List<String> = emptyList(),
    val categories: List<CategoryTotal> = emptyList(),
    val notes: List<InsightNote> = emptyList(),
    val items: List<TransactionItem> = emptyList(),
) {
    /** Perubahan pengeluaran dibanding periode sebelumnya, dalam persen; null bila tidak bisa dibandingkan. */
    val changePercent: Int?
        get() = if (previousExpense <= 0) null else ((stats.expense - previousExpense) * 100 / previousExpense).toInt()
}

@OptIn(ExperimentalCoroutinesApi::class)
class InsightViewModel(c: AppContainer) : ViewModel() {
    private val period = MutableStateFlow(Period.current(PeriodType.MONTH))

    val state = combine(c.profiles.activeProfileId, period, ::Pair).flatMapLatest { (pid, p) ->
        val prev = p.shift(-1)
        combine(
            c.transactions.observeItems(pid, p.start.startMillis(), p.end.endMillis()),
            c.transactions.observeSummary(pid, prev.start.startMillis(), prev.end.endMillis()),
            c.transactions.observeCategoryTotals(pid, TransactionType.EXPENSE, p.start.startMillis(), p.end.endMillis()),
        ) { items, prevSummary, categories ->
            val stats = computeStats(items, p.start, p.end)
            val (series, labels) = if (p.type == PeriodType.MONTH) {
                dailyExpenses(items, p.start, p.end) to (0..p.end.dayOfMonth - 1).map { p.start.plusDays(it.toLong()).dayMonth() }
            } else {
                val values = monthlyExpenses(items, p.start, p.end)
                values to values.indices.map {
                    p.start.plusMonths(it.toLong()).month.getDisplayName(TextStyle.SHORT, LocaleID)
                }
            }
            InsightState(p, stats, prevSummary.expense, series, labels, categories, buildNotes(items, stats, categories), items)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), InsightState())

    fun setType(type: PeriodType) = period.update { Period.current(type) }
    fun shift(delta: Long) = period.update { it.shift(delta) }
}

/** Catatan otomatis sederhana dari pola transaksi. */
internal fun buildNotes(items: List<TransactionItem>, stats: PeriodStats, categories: List<CategoryTotal>): List<InsightNote> {
    val notes = mutableListOf<InsightNote>()
    if (stats.income > 0) {
        val rate = ((stats.income - stats.expense) * 100 / stats.income).toInt()
        notes += when {
            rate >= 20 -> InsightNote("savings", "Kamu menyisihkan $rate% dari pemasukan. Pertahankan, idealnya minimal 20%.")
            rate >= 0 -> InsightNote("savings", "Sisa pemasukanmu $rate%. Coba naikkan ke 20% agar ada ruang menabung.")
            else -> InsightNote("bolt", "Pengeluaran melebihi pemasukan sebesar ${formatRupiah(stats.expense - stats.income)}.")
        }
    }
    categories.firstOrNull()?.let { top ->
        if (stats.expense > 0) {
            val share = (top.total * 100 / stats.expense).toInt()
            if (share >= 30) notes += InsightNote(top.icon ?: "more", "${top.name} menyerap $share% pengeluaranmu. Cek apakah ada yang bisa dihemat.")
        }
    }
    val expenses = items.filter { it.isCashflow && it.type == TransactionType.EXPENSE }
    val small = expenses.filter { it.amount < 20_000 }
    if (small.size >= 5) {
        notes += InsightNote("receipt", "${small.size} transaksi kecil di bawah Rp 20rb, totalnya ${formatRupiah(small.sumOf { it.amount })}. Yang kecil-kecil ternyata lumayan.")
    }
    val zone = ZoneId.systemDefault()
    val night = expenses.filter { Instant.ofEpochMilli(it.date).atZone(zone).hour >= 21 }
    if (night.size >= 3) {
        notes += InsightNote("movie", "${night.size} pengeluaran terjadi di atas jam 9 malam (${formatRupiah(night.sumOf { it.amount })}).")
    }
    return notes
}

@Composable
fun InsightScreen(onOpenCategory: (String?, YearMonth) -> Unit, onOpenHistory: (YearMonth) -> Unit) {
    val vm = appViewModel { InsightViewModel(it) }
    val s by vm.state.collectAsStateWithLifecycle()
    StatusBarIcons(lightIcons = false)
    val month = YearMonth.from(s.period.start)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var exportDialog by remember { mutableStateOf(false) }

    LazyColumn(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding(),
    ) {
        item {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.Bottom) {
                Text("Insight", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f))
                Text("Pola belanja dan target", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item { PeriodTabs(s.period.type, vm::setType) }
        item {
            Box(Modifier.fillMaxWidth().padding(top = 8.dp), contentAlignment = Alignment.Center) {
                PeriodPill(
                    label = s.period.label,
                    onPrev = { vm.shift(-1) },
                    onNext = { vm.shift(1) },
                    canNext = !s.period.isCurrent,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                )
            }
        }
        item {
            KopiCard(Modifier.fillMaxWidth().padding(16.dp)) {
                AnimatedRupiah(s.stats.expense, MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Pengeluaran ${s.period.label}", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                    s.changePercent?.let { ChangeBadge(it) }
                }
                Spacer(Modifier.height(8.dp))
                TrendChart(s.series, s.seriesLabels, MaterialTheme.colorScheme.primary, height = 120)
            }
        }
        item {
            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MiniStat("Masuk", formatCompact(s.stats.income), IncomeGreen, Modifier.weight(1f))
                MiniStat("Keluar", formatCompact(s.stats.expense), ExpenseRed, Modifier.weight(1f))
                val net = s.stats.income - s.stats.expense
                MiniStat("Selisih", formatCompact(net), if (net >= 0) IncomeGreen else ExpenseRed, Modifier.weight(1f))
            }
        }
        if (s.period.type == PeriodType.MONTH && s.period.isCurrent && s.stats.expense > 0) {
            item {
                val proj = projectSpending(s.stats.expense)
                KopiCard(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp)) {
                    Text("Proyeksi", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Dengan laju sekarang, pengeluaran bulan ini akan mencapai ${formatRupiah(proj.monthEnd)} " +
                            "dan sekitar ${formatCompact(proj.year)} dalam setahun.",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        item {
            KopiCard(Modifier.fillMaxWidth().padding(16.dp)) {
                Text("Rincian kategori", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                if (s.categories.isEmpty()) {
                    EmptyState("Belum ada pengeluaran", "Rincian muncul setelah kamu mencatat pengeluaran di periode ini.")
                }
                val max = s.categories.maxOfOrNull { it.total } ?: 1L
                s.categories.forEach { c -> BreakdownRow(c, max) { onOpenCategory(c.categoryId, month) } }
            }
        }
        if (s.notes.isNotEmpty()) {
            item {
                Text("Catatan untukmu", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 16.dp))
            }
            items(s.notes) { n ->
                KopiCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconBadge(AppIcons.get(n.icon).takeIf { n.icon != "tip" } ?: Icons.Outlined.Lightbulb,
                            MaterialTheme.colorScheme.secondary, 32.dp)
                        Spacer(Modifier.width(12.dp))
                        Text(n.text, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
        item {
            Column(Modifier.padding(16.dp)) {
                ActionRow(Icons.AutoMirrored.Outlined.ReceiptLong, "Riwayat lengkap", "Cari dan filter semua transaksi") {
                    onOpenHistory(month)
                }
                Spacer(Modifier.height(8.dp))
                ActionRow(Icons.Outlined.FileDownload, "Ekspor PDF / Excel", "Laporan ${s.period.label} untuk dicetak atau dibagikan") {
                    exportDialog = true
                }
            }
        }
    }
    if (exportDialog) {
        AlertDialog(
            onDismissRequest = { exportDialog = false },
            title = { Text("Ekspor ${s.period.label}") },
            text = { Text("${s.items.size} transaksi akan diekspor. CSV bisa dibuka di Excel atau Google Sheets; PDF siap dicetak.") },
            confirmButton = {
                TextButton(onClick = {
                    exportDialog = false
                    val label = s.period.label.lowercase().replace(" ", "-")
                    scope.launch {
                        val service = ExportService(context)
                        val file = withContext(Dispatchers.IO) { service.pdf(s.items, "Periode ${s.period.label}", label) }
                        service.share(file, "application/pdf")
                    }
                }) { Text("PDF") }
            },
            dismissButton = {
                TextButton(onClick = {
                    exportDialog = false
                    val label = s.period.label.lowercase().replace(" ", "-")
                    scope.launch {
                        val service = ExportService(context)
                        val file = withContext(Dispatchers.IO) { service.csv(s.items, label) }
                        service.share(file, "text/csv")
                    }
                }) { Text("CSV / Excel") }
            },
        )
    }
}

@Composable
private fun PeriodTabs(selected: PeriodType, onSelect: (PeriodType) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(3.dp),
    ) {
        PeriodType.entries.forEach { t ->
            val isSel = t == selected
            Text(
                t.label,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (isSel) MaterialTheme.colorScheme.surfaceContainerLowest else MaterialTheme.colorScheme.surfaceContainerHigh)
                    .clickable { onSelect(t) }
                    .padding(vertical = 8.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (isSel) FontWeight.SemiBold else FontWeight.Normal,
                color = if (isSel) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ChangeBadge(percent: Int) {
    // Pengeluaran naik = kurang baik (merah), turun = baik (hijau).
    val color = if (percent > 0) ExpenseRed else IncomeGreen
    Text(
        "${if (percent > 0) "+" else ""}$percent% vs sebelumnya",
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        color = color,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

@Composable
private fun MiniStat(label: String, value: String, color: androidx.compose.ui.graphics.Color, modifier: Modifier) {
    KopiCard(modifier) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = color,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun BreakdownRow(c: CategoryTotal, max: Long, onClick: () -> Unit) {
    val color = c.color.toColor(MaterialTheme.colorScheme.primary)
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBadge(AppIcons.get(c.icon), color, 28.dp)
        Spacer(Modifier.width(10.dp))
        Text(c.name, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(88.dp))
        LinearProgressIndicator(
            progress = { c.total.toFloat() / max },
            color = color,
            trackColor = color.copy(alpha = 0.12f),
            modifier = Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(3.dp)),
            drawStopIndicator = {},
        )
        Spacer(Modifier.width(10.dp))
        Text(formatCompact(c.total), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun ActionRow(icon: ImageVector, title: String, subtitle: String, soon: Boolean = false, onClick: () -> Unit) {
    KopiCard(Modifier.fillMaxWidth().clickable(enabled = !soon, onClick = onClick)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(icon, MaterialTheme.colorScheme.primary, 36.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (soon) SoonBadge() else Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null)
        }
    }
}
