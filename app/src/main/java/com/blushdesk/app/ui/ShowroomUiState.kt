package com.blushdesk.app.ui

import com.blushdesk.app.data.local.entity.Buyer
import com.blushdesk.app.data.local.entity.OperatorProfile
import com.blushdesk.app.data.local.entity.Order
import com.blushdesk.app.data.local.relation.BuyerListItem
import com.blushdesk.app.data.local.relation.BuyerWithOrders
import com.blushdesk.app.data.local.relation.ShowroomSummary
import com.blushdesk.app.export.ExportDocument
import com.blushdesk.app.export.ReceiptDocument

/** Figures for the "order dashboard" cards at the top of the detail pane. */
data class BuyerStats(
    val orderCount: Int,
    val openOrders: Int,
    val paidMinor: Long,
    val outstandingMinor: Long,
)

/** The selected buyer, with orders newest-first, and the numbers derived from them. */
data class BuyerDetail(
    val buyer: Buyer,
    val orders: List<Order>,
    val stats: BuyerStats,
)

fun BuyerWithOrders.toDetail(): BuyerDetail {
    val sorted = orders.sortedWith(compareByDescending<Order> { it.purchasedAt }.thenByDescending { it.id })
    return BuyerDetail(
        buyer = buyer,
        orders = sorted,
        stats = BuyerStats(
            orderCount = sorted.size,
            openOrders = sorted.count { it.orderStatus.next != null },
            paidMinor = sorted.filter { it.paymentStatus.isPaid }.sumOf { it.totalMinor },
            outstandingMinor = sorted.filterNot { it.paymentStatus.isPaid }.sumOf { it.totalMinor },
        ),
    )
}

/** Everything the screen renders, in one immutable snapshot. */
data class ShowroomUiState(
    val operator: OperatorProfile = OperatorProfile(),
    /** False until the first database read, so the first-run profile prompt does not flash. */
    val operatorLoaded: Boolean = false,
    val summary: ShowroomSummary = ShowroomSummary.EMPTY,
    val query: String = "",
    val buyers: List<BuyerListItem> = emptyList(),
    val buyersLoaded: Boolean = false,
    val selectedBuyerId: Long? = null,
    val detail: BuyerDetail? = null,
    val exporting: Boolean = false,
    /** Id of the order whose receipt is being generated right now, if any. */
    val receiptOrderId: Long? = null,
)

/** One-shot things the screen must do once and not repeat after a recomposition. */
sealed interface UiEvent {
    data class Message(val text: String) : UiEvent
    data class ReceiptReady(val receipt: ReceiptDocument) : UiEvent
    data class ShareWorkbook(val export: ExportDocument) : UiEvent
}
