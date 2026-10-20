package com.aamnnr.duitku.notif

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// Contoh teks dibuat mengikuti pola umum notifikasi bank/e-wallet Indonesia.
// Ganti/tambah dengan teks asli dari HP-mu untuk memperketat parser.
class NotificationParserTest {

    @Test
    fun gopayPayment() {
        val r = NotificationParser.parse("com.gojek.gopay", "Pembayaran berhasil", "Pembayaran Rp18.000 di Kopi Kenangan berhasil.")
        assertNotNull(r)
        assertEquals("EXPENSE", r!!.type)
        assertEquals(18_000L, r.amount)
        assertEquals("Kopi Kenangan", r.counterpart)
        assertEquals("GoPay", r.sourceName)
    }

    @Test
    fun bankIncomingTransfer() {
        val r = NotificationParser.parse("id.co.bri.brimo", "Dana Masuk", "Dana masuk ke rekening Anda sebesar Rp 1.500.000,00 dari BUDI SANTOSO.")
        assertEquals("INCOME", r!!.type)
        assertEquals(1_500_000L, r.amount)
        assertEquals("BUDI SANTOSO", r.counterpart)
    }

    @Test
    fun transferOut() {
        val r = NotificationParser.parse("id.bmri.livin", "Transfer Berhasil", "Transfer ke SITI AMINAH sebesar Rp250.000 berhasil")
        assertEquals("EXPENSE", r!!.type)
        assertEquals(250_000L, r.amount)
        assertEquals("SITI AMINAH", r.counterpart)
    }

    @Test
    fun danaQris() {
        val r = NotificationParser.parse("id.dana", "DANA", "Kamu bayar Rp 32.500 pakai QRIS di Warung Bakso Pak Kumis")
        assertEquals("EXPENSE", r!!.type)
        assertEquals(32_500L, r.amount)
    }

    @Test
    fun idrWithDecimalDot() {
        assertEquals(50_000L, NotificationParser.parseAmount("Debit IDR 50,000.00 pada 20/10"))
        assertEquals(1_250_000L, NotificationParser.parseAmount("Rp1.250.000,00"))
        assertEquals(7_500L, NotificationParser.parseAmount("Rp 7500"))
    }

    @Test
    fun rejectsOtpPromoAndFailures() {
        assertNull(NotificationParser.parse("com.gojek.gopay", "Kode OTP", "Kode verifikasi 123456. Jangan berikan ke siapa pun. Transfer Rp10.000"))
        assertNull(NotificationParser.parse("com.shopeepay.id", "Promo!", "Dapatkan cashback hingga Rp50.000 untuk pembayaran QRIS"))
        assertNull(NotificationParser.parse("id.dana", "Transaksi gagal", "Pembayaran Rp20.000 gagal diproses"))
    }

    @Test
    fun ignoresUnknownAppsAndBalanceInfo() {
        assertNull(NotificationParser.parse("com.whatsapp", "Budi", "Transfer Rp100.000 ya"))
        assertNull(NotificationParser.parse("id.co.bri.brimo", "Info", "Saldo rekening Anda Rp 2.000.000"))
    }

    @Test
    fun topUpIsIncomeToWallet() {
        val r = NotificationParser.parse("ovo.id", "OVO", "Top up berhasil! Saldo OVO Cash bertambah Rp100.000")
        assertEquals("INCOME", r!!.type)
        assertEquals(100_000L, r.amount)
    }

    @Test
    fun suggestsCategoryFromMerchant() {
        val cats = listOf("c1" to "Makan & Minum", "c2" to "Transportasi", "c3" to "Belanja")
        assertEquals("c1", NotificationParser.suggestCategory("Pembayaran di Kopi Kenangan", cats))
        assertEquals("c2", NotificationParser.suggestCategory("GoRide ke kantor", cats))
        assertEquals("c3", NotificationParser.suggestCategory("Indomaret Point", cats))
        assertNull(NotificationParser.suggestCategory("Toko Sinar Jaya", cats))
    }

    @Test
    fun confidenceIsHighForClearCases() {
        val r = NotificationParser.parse("com.gojek.gopay", "Pembayaran berhasil", "Pembayaran Rp18.000 di Kopi Kenangan berhasil.")
        assertTrue(r!!.confidence >= 0.8)
    }
}
