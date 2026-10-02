package com.blushdesk.app.ui.dialogs

import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.blushdesk.app.data.local.OrderStatus
import com.blushdesk.app.data.local.PaymentMode
import com.blushdesk.app.data.local.PaymentStatus
import com.blushdesk.app.data.local.entity.Order
import com.blushdesk.app.domain.Formats
import com.blushdesk.app.domain.Money
import com.blushdesk.app.domain.Validation
import com.blushdesk.app.ui.components.FormDialog
import com.blushdesk.app.ui.components.FormField
import com.blushdesk.app.ui.components.SegmentedChoice
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset

private val PRICE_INPUT = Regex("""^\d{0,10}(\.\d{0,2})?$""")

/**
 * Create ([initial] = null) or edit an order for [buyerName]. The total beneath the price and
 * quantity fields recalculates on every keystroke, so the operator can read it back to the
 * customer before saving.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrderEditorDialog(
    buyerId: Long,
    buyerName: String,
    initial: Order?,
    onSave: (Order) -> Unit,
    onDismiss: () -> Unit,
) {
    val zone = remember { ZoneId.systemDefault() }

    var product by rememberSaveable { mutableStateOf(initial?.productName.orEmpty()) }
    var priceText by rememberSaveable { mutableStateOf(initial?.let { Money.toInputString(it.unitPriceMinor) }.orEmpty()) }
    var qtyText by rememberSaveable { mutableStateOf((initial?.quantity ?: 1).toString()) }
    var purchasedAtMillis by rememberSaveable { mutableLongStateOf((initial?.purchasedAt ?: Instant.now()).toEpochMilli()) }
    var mode by rememberSaveable { mutableStateOf(initial?.paymentMode ?: PaymentMode.CASH) }
    var payment by rememberSaveable { mutableStateOf(initial?.paymentStatus ?: PaymentStatus.PENDING) }
    var stage by rememberSaveable { mutableStateOf(initial?.orderStatus ?: OrderStatus.PROCESSING) }
    var attempted by rememberSaveable { mutableStateOf(false) }
    var showDatePicker by rememberSaveable { mutableStateOf(false) }
    var showTimePicker by rememberSaveable { mutableStateOf(false) }

    val local = Instant.ofEpochMilli(purchasedAtMillis).atZone(zone).toLocalDateTime()

    val productError = Validation.product(product)
    val priceError = Validation.unitPrice(priceText)
    val qtyError = Validation.quantity(qtyText)
    val unitMinor = Money.parse(priceText)
    val quantity = qtyText.trim().toIntOrNull()
    val totalMinor = if (priceError == null && qtyError == null && unitMinor != null && quantity != null) {
        unitMinor * quantity
    } else {
        null
    }

    FormDialog(
        title = if (initial == null) "New order for $buyerName" else "Edit order",
        confirmLabel = if (initial == null) "Add order" else "Save",
        onDismiss = onDismiss,
        onConfirm = {
            attempted = true
            if (productError == null && totalMinor != null && unitMinor != null && quantity != null) {
                onSave(
                    Order(
                        id = initial?.id ?: 0L,
                        buyerId = buyerId,
                        productName = product,
                        unitPriceMinor = unitMinor,
                        quantity = quantity,
                        purchasedAt = Instant.ofEpochMilli(purchasedAtMillis),
                        paymentMode = mode,
                        paymentStatus = payment,
                        orderStatus = stage,
                    ),
                )
            }
        },
    ) {
        FormField(
            value = product,
            onValueChange = { product = it },
            label = "Product name",
            leadingIcon = Icons.Filled.Inventory2,
            error = if (attempted) productError else null,
            maxLength = Validation.MAX_PRODUCT,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
            FormField(
                value = priceText,
                onValueChange = { if (PRICE_INPUT.matches(it)) priceText = it },
                label = "Unit price",
                modifier = Modifier.weight(1f),
                prefix = Money.SYMBOL,
                keyboardType = KeyboardType.Decimal,
                error = if (attempted) priceError else null,
            )
            QuantityField(
                value = qtyText,
                onValueChange = { text -> if (text.length <= 4 && text.all { it.isDigit() }) qtyText = text },
                error = if (attempted) qtyError else null,
                modifier = Modifier.weight(1f),
            )
        }

        TotalCard(totalMinor = totalMinor, quantity = quantity.takeIf { totalMinor != null }, unitMinor = unitMinor.takeIf { totalMinor != null })

        Text("Purchase date and time", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = { showDatePicker = true }, modifier = Modifier.weight(1f)) {
                Icon(Icons.Filled.CalendarMonth, contentDescription = null, modifier = Modifier.size(18.dp))
                Text("  " + Formats.date(local))
            }
            OutlinedButton(onClick = { showTimePicker = true }, modifier = Modifier.weight(1f)) {
                Icon(Icons.Filled.Schedule, contentDescription = null, modifier = Modifier.size(18.dp))
                Text("  " + Formats.time(local))
            }
        }

        Text("Payment mode", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary)
        SegmentedChoice(PaymentMode.entries, mode, { it.label }, { mode = it })

        Text("Payment status", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary)
        SegmentedChoice(PaymentStatus.entries, payment, { it.label }, { payment = it })
        if (payment.isPaid) {
            Text(
                "Paid orders can have a PDF receipt generated.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Text("Order stage", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary)
        SegmentedChoice(OrderStatus.entries, stage, { it.label }, { stage = it })
    }

    if (showDatePicker) {
        // The Material date picker works in UTC midnights, so convert the local date both ways.
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = local.toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { picked ->
                        val date = Instant.ofEpochMilli(picked).atZone(ZoneOffset.UTC).toLocalDate()
                        purchasedAtMillis = date.atTime(local.toLocalTime()).atZone(zone).toInstant().toEpochMilli()
                    }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancel") } },
        ) {
            DatePicker(state = pickerState)
        }
    }

    if (showTimePicker) {
        TimeChooserDialog(
            initial = local.toLocalTime(),
            is24Hour = DateFormat.is24HourFormat(LocalContext.current),
            onConfirm = { time ->
                purchasedAtMillis = local.toLocalDate().atTime(time).atZone(zone).toInstant().toEpochMilli()
                showTimePicker = false
            },
            onDismiss = { showTimePicker = false },
        )
    }
}

/** Number field flanked by - and + buttons, for quantities where tapping beats typing. */
@Composable
private fun QuantityField(
    value: String,
    onValueChange: (String) -> Unit,
    error: String?,
    modifier: Modifier = Modifier,
) {
    val current = value.toIntOrNull() ?: 1
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        label = { Text("Quantity") },
        singleLine = true,
        isError = error != null,
        supportingText = error?.let { message -> { Text(message) } },
        textStyle = MaterialTheme.typography.bodyLarge.copy(textAlign = TextAlign.Center),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
        leadingIcon = {
            IconButton(
                onClick = { onValueChange(maxOf(1, current - 1).toString()) },
                enabled = current > 1,
            ) { Icon(Icons.Filled.Remove, contentDescription = "Decrease quantity") }
        },
        trailingIcon = {
            IconButton(
                onClick = { onValueChange(minOf(Validation.MAX_QUANTITY, current + 1).toString()) },
                enabled = current < Validation.MAX_QUANTITY,
            ) { Icon(Icons.Filled.Add, contentDescription = "Increase quantity") }
        },
    )
}

/** The live "quantity x price = total" read-out. Announced politely by screen readers as it changes. */
@Composable
private fun TotalCard(totalMinor: Long?, quantity: Int?, unitMinor: Long?) {
    Surface(
        modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.primaryContainer,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("TOTAL", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                Text(
                    text = if (quantity != null && unitMinor != null) "$quantity × ${Money.format(unitMinor)}" else "Enter price and quantity",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = totalMinor?.let { Money.format(it) } ?: "—",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

/** Material's TimePicker needs a dialog around it; this one sizes itself to the picker. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeChooserDialog(
    initial: LocalTime,
    is24Hour: Boolean,
    onConfirm: (LocalTime) -> Unit,
    onDismiss: () -> Unit,
) {
    val state = rememberTimePickerState(initialHour = initial.hour, initialMinute = initial.minute, is24Hour = is24Hour)
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier.width(IntrinsicSize.Min),
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    "Select time",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.padding(bottom = 16.dp),
                )
                TimePicker(state = state)
                Row(modifier = Modifier.fillMaxWidth()) {
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    TextButton(onClick = { onConfirm(LocalTime.of(state.hour, state.minute)) }) { Text("OK") }
                }
            }
        }
    }
}
