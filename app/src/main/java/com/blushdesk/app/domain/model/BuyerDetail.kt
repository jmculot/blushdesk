package com.blushdesk.app.domain.model

import com.blushdesk.app.data.local.database.Buyer
import com.blushdesk.app.data.local.database.Order
import com.blushdesk.app.data.local.database.OrderTotals

/** The selected buyer, their orders (newest first) and the totals behind the dashboard cards. */
data class BuyerDetail(
    val buyer: Buyer,
    val orders: List<Order>,
    val totals: OrderTotals,
)
