package com.blushdesk.app.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal

class MoneyTest {

    @Test
    fun `parses whole and decimal amounts with two decimals`() {
        assertEquals(BigDecimal("1250.00"), Money.parse("1250"))
        assertEquals(BigDecimal("1250.50"), Money.parse("1250.5"))
        assertEquals(BigDecimal("1250.50"), Money.parse("1250.50"))
        assertEquals(BigDecimal("0.05"), Money.parse("0.05"))
    }

    @Test
    fun `ignores thousands separators, spaces and the currency symbol`() {
        assertEquals(BigDecimal("1234567.89"), Money.parse("1,234,567.89"))
        assertEquals(BigDecimal("99.99"), Money.parse("₱ 99.99"))
        assertEquals(BigDecimal("100.00"), Money.parse("  100  "))
        assertEquals(BigDecimal("10.00"), Money.parse("10."))
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
        assertNull(Money.parse("12345678901"))
    }

    @Test
    fun `total is unit price times quantity, exactly`() {
        // 0.10 x 3 is 0.30000000000000004 in floating point; BigDecimal keeps it exact.
        assertEquals(BigDecimal("0.30"), Money.total(Money.of("0.10"), 3))
        assertEquals(BigDecimal("2999.97"), Money.total(Money.of("999.99"), 3))
        assertEquals(BigDecimal("99990000000.00"), Money.total(Money.MAX_UNIT_PRICE, 9_999))
    }

    @Test
    fun `formats with symbol, grouping and two decimals`() {
        assertEquals("₱0.00", Money.format(Money.ZERO))
        assertEquals("₱1,234.50", Money.format(Money.of("1234.5")))
        assertEquals("₱10,000,000.00", Money.format(Money.MAX_UNIT_PRICE))
        assertEquals("1,234.50", Money.formatPlain(Money.of("1234.50")))
    }

    @Test
    fun `centavos round-trip and refuse to drop a third decimal`() {
        assertEquals(123_450L, Money.toCentavos(Money.of("1234.50")))
        assertEquals(Money.of("1234.50"), Money.fromCentavos(123_450))
        try {
            Money.toCentavos(BigDecimal("1.005"))
            throw AssertionError("expected ArithmeticException")
        } catch (_: ArithmeticException) {
            // expected: never silently round money
        }
    }

    @Test
    fun `input string round-trips through parse`() {
        listOf("0.00", "0.05", "1.00", "1234.50", "9999999.99").forEach {
            val amount = Money.of(it)
            assertEquals(amount, Money.parse(Money.toInputString(amount)))
        }
    }

    @Test
    fun `excel number keeps centavos exact`() {
        assertEquals(1234.5, Money.toExcelNumber(Money.of("1234.50")), 0.0)
        assertEquals(0.1, Money.toExcelNumber(Money.of("0.10")), 0.0)
    }
}
