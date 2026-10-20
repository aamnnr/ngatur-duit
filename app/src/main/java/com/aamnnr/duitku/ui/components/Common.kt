package com.aamnnr.duitku.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.aamnnr.duitku.domain.TransactionItem
import com.aamnnr.duitku.domain.TransactionType
import com.aamnnr.duitku.domain.TransferType
import com.aamnnr.duitku.ui.AppIcons
import com.aamnnr.duitku.ui.PaletteColors
import com.aamnnr.duitku.ui.formatRupiah
import com.aamnnr.duitku.ui.formatThousands
import com.aamnnr.duitku.ui.parseAmount
import com.aamnnr.duitku.ui.theme.ExpenseRed
import com.aamnnr.duitku.ui.theme.IncomeGreen
import com.aamnnr.duitku.ui.theme.TransferBlue

fun Long?.toColor(fallback: Color): Color = this?.let { Color(it) } ?: fallback

/** Ikon dalam kotak lembut berwarna (warna @ 12%). */
@Composable
fun IconBadge(icon: ImageVector, color: Color, size: Dp = 40.dp) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.3f))
            .background(color.copy(alpha = 0.12f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(size * 0.55f))
    }
}

private val timeFormat = java.time.format.DateTimeFormatter.ofPattern("HH:mm")

/**
 * Satu transaksi sebagai kartu: ikon kategori, judul (catatan atau kategori), "Kategori • 13:00",
 * nominal berwarna dan label status kecil. [inset] = beri jarak kartu dari tepi layar.
 */
@Composable
fun TransactionRow(item: TransactionItem, onClick: () -> Unit, modifier: Modifier = Modifier, inset: Boolean = true) {
    val (icon, color, kind) = when {
        item.isTransfer -> Triple(Icons.AutoMirrored.Filled.CompareArrows, TransferBlue,
            if (item.transferType == TransferType.OUT) "Transfer keluar" else "Transfer masuk")
        item.isAdjustment -> Triple(Icons.Filled.Tune, MaterialTheme.colorScheme.onSurfaceVariant, "Penyesuaian saldo")
        else -> Triple(AppIcons.get(item.categoryIcon), item.categoryColor.toColor(MaterialTheme.colorScheme.primary),
            item.categoryName ?: "Tanpa kategori")
    }
    val amountColor = when {
        item.isTransfer -> TransferBlue
        item.type == TransactionType.INCOME -> IncomeGreen
        else -> ExpenseRed
    }
    val status = when {
        item.isAdjustment -> "Penyesuaian"
        item.isTransfer -> "Dipindah"
        item.type == TransactionType.INCOME -> "Diterima"
        else -> "Selesai"
    }
    val title = item.note?.takeIf { it.isNotBlank() } ?: kind
    val time = java.time.Instant.ofEpochMilli(item.date).atZone(java.time.ZoneId.systemDefault()).format(timeFormat)
    val subtitle = listOfNotNull(if (title == kind) item.walletName else kind, time).joinToString(" • ")
    Row(
        modifier = modifier
            .then(if (inset) Modifier.padding(horizontal = 16.dp, vertical = 5.dp) else Modifier)
            .fillMaxWidth()
            .neuSurface(RoundedCornerShape(18.dp), elevation = 4.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBadge(icon, color, 44.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            val sign = if (item.isTransfer) "" else if (item.type == TransactionType.INCOME) "+" else "-"
            Text(money(item.amount, sign), color = amountColor,
                style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
            Text(status, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Status kosong dengan ilustrasi garis dan petunjuk langkah berikutnya. */
@Composable
fun EmptyState(title: String, message: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        LineArtReceipt()
        Spacer(Modifier.size(12.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Spacer(Modifier.size(4.dp))
        Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}

/** Input nominal rupiah dengan pemisah ribuan otomatis. */
@Composable
fun AmountField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    allowNegative: Boolean = false,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { new ->
            val negative = allowNegative && new.text.trimStart().startsWith("-")
            val digits = parseAmount(new.text)
            val text = when {
                digits == 0L && new.text.none(Char::isDigit) -> if (negative) "-" else ""
                else -> (if (negative) "-" else "") + formatThousands(digits)
            }
            onValueChange(TextFieldValue(text, TextRange(text.length)))
        },
        label = { Text(label) },
        prefix = { Text("Rp ") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = if (allowNegative) KeyboardType.Text else KeyboardType.Number),
        modifier = modifier.fillMaxWidth(),
    )
}

fun amountFieldValue(amount: Long): TextFieldValue {
    val text = if (amount == 0L) "" else (if (amount < 0) "-" else "") + formatThousands(kotlin.math.abs(amount))
    return TextFieldValue(text, TextRange(text.length))
}

fun TextFieldValue.signedAmount(): Long =
    parseAmount(text).let { if (text.trimStart().startsWith("-")) -it else it }

@Composable
fun ColorPicker(selected: Long?, onSelect: (Long) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        PaletteColors.forEach { c ->
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(Color(c))
                    .clickable { onSelect(c) },
                contentAlignment = Alignment.Center,
            ) {
                if (c == selected) Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
fun IconPicker(keys: List<String>, selected: String, color: Color, onSelect: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        keys.forEach { key ->
            val isSelected = key == selected
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(if (isSelected) color.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceContainer)
                    .border(if (isSelected) 2.dp else 0.dp, if (isSelected) color else Color.Transparent, CircleShape)
                    .clickable { onSelect(key) },
                contentAlignment = Alignment.Center,
            ) {
                Icon(AppIcons.get(key), null,
                    tint = if (isSelected) color else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp))
            }
        }
    }
}

@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    destructive: Boolean = true,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = { onConfirm(); onDismiss() }) {
                Text(confirmLabel, color = if (destructive) MaterialTheme.colorScheme.error else Color.Unspecified)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } },
    )
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Row(
        modifier = modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 16.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f))
        action?.invoke()
    }
}
