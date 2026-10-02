package com.blushdesk.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.blushdesk.app.data.local.OrderStatus
import com.blushdesk.app.domain.BrandPalette
import com.blushdesk.app.ui.theme.ToneColors
import com.blushdesk.app.ui.theme.toColors

/** A small rounded label colored by [tone]; used for payment status, stage and payment mode. */
@Composable
fun StatusChip(
    label: String,
    tone: ToneColors,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
) {
    Surface(modifier = modifier, shape = CircleShape, color = tone.background, contentColor = tone.foreground) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (icon != null) Icon(icon, contentDescription = null, modifier = Modifier.size(14.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
        }
    }
}

/**
 * The Processing -> Preparing -> Delivered progression. Completed stages are filled and ticked,
 * the current stage glows, later stages are outlined. Every stage is tappable, so one tap sets the
 * order to that stage (forward to progress it, backward to undo a mis-tap).
 */
@Composable
fun OrderStepper(
    current: OrderStatus,
    onSelect: (OrderStatus) -> Unit,
    modifier: Modifier = Modifier,
) {
    val delivered = BrandPalette.tone(OrderStatus.DELIVERED).toColors().foreground
    val accent = if (current == OrderStatus.DELIVERED) delivered else MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.outlineVariant

    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        OrderStatus.entries.forEachIndexed { index, stage ->
            if (index > 0) {
                val reached = stage.ordinal <= current.ordinal
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(top = NODE_SIZE / 2 - 2.dp + 4.dp, start = 2.dp, end = 2.dp)
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(if (reached) accent else track),
                )
            }
            StepNode(stage = stage, current = current, accent = accent, onClick = { onSelect(stage) })
        }
    }
}

private val NODE_SIZE = 32.dp

@Composable
private fun StepNode(stage: OrderStatus, current: OrderStatus, accent: Color, onClick: () -> Unit) {
    val isCurrent = stage == current
    // A stage counts as done if it is behind the current one, or it is the final stage and current.
    val done = stage.ordinal < current.ordinal || (isCurrent && stage.next == null)

    Column(
        modifier = Modifier
            .width(96.dp)
            .clip(RoundedCornerShape(12.dp))
            .selectable(selected = isCurrent, role = Role.RadioButton, onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        when {
            done -> Box(Modifier.size(NODE_SIZE).clip(CircleShape).background(accent), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
            }

            isCurrent -> Box(
                Modifier.size(NODE_SIZE).clip(CircleShape).background(accent.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.size(20.dp).clip(CircleShape).background(accent), contentAlignment = Alignment.Center) {
                    Box(Modifier.size(7.dp).clip(CircleShape).background(Color.White))
                }
            }

            else -> Box(
                Modifier
                    .size(NODE_SIZE)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface)
                    .border(2.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "${stage.ordinal + 1}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Text(
            text = stage.label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
            color = if (isCurrent) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** One figure of the order dashboard: a small caption above a large value. */
@Composable
fun StatCard(
    label: String,
    value: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    accent: Color = MaterialTheme.colorScheme.primary,
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                Modifier.size(40.dp).clip(CircleShape).background(accent.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(22.dp))
            }
            Column {
                Text(
                    text = label.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}
