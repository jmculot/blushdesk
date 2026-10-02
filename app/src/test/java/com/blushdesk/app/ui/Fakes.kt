package com.blushdesk.app.ui

import android.net.Uri
import com.blushdesk.app.data.local.OrderStatus
import com.blushdesk.app.data.local.PaymentStatus
import com.blushdesk.app.data.local.entity.Buyer
import com.blushdesk.app.data.local.entity.OperatorProfile
import com.blushdesk.app.data.local.entity.Order
import com.blushdesk.app.data.local.relation.BuyerListItem
import com.blushdesk.app.data.local.relation.BuyerWithOrders
import com.blushdesk.app.data.local.relation.ShowroomSummary
import com.blushdesk.app.data.repository.ShowroomRepository
import com.blushdesk.app.data.storage.PhotoStore
import com.blushdesk.app.export.DocumentService
import com.blushdesk.app.export.ExportDocument
import com.blushdesk.app.export.ReceiptDocument
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.io.File

/** An in-memory [ShowroomRepository] that behaves like the Room one for the cases the tests need. */
class FakeRepository : ShowroomRepository {
    private val operatorState = MutableStateFlow(OperatorProfile())
    private val buyers = MutableStateFlow<List<Buyer>>(emptyList())
    private val orders = MutableStateFlow<List<Order>>(emptyList())
    private var nextBuyerId = 1L
    private var nextOrderId = 1L

    override val operator: Flow<OperatorProfile> = operatorState

    override val summary: Flow<ShowroomSummary> = combine(buyers, orders) { b, o ->
        ShowroomSummary(
            buyerCount = b.size,
            openOrders = o.count { it.orderStatus != OrderStatus.DELIVERED },
            paidMinor = o.filter { it.paymentStatus.isPaid }.sumOf { it.totalMinor },
            outstandingMinor = o.filterNot { it.paymentStatus.isPaid }.sumOf { it.totalMinor },
        )
    }

    override fun observeBuyers(query: String): Flow<List<BuyerListItem>> = combine(buyers, orders) { b, o ->
        val q = query.trim().lowercase()
        b.filter { q.isEmpty() || it.fullName.lowercase().contains(q) || it.contact.contains(q) || it.email.lowercase().contains(q) }
            .sortedBy { it.fullName.lowercase() }
            .map { buyer ->
                val mine = o.filter { it.buyerId == buyer.id }
                BuyerListItem(buyer, mine.size, mine.filterNot { it.paymentStatus.isPaid }.sumOf { it.totalMinor })
            }
    }

    override fun observeBuyerDetail(buyerId: Long): Flow<BuyerWithOrders?> = combine(buyers, orders) { b, o ->
        b.firstOrNull { it.id == buyerId }?.let { BuyerWithOrders(it, o.filter { order -> order.buyerId == buyerId }) }
    }

    override suspend fun getOperator() = operatorState.value
    override suspend fun getBuyer(buyerId: Long) = buyers.value.firstOrNull { it.id == buyerId }
    override suspend fun getOrder(orderId: Long) = orders.value.firstOrNull { it.id == orderId }
    override suspend fun getAllBuyersWithOrders() =
        buyers.value.map { b -> BuyerWithOrders(b, orders.value.filter { it.buyerId == b.id }) }

    override suspend fun saveOperator(profile: OperatorProfile) {
        operatorState.value = profile
    }

    override suspend fun saveBuyer(buyer: Buyer): Long {
        if (buyer.id == 0L) {
            val saved = buyer.copy(id = nextBuyerId++)
            buyers.value = buyers.value + saved
            return saved.id
        }
        buyers.value = buyers.value.map { if (it.id == buyer.id) buyer else it }
        return buyer.id
    }

    override suspend fun deleteBuyer(buyerId: Long) {
        buyers.value = buyers.value.filterNot { it.id == buyerId }
        orders.value = orders.value.filterNot { it.buyerId == buyerId }
    }

    override suspend fun saveOrder(order: Order): Long {
        if (order.id == 0L) {
            val saved = order.copy(id = nextOrderId++)
            orders.value = orders.value + saved
            return saved.id
        }
        orders.value = orders.value.map { if (it.id == order.id) order else it }
        return order.id
    }

    override suspend fun deleteOrder(orderId: Long) {
        orders.value = orders.value.filterNot { it.id == orderId }
    }

    override suspend fun setOrderStatus(orderId: Long, status: OrderStatus) {
        orders.value = orders.value.map { if (it.id == orderId) it.copy(orderStatus = status) else it }
    }

    override suspend fun setPaymentStatus(orderId: Long, status: PaymentStatus) {
        orders.value = orders.value.map { if (it.id == orderId) it.copy(paymentStatus = status) else it }
    }

    fun orderCount() = orders.value.size
    fun ordersSnapshot() = orders.value
}

class FakeDocuments : DocumentService {
    var failure: Exception? = null
    val receiptsRequested = mutableListOf<Long>()
    var workbooksCreated = 0

    override suspend fun createReceipt(orderId: Long): ReceiptDocument {
        failure?.let { throw it }
        receiptsRequested += orderId
        return ReceiptDocument(File("receipt_$orderId.pdf"), "receipt_$orderId.pdf", "Download/BlushDesk/receipt_$orderId.pdf")
    }

    override suspend fun createWorkbook(): ExportDocument {
        failure?.let { throw it }
        workbooksCreated++
        return ExportDocument(File("export.xlsx"), "export.xlsx")
    }
}

class FakePhotos : PhotoStore {
    val discarded = mutableListOf<String?>()
    override suspend fun importPhoto(source: Uri): String = error("not used in these tests")
    override fun delete(path: String?) {
        discarded += path
    }
}
