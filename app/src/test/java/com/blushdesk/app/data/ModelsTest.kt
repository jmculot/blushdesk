package com.blushdesk.app.data

import com.blushdesk.app.data.local.Converters
import com.blushdesk.app.data.local.OrderStatus
import com.blushdesk.app.data.local.PaymentMode
import com.blushdesk.app.data.local.PaymentStatus
import com.blushdesk.app.data.local.entity.Order
import com.blushdesk.app.data.repository.ShowroomRepositoryImpl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class ModelsTest {

    @Test
    fun `order status advances processing then preparing then delivered`() {
        assertEquals(OrderStatus.PREPARING, OrderStatus.PROCESSING.next)
        assertEquals(OrderStatus.DELIVERED, OrderStatus.PREPARING.next)
        assertNull(OrderStatus.DELIVERED.next)
    }

    @Test
    fun `only the paid status counts as paid`() {
        assertTrue(PaymentStatus.PAID.isPaid)
        assertFalse(PaymentStatus.PENDING.isPaid)
        assertFalse(PaymentStatus.UNPAID.isPaid)
    }

    @Test
    fun `order total is unit price times quantity without floating point error`() {
        val order = Order(
            buyerId = 1, productName = "Lamp", unitPriceMinor = 10, quantity = 3,
            purchasedAt = Instant.EPOCH, paymentMode = PaymentMode.CASH, paymentStatus = PaymentStatus.PAID,
        )
        assertEquals(30L, order.totalMinor) // 0.10 x 3 is exactly 0.30
    }

    @Test
    fun `converters round-trip and fall back safely on unknown names`() {
        val c = Converters()
        val now = Instant.parse("2026-10-02T07:45:12.345Z")
        assertEquals(now, c.epochMillisToInstant(c.instantToEpochMillis(now)))
        OrderStatus.entries.forEach { assertEquals(it, c.nameToOrderStatus(c.orderStatusToName(it))) }
        PaymentStatus.entries.forEach { assertEquals(it, c.nameToPaymentStatus(c.paymentStatusToName(it))) }
        PaymentMode.entries.forEach { assertEquals(it, c.nameToPaymentMode(c.paymentModeToName(it))) }

        assertEquals(OrderStatus.PROCESSING, c.nameToOrderStatus("SHIPPED_TO_MARS"))
        assertEquals(PaymentStatus.PENDING, c.nameToPaymentStatus("???"))
    }

    @Test
    fun `search pattern escapes LIKE wildcards`() {
        assertEquals("%ana%", ShowroomRepositoryImpl.likePattern("  ana "))
        assertEquals("%50\\%%", ShowroomRepositoryImpl.likePattern("50%"))
        assertEquals("%a\\_b%", ShowroomRepositoryImpl.likePattern("a_b"))
        assertEquals("%a\\\\b%", ShowroomRepositoryImpl.likePattern("a\\b"))
        assertEquals("%%", ShowroomRepositoryImpl.likePattern(""))
    }
}
