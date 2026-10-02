package com.blushdesk.app.ui.order

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.blushdesk.app.data.local.database.OrderWithItems
import com.blushdesk.app.domain.model.FulfillmentStatus
import com.blushdesk.app.ui.components.FulfillmentProgress
import com.blushdesk.app.ui.components.FulfillmentStatusBadge
import com.blushdesk.app.ui.components.InlineMessage
import com.blushdesk.app.ui.components.OrderHeadline
import com.blushdesk.app.ui.components.PaymentStatusBadge
import com.blushdesk.app.ui.theme.Dimens
import com.blushdesk.app.utils.Formats
import com.blushdesk.app.utils.Money

/** What an expanded order can ask for. */
class OrderActions(
    val onEdit: (OrderWithItems) -> Unit,
    val onDelete: (OrderWithItems) -> Unit,
    val onAdvance: (OrderWithItems) -> Unit,
    val onSetFulfillment: (OrderWithItems, FulfillmentStatus) -> Unit,
    val onMarkPaid: (OrderWithItems) -> Unit,
    val onReceipt: (OrderWithItems) -> Unit,
)

/**
 * The expanded order: its product lines, purchase and payment details, the fulfillment progress
 * with one-tap updates, and the actions (mark paid, PDF receipt, edit, delete). The receipt button
 * is only enabled for paid orders, and says why when it is not.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OrderDetailCard(
    order: OrderWithItems,
    receiptBusy: Boolean,
    anyReceiptBusy: Boolean,
    actions: OrderActions,
    modifier: Modifier = Modifier,
) {
    val details = order.order
    val paid = details.paymentStatus.isPaid

    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(Dimens.borderSelected, MaterialTheme.colorScheme.primary),
    ) {
        Column(modifier = Modifier.padding(Dimens.spaceXl), verticalArrangement = Arrangement.spacedBy(Dimens.spaceL)) {
            OrderHeadline(order)

            ItemsTable(order)

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Dimens.spaceXxl),
                verticalArrangement = Arrangement.spacedBy(Dimens.spaceM),
            ) {
                Fact("Purchase date") { FactText(Formats.date(details.purchaseDateTime)) }
                Fact("Purchase time") { FactText(Formats.time(details.purchaseDateTime)) }
                Fact("Payment method") { FactText(details.paymentMode.label) }
                Fact("Payment status") { PaymentStatusBadge(details.paymentStatus) }
                Fact("Fulfillment status") { FulfillmentStatusBadge(details.fulfillmentStatus) }
            }

            Caption("ORDER LIFECYCLE")
            FulfillmentProgress(current = details.fulfillmentStatus, onSelect = { actions.onSetFulfillment(order, it) })

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Dimens.spaceS),
                verticalArrangement = Arrangement.spacedBy(Dimens.spaceS),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                details.fulfillmentStatus.next?.let { next ->
                    FilledTonalButton(onClick = { actions.onAdvance(order) }) {
                        Text("Move to ${next.label}")
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            modifier = Modifier.padding(start = Dimens.spaceS).size(Dimens.iconMedium),
                        )
                    }
                }
                if (!paid) {
                    OutlinedButton(onClick = { actions.onMarkPaid(order) }) { Text("Mark as paid") }
                }
                Button(onClick = { actions.onReceipt(order) }, enabled = paid && !anyReceiptBusy) {
                    if (receiptBusy) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(Dimens.iconMedium),
                            strokeWidth = Dimens.progressStroke,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    } else {
                        Icon(Icons.Filled.PictureAsPdf, contentDescription = null, modifier = Modifier.size(Dimens.iconMedium))
                    }
                    Text(if (receiptBusy) "  Creating…" else "  Paid receipt (PDF)")
                }
                val label = Formats.itemsSummary(order.items.map { it.productName })
                IconButton(onClick = { actions.onEdit(order) }) {
                    Icon(Icons.Filled.Edit, contentDescription = "Edit order $label", tint = MaterialTheme.colorScheme.secondary)
                }
                IconButton(onClick = { actions.onDelete(order) }) {
                    Icon(Icons.Filled.Delete, contentDescription = "Delete order $label", tint = MaterialTheme.colorScheme.error)
                }
            }
            if (!paid) {
                InlineMessage("The receipt unlocks once the payment is marked as paid.", isError = false)
            }
        }
    }
}

/** Every product line: name, quantity × unit price, and the line total; then the order total. */
@Composable
private fun ItemsTable(order: OrderWithItems) {
    Column(verticalArrangement = Arrangement.spacedBy(Dimens.spaceS)) {
        Caption("ITEMS (${order.items.size})")
        order.items.forEach { item ->
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(Dimens.spaceM)) {
                Text(
                    text = item.productName,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "${item.quantity} × ${Money.format(item.unitPrice)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.End,
                    modifier = Modifier.widthIn(min = 140.dp),
                )
                Text(
                    text = Money.format(item.lineTotal),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.End,
                    modifier = Modifier.width(130.dp),
                )
            }
        }
        if (order.items.size > 1) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Order total",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    Money.format(order.order.totalAmount),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }
        }
    }
}

@Composable
private fun Caption(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.semantics { heading() },
    )
}

/** A small caption above a value. */
@Composable
private fun Fact(label: String, value: @Composable () -> Unit) {
    Column(modifier = Modifier.widthIn(min = 120.dp), verticalArrangement = Arrangement.spacedBy(Dimens.spaceXs)) {
        Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        value()
    }
}

@Composable
private fun FactText(text: String) {
    Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
}
