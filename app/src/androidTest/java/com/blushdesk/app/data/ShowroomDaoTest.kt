package com.blushdesk.app.data

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.blushdesk.app.TestData
import com.blushdesk.app.data.local.OrderStatus
import com.blushdesk.app.data.local.PaymentStatus
import com.blushdesk.app.data.local.ShowroomDao
import com.blushdesk.app.data.local.ShowroomDatabase
import com.blushdesk.app.data.repository.ShowroomRepositoryImpl
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Runs the real SQL against a real (in-memory) SQLite, with the same foreign-key settings as production. */
@RunWith(AndroidJUnit4::class)
class ShowroomDaoTest {

    private lateinit var db: ShowroomDatabase
    private lateinit var dao: ShowroomDao

    @Before
    fun create() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, ShowroomDatabase::class.java).allowMainThreadQueries().build()
        dao = db.dao()
    }

    @After
    fun close() = db.close()

    @Test
    fun buyer_round_trips_with_all_fields() = runBlocking {
        val id = dao.insertBuyer(TestData.buyer("Ana Reyes"))
        val loaded = dao.getBuyer(id)!!
        assertEquals("Ana Reyes", loaded.fullName)
        assertEquals("ana@example.com", loaded.email)
        assertEquals(TestData.buyer().dateAdded, loaded.dateAdded) // Instant survives the converter
    }

    @Test
    fun order_round_trips_enums_money_and_time() = runBlocking {
        val buyerId = dao.insertBuyer(TestData.buyer())
        val orderId = dao.insertOrder(TestData.order(buyerId, unitMinor = 99_999, qty = 3))
        val loaded = dao.getOrder(orderId)!!
        assertEquals(99_999L, loaded.unitPriceMinor)
        assertEquals(299_997L, loaded.totalMinor)
        assertEquals(PaymentStatus.PAID, loaded.paymentStatus)
        assertEquals(OrderStatus.DELIVERED, loaded.orderStatus)
        assertEquals(TestData.order(buyerId).purchasedAt, loaded.purchasedAt)
    }

    @Test
    fun deleting_a_buyer_cascades_to_their_orders_only() = runBlocking {
        val ana = dao.insertBuyer(TestData.buyer("Ana"))
        val ben = dao.insertBuyer(TestData.buyer("Ben"))
        dao.insertOrder(TestData.order(ana))
        dao.insertOrder(TestData.order(ana, product = "Chair"))
        val bensOrder = dao.insertOrder(TestData.order(ben))

        dao.deleteBuyer(ana)

        assertNull(dao.getBuyer(ana))
        assertEquals(1, dao.getAllBuyersWithOrders().single().orders.size)
        assertNotNull(dao.getOrder(bensOrder))
    }

    @Test
    fun an_order_for_a_missing_buyer_is_rejected_by_the_foreign_key() = runBlocking {
        try {
            dao.insertOrder(TestData.order(buyerId = 999))
            fail("expected the foreign key to reject an orphan order")
        } catch (_: SQLiteConstraintException) {
            // expected
        }
    }

    @Test
    fun buyer_list_counts_orders_and_sums_only_the_unpaid_balance() = runBlocking {
        val ana = dao.insertBuyer(TestData.buyer("Ana"))
        dao.insertBuyer(TestData.buyer("Ben"))
        dao.insertOrder(TestData.order(ana, unitMinor = 100_000, qty = 1, pay = PaymentStatus.PAID))
        dao.insertOrder(TestData.order(ana, unitMinor = 50_000, qty = 2, pay = PaymentStatus.PENDING))
        dao.insertOrder(TestData.order(ana, unitMinor = 10_000, qty = 1, pay = PaymentStatus.UNPAID))

        val list = dao.observeBuyerList("%").first()

        assertEquals(listOf("Ana", "Ben"), list.map { it.buyer.fullName })
        assertEquals(3, list[0].orderCount)
        assertEquals(110_000L, list[0].outstandingMinor) // 100,000 pending + 10,000 unpaid; the paid one is excluded
        assertEquals(0, list[1].orderCount) // a buyer with no orders still appears (LEFT JOIN)
        assertEquals(0L, list[1].outstandingMinor)
    }

    @Test
    fun search_is_case_insensitive_and_matches_name_phone_or_email() = runBlocking {
        dao.insertBuyer(TestData.buyer("Ana Reyes", email = "ana@example.com"))
        dao.insertBuyer(TestData.buyer("Ben Cruz", email = "bc@shop.example"))

        fun search(q: String) = runBlocking {
            dao.observeBuyerList(ShowroomRepositoryImpl.likePattern(q)).first().map { it.buyer.fullName }
        }

        assertEquals(listOf("Ana Reyes"), search("ANA"))
        assertEquals(listOf("Ben Cruz"), search("shop.example"))
        assertEquals(listOf("Ana Reyes", "Ben Cruz"), search("0917 123")) // both share the same contact number
        assertEquals(emptyList<String>(), search("nobody"))
    }

    @Test
    fun search_treats_percent_and_underscore_literally() = runBlocking {
        dao.insertBuyer(TestData.buyer("Ana 50% Off"))
        dao.insertBuyer(TestData.buyer("Ben Cruz"))
        dao.insertBuyer(TestData.buyer("Cy_Lo"))

        fun search(q: String) = runBlocking {
            dao.observeBuyerList(ShowroomRepositoryImpl.likePattern(q)).first().map { it.buyer.fullName }
        }

        assertEquals(listOf("Ana 50% Off"), search("%")) // not "match everything"
        assertEquals(listOf("Cy_Lo"), search("_"))
        assertEquals(listOf("Cy_Lo"), search("y_l"))
    }

    @Test
    fun summary_counts_open_orders_and_splits_paid_from_outstanding() = runBlocking {
        val ana = dao.insertBuyer(TestData.buyer("Ana"))
        dao.insertBuyer(TestData.buyer("Ben"))
        dao.insertOrder(TestData.order(ana, unitMinor = 200_000, qty = 1, pay = PaymentStatus.PAID, stage = OrderStatus.DELIVERED))
        dao.insertOrder(TestData.order(ana, unitMinor = 30_000, qty = 2, pay = PaymentStatus.PAID, stage = OrderStatus.PREPARING))
        dao.insertOrder(TestData.order(ana, unitMinor = 5_000, qty = 1, pay = PaymentStatus.UNPAID, stage = OrderStatus.PROCESSING))

        val summary = dao.observeSummary().first()

        assertEquals(2, summary.buyerCount)
        assertEquals(2, summary.openOrders)
        assertEquals(260_000L, summary.paidMinor)
        assertEquals(5_000L, summary.outstandingMinor)
    }

    @Test
    fun summary_of_an_empty_database_is_all_zeros() = runBlocking {
        val summary = dao.observeSummary().first()
        assertEquals(0, summary.buyerCount)
        assertEquals(0L, summary.paidMinor)
    }

    @Test
    fun targeted_updates_change_one_column_of_one_order() = runBlocking {
        val buyer = dao.insertBuyer(TestData.buyer())
        val target = dao.insertOrder(TestData.order(buyer, pay = PaymentStatus.PENDING, stage = OrderStatus.PROCESSING))
        val other = dao.insertOrder(TestData.order(buyer, pay = PaymentStatus.PENDING, stage = OrderStatus.PROCESSING))

        dao.updateOrderStatus(target, OrderStatus.PREPARING)
        dao.updatePaymentStatus(target, PaymentStatus.PAID)

        val changed = dao.getOrder(target)!!
        assertEquals(OrderStatus.PREPARING, changed.orderStatus)
        assertEquals(PaymentStatus.PAID, changed.paymentStatus)
        assertEquals(OrderStatus.PROCESSING, dao.getOrder(other)!!.orderStatus)
        assertEquals(PaymentStatus.PENDING, dao.getOrder(other)!!.paymentStatus)
    }

    @Test
    fun operator_is_a_single_row_that_upsert_replaces() = runBlocking {
        assertNull(dao.getOperator())
        dao.upsertOperator(TestData.operator)
        dao.upsertOperator(TestData.operator.copy(storeName = "Renamed"))

        assertEquals("Renamed", dao.observeOperator().first()!!.storeName)
        assertEquals("Lia Santos", dao.getOperator()!!.fullName)
    }

    @Test
    fun buyer_with_orders_relation_returns_each_buyers_own_orders() = runBlocking {
        val ana = dao.insertBuyer(TestData.buyer("Ana"))
        val ben = dao.insertBuyer(TestData.buyer("Ben"))
        dao.insertOrder(TestData.order(ana, product = "Sofa"))
        dao.insertOrder(TestData.order(ana, product = "Table"))
        dao.insertOrder(TestData.order(ben, product = "Lamp"))

        val all = dao.getAllBuyersWithOrders()
        assertEquals(listOf("Ana", "Ben"), all.map { it.buyer.fullName })
        assertEquals(setOf("Sofa", "Table"), all[0].orders.map { it.productName }.toSet())
        assertEquals(listOf("Lamp"), all[1].orders.map { it.productName })

        assertEquals(2, dao.observeBuyerWithOrders(ana).first()!!.orders.size)
        assertNull(dao.observeBuyerWithOrders(12345).first())
    }

    @Test
    fun observing_a_buyer_emits_again_when_an_order_is_added() = runBlocking {
        val ana = dao.insertBuyer(TestData.buyer("Ana"))
        assertEquals(0, dao.observeBuyerWithOrders(ana).first()!!.orders.size)

        dao.insertOrder(TestData.order(ana))

        assertEquals(1, dao.observeBuyerWithOrders(ana).first()!!.orders.size)
    }
}
