package com.blushdesk.app

import android.content.Context
import androidx.room.Room
import com.blushdesk.app.data.local.database.AppDatabase
import com.blushdesk.app.data.local.database.Buyer
import com.blushdesk.app.data.local.database.BuyerWithOrders
import com.blushdesk.app.data.local.database.ExportSummary
import com.blushdesk.app.data.local.database.OperatorProfile
import com.blushdesk.app.data.local.database.Order
import com.blushdesk.app.domain.model.ExportSnapshot
import com.blushdesk.app.domain.model.FulfillmentStatus
import com.blushdesk.app.domain.model.PaymentMode
import com.blushdesk.app.domain.model.PaymentStatus
import com.blushdesk.app.utils.Money
import java.time.Instant

/** Shared fixtures for the instrumented tests. */
object TestData {
    val operator = OperatorProfile(
        fullName = "Lia Santos",
        storeName = "Rosé Showroom",
        email = "lia@rose.example",
        phoneNumber = "0917 555 0100",
    )

    fun inMemoryDatabase(context: Context): AppDatabase =
        Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()

    fun buyer(name: String = "Ana Reyes", id: Long = 0, email: String = "ana@example.com") = Buyer(
        id = id,
        fullName = name,
        contactNumber = "0917 123 4567",
        email = email,
        dateAdded = Instant.parse("2026-09-01T02:00:00Z"),
    )

    fun order(
        buyerId: Long,
        product: String = "Velvet Sofa",
        unit: String = "12500.50",
        qty: Int = 2,
        pay: PaymentStatus = PaymentStatus.PAID,
        stage: FulfillmentStatus = FulfillmentStatus.DELIVERED,
        mode: PaymentMode = PaymentMode.ONLINE_PAYMENT,
        id: Long = 0,
        at: String = "2026-09-20T03:00:00Z",
    ) = Order(
        id = id,
        buyerId = buyerId,
        productName = product,
        unitPrice = Money.of(unit),
        quantity = qty,
        purchaseDateTime = Instant.parse(at),
        paymentMode = mode,
        paymentStatus = pay,
        fulfillmentStatus = stage,
    )

    /** Two buyers, three orders, every status represented. */
    fun sampleSnapshot(): ExportSnapshot {
        val ana = buyer("Ana Reyes", id = 1)
        val ben = buyer("Ben Cruz", id = 2, email = "")
        val buyers = listOf(
            BuyerWithOrders(
                ana,
                listOf(
                    order(1, "Velvet Sofa", "12500.50", 2, PaymentStatus.PAID, FulfillmentStatus.DELIVERED, PaymentMode.ONLINE_PAYMENT, id = 10, at = "2026-09-20T03:00:00Z"),
                    order(1, "Side Table", "3500", 1, PaymentStatus.PENDING, FulfillmentStatus.PREPARING, PaymentMode.CASH, id = 11, at = "2026-10-01T03:00:00Z"),
                ),
            ),
            BuyerWithOrders(
                ben,
                listOf(order(2, "Floor Lamp", "999.99", 3, PaymentStatus.UNPAID, FulfillmentStatus.PROCESSING, PaymentMode.CASH, id = 12, at = "2026-09-25T03:00:00Z")),
            ),
        )
        val summary = ExportSummary(2, 3, 1, 1, 1, 1, 1, 1, Money.of("31500.97"))
        return ExportSnapshot(operator, buyers, summary, Instant.parse("2026-10-02T07:45:00Z"))
    }
}
