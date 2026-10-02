package com.blushdesk.app.utils

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

/**
 * Money is a [BigDecimal] with exactly two decimals everywhere in the app, and whole centavos
 * (a Long) in the database. This object is the only place that parses, formats or converts it.
 *
 * Nothing here rounds silently. A third decimal is rejected when parsing and throws when storing,
 * because quietly turning 10.999 into 11.00 would change what a customer is charged.
 *
 * Change [SYMBOL] to re-badge the app for another currency; the PDF and Excel formats follow it.
 */
object Money {
    const val SYMBOL = "₱"

    val ZERO: BigDecimal = BigDecimal.ZERO.setScale(2)

    /** Largest accepted unit price, 10,000,000.00. Keeps any total far inside a Long of centavos. */
    val MAX_UNIT_PRICE: BigDecimal = BigDecimal("10000000.00")

    private val INPUT = Regex("""^\d{1,10}(\.\d{0,2})?$""")

    /** A two-decimal amount from a literal, for constants and tests: `Money.of("12.50")`. */
    fun of(text: String): BigDecimal = BigDecimal(text).setScale(2, RoundingMode.UNNECESSARY)

    /**
     * What the user typed ("1,250", "1250.5", "₱ 99.99") as an amount, or null when it is not a plain
     * non-negative number with at most two decimals.
     */
    fun parse(input: String): BigDecimal? {
        val cleaned = input.trim().removePrefix(SYMBOL).replace(",", "").replace(" ", "")
        if (!INPUT.matches(cleaned)) return null
        return BigDecimal(cleaned.removeSuffix(".")).setScale(2, RoundingMode.UNNECESSARY)
    }

    /** unitPrice x quantity. Exact: a two-decimal price times a whole number has two decimals. */
    fun total(unitPrice: BigDecimal, quantity: Int): BigDecimal =
        unitPrice.multiply(BigDecimal.valueOf(quantity.toLong())).setScale(2, RoundingMode.UNNECESSARY)

    /** "₱1,234.50", for the screen and the PDF. */
    fun format(amount: BigDecimal): String = SYMBOL + formatPlain(amount)

    /** "1,234.50" without the symbol. */
    fun formatPlain(amount: BigDecimal): String = decimalFormat().format(amount.setScale(2, RoundingMode.UNNECESSARY))

    /** "1234.50", round-trips through [parse]; used to pre-fill the edit form. */
    fun toInputString(amount: BigDecimal): String = amount.setScale(2, RoundingMode.UNNECESSARY).toPlainString()

    /** Database form. Throws [ArithmeticException] on a third decimal instead of rounding it away. */
    fun toCentavos(amount: BigDecimal): Long = amount.setScale(2, RoundingMode.UNNECESSARY).unscaledValue().longValueExact()

    fun fromCentavos(centavos: Long): BigDecimal = BigDecimal.valueOf(centavos, 2)

    /** Value for an Excel number cell. Exact for any amount below 2^53 centavos. */
    fun toExcelNumber(amount: BigDecimal): Double = amount.toDouble()

    // DecimalFormat is not thread-safe, so a fresh one per call; this is far from a hot path.
    private fun decimalFormat() = DecimalFormat("#,##0.00", DecimalFormatSymbols(Locale.US))
}
