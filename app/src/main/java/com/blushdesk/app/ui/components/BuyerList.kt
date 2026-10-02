package com.blushdesk.app.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PersonSearch
import androidx.compose.material.icons.filled.People
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.blushdesk.app.data.local.database.BuyerListItem
import com.blushdesk.app.ui.theme.Dimens

/**
 * Adds the buyer list to a LazyColumn: the buyer count, one [BuyerListItemCard] per buyer, or an empty
 * state (no buyers yet, or nothing matches the search). Written as a LazyListScope extension so the
 * master pane can put its own header items above it in the same scrolling list.
 */
fun LazyListScope.buyerList(
    buyers: List<BuyerListItem>,
    loaded: Boolean,
    searching: Boolean,
    selectedBuyerId: Long?,
    onSelect: (Long) -> Unit,
) {
    when {
        !loaded -> item(key = "buyers-loading") { LoadingIndicator("Loading buyers…") }

        buyers.isEmpty() -> item(key = "buyers-empty") {
            EmptyState(
                icon = if (searching) Icons.Filled.PersonSearch else Icons.Filled.People,
                title = if (searching) "No matches" else "No buyers yet",
                message = if (searching) {
                    "No buyer has that name, phone number or email."
                } else {
                    "Tap Add buyer to record your first customer."
                },
            )
        }

        else -> {
            item(key = "buyers-count") {
                Text(
                    text = "BUYERS (${buyers.size})",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .padding(start = Dimens.spaceXs, top = Dimens.spaceS)
                        .semantics { heading() },
                )
            }
            items(buyers, key = { it.buyer.id }) { item ->
                BuyerListItemCard(
                    item = item,
                    selected = item.buyer.id == selectedBuyerId,
                    onClick = { onSelect(item.buyer.id) },
                )
            }
        }
    }
}
