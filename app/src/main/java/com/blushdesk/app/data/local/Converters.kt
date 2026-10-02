package com.blushdesk.app.data.local

import androidx.room.TypeConverter
import java.time.Instant

/**
 * Room can only store primitives and strings. Timestamps become epoch milliseconds and enums
 * become their names. Enums are stored by name rather than ordinal so reordering the declaration
 * later cannot silently re-label old rows; an unrecognised name falls back to a safe default
 * instead of crashing the whole list.
 */
class Converters {
    @TypeConverter
    fun instantToEpochMillis(value: Instant): Long = value.toEpochMilli()

    @TypeConverter
    fun epochMillisToInstant(value: Long): Instant = Instant.ofEpochMilli(value)

    @TypeConverter
    fun orderStatusToName(value: OrderStatus): String = value.name

    @TypeConverter
    fun nameToOrderStatus(value: String): OrderStatus =
        OrderStatus.entries.firstOrNull { it.name == value } ?: OrderStatus.PROCESSING

    @TypeConverter
    fun paymentModeToName(value: PaymentMode): String = value.name

    @TypeConverter
    fun nameToPaymentMode(value: String): PaymentMode =
        PaymentMode.entries.firstOrNull { it.name == value } ?: PaymentMode.CASH

    @TypeConverter
    fun paymentStatusToName(value: PaymentStatus): String = value.name

    @TypeConverter
    fun nameToPaymentStatus(value: String): PaymentStatus =
        PaymentStatus.entries.firstOrNull { it.name == value } ?: PaymentStatus.PENDING
}
