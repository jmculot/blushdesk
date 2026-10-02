package com.blushdesk.app.ui.dialogs

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import com.blushdesk.app.data.local.entity.Buyer
import com.blushdesk.app.domain.Formats
import com.blushdesk.app.domain.Validation
import com.blushdesk.app.ui.components.FormDialog
import com.blushdesk.app.ui.components.FormField
import com.blushdesk.app.ui.components.PhotoActions
import com.blushdesk.app.ui.components.PhotoPickerField
import kotlinx.coroutines.launch
import java.time.Instant

/** Add a buyer ([initial] = null) or edit an existing one. Date added is set once and never edited. */
@Composable
fun BuyerEditorDialog(
    initial: Buyer?,
    photoActions: PhotoActions,
    onSave: (Buyer) -> Unit,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var fullName by rememberSaveable { mutableStateOf(initial?.fullName.orEmpty()) }
    var contact by rememberSaveable { mutableStateOf(initial?.contact.orEmpty()) }
    var email by rememberSaveable { mutableStateOf(initial?.email.orEmpty()) }
    var photoPath by rememberSaveable { mutableStateOf(initial?.photoPath) }
    var stagedPhoto by rememberSaveable { mutableStateOf<String?>(null) }
    var attempted by rememberSaveable { mutableStateOf(false) }

    val nameError = Validation.name(fullName, "Buyer name")
    val contactError = Validation.phone(contact)
    val emailError = Validation.email(email)
    val valid = listOf(nameError, contactError, emailError).all { it == null }

    fun cancel() {
        photoActions.discard(stagedPhoto)
        onDismiss()
    }

    FormDialog(
        title = if (initial == null) "Add buyer" else "Edit buyer",
        confirmLabel = if (initial == null) "Add buyer" else "Save",
        onDismiss = ::cancel,
        onConfirm = {
            attempted = true
            if (valid) {
                stagedPhoto = null
                onSave(
                    Buyer(
                        id = initial?.id ?: 0L,
                        fullName = fullName,
                        contact = contact,
                        email = email,
                        dateAdded = initial?.dateAdded ?: Instant.now(),
                        photoPath = photoPath,
                    ),
                )
            }
        },
    ) {
        PhotoPickerField(
            photoPath = photoPath,
            name = fullName,
            onPicked = { uri ->
                scope.launch {
                    val imported = photoActions.importPhoto(uri) ?: return@launch
                    photoActions.discard(stagedPhoto)
                    stagedPhoto = imported
                    photoPath = imported
                }
            },
            onRemove = {
                photoActions.discard(stagedPhoto)
                stagedPhoto = null
                photoPath = null
            },
        )
        FormField(
            value = fullName,
            onValueChange = { fullName = it },
            label = "Full name",
            leadingIcon = Icons.Filled.Person,
            error = if (attempted) nameError else null,
            maxLength = Validation.MAX_NAME,
        )
        FormField(
            value = contact,
            onValueChange = { contact = it },
            label = "Contact number",
            leadingIcon = Icons.Filled.Phone,
            keyboardType = KeyboardType.Phone,
            error = if (attempted) contactError else null,
            maxLength = 24,
        )
        FormField(
            value = email,
            onValueChange = { email = it },
            label = "Email (optional)",
            leadingIcon = Icons.Filled.Email,
            keyboardType = KeyboardType.Email,
            capitalization = KeyboardCapitalization.None,
            imeAction = ImeAction.Done,
            error = if (attempted) emailError else null,
        )
        Text(
            text = if (initial == null) {
                "The date added is recorded automatically."
            } else {
                "Added on ${Formats.date(initial.dateAdded)}"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
