package com.aamnnr.duitku.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VoiceParserTest {
    @Test
    fun numericWithUnit() {
        val d = VoiceParser.parse("beli kopi 25 ribu pakai gopay")!!
        assertEquals(25_000L, d.amount)
        assertEquals(TransactionType.EXPENSE, d.type)
        assertEquals("Kopi gopay", d.note)
    }

    @Test
    fun shortUnitsAndDecimals() {
        assertEquals(18_000L, VoiceParser.parse("parkir 18rb")!!.amount)
        assertEquals(1_500_000L, VoiceParser.parse("bayar kos 1,5 juta")!!.amount)
        assertEquals(50_000L, VoiceParser.parse("bensin Rp50.000")!!.amount)
    }

    @Test
    fun prefersAmountWithUnitOverCount() {
        assertEquals(30_000L, VoiceParser.parse("2 kopi 30 ribu")!!.amount)
    }

    @Test
    fun wordNumbers() {
        assertEquals(25_000L, VoiceParser.parse("makan siang dua puluh lima ribu")!!.amount)
        assertEquals(150_000L, VoiceParser.parse("belanja seratus lima puluh ribu")!!.amount)
        assertEquals(1_500_000L, VoiceParser.parse("bayar kontrakan satu setengah juta")!!.amount)
    }

    @Test
    fun detectsIncome() {
        val d = VoiceParser.parse("terima gaji 6,5 juta")!!
        assertEquals(TransactionType.INCOME, d.type)
        assertEquals(6_500_000L, d.amount)
    }

    @Test
    fun noAmountReturnsNull() {
        assertNull(VoiceParser.parse("beli kopi"))
        assertNull(VoiceParser.parse(""))
    }
}
