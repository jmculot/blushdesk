package com.blushdesk.app.data.local.database

import androidx.room.Embedded
import androidx.room.Relation
import com.blushdesk.app.domain.model.FulfillmentStatus
import com.blushdesk.app.domain.model.PaymentStatus
import java.math.BigDecimal

/** An order with its product lines, loaded by Room in one transaction. */
data class OrderWithItems(
    @Embedded val order: Order,
    @Relation(parentColumn = "id", entityColumn = "orderId")
    private val itemRows: List<OrderItem>,
) {
    /** The lines in the order the operator entered them (a Relation does not sort). */
    val items: List<OrderItem> get() = itemRows.sortedWith(compareBy({ it.position }, { it.id }))

    /** Number of units across all lines. */
    val unitCount: Int get() = itemRows.sumOf { it.quantity }
}

/** A buyer with all of their orders and each order's items. Used by the Excel export. */
data class BuyerWithOrders(
    @Embedded val buyer: Buyer,
    @Relation(entity = Order::class, parentColumn = "id", entityColumn = "buyerId")
    val orders: List<OrderWithItems>,
)

/** One row of the buyer list: the buyer, how many orders they have, and their latest order's state. */
data class BuyerListItem(
    @Embedded val buyer: Buyer,
    val orderCount: Int,
    /** Status of the most recent purchase, or null if the buyer has no orders yet. */
    val latestFulfillmentStatus: FulfillmentStatus?,
    val latestPaymentStatus: PaymentStatus?,
)

/** Store-wide figures for the strip under the operator card. */
data class ShowroomSummary(
    val buyerCount: Int,
    val orderCount: Int,
    val openOrders: Int,
    val paidAmount: BigDecimal,
    val outstandingAmount: BigDecimal,
)

/** Totals for one buyer, calculated in SQL. Drives the dashboard cards of the detail pane. */
data class OrderTotals(
    val orderCount: Int,
    val openOrders: Int,
    val paidAmount: BigDecimal,
    val outstandingAmount: BigDecimal,
    val totalAmount: BigDecimal,
)

/** Everything the Excel "Summary" sheet reports, counted in one query. */
data class ExportSummary(
    val totalBuyers: Int,
    val totalOrders: Int,
    val paidOrders: Int,
    val unpaidOrders: Int,
    val pendingOrders: Int,
    val processingOrders: Int,
    val preparingOrders: Int,
    val deliveredOrders: Int,
    val totalRecordedSales: BigDecimal,
)
