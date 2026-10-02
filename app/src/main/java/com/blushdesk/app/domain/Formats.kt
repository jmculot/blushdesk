package com.blushdesk.app.domain

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Text formats used on screen, in the PDF and in file names. English month/AM-PM names throughout. */
object Formats {
    private val DATE_TIME = DateTimeFormatter.ofPattern("MMM d, yyyy '·' h:mm a", Locale.ENGLISH)
    private val DATE = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH)
    private val TIME = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)
    private val STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmm", Locale.ENGLISH)

    fun dateTime(instant: Instant, zone: ZoneId = ZoneId.systemDefault()): String =
        DATE_TIME.format(instant.atZone(zone))

    fun date(instant: Instant, zone: ZoneId = ZoneId.systemDefault()): String =
        DATE.format(instant.atZone(zone))

    fun time(local: LocalDateTime): String = TIME.format(local)

    fun date(local: LocalDateTime): String = DATE.format(local)

    /** "2026-10-02_1530" for export file names. */
    fun fileStamp(instant: Instant, zone: ZoneId = ZoneId.systemDefault()): String =
        STAMP.format(instant.atZone(zone))

    /** The receipt / order reference printed on PDFs and in the Excel export: BD-000042. */
    fun orderNumber(orderId: Long): String = "BD-" + orderId.toString().padStart(6, '0')

    /** Two-letter avatar fallback: "Ana Reyes" -> "AR", "Cher" -> "C". */
    fun initials(name: String): String {
        val words = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        return when (words.size) {
            0 -> "?"
            1 -> words[0].take(1).uppercase()
            else -> (words.first().take(1) + words.last().take(1)).uppercase()
        }
    }

    /** Strips anything unsafe in a file name and keeps it short. */
    fun fileSafe(text: String): String =
        text.trim().replace(Regex("[^A-Za-z0-9]+"), "_").trim('_').take(40).ifEmpty { "buyer" }
}
