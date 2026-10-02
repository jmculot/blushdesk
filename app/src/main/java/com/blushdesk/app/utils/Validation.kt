package com.blushdesk.app.utils

import com.blushdesk.app.data.local.database.Buyer
import com.blushdesk.app.data.local.database.OrderItem
import java.math.BigDecimal

/**
 * Input rules shared by the dialogs (which show the message under the field) and the repository
 * (which refuses to store a record that breaks them). Each function returns a message written for
 * the operator, or null when the value is fine.
 */
object Validation {
    const val MAX_NAME = 80
    const val MAX_PRODUCT = 100
    const val MAX_QUANTITY = 9_999

    /** Product lines per order; keeps a receipt to a few pages and a total far inside a Long. */
    const val MAX_ITEMS = 30

    private val EMAIL = Regex("""^[A-Za-z0-9._%+\-]+@[A-Za-z0-9\-]+(\.[A-Za-z0-9\-]+)*\.[A-Za-z]{2,}$""")
    private val PHONE_CHARS = Regex("""^[0-9+()\-.\s]+$""")

    fun name(value: String, what: String = "Name"): String? = when {
        value.isBlank() -> "$what is required"
        value.trim().length > MAX_NAME -> "Keep it under $MAX_NAME characters"
        else -> null
    }

    /** Email is optional; when given it must look like one. */
    fun email(value: String): String? = when {
        value.isBlank() -> null
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

    /** For the price as typed. */
    fun unitPrice(input: String): String? {
        val amount = Money.parse(input) ?: return "Enter an amount like 1250 or 1250.50"
        return unitPrice(amount)
    }

    fun unitPrice(amount: BigDecimal): String? = when {
        amount.signum() <= 0 -> "Price must be greater than zero"
        amount.stripTrailingZeros().scale() > 2 -> "Use at most two decimals"
        amount > Money.MAX_UNIT_PRICE -> "Price is too large"
        else -> null
    }

    /** For the quantity as typed. */
    fun quantity(input: String): String? {
        val qty = input.trim().toIntOrNull() ?: return "Enter a whole number"
        return quantity(qty)
    }

    fun quantity(qty: Int): String? = when {
        qty < 1 -> "Quantity must be at least 1"
        qty > MAX_QUANTITY -> "Quantity can be at most $MAX_QUANTITY"
        else -> null
    }

    /** The first problem with a buyer about to be saved, or null. */
    fun buyerProblem(buyer: Buyer): String? =
        name(buyer.fullName, "Buyer name") ?: phone(buyer.contactNumber) ?: email(buyer.email)

    /** The first problem with one product line, or null. */
    fun itemProblem(item: OrderItem): String? =
        product(item.productName) ?: unitPrice(item.unitPrice) ?: quantity(item.quantity)

    /** The first problem with an order's product lines, or null. */
    fun itemsProblem(items: List<OrderItem>): String? = when {
        items.isEmpty() -> "Add at least one product"
        items.size > MAX_ITEMS -> "An order can have at most $MAX_ITEMS products"
        else -> items.firstNotNullOfOrNull { itemProblem(it) }
    }
}
