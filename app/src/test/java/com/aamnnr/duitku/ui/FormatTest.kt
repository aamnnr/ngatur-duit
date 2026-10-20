package com.aamnnr.duitku.ui

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.YearMonth

class FormatTest {
    @Test
    fun formatsRupiahWithDotSeparators() {
        assertEquals("Rp 1.250.000", formatRupiah(1_250_000))
        assertEquals("Rp 0", formatRupiah(0))
        assertEquals("-Rp 5.000", formatRupiah(-5_000))
    }

    @Test
    fun parsesAmountIgnoringSeparators() {
        assertEquals(1_250_000L, parseAmount("1.250.000"))
        assertEquals(0L, parseAmount(""))
        assertEquals(5_000L, parseAmount("-5.000"))
    }

    @Test
    fun monthRangeCoversWholeMonth() {
        val (from, to) = YearMonth.of(2026, 2).millisRange()
        assertEquals(java.time.LocalDate.of(2026, 2, 1), from.toLocalDate())
        assertEquals(java.time.LocalDate.of(2026, 2, 28), to.toLocalDate())
        assertEquals(java.time.LocalDate.of(2026, 3, 1), (to + 1).toLocalDate())
    }
}
