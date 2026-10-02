package com.blushdesk.app.domain

/**
 * Pure form rules shared by the dialogs and the view model. Each function returns a message for the
 * user, or null when the value is acceptable, so the UI can show it directly under the field.
 */
object Validation {
    const val MAX_NAME = 80
    const val MAX_PRODUCT = 100
    const val MAX_QUANTITY = 9_999

    private val EMAIL = Regex("""^[A-Za-z0-9._%+\-]+@[A-Za-z0-9\-]+(\.[A-Za-z0-9\-]+)*\.[A-Za-z]{2,}$""")
    private val PHONE_CHARS = Regex("""^[0-9+()\-.\s]+$""")

    fun name(value: String, what: String = "Name"): String? = when {
        value.isBlank() -> "$what is required"
        value.trim().length > MAX_NAME -> "Keep it under $MAX_NAME characters"
        else -> null
    }

    fun email(value: String): String? = when {
        value.isBlank() -> null // optional
        !EMAIL.matches(value.trim()) -> "Enter a valid email address"
        else -> null
    }

    fun phone(value: String, required: Boolean = true): String? {
        val trimmed = value.trim()
        if (trimmed.isEmpty()) return if (required) "Contact number is required" else null
        if (!PHONE_CHARS.matches(trimmed) || trimmed.count { it.isDigit() } < 7) {
            return "Enter a valid phone number"
        }
        return null
    }

    fun product(value: String): String? = when {
        value.isBlank() -> "Product name is required"
        value.trim().length > MAX_PRODUCT -> "Keep it under $MAX_PRODUCT characters"
        else -> null
    }

    fun unitPrice(input: String): String? {
        val minor = Money.parse(input) ?: return "Enter an amount like 1250 or 1250.50"
        return when {
            minor <= 0 -> "Price must be greater than zero"
            minor > Money.MAX_UNIT_PRICE_MINOR -> "Price is too large"
            else -> null
        }
    }

    fun quantity(input: String): String? {
        val qty = input.trim().toIntOrNull() ?: return "Enter a whole number"
        return when {
            qty < 1 -> "Quantity must be at least 1"
            qty > MAX_QUANTITY -> "Quantity can be at most $MAX_QUANTITY"
            else -> null
        }
    }
}
