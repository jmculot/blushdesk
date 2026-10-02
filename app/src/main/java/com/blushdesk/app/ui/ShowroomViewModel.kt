package com.blushdesk.app.ui

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.blushdesk.app.data.local.OrderStatus
import com.blushdesk.app.data.local.PaymentStatus
import com.blushdesk.app.data.local.entity.Buyer
import com.blushdesk.app.data.local.entity.OperatorProfile
import com.blushdesk.app.data.local.entity.Order
import com.blushdesk.app.data.local.relation.BuyerListItem
import com.blushdesk.app.data.local.relation.ShowroomSummary
import com.blushdesk.app.data.repository.ShowroomRepository
import com.blushdesk.app.data.storage.PhotoStore
import com.blushdesk.app.di.AppContainer
import com.blushdesk.app.export.DocumentService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Holds the screen state and runs every user action. The screen only renders [uiState] and calls
 * the functions below; all persistence goes through [ShowroomRepository] and all file generation
 * through [DocumentService], so this class has no Android file or database code of its own.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ShowroomViewModel(
    private val repository: ShowroomRepository,
    private val photos: PhotoStore,
    private val documents: DocumentService,
) : ViewModel() {

    private val query = MutableStateFlow("")
    private val selectedBuyerId = MutableStateFlow<Long?>(null)
    private val busy = MutableStateFlow(Busy())

    private val _events = Channel<UiEvent>(Channel.BUFFERED)
    val events: Flow<UiEvent> = _events.receiveAsFlow()

    private val buyers: Flow<List<BuyerListItem>> = query.flatMapLatest { repository.observeBuyers(it) }

    private val detail: Flow<BuyerDetail?> = selectedBuyerId.flatMapLatest { id ->
        if (id == null) {
            flowOf(null)
        } else {
            repository.observeBuyerDetail(id).map { it?.toDetail() }.onEach { loaded ->
                // The selected buyer was deleted (here or by a cascade): fall back to "nothing selected".
                if (loaded == null) selectedBuyerId.compareAndSet(id, null)
            }
        }
    }

    private data class Core(val operator: OperatorProfile, val summary: ShowroomSummary, val buyers: List<BuyerListItem>)
    private data class Selection(val query: String, val id: Long?, val detail: BuyerDetail?, val busy: Busy)
    private data class Busy(val exporting: Boolean = false, val receiptOrderId: Long? = null)

    val uiState: StateFlow<ShowroomUiState> = combine(
        combine(repository.operator, repository.summary, buyers, ::Core),
        combine(query, selectedBuyerId, detail, busy, ::Selection),
    ) { core, selection ->
        ShowroomUiState(
            operator = core.operator,
            operatorLoaded = true,
            summary = core.summary,
            query = selection.query,
            buyers = core.buyers,
            buyersLoaded = true,
            selectedBuyerId = selection.id,
            detail = selection.detail,
            exporting = selection.busy.exporting,
            receiptOrderId = selection.busy.receiptOrderId,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), ShowroomUiState())

    init {
        // With nothing selected (first launch, or the selected buyer was just deleted) show the first
        // buyer in the list rather than an empty detail pane.
        viewModelScope.launch {
            combine(selectedBuyerId, buyers) { id, list -> id to list }.collect { (id, list) ->
                if (id == null && list.isNotEmpty()) selectedBuyerId.compareAndSet(null, list.first().buyer.id)
            }
        }
    }

    // ---- Navigation / search ----------------------------------------------------------------

    fun onQueryChange(value: String) {
        query.value = value
    }

    fun selectBuyer(buyerId: Long) {
        selectedBuyerId.value = buyerId
    }

    // ---- Operator ---------------------------------------------------------------------------

    fun saveOperator(profile: OperatorProfile) = act("Couldn't save your profile") {
        repository.saveOperator(profile)
        message("Profile saved")
    }

    // ---- Buyers -----------------------------------------------------------------------------

    fun saveBuyer(buyer: Buyer) = act("Couldn't save the buyer") {
        val isNew = buyer.id == 0L
        val id = repository.saveBuyer(buyer)
        if (isNew) selectedBuyerId.value = id
        message(if (isNew) "Buyer added" else "Buyer updated")
    }

    fun deleteBuyer(buyerId: Long) = act("Couldn't delete the buyer") {
        repository.deleteBuyer(buyerId)
        message("Buyer and their orders deleted")
    }

    // ---- Orders -----------------------------------------------------------------------------

    fun saveOrder(order: Order) = act("Couldn't save the order") {
        val isNew = order.id == 0L
        repository.saveOrder(order)
        message(if (isNew) "Order added" else "Order updated")
    }

    fun deleteOrder(orderId: Long) = act("Couldn't delete the order") {
        repository.deleteOrder(orderId)
        message("Order deleted")
    }

    /** One tap on "Mark as <next stage>". Does nothing for an order that is already delivered. */
    fun advanceOrder(order: Order) {
        val next = order.orderStatus.next ?: return
        setOrderStatus(order.id, next)
    }

    fun setOrderStatus(orderId: Long, status: OrderStatus) = act("Couldn't update the order") {
        repository.setOrderStatus(orderId, status)
    }

    fun setPaymentStatus(orderId: Long, status: PaymentStatus) = act("Couldn't update the payment") {
        repository.setPaymentStatus(orderId, status)
    }

    // ---- Photos -----------------------------------------------------------------------------

    /** Copies a picked or captured image into private storage. Returns its path, or null on failure. */
    suspend fun importPhoto(source: Uri): String? = try {
        photos.importPhoto(source)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        message("Couldn't use that photo: ${e.message ?: "unknown error"}")
        null
    }

    /** Drops a photo that was picked in a dialog but then replaced or cancelled. */
    fun discardPhoto(path: String?) = photos.delete(path)

    // ---- Documents --------------------------------------------------------------------------

    fun generateReceipt(orderId: Long) {
        if (busy.value.receiptOrderId != null) return
        busy.update { it.copy(receiptOrderId = orderId) }
        act("Couldn't create the receipt") {
            try {
                _events.send(UiEvent.ReceiptReady(documents.createReceipt(orderId)))
            } finally {
                busy.update { it.copy(receiptOrderId = null) }
            }
        }
    }

    fun exportToExcel() {
        if (busy.value.exporting) return
        busy.update { it.copy(exporting = true) }
        act("Couldn't export to Excel") {
            try {
                _events.send(UiEvent.ShareWorkbook(documents.createWorkbook()))
            } finally {
                busy.update { it.copy(exporting = false) }
            }
        }
    }

    // ---- Plumbing ---------------------------------------------------------------------------

    private fun message(text: String) {
        _events.trySend(UiEvent.Message(text))
    }

    /** Runs [block] in the view model's scope and turns any failure into a message instead of a crash. */
    private fun act(failure: String, block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                message("$failure: ${e.message ?: "unknown error"}")
            }
        }
    }

    companion object {
        private const val STOP_TIMEOUT_MS = 5_000L

        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                ShowroomViewModel(container.repository, container.photoStorage, container.documents)
            }
        }
    }
}
