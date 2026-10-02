package com.blushdesk.app.data

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.blushdesk.app.TestData
import com.blushdesk.app.data.local.database.AppDatabase
import com.blushdesk.app.data.repository.OfflineShowroomRepository
import com.blushdesk.app.domain.model.FulfillmentStatus
import com.blushdesk.app.domain.model.PaymentStatus
import com.blushdesk.app.domain.model.UserFacingException
import com.blushdesk.app.utils.Money
import com.blushdesk.app.utils.PhotoStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/** The rules the repository adds on top of the DAO: totals, timestamps, validation, photo cleanup. */
@RunWith(AndroidJUnit4::class)
class OfflineShowroomRepositoryTest {

    private class RecordingPhotos : PhotoStore {
        val deleted = mutableListOf<String?>()
        override suspend fun importPhoto(source: Uri): String = error("unused")
        override fun delete(imageUri: String?) {
            if (imageUri != null) deleted += imageUri
        }
    }

    /** A clock the test moves by hand. */
    private class TestClock(var now: Instant) : Clock() {
        override fun getZone() = ZoneOffset.UTC
        override fun withZone(zone: java.time.ZoneId?) = this
        override fun instant() = now
    }

    private lateinit var db: AppDatabase
    private lateinit var photos: RecordingPhotos
    private lateinit var clock: TestClock
    private lateinit var repo: OfflineShowroomRepository

    @Before
    fun setUp() {
        db = TestData.inMemoryDatabase(ApplicationProvider.getApplicationContext<Context>())
        photos = RecordingPhotos()
        clock = TestClock(Instant.parse("2026-10-01T00:00:00Z"))
        repo = OfflineShowroomRepository(db, photos, clock)
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun saving_an_order_always_recomputes_line_totals_and_the_order_total() = runBlocking {
        val buyer = repo.saveBuyer(TestData.buyer())
        val tamperedHeader = TestData.order(buyer, total = "1.00")
        val tamperedLines = listOf(
            TestData.item("Lamp", "999.99", 3).copy(lineTotal = Money.of("1.00")),
            TestData.item("Pillow", "450.00", 2),
        )

        val id = repo.saveOrder(tamperedHeader, tamperedLines)

        val saved = repo.getOrder(id)!!
        assertEquals(listOf(BigDecimal("2999.97"), BigDecimal("900.00")), saved.items.map { it.lineTotal })
        assertEquals(BigDecimal("3899.97"), saved.order.totalAmount)
    }

    @Test
    fun editing_an_order_replaces_its_lines() = runBlocking {
        val buyer = repo.saveBuyer(TestData.buyer())
        val id = repo.saveOrder(TestData.order(buyer), listOf(TestData.item("Sofa"), TestData.item("Lamp")))

        val existing = repo.getOrder(id)!!
        repo.saveOrder(existing.order, listOf(TestData.item("Rug", "3200.00", 1)))

        val edited = repo.getOrder(id)!!
        assertEquals(listOf("Rug"), edited.items.map { it.productName })
        assertEquals(BigDecimal("3200.00"), edited.order.totalAmount)
    }

    @Test
    fun created_at_is_kept_and_updated_at_moves_on_every_change() = runBlocking {
        val buyerId = repo.saveBuyer(TestData.buyer())
        val orderId = repo.saveOrder(TestData.order(buyerId), listOf(TestData.item()))
        val created = repo.getOrder(orderId)!!.order
        assertEquals(clock.now, created.createdAt)
        assertEquals(clock.now, created.updatedAt)

        clock.now = Instant.parse("2026-10-02T00:00:00Z")
        repo.saveOrder(created, listOf(TestData.item(qty = 5)))
        val edited = repo.getOrder(orderId)!!.order
        assertEquals(Instant.parse("2026-10-01T00:00:00Z"), edited.createdAt)
        assertEquals(clock.now, edited.updatedAt)

        clock.now = Instant.parse("2026-10-03T00:00:00Z")
        repo.setFulfillmentStatus(orderId, FulfillmentStatus.PREPARING)
        assertEquals(clock.now, repo.getOrder(orderId)!!.order.updatedAt)
    }

    @Test
    fun editing_a_buyer_cannot_change_the_date_added() = runBlocking {
        val id = repo.saveBuyer(TestData.buyer())
        val original = repo.getBuyer(id)!!
        repo.saveBuyer(original.copy(fullName = "Ana R.", dateAdded = Instant.EPOCH))
        assertEquals(original.dateAdded, repo.getBuyer(id)!!.dateAdded)
        assertEquals("Ana R.", repo.getBuyer(id)!!.fullName)
    }

    @Test
    fun invalid_records_are_refused_with_a_readable_message() = runBlocking {
        try {
            repo.saveBuyer(TestData.buyer().copy(contactNumber = "12"))
            fail("expected a refusal")
        } catch (e: UserFacingException) {
            assertEquals("Enter a valid phone number", e.message)
        }
        val buyer = repo.saveBuyer(TestData.buyer())
        try {
            repo.saveOrder(TestData.order(buyer), listOf(TestData.item(qty = 0)))
            fail("expected a refusal")
        } catch (e: UserFacingException) {
            assertTrue(e.message!!.contains("Quantity"))
        }
        try {
            repo.saveOrder(TestData.order(buyer), emptyList())
            fail("expected a refusal")
        } catch (e: UserFacingException) {
            assertEquals("Add at least one product", e.message)
        }
        try {
            repo.saveOrder(TestData.order(buyerId = 999), listOf(TestData.item()))
            fail("expected a refusal")
        } catch (e: UserFacingException) {
            assertEquals("That buyer no longer exists", e.message)
        }
        // A refused save leaves nothing behind (the transaction rolls back).
        assertEquals(0, repo.getExportSnapshot().summary.totalOrders)
    }

    @Test
    fun replacing_or_deleting_a_photo_deletes_the_old_file() = runBlocking {
        val id = repo.saveBuyer(TestData.buyer().copy(profileImageUri = "file:///photos/a.jpg"))
        repo.saveBuyer(repo.getBuyer(id)!!.copy(profileImageUri = "file:///photos/b.jpg"))
        assertEquals(listOf("file:///photos/a.jpg"), photos.deleted)

        repo.deleteBuyer(id)
        assertEquals(listOf("file:///photos/a.jpg", "file:///photos/b.jpg"), photos.deleted)
    }

    @Test
    fun operator_profile_is_trimmed_and_stamped() = runBlocking {
        repo.saveOperator(TestData.operator.copy(fullName = "  Lia Santos  "))
        val saved = repo.operator.first()
        assertEquals("Lia Santos", saved.fullName)
        assertEquals(clock.now, saved.createdAt)
    }

    @Test
    fun export_snapshot_agrees_with_itself() = runBlocking {
        repo.saveOperator(TestData.operator)
        val ana = repo.saveBuyer(TestData.buyer("Ana"))
        repo.saveOrder(TestData.order(ana, pay = PaymentStatus.PAID), listOf(TestData.item(unit = "100.00", qty = 2)))
        repo.saveOrder(TestData.order(ana, pay = PaymentStatus.UNPAID), listOf(TestData.item(unit = "25.00", qty = 1), TestData.item(unit = "25.00", qty = 1)))

        val snapshot = repo.getExportSnapshot()

        assertEquals(1, snapshot.buyers.size)
        assertEquals(snapshot.buyers.sumOf { it.orders.size }, snapshot.summary.totalOrders)
        assertEquals(3, snapshot.buyers.single().orders.sumOf { it.items.size })
        assertEquals(Money.of("250.00"), snapshot.summary.totalRecordedSales)
        assertEquals("Lia Santos", snapshot.operator.fullName)
    }
}
