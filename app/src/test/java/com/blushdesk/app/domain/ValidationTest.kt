package com.blushdesk.app.domain

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

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
    fun `unit price must be a positive amount within the limit`() {
        assertNull(Validation.unitPrice("1250.50"))
        assertNotNull(Validation.unitPrice("0"))
        assertNotNull(Validation.unitPrice(""))
        assertNotNull(Validation.unitPrice("1.234"))
        assertNotNull(Validation.unitPrice("10000000.01")) // one centavo over the cap
        assertNull(Validation.unitPrice("10000000"))
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
}
