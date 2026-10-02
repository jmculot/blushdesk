package com.blushdesk.app.export

import com.blushdesk.app.data.local.OrderStatus
import com.blushdesk.app.data.local.PaymentMode
import com.blushdesk.app.data.local.PaymentStatus
import com.blushdesk.app.data.local.entity.Buyer
import com.blushdesk.app.data.local.entity.OperatorProfile
import com.blushdesk.app.data.local.entity.Order
import com.blushdesk.app.data.local.relation.BuyerWithOrders
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
 * POI also works inside Android's runtime is checked separately by the instrumented test.
 */
class ExcelExporterTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val zone = ZoneId.of("Asia/Manila")
    private val exporter = ExcelExporter(zone)
    private val operator = OperatorProfile(fullName = "Lia Santos", storeName = "Rosé Showroom", email = "lia@rose.example", phone = "0917 555 0100")

    private val ana = Buyer(id = 1, fullName = "Ana Reyes", contact = "0917 123 4567", email = "ana@example.com", dateAdded = Instant.parse("2026-09-01T02:00:00Z"))
    private val ben = Buyer(id = 2, fullName = "Ben Cruz", contact = "0918 765 4321", dateAdded = Instant.parse("2026-09-05T02:00:00Z"))

    private fun order(
        id: Long, buyer: Buyer, product: String, unit: Long, qty: Int, at: String,
        mode: PaymentMode, pay: PaymentStatus, stage: OrderStatus,
    ) = Order(id, buyer.id, product, unit, qty, Instant.parse(at), mode, pay, stage)

    private val data = listOf(
        BuyerWithOrders(
            ana,
            listOf(
                order(10, ana, "Velvet Sofa", 1_250_050, 2, "2026-09-20T03:00:00Z", PaymentMode.ONLINE, PaymentStatus.PAID, OrderStatus.DELIVERED),
                order(11, ana, "Side Table", 350_000, 1, "2026-10-01T03:00:00Z", PaymentMode.CASH, PaymentStatus.PENDING, OrderStatus.PREPARING),
            ),
        ),
        BuyerWithOrders(
            ben,
            listOf(order(12, ben, "Floor Lamp", 99_999, 3, "2026-09-25T03:00:00Z", PaymentMode.CASH, PaymentStatus.UNPAID, OrderStatus.PROCESSING)),
        ),
    )

    private suspend fun export(buyers: List<BuyerWithOrders> = data): File =
        exporter.export(File(folder.root, "out.xlsx"), operator, buyers, Instant.parse("2026-10-02T07:45:00Z"))

    private fun XSSFSheet.text(row: Int, col: Int): String = getRow(row).getCell(col).stringCellValue
    private fun XSSFSheet.number(row: Int, col: Int): Double = getRow(row).getCell(col).numericCellValue

    @Test
    fun `workbook has summary, orders and buyers sheets in that order`() = runTest {
        XSSFWorkbook(export().inputStream()).use { wb ->
            assertEquals(listOf("Summary", "Orders", "Buyers"), (0 until wb.numberOfSheets).map { wb.getSheetName(it) })
        }
    }

    @Test
    fun `orders sheet has one row per order, oldest first, with exact amounts`() = runTest {
        XSSFWorkbook(export().inputStream()).use { wb ->
            val sheet = wb.getSheet("Orders")
            assertEquals(3, sheet.lastRowNum) // header + 3 orders

            val headers = (0 until 12).map { sheet.text(0, it) }
            assertEquals(
                listOf(
                    "Order #", "Buyer", "Contact", "Email", "Product", "Unit price", "Qty", "Total",
                    "Purchased", "Payment mode", "Payment status", "Fulfillment stage",
                ),
                headers,
            )

            // Chronological: Sofa (Sep 20), Lamp (Sep 25), Side Table (Oct 1).
            assertEquals(listOf("Velvet Sofa", "Floor Lamp", "Side Table"), (1..3).map { sheet.text(it, 4) })

            assertEquals("BD-000010", sheet.text(1, 0))
            assertEquals("Ana Reyes", sheet.text(1, 1))
            assertEquals(12_500.50, sheet.number(1, 5), 0.0)
            assertEquals(2.0, sheet.number(1, 6), 0.0)
            assertEquals(25_001.00, sheet.number(1, 7), 0.0)
            assertEquals("Online payment", sheet.text(1, 9))
            assertEquals("Paid", sheet.text(1, 10))
            assertEquals("Delivered", sheet.text(1, 11))

            // 999.99 x 3 must be exactly 2999.97, which a double multiplication would not guarantee.
            assertEquals(2_999.97, sheet.number(2, 7), 0.0)
            assertEquals("Unpaid", sheet.text(2, 10))
            assertEquals("Processing", sheet.text(2, 11))
        }
    }

    @Test
    fun `purchase time is written as a real date in the local zone`() = runTest {
        XSSFWorkbook(export().inputStream()).use { wb ->
            val cell = wb.getSheet("Orders").getRow(1).getCell(8)
            assertEquals(CellType.NUMERIC, cell.cellType)
            // 2026-09-20T03:00Z is 11:00 in Manila.
            val local = cell.localDateTimeCellValue
            assertEquals(2026, local.year)
            assertEquals(9, local.monthValue)
            assertEquals(20, local.dayOfMonth)
            assertEquals(11, local.hour)
        }
    }

    @Test
    fun `buyers sheet totals billed, paid and outstanding per buyer`() = runTest {
        XSSFWorkbook(export().inputStream()).use { wb ->
            val sheet = wb.getSheet("Buyers")
            assertEquals(2, sheet.lastRowNum)
            assertEquals("Ana Reyes", sheet.text(1, 1))
            assertEquals(2.0, sheet.number(1, 5), 0.0)
            assertEquals(28_501.00, sheet.number(1, 6), 0.0) // 25,001.00 + 3,500.00
            assertEquals(25_001.00, sheet.number(1, 7), 0.0)
            assertEquals(3_500.00, sheet.number(1, 8), 0.0)
            assertEquals("Ben Cruz", sheet.text(2, 1))
            assertEquals(0.0, sheet.number(2, 7), 0.0)
            assertEquals(2_999.97, sheet.number(2, 8), 0.0)
        }
    }

    @Test
    fun `summary sheet carries the showroom details and grand totals`() = runTest {
        XSSFWorkbook(export().inputStream()).use { wb ->
            val sheet = wb.getSheet("Summary")
            val cells = (0..sheet.lastRowNum).mapNotNull { sheet.getRow(it) }
                .associate { row -> (row.getCell(0)?.stringCellValue.orEmpty()) to row }

            assertEquals("Lia Santos", cells.getValue("Operator").getCell(1).stringCellValue)
            assertEquals("Rosé Showroom", cells.getValue("Store").getCell(1).stringCellValue)
            assertEquals(3.0, cells.getValue("Orders").getCell(1).numericCellValue, 0.0)
            assertEquals(2.0, cells.getValue("Buyers").getCell(1).numericCellValue, 0.0)
            assertEquals(31_500.97, cells.getValue("Total billed").getCell(1).numericCellValue, 0.0)
            assertEquals(25_001.00, cells.getValue("Collected (paid)").getCell(1).numericCellValue, 0.0)
            assertEquals(6_499.97, cells.getValue("Outstanding (pending + unpaid)").getCell(1).numericCellValue, 0.0)
            // One order per stage and per payment status in the sample.
            assertEquals(1.0, cells.getValue("Delivered").getCell(2).numericCellValue, 0.0)
            assertEquals(1.0, cells.getValue("Pending").getCell(2).numericCellValue, 0.0)
        }
    }

    @Test
    fun `tables are filterable with a frozen header row`() = runTest {
        XSSFWorkbook(export().inputStream()).use { wb ->
            listOf("Orders", "Buyers").forEach { name ->
                val sheet = wb.getSheet(name)
                assertTrue("$name should have a filter", sheet.ctWorksheet.isSetAutoFilter)
                assertNotNull("$name should freeze its header", sheet.paneInformation)
                assertEquals(1, sheet.paneInformation.horizontalSplitPosition.toInt())
            }
        }
    }

    @Test
    fun `exporting no buyers still produces a valid workbook`() = runTest {
        XSSFWorkbook(export(emptyList()).inputStream()).use { wb ->
            assertEquals(0, wb.getSheet("Orders").lastRowNum)
            assertEquals(0, wb.getSheet("Buyers").lastRowNum)
        }
    }

    @Test
    fun `no partial file is left behind and an existing file is replaced`() = runTest {
        val target = File(folder.root, "out.xlsx").apply { writeText("old") }
        exporter.export(target, operator, data)
        assertTrue(target.length() > 100)
        assertFalse(File(folder.root, "out.xlsx.part").exists())
    }
}
