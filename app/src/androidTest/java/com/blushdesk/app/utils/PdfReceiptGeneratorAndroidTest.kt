package com.blushdesk.app.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.blushdesk.app.TestData
import com.blushdesk.app.data.local.database.OperatorProfile
import com.blushdesk.app.data.local.database.OrderWithItems
import com.blushdesk.app.domain.model.PaymentStatus
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

    /** Where review copies go, so a person can look at what the test generated (see README "Tests"). */
    private val artifacts = File(context.filesDir, "test-artifacts").apply { mkdirs() }

    private val buyer = TestData.buyer("Ana Reyes", id = 1)
    private val paid = TestData.orderWithItems(10, 1, listOf(TestData.item("Velvet Sofa", "12500.50", 2)))

    private fun generate(
        name: String,
        operator: OperatorProfile = TestData.operator,
        order: OrderWithItems = paid,
    ): File = runBlocking {
        PdfReceiptGenerator().generate(File(context.cacheDir, name), operator, buyer, order, zone, issuedAt)
    }

    private fun pageCount(pdf: File): Int =
        ParcelFileDescriptor.open(pdf, ParcelFileDescriptor.MODE_READ_ONLY).use { fd -> PdfRenderer(fd).use { it.pageCount } }

    /** Renders one page at 1.5x so a person (or the pixel checks below) can inspect it. */
    private fun render(pdf: File, pngName: String, pageIndex: Int = 0): Bitmap {
        ParcelFileDescriptor.open(pdf, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
            PdfRenderer(fd).use { renderer ->
                renderer.openPage(pageIndex).use { page ->
                    val bitmap = Bitmap.createBitmap((page.width * 1.5f).toInt(), (page.height * 1.5f).toInt(), Bitmap.Config.ARGB_8888)
                    bitmap.eraseColor(Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    FileOutputStream(File(artifacts, pngName)).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    return bitmap
                }
            }
        }
    }

    /** Counts samples tinted by the semi-transparent PAID stamp in the page's middle band. */
    private fun pinkishSamples(bitmap: Bitmap): Int {
        var pinkish = 0
        for (y in 300 until bitmap.height - 200 step 3) {
            for (x in 0 until bitmap.width step 3) {
                val p = bitmap.getPixel(x, y)
                if (Color.red(p) > 235 && Color.green(p) in 200..245 && Color.blue(p) in 200..245 && Color.green(p) < Color.red(p) - 6) pinkish++
            }
        }
        return pinkish
    }

    @Test
    fun a_one_product_order_is_a_single_a4_page() {
        val pdf = generate("receipt-basic.pdf")
        assertTrue(pdf.length() > 1_000)
        assertEquals("%PDF", pdf.inputStream().use { String(it.readNBytes(4)) })
        assertEquals(1, pageCount(pdf))
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
        val pinkish = pinkishSamples(bitmap)
        assertTrue("expected a visible semi-transparent stamp, found $pinkish tinted samples", pinkish > 400)
    }

    @Test
    fun a_few_products_are_itemized_on_one_page() {
        val order = TestData.orderWithItems(
            11, 1,
            listOf(
                TestData.item("Velvet Sofa, Blush", "12500.50", 1),
                TestData.item("Throw Pillow", "450", 4),
                TestData.item("Marble Side Table", "3500", 1),
            ),
        )
        val pdf = generate("receipt-items.pdf", order = order)
        assertEquals(1, pageCount(pdf))
        render(pdf, "receipt-items.png")
    }

    @Test
    fun a_long_order_continues_on_further_pages_with_the_stamp_on_each() {
        val items = (1..30).map { n ->
            TestData.item("Showroom piece number $n with a fairly long descriptive product name", "${n * 100}.50", n % 5 + 1)
        }
        val pdf = generate("receipt-many.pdf", order = TestData.orderWithItems(12, 1, items))

        val pages = pageCount(pdf)
        assertTrue("30 long lines should need more than one page, got $pages", pages >= 2)
        (0 until pages).forEach { index ->
            val bitmap = render(pdf, "receipt-many-page${index + 1}.png", index)
            // Every page carries the brand band and the PAID stamp.
            assertTrue(isNear(bitmap.getPixel(8, 8), 0x88, 0x0E, 0x4F, tolerance = 12))
            assertTrue("page ${index + 1} should show the stamp", pinkishSamples(bitmap) > 200)
        }
    }

    @Test
    fun renders_without_an_operator_photo_or_email() {
        val bare = TestData.operator.copy(email = "", phoneNumber = "", profileImageUri = null)
        val bitmap = render(generate("receipt-bare.pdf", operator = bare), "receipt-bare.png")
        assertTrue(isNear(bitmap.getPixel(8, 8), 0x88, 0x0E, 0x4F, tolerance = 12))
    }

    @Test
    fun long_product_names_and_huge_amounts_do_not_break_the_layout() {
        val longName = "Hand-woven rattan lounge chair with removable blush velvet cushions and matching ottoman set, limited edition"
        val order = TestData.orderWithItems(123_456, 1, listOf(TestData.item(longName, "9999999.99", 9_999)))
        val pdf = generate("receipt-long.pdf", order = order)
        assertEquals(1, pageCount(pdf))
        render(pdf, "receipt-long.png")
    }

    @Test
    fun uses_an_operator_photo_when_one_exists() {
        val photo = File(context.cacheDir, "operator.jpg")
        val source = Bitmap.createBitmap(300, 200, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.rgb(30, 200, 60)) }
        FileOutputStream(photo).use { source.compress(Bitmap.CompressFormat.JPEG, 90, it) }

        val bitmap = render(generate("receipt-photo.pdf", operator = TestData.operator.copy(profileImageUri = android.net.Uri.fromFile(photo).toString())), "receipt-photo.png")

        // Avatar circle is centered near (70, 68) in PDF points, i.e. x1.5 in the bitmap: it should now be green.
        val p = bitmap.getPixel((70 * 1.5f).toInt(), (68 * 1.5f).toInt())
        assertTrue("avatar should show the green photo but was #${Integer.toHexString(p)}", Color.green(p) > 150 && Color.red(p) < 100)
    }

    @Test
    fun refuses_to_issue_a_receipt_for_an_unpaid_order() {
        listOf(PaymentStatus.PENDING, PaymentStatus.UNPAID).forEach { status ->
            try {
                generate("receipt-unpaid.pdf", order = paid.copy(order = paid.order.copy(paymentStatus = status)))
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
            generate("receipt-mismatch.pdf", order = paid.copy(order = paid.order.copy(buyerId = 99)))
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
