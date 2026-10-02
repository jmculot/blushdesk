package com.blushdesk.app.ui.order

import androidx.compose.runtime.Composable
import com.blushdesk.app.data.local.database.Order

/** Edits an existing order; the total is recalculated from the new price and quantity. */
@Composable
fun EditOrderDialog(
    order: Order,
    buyerName: String,
    onSave: (Order) -> Unit,
    onDismiss: () -> Unit,
) = OrderFormDialog(buyerId = order.buyerId, buyerName = buyerName, initial = order, onSave = onSave, onDismiss = onDismiss)
