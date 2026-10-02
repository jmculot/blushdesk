package com.blushdesk.app.ui.panes

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Store
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.blushdesk.app.data.local.PaymentStatus
import com.blushdesk.app.data.local.entity.OperatorProfile
import com.blushdesk.app.data.local.relation.BuyerListItem
import com.blushdesk.app.data.local.relation.ShowroomSummary
import com.blushdesk.app.domain.BrandPalette
import com.blushdesk.app.domain.Formats
import com.blushdesk.app.domain.Money
import com.blushdesk.app.ui.ShowroomUiState
import com.blushdesk.app.ui.components.Avatar
import com.blushdesk.app.ui.components.StatusChip
import com.blushdesk.app.ui.theme.ShowroomColors
import com.blushdesk.app.ui.theme.toColors

/**
 * The left pane: who is operating the tablet, how the shop is doing, a search box, quick actions
 * and the buyer list. Everything lives in one LazyColumn so that on a short screen the profile card
 * scrolls away and the list gets the whole height; the search and actions stay pinned at the top
 * when [pinTools] is true.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MasterPane(
    state: ShowroomUiState,
    onQueryChange: (String) -> Unit,
    onSelectBuyer: (Long) -> Unit,
    onEditProfile: () -> Unit,
    onAddBuyer: () -> Unit,
    onAddOrder: () -> Unit,
    modifier: Modifier = Modifier,
    pinTools: Boolean = true,
) {
    // The search box and quick actions: pinned under the top bar on a tall window, but part of the
    // scrolling list on a short one, where pinning would leave no room for the buyers themselves.
    val tools: @Composable () -> Unit = {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .padding(vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SearchField(state.query, onQueryChange)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onAddBuyer, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("  Add buyer", maxLines = 1)
                }
                FilledTonalButton(
                    onClick = onAddOrder,
                    enabled = state.detail != null,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("  New order", maxLines = 1)
                }
            }
        }
    }

    LazyColumn(
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceContainerLow),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "profile") { OperatorProfileCard(state.operator, onEditProfile) }
        item(key = "summary") {
            SummaryStrip(state.summary, modifier = Modifier.padding(top = 4.dp, bottom = 4.dp))
        }
        if (pinTools) {
            stickyHeader(key = "tools") { tools() }
        } else {
            item(key = "tools") { tools() }
        }

        when {
            !state.buyersLoaded -> Unit
            state.buyers.isEmpty() -> item(key = "empty") { EmptyBuyers(searching = state.query.isNotBlank()) }
            else -> {
                item(key = "count") {
                    Text(
                        text = "BUYERS (${state.buyers.size})",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp, top = 8.dp),
                    )
                }
                items(state.buyers, key = { it.buyer.id }) { item ->
                    BuyerRow(item, selected = item.buyer.id == state.selectedBuyerId, onClick = { onSelectBuyer(item.buyer.id) })
                }
            }
        }
    }
}

// ---- Operator profile card ----------------------------------------------------------------------

@Composable
private fun OperatorProfileCard(operator: OperatorProfile, onEdit: () -> Unit) {
    val gradient = Brush.linearGradient(listOf(ShowroomColors.DeepMagenta, Color(0xFFC2185B)))
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(gradient)
            .drawBehind {
                // A faint circle in the corner so the card reads as a designed surface, not a flat fill.
                drawCircle(Color.White.copy(alpha = 0.08f), radius = size.height * 0.9f, center = Offset(size.width, 0f))
            }
            .clickable(onClickLabel = "Edit your profile", onClick = onEdit),
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 16.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Avatar(
                photoPath = operator.photoPath,
                name = operator.fullName.ifBlank { operator.storeName.ifBlank { "?" } },
                size = 72.dp,
                ring = BorderStroke(3.dp, Color.White.copy(alpha = 0.85f)),
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                if (operator.isSetUp) {
                    Text(
                        text = operator.fullName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    ProfileLine(Icons.Filled.Store, operator.storeName)
                    if (operator.email.isNotBlank()) ProfileLine(Icons.Filled.Email, operator.email)
                    if (operator.phone.isNotBlank()) ProfileLine(Icons.Filled.Phone, operator.phone)
                } else {
                    Text(
                        text = "Set up your profile",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    )
                    Text(
                        text = "Add your name and showroom. They appear on every receipt.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.9f),
                    )
                }
            }
            IconButton(onClick = onEdit) {
                Icon(Icons.Filled.Edit, contentDescription = "Edit your profile", tint = Color.White)
            }
        }
    }
}

@Composable
private fun ProfileLine(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(icon, contentDescription = null, tint = Color.White.copy(alpha = 0.85f), modifier = Modifier.size(14.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.92f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

// ---- Summary ------------------------------------------------------------------------------------

@Composable
private fun SummaryStrip(summary: ShowroomSummary, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        // Weights follow the caption and value widths ('OPEN ORDERS' is the longest caption).
        MiniStat("Buyers", summary.buyerCount.toString(), Modifier.weight(0.8f))
        MiniStat("Open orders", summary.openOrders.toString(), Modifier.weight(1.25f))
        MiniStat("To collect", Money.format(summary.outstandingMinor), Modifier.weight(1.45f))
    }
}

@Composable
private fun MiniStat(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Text(
                text = label.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.secondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

// ---- Search and list ----------------------------------------------------------------------------

@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit) {
    val focus = LocalFocusManager.current
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier.fillMaxWidth(),
        placeholder = { Text("Search name, phone or email") },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Filled.Close, contentDescription = "Clear search")
                }
            }
        },
        singleLine = true,
        shape = CircleShape,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }),
    )
}

@Composable
private fun BuyerRow(item: BuyerListItem, selected: Boolean, onClick: () -> Unit) {
    Surface(
        selected = selected,
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLowest,
        border = BorderStroke(
            width = if (selected) 1.5.dp else 1.dp,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Avatar(photoPath = item.buyer.photoPath, name = item.buyer.fullName, size = 44.dp)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.buyer.fullName,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = when (item.orderCount) {
                        0 -> "No orders yet"
                        1 -> "1 order"
                        else -> "${item.orderCount} orders"
                    } + "  ·  added " + Formats.date(item.buyer.dateAdded),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (item.outstandingMinor > 0) {
                StatusChip(
                    label = Money.format(item.outstandingMinor),
                    tone = BrandPalette.tone(PaymentStatus.PENDING).toColors(),
                )
            }
        }
    }
}

@Composable
private fun EmptyBuyers(searching: Boolean) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = if (searching) "No buyers match your search" else "No buyers yet",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.secondary,
        )
        Text(
            text = if (searching) "Try a different name, phone number or email." else "Tap Add buyer to record your first customer.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
    }
}
