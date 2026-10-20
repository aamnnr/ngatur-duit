package com.aamnnr.duitku.ui.screens.add

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallMade
import androidx.compose.material.icons.automirrored.filled.CallReceived
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.outlined.DocumentScanner
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aamnnr.duitku.ui.components.IconBadge
import com.aamnnr.duitku.ui.components.KopiCard
import com.aamnnr.duitku.ui.components.SoonBadge
import com.aamnnr.duitku.ui.screens.transaction.FormMode
import com.aamnnr.duitku.ui.theme.ExpenseRed
import com.aamnnr.duitku.ui.theme.IncomeGreen
import com.aamnnr.duitku.ui.theme.TransferBlue

/** Lembar yang muncul saat tombol + diketuk: semua cara mencatat di satu tempat. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddSheet(onDismiss: () -> Unit, onPick: (FormMode) -> Unit, onScan: () -> Unit, onAssistant: () -> Unit, onVoice: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.background,
    ) {
        Column(Modifier.padding(horizontal = 16.dp).navigationBarsPadding().padding(bottom = 12.dp)) {
            Text("Catat transaksi", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ModeTile("Pengeluaran", Icons.AutoMirrored.Filled.CallMade, ExpenseRed, Modifier.weight(1f)) {
                    onPick(FormMode.EXPENSE)
                }
                ModeTile("Pemasukan", Icons.AutoMirrored.Filled.CallReceived, IncomeGreen, Modifier.weight(1f)) {
                    onPick(FormMode.INCOME)
                }
                ModeTile("Transfer", Icons.AutoMirrored.Filled.CompareArrows, TransferBlue, Modifier.weight(1f)) {
                    onPick(FormMode.TRANSFER)
                }
            }
            Text(
                "Cara cepat",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
            )
            QuickRow(Icons.Outlined.DocumentScanner, "Scan struk", "Foto nota, nominal terisi otomatis", onClick = onScan)
            QuickRow(Icons.Outlined.SmartToy, "Tanya asisten", "Catat banyak sekaligus atau minta analisa", onClick = onAssistant)
            QuickRow(Icons.Outlined.Mic, "Catat dengan suara", "\"Beli kopi 25 ribu pakai GoPay\"", onClick = onVoice)
            Row(
                Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.TouchApp, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    " Tahan tombol + untuk langsung catat pengeluaran",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ModeTile(label: String, icon: ImageVector, color: Color, modifier: Modifier, onClick: () -> Unit) {
    KopiCard(modifier.clickable(onClick = onClick)) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            IconBadge(icon, color, 40.dp)
            Spacer(Modifier.height(6.dp))
            Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun QuickRow(icon: ImageVector, title: String, subtitle: String, onClick: (() -> Unit)? = null) {
    KopiCard(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp)
            .clickable(enabled = onClick != null) { onClick?.invoke() },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(icon, MaterialTheme.colorScheme.secondary, 36.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (onClick == null) SoonBadge()
        }
    }
}
