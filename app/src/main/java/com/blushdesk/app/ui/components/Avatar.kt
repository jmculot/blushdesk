package com.blushdesk.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.blushdesk.app.domain.Formats
import java.io.File

/**
 * A circular profile picture. The initials are always drawn underneath, so a missing file, a
 * slow decode or a person without a photo all show something sensible instead of a blank circle.
 */
@Composable
fun Avatar(
    photoPath: String?,
    name: String,
    size: Dp,
    modifier: Modifier = Modifier,
    ring: BorderStroke? = null,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer)
            .then(if (ring != null) Modifier.border(ring, CircleShape) else Modifier)
            .semantics { contentDescription = "Photo of $name" },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = Formats.initials(name),
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            fontWeight = FontWeight.Bold,
            fontSize = (size.value * 0.36f).sp,
        )
        if (photoPath != null) {
            AsyncImage(
                model = File(photoPath),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
