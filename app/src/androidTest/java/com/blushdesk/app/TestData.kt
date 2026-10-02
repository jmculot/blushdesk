package com.blushdesk.app

import com.blushdesk.app.data.local.OrderStatus
import com.blushdesk.app.data.local.PaymentMode
import com.blushdesk.app.data.local.PaymentStatus
import com.blushdesk.app.data.local.entity.Buyer
import com.blushdesk.app.data.local.entity.OperatorProfile
import com.blushdesk.app.data.local.entity.Order
import com.blushdesk.app.data.local.relation.BuyerWithOrders
import java.time.Instant

/** Shared fixtures for the instrumented tests. */
object TestData {
    val operator = OperatorProfile(
        fullName = "Lia Santos",
        storeName = "Rosé Showroom",
        email = "lia@rose.example",
        phone = "0917 555 0100",
    )

    fun buyer(name: String = "Ana Reyes", id: Long = 0, email: String = "ana@example.com") = Buyer(
        id = id,
        fullName = name,
        contact = "0917 123 4567",
        email = email,
        dateAdded = Instant.parse("2026-09-01T02:00:00Z"),
    )

    fun order(
        buyerId: Long,
        product: String = "Velvet Sofa",
        unitMinor: Long = 1_250_050,
        qty: Int = 2,
        pay: PaymentStatus = PaymentStatus.PAID,
        stage: OrderStatus = OrderStatus.DELIVERED,
        mode: PaymentMode = PaymentMode.ONLINE,
        id: Long = 0,
    ) = Order(
        id = id,
        buyerId = buyerId,
        productName = product,
        unitPriceMinor = unitMinor,
        quantity = qty,
        purchasedAt = Instant.parse("2026-09-20T03:00:00Z"),
        paymentMode = mode,
        paymentStatus = pay,
        orderStatus = stage,
    )

    /** Two buyers, three orders, every status represented at least once. */
    fun sampleExport(): List<BuyerWithOrders> {
        val ana = buyer("Ana Reyes", id = 1)
        val ben = buyer("Ben Cruz", id = 2, email = "")
        return listOf(
            BuyerWithOrders(
                ana,
                listOf(
                    order(1, "Velvet Sofa", 1_250_050, 2, PaymentStatus.PAID, OrderStatus.DELIVERED, PaymentMode.ONLINE, id = 10),
                    order(1, "Side Table", 350_000, 1, PaymentStatus.PENDING, OrderStatus.PREPARING, PaymentMode.CASH, id = 11),
                ),
            ),
            BuyerWithOrders(
                ben,
                listOf(order(2, "Floor Lamp", 99_999, 3, PaymentStatus.UNPAID, OrderStatus.PROCESSING, PaymentMode.CASH, id = 12)),
            ),
        )
    }
}
