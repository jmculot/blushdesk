package com.blushdesk.app.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.blushdesk.app.domain.model.PaymentStatus
import com.blushdesk.app.ui.theme.ShowroomTheme

/** Unpaid (red), Pending (amber) or Paid (green), each with its own icon. */
@Composable
fun PaymentStatusBadge(status: PaymentStatus, modifier: Modifier = Modifier) {
    StatusBadge(
        label = status.label,
        tone = ShowroomTheme.colors.tone(status),
        icon = when (status) {
            PaymentStatus.UNPAID -> Icons.Filled.ErrorOutline
            PaymentStatus.PENDING -> Icons.Filled.HourglassTop
            PaymentStatus.PAID -> Icons.Filled.CheckCircle
        },
        modifier = modifier.semantics(mergeDescendants = true) { contentDescription = "Payment ${status.label}" },
    )
}
