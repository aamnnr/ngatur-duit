package com.aamnnr.duitku.data.export

import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import com.aamnnr.duitku.domain.TransactionItem
import com.aamnnr.duitku.domain.TransactionType
import com.aamnnr.duitku.domain.isCashflow
import com.aamnnr.duitku.ui.LocaleID
import com.aamnnr.duitku.ui.formatRupiah
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Ekspor transaksi satu periode ke CSV (dibuka di Excel/Sheets) atau PDF (cetak/bagikan),
 * lalu dibagikan lewat lembar Bagikan Android.
 */
class ExportService(private val context: Context) {
    private val dateFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
    private val prettyDate = DateTimeFormatter.ofPattern("d MMM yyyy", LocaleID)
    private val zone = ZoneId.systemDefault()

    private fun dir() = File(context.cacheDir, "exports").apply { mkdirs() }

    private fun kind(t: TransactionItem) = when {
        t.isAdjustment -> "Penyesuaian"
        t.isTransfer -> "Transfer"
        t.type == TransactionType.INCOME -> "Pemasukan"
        else -> "Pengeluaran"
    }

    /** CSV UTF-8 dengan BOM agar karakter Indonesia terbaca benar di Excel. */
    fun csv(items: List<TransactionItem>, fileLabel: String): File {
        fun q(s: String?) = "\"" + (s ?: "").replace("\"", "\"\"") + "\""
        val sb = StringBuilder("﻿")
        sb.append("Tanggal,Jenis,Kategori,Dompet,Catatan,Nominal\n")
        items.sortedBy { it.date }.forEach { t ->
            val signed = if (t.type == TransactionType.INCOME) t.amount else -t.amount
            sb.append(listOf(
                q(Instant.ofEpochMilli(t.date).atZone(zone).format(dateFmt)), q(kind(t)), q(t.categoryName), q(t.walletName), q(t.note),
                signed.toString(),
            ).joinToString(",")).append('\n')
        }
        return File(dir(), "duitku-$fileLabel.csv").apply { writeText(sb.toString()) }
    }

    /** Laporan PDF A4: ringkasan, rincian kategori, daftar transaksi. */
    fun pdf(items: List<TransactionItem>, title: String, fileLabel: String): File {
        val doc = PdfDocument()
        val pageW = 595
        val pageH = 842
        val margin = 40f
        val text = Paint().apply { textSize = 10f; isAntiAlias = true; color = 0xFF1C1C1E.toInt() }
        val muted = Paint(text).apply { color = 0xFF8E8E93.toInt() }
        val bold = Paint(text).apply { typeface = Typeface.DEFAULT_BOLD }
        val h1 = Paint(bold).apply { textSize = 18f }
        val h2 = Paint(bold).apply { textSize = 12f }
        val right = Paint(text).apply { textAlign = Paint.Align.RIGHT }
        val rule = Paint().apply { color = 0xFFE5E5EA.toInt(); strokeWidth = 0.8f }

        var pageNo = 0
        var page = doc.startPage(PdfDocument.PageInfo.Builder(pageW, pageH, ++pageNo).create())
        var y = margin
        fun newPageIfNeeded(space: Float) {
            if (y + space > pageH - margin) {
                doc.finishPage(page)
                page = doc.startPage(PdfDocument.PageInfo.Builder(pageW, pageH, ++pageNo).create())
                y = margin
            }
        }
        fun c() = page.canvas

        val flow = items.filter { it.isCashflow }
        val income = flow.filter { it.type == TransactionType.INCOME }.sumOf { it.amount }
        val expense = flow.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount }

        c().drawText("Laporan Duitku", margin, y + 18, h1); y += 30
        c().drawText(title, margin, y + 10, muted); y += 26
        listOf("Pemasukan" to income, "Pengeluaran" to expense, "Selisih" to income - expense).forEach { (label, v) ->
            c().drawText(label, margin, y + 10, text)
            c().drawText(formatRupiah(v), pageW - margin, y + 10, Paint(right).apply { typeface = Typeface.DEFAULT_BOLD })
            y += 16
        }
        y += 12
        c().drawText("Pengeluaran per kategori", margin, y + 12, h2); y += 22
        flow.filter { it.type == TransactionType.EXPENSE }
            .groupBy { it.categoryName ?: "Tanpa kategori" }
            .mapValues { (_, l) -> l.sumOf { it.amount } }
            .entries.sortedByDescending { it.value }
            .forEach { (name, v) ->
                newPageIfNeeded(16f)
                val pct = if (expense > 0) v * 100 / expense else 0
                c().drawText(name, margin, y + 10, text)
                c().drawText("$pct%", pageW - margin - 110, y + 10, Paint(right).apply { color = 0xFF8E8E93.toInt() })
                c().drawText(formatRupiah(v), pageW - margin, y + 10, right)
                y += 16
            }
        y += 12
        newPageIfNeeded(40f)
        c().drawText("Transaksi", margin, y + 12, h2); y += 22
        listOf("Tanggal" to margin, "Keterangan" to margin + 80, "Dompet" to margin + 300).forEach { (h, x) -> c().drawText(h, x, y + 10, muted) }
        c().drawText("Nominal", pageW - margin, y + 10, Paint(right).apply { color = 0xFF8E8E93.toInt() })
        y += 14
        c().drawLine(margin, y, pageW - margin, y, rule); y += 4
        items.sortedBy { it.date }.forEach { t ->
            newPageIfNeeded(16f)
            val desc = (t.note?.takeIf { it.isNotBlank() } ?: t.categoryName ?: kind(t)).take(36)
            val signed = if (t.type == TransactionType.INCOME) t.amount else -t.amount
            c().drawText(Instant.ofEpochMilli(t.date).atZone(zone).format(prettyDate), margin, y + 10, text)
            c().drawText(desc, margin + 80, y + 10, text)
            c().drawText((t.walletName ?: "").take(18), margin + 300, y + 10, muted)
            c().drawText(formatRupiah(signed), pageW - margin, y + 10, right)
            y += 15
        }
        doc.finishPage(page)
        val file = File(dir(), "duitku-$fileLabel.pdf")
        file.outputStream().use { doc.writeTo(it) }
        doc.close()
        return file
    }

    fun share(file: File, mime: String) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, "Bagikan laporan").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
