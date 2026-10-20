package com.aamnnr.duitku.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID

/** Utilitas foto struk: perkecil agar hemat ruang & kuota. */
object ImageUtils {
    /** Baca gambar dari [uri], perkecil sampai sisi terpanjang ≤ [maxSide] px. */
    fun loadScaled(context: Context, uri: Uri, maxSide: Int = 1600): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri).use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
        val decoded = context.contentResolver.openInputStream(uri).use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: error("Gambar tidak bisa dibaca")
        val scale = maxSide.toFloat() / maxOf(decoded.width, decoded.height)
        return if (scale < 1f) {
            Bitmap.createScaledBitmap(decoded, (decoded.width * scale).toInt(), (decoded.height * scale).toInt(), true)
        } else decoded
    }

    fun jpegBytes(bitmap: Bitmap, quality: Int = 85): ByteArray =
        ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.JPEG, quality, it) }.toByteArray()

    /** Simpan foto struk ke penyimpanan internal (files/receipts) dan kembalikan path-nya. */
    fun saveReceipt(context: Context, uri: Uri): String {
        val dir = File(context.filesDir, "receipts").apply { mkdirs() }
        val file = File(dir, "${UUID.randomUUID()}.jpg")
        file.writeBytes(jpegBytes(loadScaled(context, uri, 1600), 80))
        return file.absolutePath
    }
}
