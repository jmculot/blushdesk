package com.blushdesk.app.domain.model

import com.blushdesk.app.data.local.database.Buyer
import com.blushdesk.app.data.local.database.OrderTotals
import com.blushdesk.app.data.local.database.OrderWithItems

/** The selected buyer, their orders (newest first) and the totals behind the dashboard cards. */
data class BuyerDetail(
    val buyer: Buyer,
    val orders: List<OrderWithItems>,
    val totals: OrderTotals,
)
