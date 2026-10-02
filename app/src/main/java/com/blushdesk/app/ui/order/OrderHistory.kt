package com.blushdesk.app.ui.order

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.Modifier
import com.blushdesk.app.data.local.database.Order
import com.blushdesk.app.ui.components.OrderCard

/**
 * Adds a buyer's orders, newest first, to a LazyColumn. The order matching [expandedOrderId] is
 * shown in full ([OrderDetailCard]); the others are compact [OrderCard]s that expand when tapped.
 */
fun LazyListScope.orderHistory(
    orders: List<Order>,
    expandedOrderId: Long?,
    receiptOrderId: Long?,
    onSelectOrder: (Long) -> Unit,
    actions: OrderActions,
) {
    items(orders, key = { it.id }) { order ->
        if (order.id == expandedOrderId) {
            OrderDetailCard(
                order = order,
                receiptBusy = receiptOrderId == order.id,
                anyReceiptBusy = receiptOrderId != null,
                actions = actions,
                modifier = Modifier.animateItem(),
            )
        } else {
            OrderCard(order = order, onClick = { onSelectOrder(order.id) }, modifier = Modifier.animateItem())
        }
    }
}
