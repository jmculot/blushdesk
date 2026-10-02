package com.blushdesk.app.utils

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class FormatsTest {

    private val manila = ZoneId.of("Asia/Manila") // UTC+8, no daylight saving

    @Test
    fun `date and time are shown in the given zone`() {
        // 2026-10-02 07:45 UTC is 15:45 in Manila.
        val instant = Instant.parse("2026-10-02T07:45:00Z")
        assertEquals("Oct 2, 2026 · 3:45 PM", Formats.dateTime(instant, manila))
        assertEquals("Oct 2, 2026", Formats.date(instant, manila))
        assertEquals("3:45 PM", Formats.time(instant, manila))
    }

    @Test
    fun `a late-evening UTC time lands on the next local day`() {
        val instant = Instant.parse("2026-10-02T20:30:00Z")
        assertEquals("Oct 3, 2026", Formats.date(instant, manila))
    }

    @Test
    fun `file stamp sorts alphabetically in time order`() {
        val earlier = Formats.fileStamp(Instant.parse("2026-10-02T07:45:00Z"), manila)
        val later = Formats.fileStamp(Instant.parse("2026-10-02T08:05:00Z"), manila)
        assertEquals("2026-10-02_1545", earlier)
        assert(earlier < later)
    }

    @Test
    fun `order numbers are zero padded`() {
        assertEquals("BD-000001", Formats.orderNumber(1))
        assertEquals("BD-000042", Formats.orderNumber(42))
        assertEquals("BD-1234567", Formats.orderNumber(1_234_567))
    }

    @Test
    fun `initials use first and last word`() {
        assertEquals("AR", Formats.initials("Ana Reyes"))
        assertEquals("AD", Formats.initials("Ana Maria dela Cruz Dizon"))
        assertEquals("C", Formats.initials("cher"))
        assertEquals("?", Formats.initials("   "))
    }

    @Test
    fun `file safe names contain only letters, digits and underscores`() {
        assertEquals("Ana_Reyes", Formats.fileSafe("Ana Reyes"))
        assertEquals("Jose_Dela_Cruz", Formats.fileSafe("  Jose / Dela *Cruz? "))
        assertEquals("buyer", Formats.fileSafe("???"))
        assertEquals(40, Formats.fileSafe("a".repeat(100)).length)
    }
}
