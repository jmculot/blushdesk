package com.blushdesk.app.utils

import com.blushdesk.app.data.local.database.Buyer
import com.blushdesk.app.data.local.database.Order
import com.blushdesk.app.domain.model.PaymentMode
import com.blushdesk.app.domain.model.PaymentStatus
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant

class ValidationTest {

    @Test
    fun `name is required and length limited`() {
        assertNotNull(Validation.name(""))
        assertNotNull(Validation.name("   "))
        assertNull(Validation.name("Ana Reyes"))
        assertNotNull(Validation.name("x".repeat(Validation.MAX_NAME + 1)))
    }

    @Test
    fun `email is optional but must look right when given`() {
        assertNull(Validation.email(""))
        assertNull(Validation.email("ana.reyes+shop@example.co.ph"))
        assertNotNull(Validation.email("ana@"))
        assertNotNull(Validation.email("ana@example"))
        assertNotNull(Validation.email("not an email"))
    }

    @Test
    fun `phone needs at least seven digits and only phone characters`() {
        assertNull(Validation.phone("0917 123 4567"))
        assertNull(Validation.phone("+63 (917) 123-4567"))
        assertNotNull(Validation.phone("12345"))
        assertNotNull(Validation.phone("call me maybe"))
        assertNotNull(Validation.phone(""))
        assertNull(Validation.phone("", required = false))
    }

    @Test
    fun `typed unit price must be a positive amount within the limit`() {
        assertNull(Validation.unitPrice("1250.50"))
        assertNotNull(Validation.unitPrice("0"))
        assertNotNull(Validation.unitPrice(""))
        assertNotNull(Validation.unitPrice("1.234"))
        assertNotNull(Validation.unitPrice("10000000.01"))
        assertNull(Validation.unitPrice("10000000"))
    }

    @Test
    fun `stored unit price rejects zero, negatives and a third decimal`() {
        assertNull(Validation.unitPrice(BigDecimal("12.50")))
        assertNotNull(Validation.unitPrice(BigDecimal.ZERO))
        assertNotNull(Validation.unitPrice(BigDecimal("-1.00")))
        assertNotNull(Validation.unitPrice(BigDecimal("1.005")))
        assertNull(Validation.unitPrice(BigDecimal("1.500"))) // trailing zero is still two decimals
    }

    @Test
    fun `quantity is a whole number from one to the maximum`() {
        assertNull(Validation.quantity("1"))
        assertNull(Validation.quantity("9999"))
        assertNotNull(Validation.quantity("0"))
        assertNotNull(Validation.quantity("10000"))
        assertNotNull(Validation.quantity("2.5"))
        assertNotNull(Validation.quantity(""))
    }

    @Test
    fun `whole buyer and order records are checked before saving`() {
        val buyer = Buyer(fullName = "Ana", contactNumber = "0917 123 4567", dateAdded = Instant.EPOCH)
        assertNull(Validation.buyerProblem(buyer))
        assertNotNull(Validation.buyerProblem(buyer.copy(fullName = " ")))
        assertNotNull(Validation.buyerProblem(buyer.copy(contactNumber = "123")))
        assertNotNull(Validation.buyerProblem(buyer.copy(email = "nope")))

        val order = Order(
            buyerId = 1, productName = "Sofa", unitPrice = Money.of("10.00"), quantity = 1,
            purchaseDateTime = Instant.EPOCH, paymentMode = PaymentMode.CASH, paymentStatus = PaymentStatus.UNPAID,
        )
        assertNull(Validation.orderProblem(order))
        assertNotNull(Validation.orderProblem(order.copy(productName = "")))
        assertNotNull(Validation.orderProblem(order.copy(quantity = 0)))
        assertNotNull(Validation.orderProblem(order.copy(unitPrice = Money.ZERO)))
    }
}
