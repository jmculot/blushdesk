package com.blushdesk.app.ui.order

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.unit.dp
import com.blushdesk.app.data.local.database.Order
import com.blushdesk.app.domain.model.FulfillmentStatus
import com.blushdesk.app.ui.components.FulfillmentProgress
import com.blushdesk.app.ui.components.FulfillmentStatusBadge
import com.blushdesk.app.ui.components.InlineMessage
import com.blushdesk.app.ui.components.OrderHeadline
import com.blushdesk.app.ui.components.PaymentStatusBadge
import com.blushdesk.app.ui.theme.Dimens
import com.blushdesk.app.utils.Formats

/** What an expanded order can ask for. */
class OrderActions(
    val onEdit: (Order) -> Unit,
    val onDelete: (Order) -> Unit,
    val onAdvance: (Order) -> Unit,
    val onSetFulfillment: (Order, FulfillmentStatus) -> Unit,
    val onMarkPaid: (Order) -> Unit,
    val onReceipt: (Order) -> Unit,
)

/**
 * The expanded order: purchase and payment details, the fulfillment progress with one-tap
 * updates, and the actions (mark paid, PDF receipt, edit, delete). The receipt button is only
 * enabled for paid orders, and says why when it is not.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OrderDetailCard(
    order: Order,
    receiptBusy: Boolean,
    anyReceiptBusy: Boolean,
    actions: OrderActions,
    modifier: Modifier = Modifier,
) {
    val paid = order.paymentStatus.isPaid

    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(Dimens.borderSelected, MaterialTheme.colorScheme.primary),
    ) {
        Column(modifier = Modifier.padding(Dimens.spaceXl), verticalArrangement = Arrangement.spacedBy(Dimens.spaceL)) {
            OrderHeadline(order)

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Dimens.spaceXxl),
                verticalArrangement = Arrangement.spacedBy(Dimens.spaceM),
            ) {
                Fact("Purchase date") { FactText(Formats.date(order.purchaseDateTime)) }
                Fact("Purchase time") { FactText(Formats.time(order.purchaseDateTime)) }
                Fact("Payment method") { FactText(order.paymentMode.label) }
                Fact("Payment status") { PaymentStatusBadge(order.paymentStatus) }
                Fact("Fulfillment status") { FulfillmentStatusBadge(order.fulfillmentStatus) }
            }

            Text(
                text = "ORDER LIFECYCLE",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.semantics { heading() },
            )
            FulfillmentProgress(current = order.fulfillmentStatus, onSelect = { actions.onSetFulfillment(order, it) })

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Dimens.spaceS),
                verticalArrangement = Arrangement.spacedBy(Dimens.spaceS),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                order.fulfillmentStatus.next?.let { next ->
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
                IconButton(onClick = { actions.onEdit(order) }) {
                    Icon(Icons.Filled.Edit, contentDescription = "Edit order ${order.productName}", tint = MaterialTheme.colorScheme.secondary)
                }
                IconButton(onClick = { actions.onDelete(order) }) {
                    Icon(Icons.Filled.Delete, contentDescription = "Delete order ${order.productName}", tint = MaterialTheme.colorScheme.error)
                }
            }
            if (!paid) {
                InlineMessage("The receipt unlocks once the payment is marked as paid.", isError = false)
            }
        }
    }
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
