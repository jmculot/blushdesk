package com.blushdesk.app.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType

/**
 * The app's one single-line text field: label, optional leading icon or prefix, and an error line
 * that appears under it. Pass `error = null` to show nothing (callers hide errors until the user
 * has pressed Save once, so an empty form does not open covered in red).
 */
@Composable
fun FormField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    error: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Next,
    capitalization: KeyboardCapitalization = KeyboardCapitalization.Words,
    leadingIcon: ImageVector? = null,
    prefix: String? = null,
    maxLength: Int? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { next -> if (maxLength == null || next.length <= maxLength) onValueChange(next) },
        modifier = modifier.fillMaxWidth(),
        label = { Text(label) },
        singleLine = true,
        isError = error != null,
        supportingText = error?.let { message -> { Text(message) } },
        leadingIcon = leadingIcon?.let { icon -> { Icon(icon, contentDescription = null) } },
        prefix = prefix?.let { text -> { Text(text) } },
        keyboardOptions = KeyboardOptions(
            keyboardType = keyboardType,
            imeAction = imeAction,
            capitalization = capitalization,
        ),
    )
}
