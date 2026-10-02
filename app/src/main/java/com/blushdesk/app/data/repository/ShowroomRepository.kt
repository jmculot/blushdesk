package com.blushdesk.app.data.repository

import com.blushdesk.app.data.local.OrderStatus
import com.blushdesk.app.data.local.PaymentStatus
import com.blushdesk.app.data.local.ShowroomDao
import com.blushdesk.app.data.local.entity.Buyer
import com.blushdesk.app.data.local.entity.OperatorProfile
import com.blushdesk.app.data.local.entity.Order
import com.blushdesk.app.data.local.relation.BuyerListItem
import com.blushdesk.app.data.local.relation.BuyerWithOrders
import com.blushdesk.app.data.local.relation.ShowroomSummary
import com.blushdesk.app.data.storage.PhotoStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * The only door to the showroom's data. ViewModels depend on this interface, never on Room, so a
 * test can swap in an in-memory fake and the storage layer can change without touching the UI.
 */
interface ShowroomRepository {
    /** The operator, or a blank [OperatorProfile] until they set one up. Never null. */
    val operator: Flow<OperatorProfile>
    val summary: Flow<ShowroomSummary>

    fun observeBuyers(query: String): Flow<List<BuyerListItem>>
    fun observeBuyerDetail(buyerId: Long): Flow<BuyerWithOrders?>

    suspend fun getOperator(): OperatorProfile
    suspend fun getBuyer(buyerId: Long): Buyer?
    suspend fun getOrder(orderId: Long): Order?
    suspend fun getAllBuyersWithOrders(): List<BuyerWithOrders>

    suspend fun saveOperator(profile: OperatorProfile)

    /** Inserts when [Buyer.id] is 0, otherwise updates. Returns the buyer's id. */
    suspend fun saveBuyer(buyer: Buyer): Long
    suspend fun deleteBuyer(buyerId: Long)

    /** Inserts when [Order.id] is 0, otherwise updates. Returns the order's id. */
    suspend fun saveOrder(order: Order): Long
    suspend fun deleteOrder(orderId: Long)
    suspend fun setOrderStatus(orderId: Long, status: OrderStatus)
    suspend fun setPaymentStatus(orderId: Long, status: PaymentStatus)
}

class ShowroomRepositoryImpl(
    private val dao: ShowroomDao,
    private val photos: PhotoStore,
) : ShowroomRepository {

    override val operator: Flow<OperatorProfile> = dao.observeOperator().map { it ?: OperatorProfile() }
    override val summary: Flow<ShowroomSummary> = dao.observeSummary()

    override fun observeBuyers(query: String): Flow<List<BuyerListItem>> =
        dao.observeBuyerList(likePattern(query))

    override fun observeBuyerDetail(buyerId: Long): Flow<BuyerWithOrders?> =
        dao.observeBuyerWithOrders(buyerId)

    override suspend fun getOperator(): OperatorProfile = dao.getOperator() ?: OperatorProfile()
    override suspend fun getBuyer(buyerId: Long): Buyer? = dao.getBuyer(buyerId)
    override suspend fun getOrder(orderId: Long): Order? = dao.getOrder(orderId)
    override suspend fun getAllBuyersWithOrders(): List<BuyerWithOrders> = dao.getAllBuyersWithOrders()

    override suspend fun saveOperator(profile: OperatorProfile) {
        val previous = dao.getOperator()
        dao.upsertOperator(
            profile.copy(
                id = OperatorProfile.SINGLETON_ID,
                fullName = profile.fullName.trim(),
                storeName = profile.storeName.trim(),
                email = profile.email.trim(),
                phone = profile.phone.trim(),
            ),
        )
        // Delete the replaced picture only after the new row is safely written.
        if (previous?.photoPath != profile.photoPath) photos.delete(previous?.photoPath)
    }

    override suspend fun saveBuyer(buyer: Buyer): Long {
        val clean = buyer.copy(
            fullName = buyer.fullName.trim(),
            contact = buyer.contact.trim(),
            email = buyer.email.trim(),
        )
        if (clean.id == 0L) return dao.insertBuyer(clean)

        val previous = dao.getBuyer(clean.id)
        dao.updateBuyer(clean)
        if (previous != null && previous.photoPath != clean.photoPath) photos.delete(previous.photoPath)
        return clean.id
    }

    override suspend fun deleteBuyer(buyerId: Long) {
        val buyer = dao.getBuyer(buyerId)
        dao.deleteBuyer(buyerId)
        photos.delete(buyer?.photoPath)
    }

    override suspend fun saveOrder(order: Order): Long {
        val clean = order.copy(productName = order.productName.trim())
        if (clean.id == 0L) return dao.insertOrder(clean)
        dao.updateOrder(clean)
        return clean.id
    }

    override suspend fun deleteOrder(orderId: Long) = dao.deleteOrder(orderId)

    override suspend fun setOrderStatus(orderId: Long, status: OrderStatus) =
        dao.updateOrderStatus(orderId, status)

    override suspend fun setPaymentStatus(orderId: Long, status: PaymentStatus) =
        dao.updatePaymentStatus(orderId, status)

    internal companion object {
        /**
         * Wraps the user's text in `%...%` for SQL LIKE. `%` and `_` are wildcards in LIKE, so a
         * search for "50%" would otherwise match everything; they are escaped with a backslash,
         * which the query declares with `ESCAPE '\'`.
         */
        fun likePattern(query: String): String {
            val escaped = query.trim()
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_")
            return "%$escaped%"
        }
    }
}
