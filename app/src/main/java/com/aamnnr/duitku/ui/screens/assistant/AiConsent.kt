package com.aamnnr.duitku.ui.screens.assistant

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

/** Persetujuan sekali sebelum fitur AI (asisten, scan struk) pertama kali dipakai. */
@Composable
fun AiConsentDialog(onAgree: () -> Unit, onCancel: () -> Unit) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Sebelum memakai AI") },
        text = {
            Text(
                "Pesan atau foto struk, nama kategori dan dompet, serta ringkasan total bulan ini dan bulan lalu " +
                    "dikirim ke Google Gemini melalui server Duitku untuk diproses. Saldo dompet dan isi catatan " +
                    "transaksi tidak dikirim.\n\n" +
                    "Karena memakai layanan gratis, Google dapat memakai data ini untuk meningkatkan layanannya. " +
                    "Hindari mengirim data sensitif seperti nomor rekening atau PIN.",
            )
        },
        confirmButton = { TextButton(onClick = onAgree) { Text("Setuju") } },
        dismissButton = { TextButton(onClick = onCancel) { Text("Batal") } },
    )
}
