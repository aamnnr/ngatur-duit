package com.aamnnr.duitku.ui.screens.scan

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.DocumentScanner
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.aamnnr.duitku.AppContainer
import com.aamnnr.duitku.data.remote.CloudException
import com.aamnnr.duitku.data.remote.ScanRequest
import com.aamnnr.duitku.data.remote.ScanResponse
import com.aamnnr.duitku.ui.appViewModel
import com.aamnnr.duitku.ui.components.EmptyState
import com.aamnnr.duitku.ui.components.IconBadge
import com.aamnnr.duitku.ui.components.KopiCard
import com.aamnnr.duitku.ui.components.StatusBarIcons
import com.aamnnr.duitku.ui.formatRupiah
import com.aamnnr.duitku.ui.screens.assistant.AiConsentDialog
import com.aamnnr.duitku.ui.screens.assistant.buildLedgerContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File

class ScanReceiptViewModel(private val c: AppContainer) : ViewModel() {
    var preview by mutableStateOf<Bitmap?>(null)
        private set
    var busy by mutableStateOf(false)
        private set
    var result by mutableStateOf<ScanResponse?>(null)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    val signedIn get() = c.cloud.isConfigured && c.cloud.currentAccount() != null
    val consent = c.settings.aiConsent.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    fun giveConsent() = viewModelScope.launch { c.settings.setAiConsent(true) }

    fun scan(context: Context, uri: Uri) = viewModelScope.launch {
        busy = true
        error = null
        result = null
        try {
            val (bitmap, base64) = withContext(Dispatchers.IO) { loadScaled(context, uri) }
            preview = bitmap
            result = c.cloud.scanReceipt(ScanRequest(base64, "image/jpeg", buildLedgerContext(c)))
            if (result?.draft == null) {
                error = if (result?.isReceipt == true) "Struk terbaca, tapi totalnya tidak jelas. Coba foto ulang lebih dekat dan terang."
                else "Gambar ini sepertinya bukan struk belanja."
            }
        } catch (e: CloudException) {
            error = e.message
        } catch (e: Exception) {
            error = "Foto tidak bisa diproses. Coba foto lain."
        } finally {
            busy = false
        }
    }

    /** Perkecil foto (sisi terpanjang ±1600 px, JPEG 85%) agar hemat kuota dan cepat dikirim. */
    private fun loadScaled(context: Context, uri: Uri): Pair<Bitmap, String> {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri).use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_SIDE) sample *= 2
        val decoded = context.contentResolver.openInputStream(uri).use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: error("decode gagal")
        val scale = MAX_SIDE.toFloat() / maxOf(decoded.width, decoded.height)
        val bitmap = if (scale < 1f) {
            Bitmap.createScaledBitmap(decoded, (decoded.width * scale).toInt(), (decoded.height * scale).toInt(), true)
        } else decoded
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
        return bitmap to Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }

    private companion object {
        const val MAX_SIDE = 1600
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanReceiptScreen(
    onBack: () -> Unit,
    onOpenAccount: () -> Unit,
    onContinue: (amount: Long, categoryId: String?, note: String?, date: String) -> Unit,
) {
    val vm = appViewModel { ScanReceiptViewModel(it) }
    val consent by vm.consent.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val photoUri = remember {
        val dir = File(context.cacheDir, "receipts").apply { mkdirs() }
        FileProvider.getUriForFile(context, "${context.packageName}.files", File(dir, "struk.jpg"))
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok) vm.scan(context, photoUri)
    }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) vm.scan(context, uri)
    }
    StatusBarIcons(lightIcons = false)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Scan struk") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Kembali") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (!vm.signedIn) {
                EmptyState("Masuk dulu untuk scan struk", "Struk dibaca oleh AI di server Duitku, jadi perlu akun.")
                Button(onClick = onOpenAccount, modifier = Modifier.fillMaxWidth()) { Text("Masuk ke akun") }
                return@Column
            }
            KopiCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(Icons.Outlined.DocumentScanner, MaterialTheme.colorScheme.primary, 44.dp)
                    Column(Modifier.padding(start = 12.dp)) {
                        Text("Foto struk belanja", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Text("Pastikan total terlihat jelas dan tidak terpotong.", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { camera.launch(photoUri) }, enabled = !vm.busy, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Outlined.PhotoCamera, null)
                        Text("  Kamera")
                    }
                    OutlinedButton(
                        onClick = { gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                        enabled = !vm.busy,
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Outlined.PhotoLibrary, null)
                        Text("  Galeri")
                    }
                }
            }
            vm.preview?.let { bmp ->
                Image(
                    bmp.asImageBitmap(), contentDescription = "Foto struk",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxWidth().heightIn(max = 280.dp).clip(MaterialTheme.shapes.medium),
                )
            }
            if (vm.busy) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    Text("  Membaca struk…", style = MaterialTheme.typography.bodyMedium)
                }
            }
            vm.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
            vm.result?.takeIf { it.draft != null }?.let { r ->
                val d = r.draft!!
                KopiCard(Modifier.fillMaxWidth()) {
                    Text(r.merchant ?: "Struk", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(d.date, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    r.items.take(8).forEach { item ->
                        Row(Modifier.fillMaxWidth()) {
                            Text(item.name, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                            Text(formatRupiah(item.amount), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    if (r.items.size > 8) {
                        Text("+${r.items.size - 8} barang lain", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth()) {
                        Text("Total", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        Text(formatRupiah(d.amount), fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = { onContinue(d.amount, d.categoryId, d.note, d.date) },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Lanjut ke form catat") }
                }
            }
        }
    }

    if (vm.signedIn && consent == false) {
        AiConsentDialog(onAgree = { vm.giveConsent() }, onCancel = onBack)
    }
}
