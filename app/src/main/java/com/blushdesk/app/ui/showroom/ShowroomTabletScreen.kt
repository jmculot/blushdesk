package com.blushdesk.app.ui.showroom

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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.blushdesk.app.data.local.database.Buyer
import com.blushdesk.app.data.local.database.OrderWithItems
import com.blushdesk.app.domain.model.PaymentStatus
import com.blushdesk.app.ui.buyer.AddBuyerDialog
import com.blushdesk.app.ui.buyer.BuyerDetailActions
import com.blushdesk.app.ui.buyer.BuyerDetailScreen
import com.blushdesk.app.ui.buyer.EditBuyerDialog
import com.blushdesk.app.ui.components.DeleteConfirmationDialog
import com.blushdesk.app.ui.components.LoadingIndicator
import com.blushdesk.app.ui.components.PhotoActions
import com.blushdesk.app.ui.components.ShowroomSnackbarHost
import com.blushdesk.app.ui.components.show
import com.blushdesk.app.ui.export.ExportDialog
import com.blushdesk.app.ui.operator.EditOperatorProfileDialog
import com.blushdesk.app.ui.order.AddOrderDialog
import com.blushdesk.app.ui.order.EditOrderDialog
import com.blushdesk.app.ui.order.OrderActions
import com.blushdesk.app.ui.order.ReceiptReadyDialog
import com.blushdesk.app.ui.theme.Dimens
import com.blushdesk.app.utils.AppFiles
import com.blushdesk.app.utils.Formats
import com.blushdesk.app.utils.ReceiptDocument
import com.blushdesk.app.utils.Sharing
import kotlinx.coroutines.launch

/** Which modal, if any, is open. Only one at a time, so a single value is enough. */
private sealed interface ActiveDialog {
    data object None : ActiveDialog
    data object EditProfile : ActiveDialog
    data object AddBuyer : ActiveDialog
    data class EditBuyer(val buyer: Buyer) : ActiveDialog
    data object AddOrder : ActiveDialog
    data class EditOrder(val order: OrderWithItems) : ActiveDialog
    data object DeleteBuyer : ActiveDialog
    data class DeleteOrder(val order: OrderWithItems) : ActiveDialog
    data object Export : ActiveDialog
    data class ReceiptReady(val receipt: ReceiptDocument) : ActiveDialog
}

/**
 * The app's one screen: an adaptive master-detail layout. At 600dp and wider both panes show side
 * by side; narrower windows (phone, split screen, Android 16+ ignoring the landscape request) show
 * one pane at a time with a back arrow.
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

    fun share(file: java.io.File, mime: String, subject: String, title: String, noAppMessage: String) {
        if (!Sharing.share(context, file, mime, subject, title)) viewModel.reportError(noAppMessage)
    }

    // One-shot events. Each snackbar runs in its own coroutine so a message still on screen never
    // delays the receipt dialog or share sheet that follows it.
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is UiEvent.Success -> scope.launch { snackbar.show(event.message, isError = false) }
                is UiEvent.Error -> scope.launch { snackbar.show(event.message, isError = true) }
                is UiEvent.ReceiptReady -> dialog = ActiveDialog.ReceiptReady(event.receipt)
                is UiEvent.ShareWorkbook -> {
                    if (dialog == ActiveDialog.Export) dialog = ActiveDialog.None
                    share(
                        file = event.export.file,
                        mime = AppFiles.MIME_XLSX,
                        subject = "BlushDesk export ${event.export.displayName}",
                        title = "Share Excel export",
                        noAppMessage = "No app on this tablet can receive the Excel file.",
                    )
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
        val compact = maxWidth < Dimens.dualPaneMinWidth
        val masterWidth = when {
            maxWidth >= Dimens.masterPaneWideFrom -> Dimens.masterPaneWide
            maxWidth >= Dimens.masterPaneMediumFrom -> Dimens.masterPaneMedium
            else -> Dimens.masterPaneCompact
        }
        val detailOnly = compact && showDetailWhenCompact
        BackHandler(enabled = detailOnly) { showDetailWhenCompact = false }

        val detailActions = BuyerDetailActions(
            onEditBuyer = { state.detail?.buyer?.let { dialog = ActiveDialog.EditBuyer(it) } },
            onDeleteBuyer = { dialog = ActiveDialog.DeleteBuyer },
            onAddOrder = { if (state.detail != null) dialog = ActiveDialog.AddOrder },
            onAddBuyer = { dialog = ActiveDialog.AddBuyer },
            onSelectOrder = viewModel::selectOrder,
            order = OrderActions(
                onEdit = { dialog = ActiveDialog.EditOrder(it) },
                onDelete = { dialog = ActiveDialog.DeleteOrder(it) },
                onAdvance = { viewModel.advanceOrder(it.order) },
                onSetFulfillment = { order, status -> viewModel.setFulfillmentStatus(order.order.id, status) },
                onMarkPaid = { viewModel.setPaymentStatus(it.order.id, PaymentStatus.PAID) },
                onReceipt = { viewModel.generateReceipt(it.order.id) },
            ),
        )

        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            snackbarHost = { ShowroomSnackbarHost(snackbar) },
            topBar = {
                ShowroomTopBar(
                    subtitle = state.operator.storeName.ifBlank { "Showroom manager" },
                    showBack = detailOnly,
                    onBack = { showDetailWhenCompact = false },
                    compact = compact,
                    onExport = { dialog = ActiveDialog.Export },
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
                    onAddBuyer = { dialog = ActiveDialog.AddBuyer },
                    onAddOrder = {
                        if (state.detail != null) {
                            dialog = ActiveDialog.AddOrder
                            showDetailWhenCompact = true
                        }
                    },
                    pinTools = maxHeight >= Dimens.pinToolsMinHeight,
                    modifier = modifier,
                )
            }
            val detail: @Composable (Modifier) -> Unit = { modifier ->
                if (state.isLoading) {
                    Box(modifier, contentAlignment = Alignment.Center) { LoadingIndicator("Opening your showroom…") }
                } else {
                    BuyerDetailScreen(
                        detail = state.detail,
                        hasBuyers = state.summary.buyerCount > 0,
                        expandedOrderId = state.expandedOrderId,
                        receiptOrderId = state.receiptOrderId,
                        compact = compact,
                        actions = detailActions,
                        modifier = modifier,
                    )
                }
            }

            Box(modifier = Modifier.padding(padding).fillMaxSize()) {
                when {
                    detailOnly -> detail(Modifier.fillMaxSize())
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

    val dismiss = { dialog = ActiveDialog.None }
    when (val active = dialog) {
        ActiveDialog.None -> Unit

        ActiveDialog.EditProfile -> EditOperatorProfileDialog(
            initial = state.operator,
            photoActions = photoActions,
            onSave = {
                viewModel.saveOperator(it)
                dismiss()
            },
            onDismiss = dismiss,
        )

        ActiveDialog.AddBuyer -> AddBuyerDialog(
            photoActions = photoActions,
            onSave = {
                viewModel.saveBuyer(it)
                showDetailWhenCompact = true
                dismiss()
            },
            onDismiss = dismiss,
        )

        is ActiveDialog.EditBuyer -> EditBuyerDialog(
            buyer = active.buyer,
            photoActions = photoActions,
            onSave = {
                viewModel.saveBuyer(it)
                dismiss()
            },
            onDismiss = dismiss,
        )

        ActiveDialog.AddOrder -> state.detail?.buyer?.let { buyer ->
            AddOrderDialog(
                buyerId = buyer.id,
                buyerName = buyer.fullName,
                onSave = { order, items ->
                    viewModel.saveOrder(order, items)
                    dismiss()
                },
                onDismiss = dismiss,
            )
        }

        is ActiveDialog.EditOrder -> EditOrderDialog(
            order = active.order,
            buyerName = state.detail?.buyer?.fullName.orEmpty(),
            onSave = { order, items ->
                viewModel.saveOrder(order, items)
                dismiss()
            },
            onDismiss = dismiss,
        )

        ActiveDialog.DeleteBuyer -> state.detail?.let { detail ->
            val orders = detail.orders.size
            DeleteConfirmationDialog(
                title = "Delete ${detail.buyer.fullName}?",
                message = if (orders == 0) {
                    "This buyer will be removed. This cannot be undone."
                } else {
                    "Their $orders order${if (orders == 1) "" else "s"} will be deleted too. This cannot be undone."
                },
                onConfirm = {
                    viewModel.deleteBuyer(detail.buyer.id)
                    showDetailWhenCompact = false
                    dismiss()
                },
                onDismiss = dismiss,
            )
        }

        is ActiveDialog.DeleteOrder -> DeleteConfirmationDialog(
            title = "Delete this order?",
            message = "${Formats.itemsSummary(active.order.items.map { it.productName })} " +
                "(${Formats.orderNumber(active.order.order.id)}) will be removed. This cannot be undone.",
            onConfirm = {
                viewModel.deleteOrder(active.order.order.id)
                dismiss()
            },
            onDismiss = dismiss,
        )

        ActiveDialog.Export -> ExportDialog(
            summary = state.summary,
            exporting = state.exporting,
            onExport = viewModel::exportToExcel,
            onDismiss = dismiss,
        )

        is ActiveDialog.ReceiptReady -> ReceiptReadyDialog(
            receipt = active.receipt,
            onOpen = {
                if (!Sharing.view(context, active.receipt.file, AppFiles.MIME_PDF)) {
                    viewModel.reportError("No PDF viewer is installed. Use Share instead.")
                }
            },
            onShare = {
                share(
                    file = active.receipt.file,
                    mime = AppFiles.MIME_PDF,
                    subject = "Receipt ${active.receipt.displayName}",
                    title = "Share receipt",
                    noAppMessage = "No app on this tablet can receive the receipt.",
                )
            },
            onDismiss = dismiss,
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
    onExport: () -> Unit,
) {
    TopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
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
                        .size(Dimens.brandMark)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.ShoppingBag,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(Dimens.iconLarge),
                    )
                }
                Column(modifier = Modifier.padding(start = Dimens.spaceM)) {
                    Text("Fergbentables", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.secondary)
                    if (!compact) {
                        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        },
        actions = {
            Button(onClick = onExport, modifier = Modifier.padding(end = Dimens.spaceL)) {
                Icon(Icons.Filled.FileDownload, contentDescription = null, modifier = Modifier.size(Dimens.iconMedium))
                Text(if (compact) "  Export" else "  Export to Excel")
            }
        },
    )
}
