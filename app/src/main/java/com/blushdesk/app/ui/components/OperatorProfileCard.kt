package com.blushdesk.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Store
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.blushdesk.app.data.local.database.OperatorProfile
import com.blushdesk.app.ui.theme.Dimens
import com.blushdesk.app.ui.theme.ShowroomTheme

/**
 * The operator's card at the top of the home screen: photo, name, showroom, email and phone on the
 * brand gradient. The whole card (and the pencil) opens the profile editor.
 */
@Composable
fun OperatorProfileCard(operator: OperatorProfile, onEdit: () -> Unit, modifier: Modifier = Modifier) {
    val colors = ShowroomTheme.colors
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(Brush.linearGradient(listOf(colors.brandGradientStart, colors.brandGradientEnd)))
            .drawBehind {
                // A faint circle in the corner so the card reads as a designed surface, not a flat fill.
                drawCircle(colors.brandGlow, radius = size.height * 0.9f, center = Offset(size.width, 0f))
            }
            .clickable(onClickLabel = "Edit your profile", onClick = onEdit),
    ) {
        Row(
            modifier = Modifier.padding(start = Dimens.spaceL, top = Dimens.spaceL, bottom = Dimens.spaceL, end = Dimens.spaceS),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.spaceM),
        ) {
            Avatar(
                imageUri = operator.profileImageUri,
                name = operator.fullName.ifBlank { operator.storeName.ifBlank { "?" } },
                size = Dimens.avatarMedium,
                ring = BorderStroke(Dimens.avatarRing, colors.onBrandMuted),
            )
            Column(modifier = Modifier.weight(1f)) {
                if (operator.isSetUp) {
                    Text(
                        text = operator.fullName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = colors.onBrand,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    ProfileLine(Icons.Filled.Store, operator.storeName)
                    if (operator.email.isNotBlank()) ProfileLine(Icons.Filled.Email, operator.email)
                    if (operator.phoneNumber.isNotBlank()) ProfileLine(Icons.Filled.Phone, operator.phoneNumber)
                } else {
                    Text(
                        text = "Set up your profile",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = colors.onBrand,
                    )
                    Text(
                        text = "Add your name and showroom. They appear on every receipt.",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onBrandMuted,
                    )
                }
            }
            IconButton(onClick = onEdit) {
                Icon(Icons.Filled.Edit, contentDescription = "Edit your profile", tint = colors.onBrand)
            }
        }
    }
}

@Composable
private fun ProfileLine(icon: ImageVector, text: String) {
    val colors = ShowroomTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Dimens.spaceS)) {
        Icon(icon, contentDescription = null, tint = colors.onBrandMuted, modifier = Modifier.size(Dimens.iconSmall))
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = colors.onBrandMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
