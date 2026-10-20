package com.aamnnr.duitku.ui.components

import android.app.Activity
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.aamnnr.duitku.domain.TransactionItem
import com.aamnnr.duitku.ui.formatRupiah
import com.aamnnr.duitku.ui.theme.ExpenseRed
import com.aamnnr.duitku.ui.theme.TransferBlue
import kotlin.math.roundToInt

/** Mengatur warna ikon status bar: [lightIcons] = ikon putih (untuk header gelap). */
@Composable
fun StatusBarIcons(lightIcons: Boolean) {
    val view = LocalView.current
    if (view.isInEditMode) return
    DisposableEffect(lightIcons) {
        val window = (view.context as? Activity)?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        val previous = controller?.isAppearanceLightStatusBars
        controller?.isAppearanceLightStatusBars = !lightIcons
        onDispose { if (previous != null) controller?.isAppearanceLightStatusBars = previous }
    }
}

/** Nominal rupiah yang beranimasi dari nilai lama ke nilai baru. Nilai akhir selalu tepat. */
@Composable
fun AnimatedRupiah(amount: Long, style: TextStyle, color: Color = Color.Unspecified, modifier: Modifier = Modifier) {
    // Mulai dari 0 agar angka "meluncur" saat layar pertama dimuat (count-up).
    var from by remember { mutableLongStateOf(0L) }
    val lastShown = remember { longArrayOf(0L) } // nilai terakhir yang tampil, bukan state
    val progress = remember { Animatable(if (amount == 0L) 1f else 0f) }
    LaunchedEffect(amount) {
        if (amount == lastShown[0]) return@LaunchedEffect
        from = lastShown[0]
        progress.snapTo(0f)
        progress.animateTo(1f, tween(durationMillis = 600))
    }
    val shown = if (progress.value >= 1f) amount else from + ((amount - from) * progress.value).toLong()
    lastShown[0] = shown
    Text(
        if (LocalHideBalance.current) "Rp ••••••" else formatRupiah(shown),
        style = style,
        color = color,
        modifier = modifier,
    )
}

@Composable
fun KopiCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = modifier
            .neuSurface()
            .padding(16.dp),
        content = content,
    )
}

/** Label kecil untuk fitur yang belum dibuat. */
@Composable
fun SoonBadge(modifier: Modifier = Modifier) {
    Text(
        "Segera",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

/** Pemilih periode berbentuk pil. Bisa diketuk panahnya atau digeser kiri/kanan. */
@Composable
fun PeriodPill(
    label: String,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    canNext: Boolean,
    contentColor: Color,
    containerColor: Color,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(containerColor)
            .pointerInput(canNext) {
                var total = 0f
                detectHorizontalDragGestures(
                    onDragStart = { total = 0f },
                    onDragEnd = {
                        if (total > 80f) onPrev() else if (total < -80f && canNext) onNext()
                    },
                ) { _, delta -> total += delta }
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onPrev, modifier = Modifier.size(36.dp)) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Sebelumnya", tint = contentColor)
        }
        Text(label, color = contentColor, style = MaterialTheme.typography.labelLarge)
        IconButton(onClick = onNext, enabled = canNext, modifier = Modifier.size(36.dp)) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight, "Berikutnya",
                tint = if (canNext) contentColor else contentColor.copy(alpha = 0.3f),
            )
        }
    }
}

/**
 * Grafik garis + area sederhana. Ketuk grafik untuk melihat nilai titik terdekat.
 * [labels] sejajar dengan [values]; dipakai untuk keterangan titik yang diketuk.
 */
@Composable
fun TrendChart(
    values: List<Long>,
    labels: List<String>,
    lineColor: Color,
    modifier: Modifier = Modifier,
    height: Int = 96,
) {
    var selected by remember(values) { mutableIntStateOf(-1) }
    val haptic = LocalHapticFeedback.current
    val fill = lineColor.copy(alpha = 0.14f)
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    Column(modifier) {
        Text(
            if (selected in values.indices) "${labels.getOrElse(selected) { "" }} · ${formatRupiah(values[selected])}"
            else "Ketuk grafik untuk melihat detail",
            style = MaterialTheme.typography.labelMedium,
            color = if (selected in values.indices) lineColor else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Box(
            Modifier
                .fillMaxWidth()
                .height(height.dp)
                .padding(top = 8.dp)
                .pointerInput(values) {
                    detectTapGestures { pos ->
                        if (values.size < 2) return@detectTapGestures
                        val step = size.width / (values.size - 1).toFloat()
                        selected = (pos.x / step).roundToInt().coerceIn(0, values.lastIndex)
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    }
                },
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val max = (values.maxOrNull() ?: 0L).coerceAtLeast(1L).toFloat()
                drawLine(gridColor, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx())
                if (values.size < 2) return@Canvas
                val step = size.width / (values.size - 1)
                val pts = values.mapIndexed { i, v -> Offset(i * step, size.height - v / max * size.height * 0.92f) }
                val line = Path().apply {
                    moveTo(pts[0].x, pts[0].y)
                    for (i in 1 until pts.size) {
                        val mid = (pts[i - 1].x + pts[i].x) / 2
                        cubicTo(mid, pts[i - 1].y, mid, pts[i].y, pts[i].x, pts[i].y)
                    }
                }
                val area = Path().apply {
                    addPath(line)
                    lineTo(pts.last().x, size.height)
                    lineTo(pts.first().x, size.height)
                    close()
                }
                drawPath(area, fill)
                drawPath(line, lineColor, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                if (selected in pts.indices) {
                    val p = pts[selected]
                    drawLine(lineColor.copy(alpha = 0.4f), Offset(p.x, 0f), Offset(p.x, size.height), 1.dp.toPx())
                    drawCircle(Color.White, 5.dp.toPx(), p)
                    drawCircle(lineColor, 3.5.dp.toPx(), p)
                }
            }
        }
    }
}

/** Baris transaksi yang bisa digeser: ke kiri = hapus, ke kanan = ubah. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwipeTransactionRow(
    item: TransactionItem,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    val state = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            when (value) {
                SwipeToDismissBoxValue.EndToStart -> {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onDelete()
                    true
                }
                SwipeToDismissBoxValue.StartToEnd -> {
                    onClick()
                    false
                }
                SwipeToDismissBoxValue.Settled -> false
            }
        },
    )
    val deleteColor = ExpenseRed
    val editColor = TransferBlue
    val shape = RoundedCornerShape(18.dp)
    SwipeToDismissBox(
        state = state,
        modifier = modifier.padding(horizontal = 16.dp, vertical = 5.dp).clip(shape),
        backgroundContent = {
            val toDelete = state.dismissDirection == SwipeToDismissBoxValue.EndToStart
            Row(
                Modifier
                    .fillMaxSize()
                    .background(if (toDelete) deleteColor else editColor)
                    .padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = if (toDelete) Arrangement.End else Arrangement.Start,
            ) {
                Icon(if (toDelete) Icons.Outlined.Delete else Icons.Outlined.Edit, null, tint = Color.White)
                Text(
                    if (toDelete) " Hapus" else " Ubah",
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        },
    ) {
        TransactionRow(item, onClick = onClick, inset = false)
    }
}
