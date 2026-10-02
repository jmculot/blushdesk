package com.blushdesk.app.domain

import com.blushdesk.app.data.local.OrderStatus
import com.blushdesk.app.data.local.PaymentStatus

/**
 * The brand colors as plain 0xRRGGBB integers. Compose (ShowroomPinkTheme), the PDF canvas and the
 * Excel styles all need the same pinks but each wants a different color type, so the raw values
 * live here once and every consumer converts them.
 */
object BrandPalette {
    const val SOFT_PINK = 0xFCE4EC
    const val VIBRANT_ROSE = 0xE91E63
    const val DEEP_MAGENTA = 0x880E4F
    const val LAVENDER_BLUSH = 0xFFF0F5

    const val INK = 0x2B1520
    const val MUTED = 0x6E4A5A
    const val OUTLINE = 0xF3C7D6
    const val WHITE = 0xFFFFFF

    /** Foreground and background for a status chip, chosen to stay readable (>= 4.5:1). */
    data class Tone(val foreground: Int, val background: Int)

    private val BLUE = Tone(0x0D47A1, 0xE3F2FD)
    private val AMBER = Tone(0x8A4B00, 0xFFF1D6)
    private val GREEN = Tone(0x1B5E20, 0xE3F4E5)
    private val RED = Tone(0xB3261E, 0xFDE7E5)

    fun tone(status: OrderStatus): Tone = when (status) {
        OrderStatus.PROCESSING -> BLUE
        OrderStatus.PREPARING -> AMBER
        OrderStatus.DELIVERED -> GREEN
    }

    fun tone(status: PaymentStatus): Tone = when (status) {
        PaymentStatus.PAID -> GREEN
        PaymentStatus.PENDING -> AMBER
        PaymentStatus.UNPAID -> RED
    }
}
