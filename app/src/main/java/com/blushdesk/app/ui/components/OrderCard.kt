package com.blushdesk.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.blushdesk.app.data.local.database.Order
import com.blushdesk.app.ui.theme.Dimens
import com.blushdesk.app.ui.theme.ShowroomTheme
import com.blushdesk.app.utils.Formats
import com.blushdesk.app.utils.Money

/** Product, "qty x price · receipt number", and the total. Shared by the compact and detailed cards. */
@Composable
fun OrderHeadline(order: Order, modifier: Modifier = Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(Dimens.spaceM)) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = order.productName,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${order.quantity} × ${Money.format(order.unitPrice)}  ·  ${Formats.orderNumber(order.id)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        AmountText(
            text = Money.format(order.totalAmount),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.secondary,
        )
    }
}

/**
 * A collapsed entry of the order history: what was bought, when, and its two statuses. Tapping it
 * opens the full [com.blushdesk.app.ui.order.OrderDetailCard].
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OrderCard(order: Order, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        modifier = modifier.semantics { onClick(label = "Show order details") { onClick(); true } },
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(Dimens.border, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(modifier = Modifier.padding(Dimens.spaceL), verticalArrangement = Arrangement.spacedBy(Dimens.spaceM)) {
            OrderHeadline(order)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Dimens.spaceS), verticalArrangement = Arrangement.spacedBy(Dimens.spaceS)) {
                StatusBadge(Formats.dateTime(order.purchaseDateTime), ShowroomTheme.colors.neutralBadge, icon = Icons.Filled.Schedule)
                FulfillmentStatusBadge(order.fulfillmentStatus)
                PaymentStatusBadge(order.paymentStatus)
            }
        }
    }
}
