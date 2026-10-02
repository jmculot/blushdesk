package com.blushdesk.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.blushdesk.app.data.local.OrderStatus
import com.blushdesk.app.data.local.PaymentMode
import com.blushdesk.app.data.local.PaymentStatus
import java.time.Instant

/**
 * One purchased product. Deleting a buyer deletes their orders (CASCADE), so no order can outlive
 * the person it belongs to.
 *
 * Money is stored as whole minor units (centavos) in a Long. Doubles cannot represent 0.10 exactly,
 * and a receipt that is off by a centavo is a bug in a shop.
 */
@Entity(
    tableName = "orders",
    foreignKeys = [
        ForeignKey(
            entity = Buyer::class,
            parentColumns = ["id"],
            childColumns = ["buyerId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("buyerId")],
)
data class Order(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val buyerId: Long,
    val productName: String,
    val unitPriceMinor: Long,
    val quantity: Int,
    val purchasedAt: Instant,
    val paymentMode: PaymentMode,
    val paymentStatus: PaymentStatus,
    val orderStatus: OrderStatus = OrderStatus.PROCESSING,
) {
    /** Derived, not stored: it can never disagree with price and quantity. */
    val totalMinor: Long get() = unitPriceMinor * quantity
}
