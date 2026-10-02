package com.blushdesk.app.data

import com.blushdesk.app.data.local.database.Converters
import com.blushdesk.app.data.local.database.Order
import com.blushdesk.app.data.local.database.OrderItem
import com.blushdesk.app.data.local.database.OrderWithItems
import com.blushdesk.app.data.repository.OfflineShowroomRepository
import com.blushdesk.app.domain.model.FulfillmentStatus
import com.blushdesk.app.domain.model.PaymentMode
import com.blushdesk.app.domain.model.PaymentStatus
import com.blushdesk.app.utils.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant

class ModelsTest {

    @Test
    fun `fulfillment advances processing then preparing then delivered, one step at a time`() {
        assertEquals(FulfillmentStatus.PREPARING, FulfillmentStatus.PROCESSING.next)
        assertEquals(FulfillmentStatus.DELIVERED, FulfillmentStatus.PREPARING.next)
        assertNull(FulfillmentStatus.DELIVERED.next)
    }

    @Test
    fun `payment states are unpaid, pending and paid, and only paid counts as paid`() {
        assertEquals(listOf("UNPAID", "PENDING", "PAID"), PaymentStatus.entries.map { it.name })
        assertTrue(PaymentStatus.PAID.isPaid)
        assertFalse(PaymentStatus.PENDING.isPaid)
        assertFalse(PaymentStatus.UNPAID.isPaid)
    }

    @Test
    fun `payment modes are cash and online payment`() {
        assertEquals(listOf("CASH", "ONLINE_PAYMENT"), PaymentMode.entries.map { it.name })
    }

    @Test
    fun `a product line computes its total from price and quantity`() {
        val item = OrderItem(productName = "Lamp", unitPrice = Money.of("0.10"), quantity = 3)
        assertEquals(BigDecimal("0.30"), item.lineTotal)
    }

    @Test
    fun `order items come back in entry order and count their units`() {
        val order = Order(
            id = 1, buyerId = 1, purchaseDateTime = Instant.EPOCH,
            paymentMode = PaymentMode.CASH, paymentStatus = PaymentStatus.PAID,
        )
        val rows = listOf(
            OrderItem(id = 7, orderId = 1, position = 1, productName = "Second", unitPrice = Money.of("1.00"), quantity = 2),
            OrderItem(id = 9, orderId = 1, position = 0, productName = "First", unitPrice = Money.of("1.00"), quantity = 3),
        )
        val withItems = OrderWithItems(order, rows)
        assertEquals(listOf("First", "Second"), withItems.items.map { it.productName })
        assertEquals(5, withItems.unitCount)
        assertEquals(order.purchaseDateTime, order.createdAt)
    }

    @Test
    fun `converters round-trip and fall back safely on unknown names`() {
        val c = Converters()
        val now = Instant.parse("2026-10-02T07:45:12.345Z")
        assertEquals(now, c.epochMillisToInstant(c.instantToEpochMillis(now)))
        assertEquals(Money.of("1234.56"), c.centavosToMoney(c.moneyToCentavos(Money.of("1234.56"))))
        assertEquals(123_456L, c.moneyToCentavos(Money.of("1234.56")))
        FulfillmentStatus.entries.forEach { assertEquals(it, c.nameToFulfillment(c.fulfillmentToName(it))) }
        PaymentStatus.entries.forEach { assertEquals(it, c.nameToPaymentStatus(c.paymentStatusToName(it))) }
        PaymentMode.entries.forEach { assertEquals(it, c.nameToPaymentMode(c.paymentModeToName(it))) }

        assertEquals(FulfillmentStatus.PROCESSING, c.nameToFulfillment("SHIPPED_TO_MARS"))
        assertEquals(PaymentStatus.UNPAID, c.nameToPaymentStatus("???"))
        assertEquals(PaymentMode.CASH, c.nameToPaymentMode("BARTER"))
    }

    @Test
    fun `search pattern escapes LIKE wildcards`() {
        assertEquals("%ana%", OfflineShowroomRepository.likePattern("  ana "))
        assertEquals("%50\\%%", OfflineShowroomRepository.likePattern("50%"))
        assertEquals("%a\\_b%", OfflineShowroomRepository.likePattern("a_b"))
        assertEquals("%a\\\\b%", OfflineShowroomRepository.likePattern("a\\b"))
        assertEquals("%%", OfflineShowroomRepository.likePattern(""))
    }
}
