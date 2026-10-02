package com.blushdesk.app.domain.model

/**
 * How the buyer paid. [ONLINE_PAYMENT] only records the method (bank transfer, e-wallet, card
 * terminal, ...); the app never performs or verifies a transaction and needs no internet for it.
 */
enum class PaymentMode(val label: String) {
    CASH("Cash"),
    ONLINE_PAYMENT("Online payment"),
}

/**
 * [UNPAID]: nothing received. [PENDING]: payment promised or awaiting confirmation.
 * [PAID]: settled. Only [PAID] orders may have an official receipt.
 */
enum class PaymentStatus(val label: String) {
    UNPAID("Unpaid"),
    PENDING("Pending"),
    PAID("Paid");

    val isPaid: Boolean get() = this == PAID
}

/**
 * Order lifecycle. The declaration order is the progression order, so [next] and the visual
 * progress component both read it from [entries]; a one-tap advance never skips a stage.
 */
enum class FulfillmentStatus(val label: String) {
    PROCESSING("Processing"),
    PREPARING("Preparing"),
    DELIVERED("Delivered");

    /** The stage a one-tap advance moves to, or null once delivered. */
    val next: FulfillmentStatus? get() = entries.getOrNull(ordinal + 1)
}
