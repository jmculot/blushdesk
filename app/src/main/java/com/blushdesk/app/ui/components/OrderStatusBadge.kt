package com.blushdesk.app.ui.components

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
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.blushdesk.app.domain.model.FulfillmentStatus
import com.blushdesk.app.ui.theme.Dimens
import com.blushdesk.app.ui.theme.ShowroomTheme

/** Processing (blue), Preparing (amber) or Delivered (green), each with its own icon. */
@Composable
fun FulfillmentStatusBadge(status: FulfillmentStatus, modifier: Modifier = Modifier) {
    StatusBadge(
        label = status.label,
        tone = ShowroomTheme.colors.tone(status),
        icon = when (status) {
            FulfillmentStatus.PROCESSING -> Icons.Filled.Autorenew
            FulfillmentStatus.PREPARING -> Icons.Filled.Inventory2
            FulfillmentStatus.DELIVERED -> Icons.Filled.TaskAlt
        },
        modifier = modifier.semantics(mergeDescendants = true) { contentDescription = "Fulfillment ${status.label}" },
    )
}

/**
 * The Processing -> Preparing -> Delivered progression. Finished stages are filled and ticked,
 * the current one glows, later ones are outlined, so the current state is obvious at a glance.
 *
 * Each stage is a radio button: one tap sets the order to it. Moving forward one step at a time is
 * what the "Move to ..." button does; tapping a stage directly is an explicit choice by the user
 * (for example to correct a mis-tap), never an automatic skip.
 */
@Composable
fun FulfillmentProgress(
    current: FulfillmentStatus,
    onSelect: (FulfillmentStatus) -> Unit,
    modifier: Modifier = Modifier,
) {
    val delivered = ShowroomTheme.colors.tone(FulfillmentStatus.DELIVERED).foreground
    val accent = if (current == FulfillmentStatus.DELIVERED) delivered else MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.outlineVariant

    Row(
        modifier = modifier
            .fillMaxWidth()
            .selectableGroup(),
        verticalAlignment = Alignment.Top,
    ) {
        FulfillmentStatus.entries.forEachIndexed { index, stage ->
            if (index > 0) {
                val reached = stage.ordinal <= current.ordinal
                Box(
                    modifier = Modifier
                        .weight(1f)
                        // Centers the track on the node: node top padding + half the node - half the track.
                        .padding(top = Dimens.spaceXs + Dimens.stepNode / 2 - Dimens.stepTrack / 2, start = 2.dp, end = 2.dp)
                        .height(Dimens.stepTrack)
                        .clip(CircleShape)
                        .background(if (reached) accent else track),
                )
            }
            StepNode(stage = stage, current = current, accent = accent, onClick = { onSelect(stage) })
        }
    }
}

@Composable
private fun StepNode(stage: FulfillmentStatus, current: FulfillmentStatus, accent: Color, onClick: () -> Unit) {
    val isCurrent = stage == current
    // Done = behind the current stage, or the final stage once reached.
    val done = stage.ordinal < current.ordinal || (isCurrent && stage.next == null)
    val state = when {
        isCurrent -> "current stage"
        done -> "completed"
        else -> "not started, tap to set"
    }

    Column(
        modifier = Modifier
            .width(Dimens.stepWidth)
            .clip(RoundedCornerShape(Dimens.spaceM))
            .selectable(selected = isCurrent, role = Role.RadioButton, onClick = onClick)
            .semantics { stateDescription = state }
            .padding(vertical = Dimens.spaceXs),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Dimens.spaceXs),
    ) {
        when {
            done -> Box(
                Modifier.size(Dimens.stepNode).clip(CircleShape).background(accent),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(Dimens.iconMedium),
                )
            }

            isCurrent -> Box(
                Modifier.size(Dimens.stepNode).clip(CircleShape).background(accent.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier.size(Dimens.stepNodeInner).clip(CircleShape).background(accent),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(Modifier.size(Dimens.stepNodeDot).clip(CircleShape).background(MaterialTheme.colorScheme.onPrimary))
                }
            }

            else -> Box(
                Modifier
                    .size(Dimens.stepNode)
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
