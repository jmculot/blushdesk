package com.blushdesk.app.data.local.relation

import androidx.room.Embedded
import androidx.room.Relation
import com.blushdesk.app.data.local.entity.Buyer
import com.blushdesk.app.data.local.entity.Order

/** A buyer with every order they have placed. Used for the detail pane and the Excel export. */
data class BuyerWithOrders(
    @Embedded val buyer: Buyer,
    @Relation(parentColumn = "id", entityColumn = "buyerId")
    val orders: List<Order>,
)

/** One row of the buyer list: the buyer plus the two numbers worth showing at a glance. */
data class BuyerListItem(
    @Embedded val buyer: Buyer,
    val orderCount: Int,
    /** Total of every order that is not yet paid. */
    val outstandingMinor: Long,
)

/** Store-wide totals shown under the operator card. */
data class ShowroomSummary(
    val buyerCount: Int,
    val openOrders: Int,
    val paidMinor: Long,
    val outstandingMinor: Long,
) {
    companion object {
        val EMPTY = ShowroomSummary(buyerCount = 0, openOrders = 0, paidMinor = 0, outstandingMinor = 0)
    }
}
