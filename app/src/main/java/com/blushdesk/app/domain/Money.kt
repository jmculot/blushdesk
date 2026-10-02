package com.blushdesk.app.domain

import java.math.BigDecimal
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

/**
 * Money helpers. Amounts travel through the app as Long minor units (centavos); this is the only
 * place that converts to and from text. Change [SYMBOL] to re-badge the whole app for another
 * currency (the Excel number format and PDF receipts both read it).
 */
object Money {
    const val SYMBOL = "₱"

    /** Largest unit price accepted: 10,000,000.00. Keeps price x quantity far inside a Long. */
    const val MAX_UNIT_PRICE_MINOR = 1_000_000_000L

    private val INPUT = Regex("""^\d{1,10}(\.\d{0,2})?$""")

    /**
     * Turns what the user typed ("1,250", "1250.5", "₱ 99.99") into minor units, or null when it is
     * not a plain non-negative amount with at most two decimals. It rejects rather than rounds:
     * silently turning 10.999 into 11.00 would change what the customer is charged.
     */
    fun parse(input: String): Long? {
        val cleaned = input.trim().removePrefix(SYMBOL).replace(",", "").replace(" ", "")
        if (!INPUT.matches(cleaned)) return null
        return BigDecimal(cleaned.removeSuffix(".")).movePointRight(2).longValueExact()
    }

    /** "₱1,234.50" - what appears on screen, in the PDF and in notifications. */
    fun format(minor: Long): String = SYMBOL + formatPlain(minor)

    /** "1,234.50" without the symbol, for table cells that already carry the currency in the header. */
    fun formatPlain(minor: Long): String = decimalFormat().format(BigDecimal.valueOf(minor, 2))

    /** "1234.50" - round-trips through [parse], used to pre-fill the edit form. */
    fun toInputString(minor: Long): String = BigDecimal.valueOf(minor, 2).toPlainString()

    /** Value for an Excel cell. Exact for any amount below 2^53 centavos (about 90 trillion pesos). */
    fun toExcelNumber(minor: Long): Double = BigDecimal.valueOf(minor, 2).toDouble()

    // DecimalFormat is not thread-safe, so build one per call; this is far from a hot path.
    private fun decimalFormat() = DecimalFormat("#,##0.00", DecimalFormatSymbols(Locale.US))
}
