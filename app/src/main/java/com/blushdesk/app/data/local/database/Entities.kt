package com.blushdesk.app.data.local.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.blushdesk.app.domain.model.FulfillmentStatus
import com.blushdesk.app.domain.model.PaymentMode
import com.blushdesk.app.domain.model.PaymentStatus
import com.blushdesk.app.utils.Money
import java.math.BigDecimal
import java.time.Instant

/**
 * The person running the tablet. The app keeps exactly one active profile (id [ACTIVE_ID]);
 * saving replaces it. Receipts and the Excel export take their showroom details from here.
 *
 * Image fields hold a `file://` URI of a copy in the app's private storage, never a picker URI
 * (those stop working) and never shared with other apps.
 */
@Entity(tableName = "operator_profile")
data class OperatorProfile(
    @PrimaryKey val id: Int = ACTIVE_ID,
    val fullName: String = "",
    val storeName: String = "",
    val email: String = "",
    val phoneNumber: String = "",
    val profileImageUri: String? = null,
    val createdAt: Instant = Instant.EPOCH,
    val updatedAt: Instant = Instant.EPOCH,
) {
    /** False until the two fields every receipt needs are filled in. */
    val isSetUp: Boolean get() = fullName.isNotBlank() && storeName.isNotBlank()

    companion object {
        const val ACTIVE_ID = 1
    }
}

/**
 * A customer. They can be reached by [contactNumber], by [facebookName], or both; the rule that at
 * least one is filled in lives in [com.blushdesk.app.utils.Validation.buyerContact]. Email is
 * optional.
 */
@Entity(
    tableName = "buyers",
    indices = [Index("fullName")],
)
data class Buyer(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val fullName: String,
    val contactNumber: String = "",
    /** Added in schema version 4; buyers recorded before that have an empty one. */
    @ColumnInfo(defaultValue = "") val facebookName: String = "",
    val email: String = "",
    val dateAdded: Instant,
    val profileImageUri: String? = null,
    val createdAt: Instant = dateAdded,
    val updatedAt: Instant = dateAdded,
)

/**
 * One purchase: when, how it was paid, where it is in the lifecycle, and its total. What was
 * bought is in [OrderItem] (one row per product). `Buyer.id -> Order.buyerId`; deleting a buyer
 * deletes their orders, and deleting an order deletes its items (both CASCADE).
 *
 * Money is [BigDecimal] in code and whole centavos (INTEGER) in the database, see [Converters]:
 * exact in both places, unlike Double. [totalAmount] is the sum of the items' line totals; it is
 * stored so SQL can total sales quickly, but the repository recomputes it on every save.
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
    val totalAmount: BigDecimal = Money.ZERO,
    val purchaseDateTime: Instant,
    val paymentMode: PaymentMode,
    val paymentStatus: PaymentStatus,
    val fulfillmentStatus: FulfillmentStatus = FulfillmentStatus.PROCESSING,
    val createdAt: Instant = purchaseDateTime,
    val updatedAt: Instant = purchaseDateTime,
)

/**
 * One product line of an order. [lineTotal] = unitPrice x quantity, recomputed on every save and
 * never typed in. [position] keeps the lines in the order the operator entered them.
 */
@Entity(
    tableName = "order_items",
    foreignKeys = [
        ForeignKey(
            entity = Order::class,
            parentColumns = ["id"],
            childColumns = ["orderId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("orderId")],
)
data class OrderItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val orderId: Long = 0,
    val position: Int = 0,
    val productName: String,
    val unitPrice: BigDecimal,
    val quantity: Int,
    val lineTotal: BigDecimal = Money.total(unitPrice, quantity),
)
