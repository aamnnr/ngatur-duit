package com.aamnnr.duitku.ui.screens.plan

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.Cake
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.SmokingRooms
import androidx.compose.material.icons.outlined.Subscriptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.aamnnr.duitku.ui.components.IconBadge
import com.aamnnr.duitku.ui.components.NeuCircleButton
import com.aamnnr.duitku.ui.components.StatusBarIcons
import com.aamnnr.duitku.ui.components.neuSurface
import com.aamnnr.duitku.ui.formatRupiah
import com.aamnnr.duitku.ui.formatThousands
import com.aamnnr.duitku.ui.parseAmount
import com.aamnnr.duitku.ui.theme.ExpenseRed
import com.aamnnr.duitku.ui.theme.IncomeGreen
import com.aamnnr.duitku.ui.theme.TransferBlue
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Lima kalkulator ringan untuk menyadarkan nilai uang dan waktu. Semua dihitung di HP. */
@Composable
fun RealityScreen(onBack: () -> Unit) {
    StatusBarIcons(lightIcons = false)
    var open by rememberSaveable { mutableStateOf("salary") }
    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding()
            .verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            NeuCircleButton(Icons.AutoMirrored.Filled.ArrowBack, "Kembali", onBack)
            Spacer(Modifier.width(14.dp))
            Column {
                Text("Kalkulator realita", style = MaterialTheme.typography.headlineSmall)
                Text("Hitung, renungkan, bagikan ke teman", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Tool("salary", open, { open = it }, Icons.Outlined.Payments, "Realita gaji", "Berapa nilai satu detikmu?", IncomeGreen) { SalaryTool() }
        Tool("age", open, { open = it }, Icons.Outlined.Cake, "Realita umur", "Berapa persen hidup sudah terpakai?", TransferBlue) { AgeTool() }
        Tool("sleep", open, { open = it }, Icons.Outlined.Bedtime, "Hutang tidur", "Kurang tidur juga ada bunganya", MaterialTheme.colorScheme.secondary) { SleepTool() }
        Tool("smoke", open, { open = it }, Icons.Outlined.SmokingRooms, "Biaya rokok", "Yang terbakar bukan cuma tembakau", ExpenseRed) { SmokeTool() }
        Tool("subs", open, { open = it }, Icons.Outlined.Subscriptions, "Jebakan langganan", "Kecil per bulan, besar per tahun", MaterialTheme.colorScheme.primary) { SubsTool() }
    }
}

@Composable
private fun Tool(
    id: String,
    open: String,
    onOpen: (String) -> Unit,
    icon: ImageVector,
    title: String,
    subtitle: String,
    color: Color,
    content: @Composable () -> Unit,
) {
    Column(Modifier.fillMaxWidth().neuSurface()) {
        Row(Modifier.fillMaxWidth().clickable { onOpen(if (open == id) "" else id) }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBadge(icon, color, 44.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        AnimatedVisibility(open == id) {
            Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { content() }
        }
    }
}

@Composable
private fun NumberField(label: String, value: String, onChange: (String) -> Unit, money: Boolean = false, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = value,
        onValueChange = { raw ->
            val digits = raw.filter(Char::isDigit).take(13)
            onChange(if (money && digits.isNotEmpty()) formatThousands(digits.toLong()) else digits)
        },
        label = { Text(label) },
        prefix = if (money) ({ Text("Rp ") }) else null,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier.fillMaxWidth(),
    )
}

/** Kotak hasil + tombol bagikan. */
@Composable
private fun Result(lines: List<String>, shareText: String) {
    val context = LocalContext.current
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceContainer).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        lines.forEachIndexed { i, l ->
            Text(l, style = if (i == 0) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium,
                fontWeight = if (i == 0) FontWeight.Bold else FontWeight.Normal)
        }
        TextButton(onClick = {
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, "$shareText\n\nDihitung pakai Duitku.")
            }
            context.startActivity(Intent.createChooser(send, "Bagikan hasil"))
        }) {
            Icon(Icons.Outlined.Share, null)
            Text("  Bagikan")
        }
    }
}

private fun num(s: String) = parseAmount(s)

@Composable
private fun SalaryTool() {
    var salary by rememberSaveable { mutableStateOf("") }
    var hours by rememberSaveable { mutableStateOf("8") }
    var days by rememberSaveable { mutableStateOf("22") }
    var item by rememberSaveable { mutableStateOf("25.000") }
    NumberField("Gaji per bulan", salary, { salary = it }, money = true)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        NumberField("Jam kerja/hari", hours, { hours = it }, modifier = Modifier.weight(1f))
        NumberField("Hari kerja/bulan", days, { days = it }, modifier = Modifier.weight(1f))
    }
    NumberField("Harga barang yang diincar", item, { item = it }, money = true)
    val totalSeconds = num(hours) * num(days) * 3600
    if (num(salary) > 0 && totalSeconds > 0) {
        val perSecond = num(salary).toDouble() / totalSeconds
        val perHour = perSecond * 3600
        val itemMinutes = if (perSecond > 0) num(item) / perSecond / 60 else 0.0
        Result(
            listOf(
                "Rp ${"%,.2f".format(perSecond).replace(',', '#').replace('.', ',').replace('#', '.')} per detik",
                "${formatRupiah(perHour.toLong())} per jam · ${formatRupiah((perHour / 60).toLong())} per menit",
                "Barang ${formatRupiah(num(item))} = ${itemMinutes.toLong()} menit kerja kamu",
            ),
            "Gajiku setara ${formatRupiah(perHour.toLong())} per jam. Barang ${formatRupiah(num(item))} = ${itemMinutes.toLong()} menit kerja.",
        )
    }
}

@Composable
private fun AgeTool() {
    var birthYear by rememberSaveable { mutableStateOf("") }
    var expectancy by rememberSaveable { mutableStateOf("72") }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        NumberField("Tahun lahir", birthYear, { birthYear = it.take(4) }, modifier = Modifier.weight(1f))
        NumberField("Harapan hidup (tahun)", expectancy, { expectancy = it.take(3) }, modifier = Modifier.weight(1f))
    }
    val year = num(birthYear).toInt()
    val exp = num(expectancy).toInt()
    if (year in 1900..LocalDate.now().year && exp > 0) {
        val birth = LocalDate.of(year, 1, 1)
        val end = birth.plusYears(exp.toLong())
        val lived = ChronoUnit.DAYS.between(birth, LocalDate.now())
        val total = ChronoUnit.DAYS.between(birth, end).coerceAtLeast(1)
        val pct = (lived * 100.0 / total).coerceIn(0.0, 100.0)
        val weekendsLeft = (ChronoUnit.WEEKS.between(LocalDate.now(), end)).coerceAtLeast(0)
        Result(
            listOf(
                "${"%.1f".format(pct).replace('.', ',')}% hidup sudah terpakai",
                "Sekitar ${formatThousands((total - lived).coerceAtLeast(0))} hari lagi",
                "Kira-kira $weekendsLeft akhir pekan tersisa. Pakai yang baik.",
            ),
            "Hidupku sudah terpakai ${"%.1f".format(pct).replace('.', ',')}%. Tersisa sekitar $weekendsLeft akhir pekan.",
        )
    }
}

@Composable
private fun SleepTool() {
    var need by rememberSaveable { mutableStateOf("8") }
    var actual by rememberSaveable { mutableStateOf("6") }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        NumberField("Butuh (jam/malam)", need, { need = it.take(2) }, modifier = Modifier.weight(1f))
        NumberField("Rata-rata tidur (jam)", actual, { actual = it.take(2) }, modifier = Modifier.weight(1f))
    }
    val debt = num(need) - num(actual)
    if (num(need) > 0) {
        if (debt <= 0) {
            Result(listOf("Tidak ada hutang tidur", "Pertahankan, tubuhmu berterima kasih."), "Tidurku cukup, tanpa hutang.")
        } else {
            Result(
                listOf(
                    "${debt * 7} jam hutang tidur per minggu",
                    "${debt * 30} jam per bulan, setara ${(debt * 30) / 24} hari penuh begadang",
                ),
                "Hutang tidurku ${debt * 30} jam per bulan. Waktunya bayar dengan tidur.",
            )
        }
    }
}

@Composable
private fun SmokeTool() {
    var perDay by rememberSaveable { mutableStateOf("12") }
    var price by rememberSaveable { mutableStateOf("35.000") }
    var perPack by rememberSaveable { mutableStateOf("16") }
    NumberField("Batang per hari", perDay, { perDay = it.take(3) })
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        NumberField("Harga per bungkus", price, { price = it }, money = true, modifier = Modifier.weight(1f))
        NumberField("Isi per bungkus", perPack, { perPack = it.take(3) }, modifier = Modifier.weight(1f))
    }
    if (num(perPack) > 0 && num(perDay) > 0) {
        val perDayCost = num(price) * num(perDay) / num(perPack)
        Result(
            listOf(
                "${formatRupiah(perDayCost * 30)} per bulan",
                "${formatRupiah(perDayCost * 365)} per tahun",
                "${formatRupiah(perDayCost * 3650)} dalam 10 tahun",
            ),
            "Rokokku menghabiskan ${formatRupiah(perDayCost * 365)} per tahun.",
        )
    }
}

@Composable
private fun SubsTool() {
    val subs = remember { mutableStateOf(listOf("Streaming film" to "54.990", "Musik" to "34.990", "Cloud storage" to "26.900")) }
    subs.value.forEachIndexed { i, (name, price) ->
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(name, { v -> subs.value = subs.value.toMutableList().also { it[i] = v to price } },
                label = { Text("Langganan") }, singleLine = true, modifier = Modifier.weight(1.2f))
            NumberField("Per bulan", price, { v -> subs.value = subs.value.toMutableList().also { it[i] = name to v } },
                money = true, modifier = Modifier.weight(1f))
        }
    }
    TextButton(onClick = { subs.value = subs.value + ("" to "") }) { Text("+ Tambah langganan") }
    val monthly = subs.value.sumOf { num(it.second) }
    if (monthly > 0) {
        Result(
            listOf(
                "${formatRupiah(monthly * 12)} per tahun",
                "${formatRupiah(monthly)} per bulan dari ${subs.value.count { num(it.second) > 0 }} langganan",
                "Berapa yang benar-benar kamu pakai tiap minggu?",
            ),
            "Langgananku ${formatRupiah(monthly * 12)} per tahun. Saatnya bersih-bersih.",
        )
    }
}
