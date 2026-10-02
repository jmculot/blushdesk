package com.blushdesk.app.ui.buyer

import androidx.compose.runtime.Composable
import com.blushdesk.app.data.local.database.Buyer
import com.blushdesk.app.ui.components.PhotoActions

/** Edits an existing buyer's name, contact number, email and photo. */
@Composable
fun EditBuyerDialog(
    buyer: Buyer,
    photoActions: PhotoActions,
    onSave: (Buyer) -> Unit,
    onDismiss: () -> Unit,
) = BuyerFormDialog(initial = buyer, photoActions = photoActions, onSave = onSave, onDismiss = onDismiss)
