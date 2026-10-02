package com.blushdesk.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.blushdesk.app.data.local.PaymentStatus
import com.blushdesk.app.data.local.entity.Buyer
import com.blushdesk.app.data.local.entity.Order
import com.blushdesk.app.data.storage.AppFiles
import com.blushdesk.app.domain.Formats
import com.blushdesk.app.export.ReceiptDocument
import com.blushdesk.app.ui.components.ConfirmDialog
import com.blushdesk.app.ui.components.PhotoActions
import com.blushdesk.app.ui.dialogs.BuyerEditorDialog
import com.blushdesk.app.ui.dialogs.EditOperatorProfileDialog
import com.blushdesk.app.ui.dialogs.OrderEditorDialog
import com.blushdesk.app.ui.dialogs.ReceiptReadyDialog
import com.blushdesk.app.ui.panes.DetailActions
import com.blushdesk.app.ui.panes.DetailPane
import com.blushdesk.app.ui.panes.MasterPane
import kotlinx.coroutines.launch

/** Which modal, if any, is open. Only one at a time, so a single value is enough. */
private sealed interface ActiveDialog {
    data object None : ActiveDialog
    data object EditProfile : ActiveDialog

    /** [buyer] = null means "add a new buyer". */
    data class BuyerForm(val buyer: Buyer?) : ActiveDialog

    /** [order] = null means "add a new order for the selected buyer". */
    data class OrderForm(val order: Order?) : ActiveDialog
    data object ConfirmDeleteBuyer : ActiveDialog
    data class ConfirmDeleteOrder(val order: Order) : ActiveDialog
    data class ReceiptReady(val receipt: ReceiptDocument) : ActiveDialog
}

/** Windows narrower than this show one pane at a time instead of two. */
private val DUAL_PANE_MIN_WIDTH = 600.dp

/** Below this height the search and quick actions scroll with the buyer list instead of staying pinned. */
private val PIN_TOOLS_MIN_HEIGHT = 520.dp

/**
 * The whole app: a master-detail screen. On a tablet (600dp and wider) both panes are visible side
 * by side; on a narrow window (phone, split screen) it falls back to one pane at a time with a back
 * arrow, so the layout still works when Android refuses to keep the app in landscape.
 */
@Composable
fun ShowroomTabletScreen(viewModel: ShowroomViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    var dialog by remember { mutableStateOf<ActiveDialog>(ActiveDialog.None) }
    var showDetailWhenCompact by rememberSaveable { mutableStateOf(false) }
    var askedForProfile by rememberSaveable { mutableStateOf(false) }

    val photoActions = remember(viewModel) {
        PhotoActions(importPhoto = viewModel::importPhoto, discard = viewModel::discardPhoto)
    }

    fun toast(text: String) {
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            snackbar.showSnackbar(text)
        }
    }

    // One-shot events from the ViewModel. Each snackbar runs in its own coroutine so a message that
    // is still on screen never delays the receipt dialog that follows it.
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is UiEvent.Message -> toast(event.text)
                is UiEvent.ReceiptReady -> dialog = ActiveDialog.ReceiptReady(event.receipt)
                is UiEvent.ShareWorkbook -> {
                    val shared = Sharing.share(
                        context = context,
                        file = event.export.file,
                        mimeType = AppFiles.MIME_XLSX,
                        subject = "BlushDesk export ${event.export.displayName}",
                        chooserTitle = "Share Excel export",
                    )
                    if (!shared) toast("No app on this device can receive the Excel file.")
                }
            }
        }
    }

    // First launch: ask for the operator's details once, since every receipt needs them.
    LaunchedEffect(state.operatorLoaded, state.operator.isSetUp) {
        if (state.operatorLoaded && !state.operator.isSetUp && !askedForProfile) {
            askedForProfile = true
            dialog = ActiveDialog.EditProfile
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val compact = maxWidth < DUAL_PANE_MIN_WIDTH
        val masterWidth = when {
            maxWidth >= 1000.dp -> 400.dp
            maxWidth >= 840.dp -> 360.dp
            else -> 320.dp
        }
        val detailOnly = compact && showDetailWhenCompact

        BackHandler(enabled = detailOnly) { showDetailWhenCompact = false }

        val actions = DetailActions(
            onEditBuyer = { state.detail?.buyer?.let { dialog = ActiveDialog.BuyerForm(it) } },
            onDeleteBuyer = { dialog = ActiveDialog.ConfirmDeleteBuyer },
            onAddOrder = { if (state.detail != null) dialog = ActiveDialog.OrderForm(null) },
            onEditOrder = { dialog = ActiveDialog.OrderForm(it) },
            onDeleteOrder = { dialog = ActiveDialog.ConfirmDeleteOrder(it) },
            onAdvance = viewModel::advanceOrder,
            onSetStage = { order, stage -> viewModel.setOrderStatus(order.id, stage) },
            onMarkPaid = { viewModel.setPaymentStatus(it.id, PaymentStatus.PAID) },
            onReceipt = { viewModel.generateReceipt(it.id) },
            onAddBuyer = { dialog = ActiveDialog.BuyerForm(null) },
        )

        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            snackbarHost = { SnackbarHost(snackbar) },
            topBar = {
                ShowroomTopBar(
                    subtitle = state.operator.storeName.ifBlank { "Showroom manager" },
                    showBack = detailOnly,
                    onBack = { showDetailWhenCompact = false },
                    compact = compact,
                    exporting = state.exporting,
                    onExport = {
                        if (state.summary.buyerCount == 0) {
                            toast("There is nothing to export yet. Add a buyer first.")
                        } else {
                            viewModel.exportToExcel()
                        }
                    },
                )
            },
        ) { padding ->
            val master: @Composable (Modifier) -> Unit = { modifier ->
                MasterPane(
                    state = state,
                    onQueryChange = viewModel::onQueryChange,
                    onSelectBuyer = {
                        viewModel.selectBuyer(it)
                        showDetailWhenCompact = true
                    },
                    onEditProfile = { dialog = ActiveDialog.EditProfile },
                    onAddBuyer = { dialog = ActiveDialog.BuyerForm(null) },
                    onAddOrder = {
                        if (state.detail != null) {
                            dialog = ActiveDialog.OrderForm(null)
                            showDetailWhenCompact = true
                        }
                    },
                    pinTools = maxHeight >= PIN_TOOLS_MIN_HEIGHT,
                    modifier = modifier,
                )
            }
            val detail: @Composable (Modifier) -> Unit = { modifier ->
                DetailPane(
                    detail = state.detail,
                    hasBuyers = state.buyers.isNotEmpty() || state.summary.buyerCount > 0,
                    receiptOrderId = state.receiptOrderId,
                    compact = compact,
                    actions = actions,
                    modifier = modifier,
                )
            }

            Box(modifier = Modifier.padding(padding).fillMaxSize()) {
                when {
                    compact && showDetailWhenCompact -> detail(Modifier.fillMaxSize())
                    compact -> master(Modifier.fillMaxSize())
                    else -> Row(modifier = Modifier.fillMaxSize()) {
                        master(Modifier.width(masterWidth).fillMaxHeight())
                        VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        detail(Modifier.weight(1f).fillMaxHeight())
                    }
                }
            }
        }
    }

    when (val active = dialog) {
        ActiveDialog.None -> Unit

        ActiveDialog.EditProfile -> EditOperatorProfileDialog(
            initial = state.operator,
            photoActions = photoActions,
            onSave = {
                viewModel.saveOperator(it)
                dialog = ActiveDialog.None
            },
            onDismiss = { dialog = ActiveDialog.None },
        )

        is ActiveDialog.BuyerForm -> BuyerEditorDialog(
            initial = active.buyer,
            photoActions = photoActions,
            onSave = {
                viewModel.saveBuyer(it)
                showDetailWhenCompact = true
                dialog = ActiveDialog.None
            },
            onDismiss = { dialog = ActiveDialog.None },
        )

        is ActiveDialog.OrderForm -> state.detail?.buyer?.let { buyer ->
            OrderEditorDialog(
                buyerId = buyer.id,
                buyerName = buyer.fullName,
                initial = active.order,
                onSave = {
                    viewModel.saveOrder(it)
                    dialog = ActiveDialog.None
                },
                onDismiss = { dialog = ActiveDialog.None },
            )
        }

        ActiveDialog.ConfirmDeleteBuyer -> state.detail?.let { detail ->
            val orders = detail.orders.size
            ConfirmDialog(
                title = "Delete ${detail.buyer.fullName}?",
                message = if (orders == 0) {
                    "This buyer will be removed. This cannot be undone."
                } else {
                    "This also deletes their $orders order${if (orders == 1) "" else "s"}. This cannot be undone."
                },
                confirmLabel = "Delete",
                destructive = true,
                onConfirm = {
                    viewModel.deleteBuyer(detail.buyer.id)
                    showDetailWhenCompact = false
                    dialog = ActiveDialog.None
                },
                onDismiss = { dialog = ActiveDialog.None },
            )
        }

        is ActiveDialog.ConfirmDeleteOrder -> ConfirmDialog(
            title = "Delete this order?",
            message = "${active.order.productName} (${Formats.orderNumber(active.order.id)}) will be removed. This cannot be undone.",
            confirmLabel = "Delete",
            destructive = true,
            onConfirm = {
                viewModel.deleteOrder(active.order.id)
                dialog = ActiveDialog.None
            },
            onDismiss = { dialog = ActiveDialog.None },
        )

        is ActiveDialog.ReceiptReady -> ReceiptReadyDialog(
            receipt = active.receipt,
            onOpen = {
                if (!Sharing.view(context, active.receipt.file, AppFiles.MIME_PDF)) {
                    toast("No PDF viewer is installed. Use Share instead.")
                }
            },
            onShare = {
                val shared = Sharing.share(
                    context = context,
                    file = active.receipt.file,
                    mimeType = AppFiles.MIME_PDF,
                    subject = "Receipt ${active.receipt.displayName}",
                    chooserTitle = "Share receipt",
                )
                if (!shared) toast("No app on this device can receive the receipt.")
            },
            onDismiss = { dialog = ActiveDialog.None },
        )
    }
}

/** App bar with the brand, the store name, and the prominent "Export to Excel" action. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ShowroomTopBar(
    subtitle: String,
    showBack: Boolean,
    onBack: () -> Unit,
    compact: Boolean,
    exporting: Boolean,
    onExport: () -> Unit,
) {
    TopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        navigationIcon = {
            if (showBack) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to buyers")
                }
            }
        },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.ShoppingBag,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Column(modifier = Modifier.padding(start = 12.dp)) {
                    Text("BlushDesk", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.secondary)
                    if (!compact) {
                        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        },
        actions = {
            Button(onClick = onExport, enabled = !exporting, modifier = Modifier.padding(end = 16.dp)) {
                if (exporting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Icon(Icons.Filled.FileDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                }
                Text(if (exporting) "  Exporting…" else if (compact) "  Export" else "  Export to Excel")
            }
        },
    )
}
