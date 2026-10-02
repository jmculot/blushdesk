package com.blushdesk.app.data.local.database

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

@Entity(
    tableName = "buyers",
    indices = [Index("fullName")],
)
data class Buyer(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val fullName: String,
    val contactNumber: String,
    val email: String = "",
    val dateAdded: Instant,
    val profileImageUri: String? = null,
    val createdAt: Instant = dateAdded,
    val updatedAt: Instant = dateAdded,
)

/**
 * One purchase of one product. `Buyer.id -> Order.buyerId`; deleting a buyer deletes their orders
 * (CASCADE), so no order can outlive its buyer.
 *
 * Money is [BigDecimal] in code and whole centavos (INTEGER) in the database, see [Converters]:
 * exact in both places, unlike Double. [totalAmount] is stored for reporting but is always
 * recomputed as unitPrice x quantity when an order is saved, never typed in.
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
    val unitPrice: BigDecimal,
    val quantity: Int,
    val totalAmount: BigDecimal = Money.total(unitPrice, quantity),
    val purchaseDateTime: Instant,
    val paymentMode: PaymentMode,
    val paymentStatus: PaymentStatus,
    val fulfillmentStatus: FulfillmentStatus = FulfillmentStatus.PROCESSING,
    val createdAt: Instant = purchaseDateTime,
    val updatedAt: Instant = purchaseDateTime,
)
