package com.blushdesk.app.data

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.blushdesk.app.TestData
import com.blushdesk.app.data.local.database.AppDatabase
import com.blushdesk.app.data.local.database.ShowroomDao
import com.blushdesk.app.data.repository.OfflineShowroomRepository
import com.blushdesk.app.domain.model.FulfillmentStatus
import com.blushdesk.app.domain.model.PaymentStatus
import com.blushdesk.app.utils.Money
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
import java.math.BigDecimal
import java.time.Instant

/** The real SQL against a real (in-memory) SQLite, with foreign keys on as in production. */
@RunWith(AndroidJUnit4::class)
class ShowroomDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: ShowroomDao

    @Before
    fun create() {
        db = TestData.inMemoryDatabase(ApplicationProvider.getApplicationContext<Context>())
        dao = db.dao()
    }

    @After
    fun close() = db.close()

    private fun search(q: String) = runBlocking {
        dao.searchBuyers(OfflineShowroomRepository.likePattern(q)).first().map { it.buyer.fullName }
    }

    // ---- CRUD and types ----------------------------------------------------------------------

    @Test
    fun buyer_round_trips_with_all_fields() = runBlocking {
        val id = dao.insertBuyer(TestData.buyer("Ana Reyes"))
        val loaded = dao.getBuyer(id)!!
        assertEquals("Ana Reyes", loaded.fullName)
        assertEquals("0917 123 4567", loaded.contactNumber)
        assertEquals(TestData.buyer().dateAdded, loaded.dateAdded)
        assertEquals(loaded.dateAdded, loaded.createdAt)
    }

    @Test
    fun order_round_trips_money_enums_and_time_exactly() = runBlocking {
        val buyerId = dao.insertBuyer(TestData.buyer())
        val orderId = dao.insertOrder(TestData.order(buyerId, unit = "999.99", qty = 3))
        val loaded = dao.getOrder(orderId)!!
        assertEquals(BigDecimal("999.99"), loaded.unitPrice)
        assertEquals(BigDecimal("2999.97"), loaded.totalAmount)
        assertEquals(PaymentStatus.PAID, loaded.paymentStatus)
        assertEquals(FulfillmentStatus.DELIVERED, loaded.fulfillmentStatus)
        assertEquals(TestData.order(buyerId).purchaseDateTime, loaded.purchaseDateTime)
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
    fun operator_is_one_row_that_upsert_replaces() = runBlocking {
        assertNull(dao.getOperator())
        dao.upsertOperator(TestData.operator)
        dao.upsertOperator(TestData.operator.copy(storeName = "Renamed"))
        assertEquals("Renamed", dao.observeOperator().first()!!.storeName)
        assertEquals("Lia Santos", dao.getOperator()!!.fullName)
    }

    @Test
    fun targeted_updates_change_one_order_and_stamp_updated_at() = runBlocking {
        val buyer = dao.insertBuyer(TestData.buyer())
        val target = dao.insertOrder(TestData.order(buyer, pay = PaymentStatus.PENDING, stage = FulfillmentStatus.PROCESSING))
        val other = dao.insertOrder(TestData.order(buyer, pay = PaymentStatus.PENDING, stage = FulfillmentStatus.PROCESSING))
        val later = Instant.parse("2026-10-05T00:00:00Z")

        dao.updateFulfillmentStatus(target, FulfillmentStatus.PREPARING, later)
        dao.updatePaymentStatus(target, PaymentStatus.PAID, later)

        val changed = dao.getOrder(target)!!
        assertEquals(FulfillmentStatus.PREPARING, changed.fulfillmentStatus)
        assertEquals(PaymentStatus.PAID, changed.paymentStatus)
        assertEquals(later, changed.updatedAt)
        assertEquals(FulfillmentStatus.PROCESSING, dao.getOrder(other)!!.fulfillmentStatus)
        assertEquals(PaymentStatus.PENDING, dao.getOrder(other)!!.paymentStatus)
    }

    // ---- Buyer list and search ---------------------------------------------------------------

    @Test
    fun buyer_list_shows_order_count_and_the_latest_orders_statuses() = runBlocking {
        val ana = dao.insertBuyer(TestData.buyer("Ana"))
        dao.insertBuyer(TestData.buyer("Ben"))
        dao.insertOrder(TestData.order(ana, pay = PaymentStatus.PAID, stage = FulfillmentStatus.DELIVERED, at = "2026-09-01T00:00:00Z"))
        dao.insertOrder(TestData.order(ana, pay = PaymentStatus.PENDING, stage = FulfillmentStatus.PREPARING, at = "2026-10-01T00:00:00Z"))

        val list = dao.searchBuyers("%").first()

        assertEquals(listOf("Ana", "Ben"), list.map { it.buyer.fullName })
        assertEquals(2, list[0].orderCount)
        assertEquals(FulfillmentStatus.PREPARING, list[0].latestFulfillmentStatus) // the October order
        assertEquals(PaymentStatus.PENDING, list[0].latestPaymentStatus)
        assertEquals(0, list[1].orderCount)
        assertNull(list[1].latestFulfillmentStatus)
        assertNull(list[1].latestPaymentStatus)
    }

    @Test
    fun search_matches_name_phone_or_email_case_insensitively() = runBlocking {
        dao.insertBuyer(TestData.buyer("Ana Reyes", email = "ana@example.com"))
        dao.insertBuyer(TestData.buyer("Ben Cruz", email = "bc@shop.example"))

        assertEquals(listOf("Ana Reyes"), search("ANA"))
        assertEquals(listOf("Ben Cruz"), search("shop.example"))
        assertEquals(listOf("Ana Reyes", "Ben Cruz"), search("0917 123"))
        assertEquals(emptyList<String>(), search("nobody"))
    }

    @Test
    fun search_treats_percent_and_underscore_literally() = runBlocking {
        dao.insertBuyer(TestData.buyer("Ana 50% Off"))
        dao.insertBuyer(TestData.buyer("Ben Cruz"))
        dao.insertBuyer(TestData.buyer("Cy_Lo"))

        assertEquals(listOf("Ana 50% Off"), search("%"))
        assertEquals(listOf("Cy_Lo"), search("_"))
        assertEquals(listOf("Cy_Lo"), search("y_l"))
    }

    // ---- Orders and totals -------------------------------------------------------------------

    @Test
    fun buyer_orders_are_newest_first() = runBlocking {
        val ana = dao.insertBuyer(TestData.buyer())
        dao.insertOrder(TestData.order(ana, product = "Old", at = "2026-09-01T00:00:00Z"))
        dao.insertOrder(TestData.order(ana, product = "New", at = "2026-10-01T00:00:00Z"))
        assertEquals(listOf("New", "Old"), dao.observeOrdersForBuyer(ana).first().map { it.productName })
    }

    @Test
    fun order_totals_split_paid_from_outstanding() = runBlocking {
        val ana = dao.insertBuyer(TestData.buyer())
        dao.insertOrder(TestData.order(ana, unit = "1000.00", qty = 1, pay = PaymentStatus.PAID, stage = FulfillmentStatus.DELIVERED))
        dao.insertOrder(TestData.order(ana, unit = "500.00", qty = 2, pay = PaymentStatus.PENDING, stage = FulfillmentStatus.PREPARING))
        dao.insertOrder(TestData.order(ana, unit = "100.00", qty = 1, pay = PaymentStatus.UNPAID, stage = FulfillmentStatus.PROCESSING))

        val totals = dao.observeOrderTotals(ana).first()

        assertEquals(3, totals.orderCount)
        assertEquals(2, totals.openOrders)
        assertEquals(Money.of("1000.00"), totals.paidAmount)
        assertEquals(Money.of("1100.00"), totals.outstandingAmount)
        assertEquals(Money.of("2100.00"), totals.totalAmount)
    }

    @Test
    fun totals_of_a_buyer_without_orders_are_zero() = runBlocking {
        val ana = dao.insertBuyer(TestData.buyer())
        val totals = dao.observeOrderTotals(ana).first()
        assertEquals(0, totals.orderCount)
        assertEquals(Money.ZERO, totals.totalAmount)
    }

    @Test
    fun orders_can_be_retrieved_by_payment_and_by_fulfillment_status() = runBlocking {
        val ana = dao.insertBuyer(TestData.buyer())
        dao.insertOrder(TestData.order(ana, product = "A", pay = PaymentStatus.UNPAID, stage = FulfillmentStatus.PROCESSING))
        dao.insertOrder(TestData.order(ana, product = "B", pay = PaymentStatus.PAID, stage = FulfillmentStatus.PREPARING))
        dao.insertOrder(TestData.order(ana, product = "C", pay = PaymentStatus.UNPAID, stage = FulfillmentStatus.PREPARING))

        assertEquals(listOf("A", "C"), dao.observeOrdersByPaymentStatus(PaymentStatus.UNPAID).first().map { it.productName })
        assertEquals(listOf("B"), dao.observeOrdersByPaymentStatus(PaymentStatus.PAID).first().map { it.productName })
        assertEquals(listOf("B", "C"), dao.observeOrdersByFulfillmentStatus(FulfillmentStatus.PREPARING).first().map { it.productName })
        assertEquals(emptyList<String>(), dao.observeOrdersByFulfillmentStatus(FulfillmentStatus.DELIVERED).first().map { it.productName })
    }

    @Test
    fun showroom_summary_counts_buyers_orders_and_amounts() = runBlocking {
        val ana = dao.insertBuyer(TestData.buyer("Ana"))
        dao.insertBuyer(TestData.buyer("Ben"))
        dao.insertOrder(TestData.order(ana, unit = "2000.00", qty = 1, pay = PaymentStatus.PAID, stage = FulfillmentStatus.DELIVERED))
        dao.insertOrder(TestData.order(ana, unit = "50.00", qty = 1, pay = PaymentStatus.UNPAID, stage = FulfillmentStatus.PROCESSING))

        val summary = dao.observeShowroomSummary().first()

        assertEquals(2, summary.buyerCount)
        assertEquals(2, summary.orderCount)
        assertEquals(1, summary.openOrders)
        assertEquals(Money.of("2000.00"), summary.paidAmount)
        assertEquals(Money.of("50.00"), summary.outstandingAmount)
    }

    @Test
    fun export_summary_counts_every_status_and_total_sales() = runBlocking {
        val ana = dao.insertBuyer(TestData.buyer("Ana"))
        dao.insertBuyer(TestData.buyer("Ben"))
        dao.insertOrder(TestData.order(ana, unit = "100.00", qty = 1, pay = PaymentStatus.PAID, stage = FulfillmentStatus.DELIVERED))
        dao.insertOrder(TestData.order(ana, unit = "200.00", qty = 1, pay = PaymentStatus.PENDING, stage = FulfillmentStatus.PREPARING))
        dao.insertOrder(TestData.order(ana, unit = "300.00", qty = 1, pay = PaymentStatus.UNPAID, stage = FulfillmentStatus.PROCESSING))
        dao.insertOrder(TestData.order(ana, unit = "400.00", qty = 1, pay = PaymentStatus.UNPAID, stage = FulfillmentStatus.PROCESSING))

        val s = dao.getExportSummary()

        assertEquals(2, s.totalBuyers)
        assertEquals(4, s.totalOrders)
        assertEquals(1, s.paidOrders)
        assertEquals(2, s.unpaidOrders)
        assertEquals(1, s.pendingOrders)
        assertEquals(2, s.processingOrders)
        assertEquals(1, s.preparingOrders)
        assertEquals(1, s.deliveredOrders)
        assertEquals(Money.of("1000.00"), s.totalRecordedSales)
    }

    @Test
    fun relation_returns_each_buyers_own_orders() = runBlocking {
        val ana = dao.insertBuyer(TestData.buyer("Ana"))
        val ben = dao.insertBuyer(TestData.buyer("Ben"))
        dao.insertOrder(TestData.order(ana, product = "Sofa"))
        dao.insertOrder(TestData.order(ana, product = "Table"))
        dao.insertOrder(TestData.order(ben, product = "Lamp"))

        val all = dao.getAllBuyersWithOrders()
        assertEquals(listOf("Ana", "Ben"), all.map { it.buyer.fullName })
        assertEquals(setOf("Sofa", "Table"), all[0].orders.map { it.productName }.toSet())
        assertEquals(listOf("Lamp"), all[1].orders.map { it.productName })
    }

    @Test
    fun observing_a_buyer_emits_null_after_deletion() = runBlocking {
        val ana = dao.insertBuyer(TestData.buyer("Ana"))
        assertNotNull(dao.observeBuyer(ana).first())
        dao.deleteBuyer(ana)
        assertNull(dao.observeBuyer(ana).first())
    }
}
