package com.blushdesk.app.domain.repository

import com.blushdesk.app.data.local.database.Buyer
import com.blushdesk.app.data.local.database.BuyerListItem
import com.blushdesk.app.data.local.database.OperatorProfile
import com.blushdesk.app.data.local.database.Order
import com.blushdesk.app.data.local.database.OrderTotals
import com.blushdesk.app.data.local.database.ShowroomSummary
import com.blushdesk.app.domain.model.ExportSnapshot
import com.blushdesk.app.domain.model.FulfillmentStatus
import com.blushdesk.app.domain.model.PaymentStatus
import kotlinx.coroutines.flow.Flow

/**
 * The only door to the showroom's data. ViewModels depend on this interface, never on Room, so
 * tests can use an in-memory fake and storage can change without touching the UI.
 *
 * Saving functions validate their input and throw
 * [com.blushdesk.app.domain.model.UserFacingException] with a readable message when it is wrong.
 */
interface ShowroomRepository {
    /** The active operator, or a blank profile until one is set up. Never null. */
    val operator: Flow<OperatorProfile>
    val summary: Flow<ShowroomSummary>

    fun searchBuyers(query: String): Flow<List<BuyerListItem>>
    fun observeBuyer(buyerId: Long): Flow<Buyer?>
    fun observeOrders(buyerId: Long): Flow<List<Order>>
    fun observeOrderTotals(buyerId: Long): Flow<OrderTotals>

    suspend fun getOperator(): OperatorProfile
    suspend fun getBuyer(buyerId: Long): Buyer?
    suspend fun getOrder(orderId: Long): Order?
    suspend fun getExportSnapshot(): ExportSnapshot

    suspend fun saveOperator(profile: OperatorProfile)

    /** Inserts when [Buyer.id] is 0, otherwise updates. Returns the buyer's id. */
    suspend fun saveBuyer(buyer: Buyer): Long
    suspend fun deleteBuyer(buyerId: Long)

    /** Inserts when [Order.id] is 0, otherwise updates; the total is always recomputed. Returns the id. */
    suspend fun saveOrder(order: Order): Long
    suspend fun deleteOrder(orderId: Long)
    suspend fun setFulfillmentStatus(orderId: Long, status: FulfillmentStatus)
    suspend fun setPaymentStatus(orderId: Long, status: PaymentStatus)
}
