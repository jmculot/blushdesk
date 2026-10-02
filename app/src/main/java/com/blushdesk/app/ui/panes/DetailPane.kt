package com.blushdesk.app.ui.panes

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Paid
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.ShoppingBag
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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.blushdesk.app.data.local.OrderStatus
import com.blushdesk.app.data.local.PaymentMode
import com.blushdesk.app.data.local.PaymentStatus
import com.blushdesk.app.data.local.entity.Buyer
import com.blushdesk.app.data.local.entity.Order
import com.blushdesk.app.domain.BrandPalette
import com.blushdesk.app.domain.Formats
import com.blushdesk.app.domain.Money
import com.blushdesk.app.ui.BuyerDetail
import com.blushdesk.app.ui.BuyerStats
import com.blushdesk.app.ui.components.Avatar
import com.blushdesk.app.ui.components.OrderStepper
import com.blushdesk.app.ui.components.StatCard
import com.blushdesk.app.ui.components.StatusChip
import com.blushdesk.app.ui.theme.ToneColors
import com.blushdesk.app.ui.theme.toColors

/** Everything the detail pane can ask the screen to do, bundled so the composables stay readable. */
class DetailActions(
    val onEditBuyer: () -> Unit,
    val onDeleteBuyer: () -> Unit,
    val onAddOrder: () -> Unit,
    val onEditOrder: (Order) -> Unit,
    val onDeleteOrder: (Order) -> Unit,
    val onAdvance: (Order) -> Unit,
    val onSetStage: (Order, OrderStatus) -> Unit,
    val onMarkPaid: (Order) -> Unit,
    val onReceipt: (Order) -> Unit,
    val onAddBuyer: () -> Unit,
)

/**
 * The right pane: the selected buyer's profile, a dashboard of their numbers, and their order
 * history with lifecycle controls and PDF receipts.
 */
@Composable
fun DetailPane(
    detail: BuyerDetail?,
    hasBuyers: Boolean,
    receiptOrderId: Long?,
    compact: Boolean,
    actions: DetailActions,
    modifier: Modifier = Modifier,
) {
    if (detail == null) {
        EmptyDetail(hasBuyers = hasBuyers, onAddBuyer = actions.onAddBuyer, modifier = modifier)
        return
    }

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(if (compact) 16.dp else 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item(key = "header") { BuyerHeader(detail.buyer, actions) }
        item(key = "stats") { StatsRow(detail.stats, compact) }
        item(key = "ordersHeader") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Order history",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.weight(1f),
                )
                Button(onClick = actions.onAddOrder) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("  New order")
                }
            }
        }
        if (detail.orders.isEmpty()) {
            item(key = "noOrders") { NoOrders(actions.onAddOrder) }
        } else {
            items(detail.orders, key = { it.id }) { order ->
                OrderCard(order = order, receiptBusy = receiptOrderId == order.id, anyReceiptBusy = receiptOrderId != null, actions = actions)
            }
        }
    }
}

// ---- Buyer header -------------------------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BuyerHeader(buyer: Buyer, actions: DetailActions) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Avatar(
                photoPath = buyer.photoPath,
                name = buyer.fullName,
                size = 88.dp,
                ring = BorderStroke(3.dp, MaterialTheme.colorScheme.primaryContainer),
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = buyer.fullName,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.secondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                InfoLine(Icons.Filled.Phone, buyer.contact)
                if (buyer.email.isNotBlank()) InfoLine(Icons.Filled.Email, buyer.email)
                InfoLine(Icons.Filled.CalendarMonth, "Customer since ${Formats.date(buyer.dateAdded)}")
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                OutlinedButton(onClick = actions.onEditBuyer) {
                    Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("  Edit")
                }
                TextButton(onClick = actions.onDeleteBuyer) {
                    Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error)
                    Text("  Delete", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
private fun InfoLine(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

// ---- Dashboard ----------------------------------------------------------------------------------

@Composable
private fun StatsRow(stats: BuyerStats, compact: Boolean) {
    val paidTone = BrandPalette.tone(PaymentStatus.PAID).toColors().foreground
    val dueTone = if (stats.outstandingMinor > 0) {
        BrandPalette.tone(PaymentStatus.UNPAID).toColors().foreground
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    val cards = listOf<@Composable (Modifier) -> Unit>(
        { StatCard("Orders", stats.orderCount.toString(), Icons.Filled.ShoppingBag, it) },
        { StatCard("Open orders", stats.openOrders.toString(), Icons.Filled.LocalShipping, it, accent = MaterialTheme.colorScheme.tertiary) },
        { StatCard("Paid", Money.format(stats.paidMinor), Icons.Filled.Paid, it, accent = paidTone) },
        { StatCard("Outstanding", Money.format(stats.outstandingMinor), Icons.Filled.Payments, it, accent = dueTone) },
    )
    if (compact) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            cards.chunked(2).forEach { pair ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { pair.forEach { it(Modifier.weight(1f)) } }
            }
        }
    } else {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { cards.forEach { it(Modifier.weight(1f)) } }
    }
}

// ---- Orders -------------------------------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OrderCard(order: Order, receiptBusy: Boolean, anyReceiptBusy: Boolean, actions: DetailActions) {
    val paid = order.paymentStatus.isPaid
    val neutral = ToneColors(MaterialTheme.colorScheme.onSurfaceVariant, MaterialTheme.colorScheme.surfaceContainerHigh)

    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = order.productName,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "${order.quantity} × ${Money.format(order.unitPriceMinor)}  ·  ${Formats.orderNumber(order.id)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = Money.format(order.totalMinor),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }

            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusChip(Formats.dateTime(order.purchasedAt), neutral, icon = Icons.Filled.Schedule)
                StatusChip(
                    label = order.paymentMode.label,
                    tone = neutral,
                    icon = if (order.paymentMode == PaymentMode.CASH) Icons.Filled.Payments else Icons.Filled.CreditCard,
                )
                StatusChip(order.paymentStatus.label, BrandPalette.tone(order.paymentStatus).toColors())
            }

            OrderStepper(current = order.orderStatus, onSelect = { actions.onSetStage(order, it) })

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                order.orderStatus.next?.let { next ->
                    FilledTonalButton(onClick = { actions.onAdvance(order) }) {
                        Text("Move to ${next.label}")
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.padding(start = 6.dp).size(18.dp))
                    }
                }
                if (!paid) {
                    OutlinedButton(onClick = { actions.onMarkPaid(order) }) { Text("Mark as paid") }
                }
                Button(onClick = { actions.onReceipt(order) }, enabled = paid && !anyReceiptBusy) {
                    if (receiptBusy) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    } else {
                        Icon(Icons.Filled.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                    Text(if (receiptBusy) "  Creating…" else "  Receipt (PDF)")
                }
                IconButton(onClick = { actions.onEditOrder(order) }) {
                    Icon(Icons.Filled.Edit, contentDescription = "Edit order ${order.productName}", tint = MaterialTheme.colorScheme.secondary)
                }
                IconButton(onClick = { actions.onDeleteOrder(order) }) {
                    Icon(Icons.Filled.Delete, contentDescription = "Delete order ${order.productName}", tint = MaterialTheme.colorScheme.error)
                }
            }
            if (!paid) {
                Text(
                    text = "Receipts are available once the order is marked as paid.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun NoOrders(onAddOrder: () -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("No orders yet", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.secondary)
            Text(
                "Record this buyer's first purchase to start tracking payment and delivery.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FilledTonalButton(onClick = onAddOrder) { Text("Add the first order") }
        }
    }
}

@Composable
private fun EmptyDetail(hasBuyers: Boolean, onAddBuyer: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Person, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(48.dp))
            }
            Text(
                text = if (hasBuyers) "Select a buyer" else "Welcome to BlushDesk",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.secondary,
            )
            Text(
                text = if (hasBuyers) {
                    "Pick someone from the list to see their orders."
                } else {
                    "Add your first buyer to start recording orders, receipts and exports."
                },
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!hasBuyers) {
                Button(onClick = onAddBuyer) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("  Add buyer")
                }
            }
        }
    }
}
