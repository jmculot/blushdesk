package com.blushdesk.app.data.repository

import androidx.room.withTransaction
import com.blushdesk.app.data.local.database.AppDatabase
import com.blushdesk.app.data.local.database.Buyer
import com.blushdesk.app.data.local.database.BuyerListItem
import com.blushdesk.app.data.local.database.OperatorProfile
import com.blushdesk.app.data.local.database.Order
import com.blushdesk.app.data.local.database.OrderTotals
import com.blushdesk.app.data.local.database.ShowroomSummary
import com.blushdesk.app.domain.model.ExportSnapshot
import com.blushdesk.app.domain.model.FulfillmentStatus
import com.blushdesk.app.domain.model.PaymentStatus
import com.blushdesk.app.domain.model.UserFacingException
import com.blushdesk.app.domain.repository.ShowroomRepository
import com.blushdesk.app.utils.Money
import com.blushdesk.app.utils.PhotoStore
import com.blushdesk.app.utils.Validation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Clock

/**
 * [ShowroomRepository] backed by the local Room database: fully offline.
 *
 * This is where records are made consistent before they are stored: text is trimmed, the order
 * total is recomputed from price x quantity, createdAt / updatedAt are stamped from [clock], and
 * photos replaced by an edit are deleted once the new row is safely written.
 */
class OfflineShowroomRepository(
    private val database: AppDatabase,
    private val photos: PhotoStore,
    private val clock: Clock = Clock.systemUTC(),
) : ShowroomRepository {

    private val dao = database.dao()

    override val operator: Flow<OperatorProfile> = dao.observeOperator().map { it ?: OperatorProfile() }
    override val summary: Flow<ShowroomSummary> = dao.observeShowroomSummary()

    override fun searchBuyers(query: String): Flow<List<BuyerListItem>> = dao.searchBuyers(likePattern(query))
    override fun observeBuyer(buyerId: Long): Flow<Buyer?> = dao.observeBuyer(buyerId)
    override fun observeOrders(buyerId: Long): Flow<List<Order>> = dao.observeOrdersForBuyer(buyerId)
    override fun observeOrderTotals(buyerId: Long): Flow<OrderTotals> = dao.observeOrderTotals(buyerId)

    override suspend fun getOperator(): OperatorProfile = dao.getOperator() ?: OperatorProfile()
    override suspend fun getBuyer(buyerId: Long): Buyer? = dao.getBuyer(buyerId)
    override suspend fun getOrder(orderId: Long): Order? = dao.getOrder(orderId)

    override suspend fun getExportSnapshot(): ExportSnapshot = database.withTransaction {
        ExportSnapshot(
            operator = getOperator(),
            buyers = dao.getAllBuyersWithOrders(),
            summary = dao.getExportSummary(),
            takenAt = clock.instant(),
        )
    }

    override suspend fun saveOperator(profile: OperatorProfile) {
        val problem = Validation.name(profile.fullName, "Your name")
            ?: Validation.name(profile.storeName, "Showroom name")
            ?: Validation.email(profile.email)
            ?: Validation.phone(profile.phoneNumber, required = false)
        if (problem != null) throw UserFacingException(problem)

        val now = clock.instant()
        val previous = dao.getOperator()
        dao.upsertOperator(
            profile.copy(
                id = OperatorProfile.ACTIVE_ID,
                fullName = profile.fullName.trim(),
                storeName = profile.storeName.trim(),
                email = profile.email.trim(),
                phoneNumber = profile.phoneNumber.trim(),
                createdAt = previous?.createdAt ?: now,
                updatedAt = now,
            ),
        )
        if (previous?.profileImageUri != profile.profileImageUri) photos.delete(previous?.profileImageUri)
    }

    override suspend fun saveBuyer(buyer: Buyer): Long {
        val clean = buyer.copy(
            fullName = buyer.fullName.trim(),
            contactNumber = buyer.contactNumber.trim(),
            email = buyer.email.trim(),
        )
        Validation.buyerProblem(clean)?.let { throw UserFacingException(it) }
        val now = clock.instant()

        if (clean.id == 0L) return dao.insertBuyer(clean.copy(createdAt = now, updatedAt = now))

        val previous = dao.getBuyer(clean.id) ?: throw UserFacingException("That buyer no longer exists")
        dao.updateBuyer(clean.copy(dateAdded = previous.dateAdded, createdAt = previous.createdAt, updatedAt = now))
        if (previous.profileImageUri != clean.profileImageUri) photos.delete(previous.profileImageUri)
        return clean.id
    }

    override suspend fun deleteBuyer(buyerId: Long) {
        val buyer = dao.getBuyer(buyerId)
        dao.deleteBuyer(buyerId)
        photos.delete(buyer?.profileImageUri)
    }

    override suspend fun saveOrder(order: Order): Long {
        val clean = order.copy(
            productName = order.productName.trim(),
            totalAmount = Money.total(order.unitPrice, order.quantity),
        )
        Validation.orderProblem(clean)?.let { throw UserFacingException(it) }
        dao.getBuyer(clean.buyerId) ?: throw UserFacingException("That buyer no longer exists")
        val now = clock.instant()

        if (clean.id == 0L) return dao.insertOrder(clean.copy(createdAt = now, updatedAt = now))

        val previous = dao.getOrder(clean.id) ?: throw UserFacingException("That order no longer exists")
        dao.updateOrder(clean.copy(createdAt = previous.createdAt, updatedAt = now))
        return clean.id
    }

    override suspend fun deleteOrder(orderId: Long) = dao.deleteOrder(orderId)

    override suspend fun setFulfillmentStatus(orderId: Long, status: FulfillmentStatus) =
        dao.updateFulfillmentStatus(orderId, status, clock.instant())

    override suspend fun setPaymentStatus(orderId: Long, status: PaymentStatus) =
        dao.updatePaymentStatus(orderId, status, clock.instant())

    internal companion object {
        /**
         * Wraps the user's text in `%...%` for SQL LIKE. `%` and `_` are wildcards in LIKE, so a
         * search for "50%" would match everything; they are escaped with a backslash, which the
         * query declares with `ESCAPE '\'`.
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
