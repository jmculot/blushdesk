package com.blushdesk.app.utils

import com.blushdesk.app.data.local.database.Buyer
import com.blushdesk.app.data.local.database.BuyerWithOrders
import com.blushdesk.app.data.local.database.ExportSummary
import com.blushdesk.app.data.local.database.OperatorProfile
import com.blushdesk.app.data.local.database.Order
import com.blushdesk.app.domain.model.ExportSnapshot
import com.blushdesk.app.domain.model.FulfillmentStatus
import com.blushdesk.app.domain.model.PaymentMode
import com.blushdesk.app.domain.model.PaymentStatus
import kotlinx.coroutines.test.runTest
import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.xssf.usermodel.XSSFSheet
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.Instant
import java.time.ZoneId

/**
 * Runs the exporter on the desktop JVM, which is fast and checks the workbook's content. Whether
 * POI also runs inside Android's runtime is checked by the instrumented test.
 */
class ExcelExporterTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val zone = ZoneId.of("Asia/Manila")
    private val exporter = ExcelExporter(zone)
    private val operator = OperatorProfile(
        fullName = "Lia Santos", storeName = "Rosé Showroom", email = "lia@rose.example", phoneNumber = "0917 555 0100",
    )

    private val ana = Buyer(id = 1, fullName = "Ana Reyes", contactNumber = "0917 123 4567", email = "ana@example.com", dateAdded = Instant.parse("2026-09-01T02:00:00Z"))
    private val ben = Buyer(id = 2, fullName = "Ben Cruz", contactNumber = "0918 765 4321", dateAdded = Instant.parse("2026-09-05T02:00:00Z"))

    private fun order(
        id: Long, buyer: Buyer, product: String, unit: String, qty: Int, at: String,
        mode: PaymentMode, pay: PaymentStatus, stage: FulfillmentStatus,
    ) = Order(
        id = id, buyerId = buyer.id, productName = product, unitPrice = Money.of(unit), quantity = qty,
        purchaseDateTime = Instant.parse(at), paymentMode = mode, paymentStatus = pay, fulfillmentStatus = stage,
    )

    private val buyers = listOf(
        BuyerWithOrders(
            ana,
            listOf(
                order(10, ana, "Velvet Sofa", "12500.50", 2, "2026-09-20T03:00:00Z", PaymentMode.ONLINE_PAYMENT, PaymentStatus.PAID, FulfillmentStatus.DELIVERED),
                order(11, ana, "Side Table", "3500", 1, "2026-10-01T03:00:00Z", PaymentMode.CASH, PaymentStatus.PENDING, FulfillmentStatus.PREPARING),
            ),
        ),
        BuyerWithOrders(
            ben,
            listOf(order(12, ben, "Floor Lamp", "999.99", 3, "2026-09-25T03:00:00Z", PaymentMode.CASH, PaymentStatus.UNPAID, FulfillmentStatus.PROCESSING)),
        ),
    )

    private val summary = ExportSummary(
        totalBuyers = 2, totalOrders = 3, paidOrders = 1, unpaidOrders = 1, pendingOrders = 1,
        processingOrders = 1, preparingOrders = 1, deliveredOrders = 1, totalRecordedSales = Money.of("31500.97"),
    )

    private suspend fun export(snapshotBuyers: List<BuyerWithOrders> = buyers): File = exporter.export(
        File(folder.root, "out.xlsx"),
        ExportSnapshot(operator, snapshotBuyers, summary, Instant.parse("2026-10-02T07:45:00Z")),
    )

    private fun XSSFSheet.text(row: Int, col: Int): String = getRow(row).getCell(col).stringCellValue
    private fun XSSFSheet.number(row: Int, col: Int): Double = getRow(row).getCell(col).numericCellValue
    private fun XSSFSheet.headers(count: Int) = (0 until count).map { text(0, it) }

    @Test
    fun `workbook has the four sheets in the specified order`() = runTest {
        XSSFWorkbook(export().inputStream()).use { wb ->
            assertEquals(listOf("Buyers", "Orders", "Operator", "Summary"), (0 until wb.numberOfSheets).map { wb.getSheetName(it) })
        }
    }

    @Test
    fun `buyers sheet has the specified columns and one row per buyer`() = runTest {
        XSSFWorkbook(export().inputStream()).use { wb ->
            val sheet = wb.getSheet("Buyers")
            assertEquals(
                listOf("Buyer ID", "Full Name", "Contact Number", "Email", "Date Added", "Number of Orders"),
                sheet.headers(6),
            )
            assertEquals(2, sheet.lastRowNum)
            assertEquals(1.0, sheet.number(1, 0), 0.0)
            assertEquals("Ana Reyes", sheet.text(1, 1))
            assertEquals("0917 123 4567", sheet.text(1, 2))
            assertEquals("ana@example.com", sheet.text(1, 3))
            assertEquals(2026, sheet.getRow(1).getCell(4).localDateTimeCellValue.year)
            assertEquals(2.0, sheet.number(1, 5), 0.0)
            assertEquals(1.0, sheet.number(2, 5), 0.0)
        }
    }

    @Test
    fun `orders sheet has the specified columns, oldest first, with exact amounts`() = runTest {
        XSSFWorkbook(export().inputStream()).use { wb ->
            val sheet = wb.getSheet("Orders")
            assertEquals(
                listOf(
                    "Order ID", "Buyer ID", "Buyer Name", "Product", "Unit Price", "Quantity", "Total Amount",
                    "Purchase Date", "Purchase Time", "Payment Mode", "Payment Status", "Fulfillment Status",
                ),
                sheet.headers(12),
            )
            assertEquals(3, sheet.lastRowNum)
            // Chronological: Sofa (Sep 20), Lamp (Sep 25), Side Table (Oct 1).
            assertEquals(listOf("Velvet Sofa", "Floor Lamp", "Side Table"), (1..3).map { sheet.text(it, 3) })

            assertEquals(10.0, sheet.number(1, 0), 0.0)
            assertEquals(1.0, sheet.number(1, 1), 0.0)
            assertEquals("Ana Reyes", sheet.text(1, 2))
            assertEquals(12_500.50, sheet.number(1, 4), 0.0)
            assertEquals(2.0, sheet.number(1, 5), 0.0)
            assertEquals(25_001.00, sheet.number(1, 6), 0.0)
            assertEquals("Online payment", sheet.text(1, 9))
            assertEquals("Paid", sheet.text(1, 10))
            assertEquals("Delivered", sheet.text(1, 11))

            assertEquals(2_999.97, sheet.number(2, 6), 0.0) // 999.99 x 3, exact
            assertEquals("Unpaid", sheet.text(2, 10))
            assertEquals("Processing", sheet.text(2, 11))
        }
    }

    @Test
    fun `purchase date and time are separate cells in the local zone`() = runTest {
        XSSFWorkbook(export().inputStream()).use { wb ->
            val row = wb.getSheet("Orders").getRow(1)
            // 2026-09-20T03:00Z is 11:00 AM in Manila.
            val date = row.getCell(7)
            assertEquals(CellType.NUMERIC, date.cellType)
            assertEquals(20, date.localDateTimeCellValue.dayOfMonth)
            assertEquals("d mmm yyyy", date.cellStyle.dataFormatString)

            val time = row.getCell(8)
            assertEquals(11.0 / 24.0, time.numericCellValue, 1e-9)
            assertEquals("h:mm AM/PM", time.cellStyle.dataFormatString)
        }
    }

    @Test
    fun `money cells carry a peso currency format`() = runTest {
        XSSFWorkbook(export().inputStream()).use { wb ->
            val format = wb.getSheet("Orders").getRow(1).getCell(4).cellStyle.dataFormatString
            assertTrue(format, format.contains("₱") && format.contains("#,##0.00"))
        }
    }

    @Test
    fun `operator sheet has the specified columns and the operator's details`() = runTest {
        XSSFWorkbook(export().inputStream()).use { wb ->
            val sheet = wb.getSheet("Operator")
            assertEquals(listOf("Operator Name", "Store Name", "Email", "Phone Number"), sheet.headers(4))
            assertEquals(listOf("Lia Santos", "Rosé Showroom", "lia@rose.example", "0917 555 0100"), (0..3).map { sheet.text(1, it) })
        }
    }

    @Test
    fun `summary sheet reports every specified figure`() = runTest {
        XSSFWorkbook(export().inputStream()).use { wb ->
            val sheet = wb.getSheet("Summary")
            val figures = (ExcelExporter.SUMMARY_HEADER_ROW + 1..sheet.lastRowNum).associate { r ->
                sheet.text(r, 0) to sheet.number(r, 1)
            }
            assertEquals(
                listOf(
                    "Total Buyers", "Total Orders", "Paid Orders", "Unpaid Orders", "Pending Orders",
                    "Processing Orders", "Preparing Orders", "Delivered Orders", "Total Recorded Sales",
                ),
                figures.keys.toList(),
            )
            assertEquals(2.0, figures.getValue("Total Buyers"), 0.0)
            assertEquals(3.0, figures.getValue("Total Orders"), 0.0)
            assertEquals(1.0, figures.getValue("Delivered Orders"), 0.0)
            assertEquals(31_500.97, figures.getValue("Total Recorded Sales"), 0.0)
        }
    }

    @Test
    fun `table sheets freeze their header and the order and buyer tables are filterable`() = runTest {
        XSSFWorkbook(export().inputStream()).use { wb ->
            listOf("Buyers", "Orders", "Operator").forEach { name ->
                val pane = wb.getSheet(name).paneInformation
                assertNotNull("$name should freeze its header", pane)
                assertEquals(1, pane.horizontalSplitPosition.toInt())
            }
            listOf("Buyers", "Orders").forEach { assertTrue(wb.getSheet(it).ctWorksheet.isSetAutoFilter) }
        }
    }

    @Test
    fun `an empty database still produces a valid workbook`() = runTest {
        XSSFWorkbook(export(emptyList()).inputStream()).use { wb ->
            assertEquals(0, wb.getSheet("Orders").lastRowNum)
            assertEquals(0, wb.getSheet("Buyers").lastRowNum)
        }
    }

    @Test
    fun `no partial file is left behind and an existing file is replaced`() = runTest {
        val target = File(folder.root, "out.xlsx").apply { writeText("old") }
        exporter.export(target, ExportSnapshot(operator, buyers, summary, Instant.EPOCH))
        assertTrue(target.length() > 100)
        assertFalse(File(folder.root, "out.xlsx.part").exists())
    }
}
