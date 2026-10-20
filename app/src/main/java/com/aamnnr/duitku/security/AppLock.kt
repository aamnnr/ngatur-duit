package com.aamnnr.duitku.security

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.aamnnr.duitku.ui.components.IconBadge
import com.aamnnr.duitku.ui.components.neuSurface
import kotlinx.coroutines.delay
import java.security.MessageDigest
import java.security.SecureRandom

object PinHasher {
    /** Disimpan sebagai "salt:hash" (SHA-256), bukan PIN aslinya. */
    fun hash(pin: String, salt: String = newSalt()): String = "$salt:" + sha256("$salt|$pin")

    fun verify(pin: String, stored: String?): Boolean {
        val salt = stored?.substringBefore(':') ?: return false
        return hash(pin, salt) == stored
    }

    private fun newSalt(): String = ByteArray(16).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it) }
    private fun sha256(s: String) = MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }
}

fun biometricAvailable(activity: FragmentActivity): Boolean =
    BiometricManager.from(activity).canAuthenticate(
        BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.BIOMETRIC_WEAK,
    ) == BiometricManager.BIOMETRIC_SUCCESS

fun showBiometricPrompt(activity: FragmentActivity, onSuccess: () -> Unit) {
    val prompt = BiometricPrompt(
        activity,
        ContextCompat.getMainExecutor(activity),
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onSuccess()
        },
    )
    prompt.authenticate(
        BiometricPrompt.PromptInfo.Builder()
            .setTitle("Buka Duitku")
            .setSubtitle("Gunakan sidik jari atau wajah")
            .setNegativeButtonText("Pakai PIN")
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.BIOMETRIC_WEAK)
            .build(),
    )
}

/**
 * Papan PIN 6 digit. [onComplete] dipanggil saat 6 digit terisi; kembalikan false untuk menandai salah
 * (titik bergetar dan dikosongkan).
 */
@Composable
fun PinPad(
    title: String,
    subtitle: String,
    onComplete: (String) -> Boolean,
    biometric: (() -> Unit)? = null,
    error: String? = null,
) {
    var pin by remember { mutableStateOf("") }
    var wrong by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current
    LaunchedEffect(pin) {
        if (pin.length == 6) {
            if (!onComplete(pin)) {
                wrong = true
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                delay(400)
                pin = ""
                wrong = false
            } else {
                pin = ""
            }
        }
    }
    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).systemBarsPadding().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        IconBadge(Icons.Outlined.Lock, MaterialTheme.colorScheme.primary, 56.dp)
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleLarge)
        Text(error ?: subtitle, style = MaterialTheme.typography.bodyMedium,
            color = if (error != null || wrong) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            repeat(6) { i ->
                Box(
                    Modifier.size(14.dp).clip(CircleShape).background(
                        when {
                            wrong -> MaterialTheme.colorScheme.error
                            i < pin.length -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.surfaceContainerHighest
                        },
                    ),
                )
            }
        }
        Spacer(Modifier.height(36.dp))
        val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "bio", "0", "del")
        keys.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp), modifier = Modifier.padding(vertical = 8.dp)) {
                row.forEach { k ->
                    Box(
                        Modifier
                            .size(72.dp)
                            .then(if (k == "bio" && biometric == null) Modifier else Modifier.neuSurface(CircleShape, 4.dp))
                            .clickable(enabled = !(k == "bio" && biometric == null)) {
                                when (k) {
                                    "del" -> pin = pin.dropLast(1)
                                    "bio" -> biometric?.invoke()
                                    else -> if (pin.length < 6) pin += k
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        when (k) {
                            "del" -> Icon(Icons.AutoMirrored.Outlined.Backspace, "Hapus")
                            "bio" -> if (biometric != null) Icon(Icons.Outlined.Fingerprint, "Sidik jari")
                            else -> Text(k, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}

/** Layar kunci saat aplikasi dibuka. Menawarkan biometrik otomatis bila diaktifkan. */
@Composable
fun LockScreen(pinHash: String?, useBiometric: Boolean, onUnlock: () -> Unit) {
    val activity = LocalContext.current as? FragmentActivity
    val bio = activity?.takeIf { useBiometric && biometricAvailable(it) }
    LaunchedEffect(Unit) { bio?.let { showBiometricPrompt(it, onUnlock) } }
    PinPad(
        title = "Duitku terkunci",
        subtitle = "Masukkan PIN 6 digit",
        onComplete = { pin -> PinHasher.verify(pin, pinHash).also { if (it) onUnlock() } },
        biometric = bio?.let { a -> { showBiometricPrompt(a, onUnlock) } },
    )
}

/** Membuat PIN baru: masukkan dua kali. */
@Composable
fun PinSetup(onDone: (String) -> Unit, onCancel: () -> Unit) {
    var first by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    Box(Modifier.fillMaxSize()) {
        PinPad(
            title = if (first == null) "Buat PIN" else "Ulangi PIN",
            subtitle = if (first == null) "6 digit untuk membuka Duitku" else "Masukkan PIN yang sama",
            error = error,
            onComplete = { pin ->
                val f = first
                when {
                    f == null -> { first = pin; error = null; true }
                    f == pin -> { onDone(PinHasher.hash(pin)); true }
                    else -> { first = null; error = "PIN tidak sama, ulangi dari awal"; false }
                }
            },
        )
        Text(
            "Batal",
            color = MaterialTheme.colorScheme.secondary,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.align(Alignment.TopEnd).systemBarsPadding().padding(20.dp).clickable(onClick = onCancel),
        )
    }
}
