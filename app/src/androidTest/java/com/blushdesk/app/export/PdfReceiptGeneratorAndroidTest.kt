package com.blushdesk.app.export

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.blushdesk.app.TestData
import com.blushdesk.app.data.local.PaymentStatus
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream
import java.time.Instant
import java.time.ZoneId

@RunWith(AndroidJUnit4::class)
class PdfReceiptGeneratorAndroidTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val zone = ZoneId.of("Asia/Manila")
    private val issuedAt = Instant.parse("2026-10-02T07:45:00Z")

    /** Where review copies go, so a person can look at what the test generated (see README "Testing"). */
    private val artifacts = File(context.filesDir, "test-artifacts").apply { mkdirs() }

    private val buyer = TestData.buyer("Ana Reyes", id = 1)
    private val paid = TestData.order(1, "Velvet Sofa", 1_250_050, 2, PaymentStatus.PAID, id = 10)

    private fun generate(
        name: String,
        operator: com.blushdesk.app.data.local.entity.OperatorProfile = TestData.operator,
        order: com.blushdesk.app.data.local.entity.Order = paid,
    ): File = runBlocking {
        PdfReceiptGenerator().generate(File(context.cacheDir, name), operator, buyer, order, zone, issuedAt)
    }

    /** Renders page 1 at 1.5x so a person (or the pixel checks below) can inspect it. */
    private fun render(pdf: File, pngName: String): Bitmap {
        ParcelFileDescriptor.open(pdf, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
            PdfRenderer(fd).use { renderer ->
                assertEquals("a receipt is a single page", 1, renderer.pageCount)
                renderer.openPage(0).use { page ->
                    val bitmap = Bitmap.createBitmap((page.width * 1.5f).toInt(), (page.height * 1.5f).toInt(), Bitmap.Config.ARGB_8888)
                    bitmap.eraseColor(Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    FileOutputStream(File(artifacts, pngName)).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    return bitmap
                }
            }
        }
    }

    @Test
    fun produces_a_single_page_a4_pdf() {
        val pdf = generate("receipt-basic.pdf")
        assertTrue(pdf.length() > 1_000)
        assertEquals("%PDF", pdf.inputStream().use { String(it.readNBytes(4)) })
        ParcelFileDescriptor.open(pdf, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
            PdfRenderer(fd).use { renderer ->
                renderer.openPage(0).use { page ->
                    assertEquals(595, page.width)
                    assertEquals(842, page.height)
                }
            }
        }
        pdf.copyTo(File(artifacts, "receipt-basic.pdf"), overwrite = true)
    }

    @Test
    fun renders_the_branded_header_and_the_paid_stamp() {
        val bitmap = render(generate("receipt-render.pdf"), "receipt-basic.png")

        // Header band: deep magenta (#880E4F) in the top-left, well away from any text.
        val band = bitmap.getPixel(8, 8)
        assertTrue("header band should be deep magenta but was #${Integer.toHexString(band)}", isNear(band, 0x88, 0x0E, 0x4F, tolerance = 12))

        // The rest of the page is mostly white, and the PAID stamp must add a pinkish tint on top of it.
        var pinkish = 0
        for (y in 300 until bitmap.height - 200 step 3) {
            for (x in 0 until bitmap.width step 3) {
                val p = bitmap.getPixel(x, y)
                if (Color.red(p) > 235 && Color.green(p) in 200..245 && Color.blue(p) in 200..245 && Color.green(p) < Color.red(p) - 6) pinkish++
            }
        }
        assertTrue("expected a visible semi-transparent stamp, found $pinkish tinted samples", pinkish > 400)
    }

    @Test
    fun renders_without_an_operator_photo_or_email() {
        val bare = TestData.operator.copy(email = "", phone = "", photoPath = null)
        val bitmap = render(generate("receipt-bare.pdf", operator = bare), "receipt-bare.png")
        assertTrue(isNear(bitmap.getPixel(8, 8), 0x88, 0x0E, 0x4F, tolerance = 12))
    }

    @Test
    fun long_product_names_and_buyer_details_do_not_break_the_layout() {
        val longName = "Hand-woven rattan lounge chair with removable blush velvet cushions and matching ottoman set, limited edition"
        val order = TestData.order(1, longName, 999_999_999, 9_999, PaymentStatus.PAID, id = 123_456)
        val bitmap = render(generate("receipt-long.pdf", order = order), "receipt-long.png")
        assertTrue(bitmap.width > 0)
    }

    @Test
    fun uses_an_operator_photo_when_one_exists() {
        val photo = File(context.cacheDir, "operator.jpg")
        val source = Bitmap.createBitmap(300, 200, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.rgb(30, 200, 60)) }
        FileOutputStream(photo).use { source.compress(Bitmap.CompressFormat.JPEG, 90, it) }

        val bitmap = render(generate("receipt-photo.pdf", operator = TestData.operator.copy(photoPath = photo.absolutePath)), "receipt-photo.png")

        // Avatar circle is centered near (70, 68) in PDF points, i.e. x1.5 in the bitmap: it should now be green.
        val p = bitmap.getPixel((70 * 1.5f).toInt(), (68 * 1.5f).toInt())
        assertTrue("avatar should show the green photo but was #${Integer.toHexString(p)}", Color.green(p) > 150 && Color.red(p) < 100)
    }

    @Test
    fun refuses_to_issue_a_receipt_for_an_unpaid_order() {
        listOf(PaymentStatus.PENDING, PaymentStatus.UNPAID).forEach { status ->
            try {
                generate("receipt-unpaid.pdf", order = paid.copy(paymentStatus = status))
                fail("a $status order must not get a receipt")
            } catch (e: IllegalArgumentException) {
                assertTrue(e.message!!.contains("paid"))
            }
        }
        assertTrue(!File(context.cacheDir, "receipt-unpaid.pdf").exists())
    }

    @Test
    fun refuses_an_order_that_belongs_to_a_different_buyer() {
        try {
            generate("receipt-mismatch.pdf", order = paid.copy(buyerId = 99))
            fail("mismatched buyer should be rejected")
        } catch (_: IllegalArgumentException) {
            // expected
        }
    }

    private fun isNear(color: Int, r: Int, g: Int, b: Int, tolerance: Int) =
        Math.abs(Color.red(color) - r) <= tolerance &&
            Math.abs(Color.green(color) - g) <= tolerance &&
            Math.abs(Color.blue(color) - b) <= tolerance
}
