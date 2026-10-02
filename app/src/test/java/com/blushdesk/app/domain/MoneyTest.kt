package com.blushdesk.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MoneyTest {

    @Test
    fun `parses whole and decimal amounts into minor units`() {
        assertEquals(125_000L, Money.parse("1250"))
        assertEquals(125_050L, Money.parse("1250.5"))
        assertEquals(125_050L, Money.parse("1250.50"))
        assertEquals(5L, Money.parse("0.05"))
    }

    @Test
    fun `ignores thousands separators, spaces and the currency symbol`() {
        assertEquals(123_456_789L, Money.parse("1,234,567.89"))
        assertEquals(9_999L, Money.parse("₱ 99.99"))
        assertEquals(10_000L, Money.parse("  100  "))
    }

    @Test
    fun `accepts a trailing decimal point`() {
        assertEquals(1_000L, Money.parse("10."))
    }

    @Test
    fun `rejects instead of rounding a third decimal`() {
        assertNull(Money.parse("10.999"))
    }

    @Test
    fun `rejects text, negatives and empty input`() {
        assertNull(Money.parse(""))
        assertNull(Money.parse("abc"))
        assertNull(Money.parse("-5"))
        assertNull(Money.parse("1.2.3"))
        assertNull(Money.parse("12345678901")) // more than 10 integer digits
    }

    @Test
    fun `formats with symbol, grouping and two decimals`() {
        assertEquals("₱0.00", Money.format(0))
        assertEquals("₱1,234.50", Money.format(123_450))
        assertEquals("₱10,000,000.00", Money.format(1_000_000_000))
        assertEquals("1,234.50", Money.formatPlain(123_450))
    }

    @Test
    fun `input string round-trips through parse`() {
        listOf(0L, 5L, 100L, 123_450L, 999_999_999L).forEach { minor ->
            assertEquals(minor, Money.parse(Money.toInputString(minor)))
        }
    }

    @Test
    fun `excel number keeps centavos exact`() {
        assertEquals(1234.5, Money.toExcelNumber(123_450), 0.0)
        assertEquals(0.1, Money.toExcelNumber(10), 0.0)
    }
}
