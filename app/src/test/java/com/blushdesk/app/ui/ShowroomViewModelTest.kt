package com.blushdesk.app.ui

import com.blushdesk.app.data.local.OrderStatus
import com.blushdesk.app.data.local.PaymentMode
import com.blushdesk.app.data.local.PaymentStatus
import com.blushdesk.app.data.local.entity.Buyer
import com.blushdesk.app.data.local.entity.Order
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class ShowroomViewModelTest {

    private lateinit var repo: FakeRepository
    private lateinit var docs: FakeDocuments
    private lateinit var photos: FakePhotos
    private lateinit var vm: ShowroomViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        repo = FakeRepository()
        docs = FakeDocuments()
        photos = FakePhotos()
        vm = ShowroomViewModel(repo, photos, docs)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun buyer(name: String, contact: String = "0917 000 0000") =
        Buyer(fullName = name, contact = contact, dateAdded = Instant.parse("2026-10-01T00:00:00Z"))

    private fun order(
        buyerId: Long,
        product: String = "Sofa",
        unit: Long = 100_000,
        qty: Int = 1,
        pay: PaymentStatus = PaymentStatus.PENDING,
        stage: OrderStatus = OrderStatus.PROCESSING,
    ) = Order(
        buyerId = buyerId, productName = product, unitPriceMinor = unit, quantity = qty,
        purchasedAt = Instant.parse("2026-10-01T00:00:00Z"), paymentMode = PaymentMode.CASH,
        paymentStatus = pay, orderStatus = stage,
    )

    /** Keeps uiState and the event stream hot for the duration of the test. */
    private fun TestScope.observe(): MutableList<UiEvent> {
        val events = mutableListOf<UiEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.events.collect { events += it } }
        return events
    }

    @Test
    fun `on start the alphabetically first buyer is selected`() = runTest {
        repo.saveBuyer(buyer("Zed"))
        repo.saveBuyer(buyer("Ana"))
        vm = ShowroomViewModel(repo, photos, docs) // a fresh launch with existing data
        observe()
        advanceUntilIdle()

        assertEquals(2L, vm.uiState.value.selectedBuyerId)
        assertEquals("Ana", vm.uiState.value.detail?.buyer?.fullName)
    }

    @Test
    fun `selection picks up the first buyer ever added and then stays put`() = runTest {
        observe()
        assertNull(vm.uiState.value.selectedBuyerId)

        repo.saveBuyer(buyer("Zed"))
        advanceUntilIdle()
        assertEquals(1L, vm.uiState.value.selectedBuyerId)

        repo.saveBuyer(buyer("Ana")) // sorts first, but must not steal the selection
        advanceUntilIdle()
        assertEquals(1L, vm.uiState.value.selectedBuyerId)
    }

    @Test
    fun `saving a new buyer selects it and announces it`() = runTest {
        val events = observe()
        repo.saveBuyer(buyer("Ana"))

        vm.saveBuyer(buyer("Ben"))
        advanceUntilIdle()

        assertEquals("Ben", vm.uiState.value.detail?.buyer?.fullName)
        assertTrue(events.any { it is UiEvent.Message && it.text == "Buyer added" })
    }

    @Test
    fun `search filters the list but keeps the selected buyer's detail`() = runTest {
        observe()
        repo.saveBuyer(buyer("Ana Reyes"))
        repo.saveBuyer(buyer("Ben Cruz"))
        advanceUntilIdle()
        vm.selectBuyer(1)

        vm.onQueryChange("cruz")
        advanceUntilIdle()

        assertEquals(listOf("Ben Cruz"), vm.uiState.value.buyers.map { it.buyer.fullName })
        assertEquals("Ana Reyes", vm.uiState.value.detail?.buyer?.fullName)
    }

    @Test
    fun `detail stats add up paid and outstanding orders`() = runTest {
        observe()
        val id = repo.saveBuyer(buyer("Ana"))
        repo.saveOrder(order(id, unit = 250_000, qty = 2, pay = PaymentStatus.PAID, stage = OrderStatus.DELIVERED))
        repo.saveOrder(order(id, unit = 100_000, qty = 1, pay = PaymentStatus.PENDING))
        repo.saveOrder(order(id, unit = 50_000, qty = 3, pay = PaymentStatus.UNPAID, stage = OrderStatus.PREPARING))
        advanceUntilIdle()

        val stats = vm.uiState.value.detail!!.stats
        assertEquals(3, stats.orderCount)
        assertEquals(2, stats.openOrders)
        assertEquals(500_000L, stats.paidMinor)
        assertEquals(250_000L, stats.outstandingMinor)
    }

    @Test
    fun `advancing walks processing to preparing to delivered and stops`() = runTest {
        observe()
        val buyerId = repo.saveBuyer(buyer("Ana"))
        val orderId = repo.saveOrder(order(buyerId))
        advanceUntilIdle()

        vm.advanceOrder(repo.getOrder(orderId)!!)
        advanceUntilIdle()
        assertEquals(OrderStatus.PREPARING, repo.getOrder(orderId)!!.orderStatus)

        vm.advanceOrder(repo.getOrder(orderId)!!)
        advanceUntilIdle()
        assertEquals(OrderStatus.DELIVERED, repo.getOrder(orderId)!!.orderStatus)

        vm.advanceOrder(repo.getOrder(orderId)!!) // already delivered: nothing happens
        advanceUntilIdle()
        assertEquals(OrderStatus.DELIVERED, repo.getOrder(orderId)!!.orderStatus)
    }

    @Test
    fun `a stage can be set directly, including going back`() = runTest {
        observe()
        val buyerId = repo.saveBuyer(buyer("Ana"))
        val orderId = repo.saveOrder(order(buyerId, stage = OrderStatus.DELIVERED))

        vm.setOrderStatus(orderId, OrderStatus.PROCESSING)
        advanceUntilIdle()

        assertEquals(OrderStatus.PROCESSING, repo.getOrder(orderId)!!.orderStatus)
    }

    @Test
    fun `receipt for a paid order raises a ReceiptReady event and clears the busy flag`() = runTest {
        val events = observe()
        val buyerId = repo.saveBuyer(buyer("Ana"))
        val orderId = repo.saveOrder(order(buyerId, pay = PaymentStatus.PAID))
        advanceUntilIdle()

        vm.generateReceipt(orderId)
        advanceUntilIdle()

        assertEquals(listOf(orderId), docs.receiptsRequested)
        val ready = events.filterIsInstance<UiEvent.ReceiptReady>().single()
        assertEquals("receipt_$orderId.pdf", ready.receipt.displayName)
        assertNull(vm.uiState.value.receiptOrderId)
    }

    @Test
    fun `a failed receipt is reported as a message and does not crash or stay busy`() = runTest {
        val events = observe()
        docs.failure = IllegalStateException("Mark the order as paid first")

        vm.generateReceipt(7)
        advanceUntilIdle()

        val message = events.filterIsInstance<UiEvent.Message>().single().text
        assertTrue(message, message.contains("Mark the order as paid first"))
        assertNull(vm.uiState.value.receiptOrderId)
    }

    @Test
    fun `excel export shares the workbook and clears the exporting flag`() = runTest {
        val events = observe()

        vm.exportToExcel()
        advanceUntilIdle()

        assertEquals(1, docs.workbooksCreated)
        assertEquals("export.xlsx", events.filterIsInstance<UiEvent.ShareWorkbook>().single().export.displayName)
        assertFalse(vm.uiState.value.exporting)
    }

    @Test
    fun `a failed export is reported and the button becomes usable again`() = runTest {
        val events = observe()
        docs.failure = IllegalStateException("There is nothing to export yet")

        vm.exportToExcel()
        advanceUntilIdle()

        assertTrue(events.filterIsInstance<UiEvent.Message>().single().text.contains("nothing to export"))
        assertFalse(vm.uiState.value.exporting)
    }

    @Test
    fun `deleting the selected buyer removes their orders and selects the next buyer`() = runTest {
        observe()
        val ana = repo.saveBuyer(buyer("Ana"))
        val ben = repo.saveBuyer(buyer("Ben"))
        repo.saveOrder(order(ana))
        advanceUntilIdle()
        vm.selectBuyer(ana)

        vm.deleteBuyer(ana)
        advanceUntilIdle()

        assertEquals(0, repo.orderCount())
        assertEquals(ben, vm.uiState.value.selectedBuyerId)
    }

    @Test
    fun `deleting the last buyer leaves nothing selected`() = runTest {
        observe()
        val only = repo.saveBuyer(buyer("Ana"))
        advanceUntilIdle()

        vm.deleteBuyer(only)
        advanceUntilIdle()

        assertNull(vm.uiState.value.selectedBuyerId)
        assertNull(vm.uiState.value.detail)
        assertTrue(vm.uiState.value.buyers.isEmpty())
    }

    @Test
    fun `discarding a photo asks the store to delete it`() {
        vm.discardPhoto("/data/photos/a.jpg")
        assertEquals(listOf<String?>("/data/photos/a.jpg"), photos.discarded)
    }

    @Test
    fun `saving the operator profile stores it`() = runTest {
        observe()
        vm.saveOperator(repo.getOperator().copy(fullName = "Lia Santos", storeName = "Rosé Showroom"))
        advanceUntilIdle()

        assertTrue(vm.uiState.value.operator.isSetUp)
        assertNotNull(vm.uiState.value.operator.storeName)
    }
}
