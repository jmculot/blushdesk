package com.blushdesk.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import com.blushdesk.app.data.local.entity.Buyer
import com.blushdesk.app.data.local.entity.OperatorProfile
import com.blushdesk.app.data.local.entity.Order
import com.blushdesk.app.data.local.relation.BuyerListItem
import com.blushdesk.app.data.local.relation.BuyerWithOrders
import com.blushdesk.app.data.local.relation.ShowroomSummary
import kotlinx.coroutines.flow.Flow

/**
 * Every query the app runs. The SQL spells out enum values ('PAID', 'DELIVERED') because
 * [Converters] stores enums by name; renaming an enum constant therefore needs a migration.
 */
@Dao
interface ShowroomDao {

    // ---- Operator ---------------------------------------------------------------------------

    @Query("SELECT * FROM operator_profile WHERE id = ${OperatorProfile.SINGLETON_ID}")
    fun observeOperator(): Flow<OperatorProfile?>

    @Query("SELECT * FROM operator_profile WHERE id = ${OperatorProfile.SINGLETON_ID}")
    suspend fun getOperator(): OperatorProfile?

    @Upsert
    suspend fun upsertOperator(profile: OperatorProfile)

    // ---- Buyers -----------------------------------------------------------------------------

    @Insert
    suspend fun insertBuyer(buyer: Buyer): Long

    @Update
    suspend fun updateBuyer(buyer: Buyer)

    /** Orders go with the buyer through the foreign key's ON DELETE CASCADE. */
    @Query("DELETE FROM buyers WHERE id = :buyerId")
    suspend fun deleteBuyer(buyerId: Long)

    @Query("SELECT * FROM buyers WHERE id = :buyerId")
    suspend fun getBuyer(buyerId: Long): Buyer?

    /**
     * The buyer list with an order count and unpaid balance per buyer. [pattern] is a ready-made
     * LIKE pattern (see ShowroomRepositoryImpl) so `%` and `_` typed by the user stay literal.
     */
    @Query(
        """
        SELECT b.*,
               COUNT(o.id) AS orderCount,
               COALESCE(SUM(CASE WHEN o.paymentStatus <> 'PAID' THEN o.unitPriceMinor * o.quantity END), 0)
                   AS outstandingMinor
        FROM buyers AS b
        LEFT JOIN orders AS o ON o.buyerId = b.id
        WHERE b.fullName LIKE :pattern ESCAPE '\'
           OR b.contact LIKE :pattern ESCAPE '\'
           OR b.email LIKE :pattern ESCAPE '\'
        GROUP BY b.id
        ORDER BY b.fullName COLLATE NOCASE ASC, b.id ASC
        """,
    )
    fun observeBuyerList(pattern: String): Flow<List<BuyerListItem>>

    // ---- Orders -----------------------------------------------------------------------------

    @Insert
    suspend fun insertOrder(order: Order): Long

    @Update
    suspend fun updateOrder(order: Order)

    @Query("DELETE FROM orders WHERE id = :orderId")
    suspend fun deleteOrder(orderId: Long)

    @Query("SELECT * FROM orders WHERE id = :orderId")
    suspend fun getOrder(orderId: Long): Order?

    @Query("UPDATE orders SET orderStatus = :status WHERE id = :orderId")
    suspend fun updateOrderStatus(orderId: Long, status: OrderStatus)

    @Query("UPDATE orders SET paymentStatus = :status WHERE id = :orderId")
    suspend fun updatePaymentStatus(orderId: Long, status: PaymentStatus)

    // ---- Relations / reporting --------------------------------------------------------------

    /** Live buyer + orders for the detail pane; emits null if the buyer is deleted. */
    @Transaction
    @Query("SELECT * FROM buyers WHERE id = :buyerId")
    fun observeBuyerWithOrders(buyerId: Long): Flow<BuyerWithOrders?>

    /** Everything, for the Excel export. */
    @Transaction
    @Query("SELECT * FROM buyers ORDER BY fullName COLLATE NOCASE ASC, id ASC")
    suspend fun getAllBuyersWithOrders(): List<BuyerWithOrders>

    @Query(
        """
        SELECT (SELECT COUNT(*) FROM buyers) AS buyerCount,
               (SELECT COUNT(*) FROM orders WHERE orderStatus <> 'DELIVERED') AS openOrders,
               (SELECT COALESCE(SUM(unitPriceMinor * quantity), 0) FROM orders WHERE paymentStatus = 'PAID')
                   AS paidMinor,
               (SELECT COALESCE(SUM(unitPriceMinor * quantity), 0) FROM orders WHERE paymentStatus <> 'PAID')
                   AS outstandingMinor
        """,
    )
    fun observeSummary(): Flow<ShowroomSummary>
}
