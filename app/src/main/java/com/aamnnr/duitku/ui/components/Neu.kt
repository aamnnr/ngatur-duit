package com.aamnnr.duitku.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Contactless
import androidx.compose.material.icons.automirrored.outlined.TrendingFlat
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aamnnr.duitku.domain.WalletWithBalance
import com.aamnnr.duitku.ui.formatCompact
import com.aamnnr.duitku.ui.formatRupiah
import com.aamnnr.duitku.ui.theme.CardShadow
import com.aamnnr.duitku.ui.theme.ExpenseRed
import com.aamnnr.duitku.ui.theme.IncomeGreen

/** True bila pengguna memilih menyembunyikan nominal saldo (tombol mata di Beranda). */
val LocalHideBalance = compositionLocalOf { false }

/** Nominal yang menghormati pilihan sembunyikan saldo. */
@Composable
fun money(amount: Long, prefix: String = ""): String =
    if (LocalHideBalance.current) "${prefix}Rp ••••••" else prefix + formatRupiah(amount)

/** Permukaan kartu soft-neumorphic: putih, bayangan lembut, garis tepi tipis. */
@Composable
fun Modifier.neuSurface(shape: Shape = MaterialTheme.shapes.medium, elevation: Dp = 8.dp): Modifier =
    this
        .shadow(elevation, shape, clip = false, ambientColor = CardShadow.copy(alpha = 0.10f), spotColor = CardShadow.copy(alpha = 0.14f))
        .clip(shape)
        .background(MaterialTheme.colorScheme.surfaceContainerLowest)
        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)

/** Tombol bulat lembut (lonceng, mata, aksi dompet). [badge] = titik merah. */
@Composable
fun NeuCircleButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    badge: Boolean = false,
    tint: Color = MaterialTheme.colorScheme.onSurface,
) {
    Box(modifier.size(size)) {
        Box(
            Modifier
                .size(size)
                .neuSurface(CircleShape, elevation = 6.dp)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, contentDescription, tint = tint, modifier = Modifier.size(size * 0.48f)) }
        if (badge) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = size * 0.18f, end = size * 0.18f)
                    .size(9.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                    .padding(1.5.dp)
                    .clip(CircleShape)
                    .background(ExpenseRed),
            )
        }
    }
}

enum class SyncStatus(val label: String) { SYNCED("Sinkron"), SYNCING("Menyinkron"), OFFLINE("Offline"), LOCAL("Lokal") }

@Composable
fun SyncChip(status: SyncStatus) {
    val color = when (status) {
        SyncStatus.SYNCED -> IncomeGreen
        SyncStatus.SYNCING -> MaterialTheme.colorScheme.secondary
        SyncStatus.OFFLINE -> ExpenseRed
        SyncStatus.LOCAL -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(color))
        Text(" ${status.label}", style = MaterialTheme.typography.labelSmall, color = color, fontWeight = FontWeight.SemiBold)
    }
}

/** Label kapital kecil seperti "TOTAL SALDO AKTIF". */
@Composable
fun OverlineText(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.2.sp),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}

/** Kartu dompet gelap ala kartu fisik/e-wallet. */
@Composable
fun WalletHeroCard(
    wallet: WalletWithBalance,
    ownerName: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val ink = Color(0xFFF8F6F3)
    val muted = ink.copy(alpha = 0.62f)
    val shape = RoundedCornerShape(26.dp)
    Column(
        modifier
            .fillMaxWidth()
            .height(196.dp)
            .shadow(14.dp, shape, ambientColor = Color.Black.copy(alpha = 0.25f), spotColor = Color.Black.copy(alpha = 0.3f))
            .clip(shape)
            .background(Brush.linearGradient(listOf(Color(0xFF4A3F39), Color(0xFF2A2421), Color(0xFF1B1715))))
            .border(1.dp, Color.White.copy(alpha = 0.08f), shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(20.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(wallet.name, color = ink, style = MaterialTheme.typography.titleMedium, maxLines = 1,
                overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
            Spacer(Modifier.width(8.dp))
            Text(
                "OFFLINE READY",
                color = ink.copy(alpha = 0.85f),
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.8.sp),
                modifier = Modifier.clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.12f))
                    .padding(horizontal = 8.dp, vertical = 3.dp),
            )
            Spacer(Modifier.weight(1f))
            Icon(Icons.Outlined.Contactless, null, tint = muted, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Box(Modifier.size(width = 26.dp, height = 18.dp).clip(RoundedCornerShape(4.dp)).background(Color(0xFFD4A94F)))
        }
        Row(verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                Text("Saldo Tersedia", color = muted, style = MaterialTheme.typography.bodyMedium)
                AnimatedRupiah(wallet.balance, MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold), ink)
            }
            wallet.accountSuffix?.let {
                Text("••••  $it", color = muted, style = MaterialTheme.typography.titleMedium.copy(letterSpacing = 2.sp))
            }
        }
        Row(verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                Text("NAMA PEMILIK", color = muted, style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.sp))
                Text(ownerName.ifBlank { "Pemilik" }, color = ink, style = MaterialTheme.typography.titleSmall,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(
                listOfNotNull(wallet.institution?.takeIf { it.isNotBlank() }, if (wallet.isDefault) "Utama" else null)
                    .joinToString(" • ").ifBlank { "Dompet" },
                color = muted,
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.width(8.dp))
            Box {
                Box(Modifier.size(22.dp).clip(CircleShape).background(Color(0xFFB4533A)))
                Box(Modifier.padding(start = 12.dp).size(22.dp).clip(CircleShape).background(Color(0xFFD4A94F).copy(alpha = 0.9f)))
            }
        }
    }
}

/** Kartu progres pengeluaran terhadap batas bulanan. */
@Composable
fun MonthlyProgressCard(
    title: String,
    spent: Long,
    limit: Long,
    onSetLimit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ratio = if (limit > 0) spent.toFloat() / limit else 0f
    val progress by animateFloatAsState(ratio.coerceIn(0f, 1f), tween(800), label = "progress")
    val (status, color) = when {
        limit <= 0 -> "Atur batas bulanan agar progres terlihat" to MaterialTheme.colorScheme.secondary
        ratio >= 1f -> "Melewati batas" to ExpenseRed
        ratio >= 0.75f -> "Mulai waspada" to MaterialTheme.colorScheme.secondary
        else -> "Terkontrol rapi" to IncomeGreen
    }
    Column(modifier.fillMaxWidth().neuSurface().clickable(onClick = onSetLimit).padding(18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Text(money(spent), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            if (limit > 0) {
                Text(" / ${formatCompact(limit).removePrefix("Rp ")}", style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(12.dp))
        Box(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.surfaceContainerHighest)) {
            if (limit > 0) {
                Box(Modifier.fillMaxWidth(progress).height(10.dp).clip(RoundedCornerShape(50)).background(color))
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.AutoMirrored.Outlined.TrendingFlat, null, tint = color, modifier = Modifier.size(14.dp))
            Text(" $status", style = MaterialTheme.typography.labelSmall, color = color, modifier = Modifier.weight(1f))
            if (limit > 0) {
                Text("${(ratio * 100).toInt()}% dari batas anggaran", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** Kartu aksi cepat: ikon dalam kotak lembut + judul + subjudul. */
@Composable
fun QuickActionCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    tint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    badge: String? = null,
) {
    Column(modifier.neuSurface().clickable(onClick = onClick).padding(16.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            Box(
                Modifier.size(42.dp).clip(RoundedCornerShape(12.dp)).background(tint.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) { Icon(icon, null, tint = tint, modifier = Modifier.size(22.dp)) }
            Spacer(Modifier.weight(1f))
            badge?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.secondaryContainer)
                        .padding(horizontal = 7.dp, vertical = 2.dp),
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        Text(title, style = MaterialTheme.typography.titleSmall)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

/** Ilustrasi garis sederhana (struk + koin) untuk status kosong. */
@Composable
fun LineArtReceipt(modifier: Modifier = Modifier, size: Dp = 88.dp) {
    val line = MaterialTheme.colorScheme.outline
    val accent = MaterialTheme.colorScheme.secondary
    Canvas(modifier.size(size)) {
        val w = this.size.width
        val stroke = Stroke(width = w * 0.025f, cap = StrokeCap.Round)
        // Struk dengan tepi bawah bergerigi.
        val left = w * 0.18f
        val right = w * 0.62f
        val topY = w * 0.12f
        val bottomY = w * 0.82f
        drawRoundRect(line, Offset(left, topY), Size(right - left, bottomY - topY), CornerRadius(w * 0.04f), style = stroke)
        listOf(0.28f, 0.38f, 0.48f).forEach { y ->
            drawLine(line, Offset(left + w * 0.08f, w * y), Offset(right - w * 0.08f, w * y), stroke.width, StrokeCap.Round)
        }
        drawLine(line, Offset(left + w * 0.08f, w * 0.62f), Offset(left + w * 0.22f, w * 0.62f), stroke.width, StrokeCap.Round)
        // Koin.
        drawCircle(accent, radius = w * 0.17f, center = Offset(w * 0.7f, w * 0.68f), style = stroke)
        drawLine(accent, Offset(w * 0.7f, w * 0.6f), Offset(w * 0.7f, w * 0.76f), stroke.width, StrokeCap.Round)
    }
}
