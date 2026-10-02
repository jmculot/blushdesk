package com.blushdesk.app.ui.order

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.Modifier
import com.blushdesk.app.data.local.database.OrderWithItems
import com.blushdesk.app.ui.components.OrderCard

/**
 * Adds a buyer's orders, newest first, to a LazyColumn. The order matching [expandedOrderId] is
 * shown in full ([OrderDetailCard]); the others are compact [OrderCard]s that expand when tapped.
 */
fun LazyListScope.orderHistory(
    orders: List<OrderWithItems>,
    expandedOrderId: Long?,
    receiptOrderId: Long?,
    onSelectOrder: (Long) -> Unit,
    actions: OrderActions,
) {
    items(orders, key = { it.order.id }) { order ->
        if (order.order.id == expandedOrderId) {
            OrderDetailCard(
                order = order,
                receiptBusy = receiptOrderId == order.order.id,
                anyReceiptBusy = receiptOrderId != null,
                actions = actions,
                modifier = Modifier.animateItem(),
            )
        } else {
            OrderCard(order = order, onClick = { onSelectOrder(order.order.id) }, modifier = Modifier.animateItem())
        }
    }
}
