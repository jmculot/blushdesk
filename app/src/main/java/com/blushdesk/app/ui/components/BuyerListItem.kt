package com.blushdesk.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import com.blushdesk.app.data.local.database.BuyerListItem
import com.blushdesk.app.ui.theme.Dimens
import com.blushdesk.app.ui.theme.ShowroomTheme

/**
 * One buyer in the list: photo, name, contact number, order count, and the fulfillment and payment
 * status of their latest order. The selected buyer gets the pink container and a rose outline.
 */
@Composable
fun BuyerListItemCard(
    item: BuyerListItem,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Gray text sits on pink when selected, where the plain muted gray would be too faint.
    val secondaryText = if (selected) ShowroomTheme.colors.mutedOnTint else MaterialTheme.colorScheme.onSurfaceVariant

    Surface(
        selected = selected,
        onClick = onClick,
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            width = if (selected) Dimens.borderSelected else Dimens.border,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Dimens.spaceM, vertical = Dimens.spaceM),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.spaceM),
        ) {
            Avatar(imageUri = item.buyer.profileImageUri, name = item.buyer.fullName, size = Dimens.avatarSmall)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.buyer.fullName,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = item.buyer.contactNumber,
                    style = MaterialTheme.typography.bodySmall,
                    color = secondaryText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = when (item.orderCount) {
                        0 -> "No orders yet"
                        1 -> "1 order"
                        else -> "${item.orderCount} orders"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = secondaryText,
                )
            }
            val fulfillment = item.latestFulfillmentStatus
            val payment = item.latestPaymentStatus
            if (fulfillment != null && payment != null) {
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(Dimens.spaceXs)) {
                    FulfillmentStatusBadge(fulfillment)
                    PaymentStatusBadge(payment)
                }
            }
        }
    }
}
