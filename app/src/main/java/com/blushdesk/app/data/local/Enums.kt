package com.blushdesk.app.data.local

/**
 * Fulfillment lifecycle of an order. The declaration order IS the progression order, so [next] and
 * the visual stepper both read it straight from [entries].
 */
enum class OrderStatus(val label: String) {
    PROCESSING("Processing"),
    PREPARING("Preparing"),
    DELIVERED("Delivered");

    /** The stage a one-tap "advance" moves to, or null once the order is delivered. */
    val next: OrderStatus? get() = entries.getOrNull(ordinal + 1)
}

enum class PaymentMode(val label: String) {
    CASH("Cash"),
    ONLINE("Online payment"),
}

/**
 * [PENDING] means money is expected (for example an online transfer awaiting confirmation);
 * [UNPAID] means nothing has been received. Only [PAID] unlocks the PDF receipt.
 */
enum class PaymentStatus(val label: String) {
    PAID("Paid"),
    PENDING("Pending"),
    UNPAID("Unpaid");

    val isPaid: Boolean get() = this == PAID
}
