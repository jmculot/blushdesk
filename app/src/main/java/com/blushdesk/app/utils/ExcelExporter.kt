package com.blushdesk.app.utils

import com.blushdesk.app.data.local.database.BuyerWithOrders
import com.blushdesk.app.data.local.database.ExportSummary
import com.blushdesk.app.data.local.database.OperatorProfile
import com.blushdesk.app.domain.model.ExportSnapshot
import com.blushdesk.app.ui.theme.BrandPalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.poi.ss.usermodel.BorderStyle
import org.apache.poi.ss.usermodel.FillPatternType
import org.apache.poi.ss.usermodel.HorizontalAlignment
import org.apache.poi.ss.usermodel.VerticalAlignment
import org.apache.poi.ss.util.CellRangeAddress
import org.apache.poi.xssf.usermodel.XSSFCellStyle
import org.apache.poi.xssf.usermodel.XSSFColor
import org.apache.poi.xssf.usermodel.XSSFFont
import org.apache.poi.xssf.usermodel.XSSFRow
import org.apache.poi.xssf.usermodel.XSSFSheet
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.File
import java.io.FileOutputStream
import java.time.ZoneId

/**
 * Writes the whole database to a formatted .xlsx with four sheets: Buyers, Orders, Operator and
 * Summary. Runs on [Dispatchers.IO].
 *
 * Apache POI is a desktop-Java library, so a few of its habits are avoided on purpose:
 *  - No `autoSizeColumn`: it measures text with java.awt.font, which Android does not have.
 *    Column widths are fixed instead.
 *  - No formulas: POI stores no cached results, so previewers that do not recalculate (mail and
 *    Drive viewers) would show blanks. Figures are written as plain values.
 *  - The workbook is written to a `.part` file and renamed, so a failure never leaves a truncated
 *    file that could be shared as if it were good.
 *
 * Nothing here touches Android APIs, so the same class runs in plain JVM unit tests.
 */
class ExcelExporter(private val zone: ZoneId = ZoneId.systemDefault()) {

    /** Builds the workbook for [snapshot] into [destination] and returns it. */
    suspend fun export(destination: File, snapshot: ExportSnapshot): File = withContext(Dispatchers.IO) {
        destination.absoluteFile.parentFile?.mkdirs()
        val partial = File(destination.absoluteFile.parentFile, destination.name + ".part")
        try {
            XSSFWorkbook().use { workbook ->
                val styles = Styles(workbook)
                writeBuyers(workbook.createSheet(SHEET_BUYERS), styles, snapshot.buyers)
                writeOrders(workbook.createSheet(SHEET_ORDERS), styles, snapshot.buyers)
                writeOperator(workbook.createSheet(SHEET_OPERATOR), styles, snapshot.operator)
                writeSummary(workbook.createSheet(SHEET_SUMMARY), styles, snapshot)

                workbook.properties.coreProperties.apply {
                    creator = "BlushDesk"
                    title = "${snapshot.operator.storeName.ifBlank { "Showroom" }} export"
                }
                FileOutputStream(partial).use { workbook.write(it) }
            }
            if (destination.exists()) destination.delete()
            if (!partial.renameTo(destination)) {
                partial.copyTo(destination, overwrite = true)
                partial.delete()
            }
        } catch (e: Throwable) {
            partial.delete()
            throw e
        }
        destination
    }

    // ---- Worksheet 1: Buyers ----------------------------------------------------------------

    private fun writeBuyers(sheet: XSSFSheet, styles: Styles, buyers: List<BuyerWithOrders>) {
        writeHeader(sheet, styles, BUYER_COLUMNS, widths = listOf(10, 26, 18, 30, 16, 18))
        buyers.forEachIndexed { index, (buyer, orders) ->
            val zebra = index % 2 == 1
            sheet.createRow(index + 1).apply {
                number(0, buyer.id.toDouble(), styles.body(Kind.ID, zebra))
                text(1, buyer.fullName, styles.body(Kind.TEXT, zebra))
                text(2, buyer.contactNumber, styles.body(Kind.TEXT, zebra))
                text(3, buyer.email, styles.body(Kind.TEXT, zebra))
                createCell(4).apply {
                    setCellValue(buyer.dateAdded.atZone(zone).toLocalDate())
                    cellStyle = styles.body(Kind.DATE, zebra)
                }
                number(5, orders.size.toDouble(), styles.body(Kind.COUNT, zebra))
            }
        }
        finishTable(sheet, BUYER_COLUMNS.size, buyers.size)
    }

    // ---- Worksheet 2: Orders ----------------------------------------------------------------

    private fun writeOrders(sheet: XSSFSheet, styles: Styles, buyers: List<BuyerWithOrders>) {
        writeHeader(sheet, styles, ORDER_COLUMNS, widths = listOf(10, 10, 24, 32, 15, 10, 16, 15, 14, 17, 16, 18))
        val rows = buyers
            .flatMap { entry -> entry.orders.map { entry.buyer to it } }
            .sortedWith(compareBy({ it.second.purchaseDateTime }, { it.second.id }))

        rows.forEachIndexed { index, (buyer, order) ->
            val zebra = index % 2 == 1
            val purchased = order.purchaseDateTime.atZone(zone)
            sheet.createRow(index + 1).apply {
                number(0, order.id.toDouble(), styles.body(Kind.ID, zebra))
                number(1, buyer.id.toDouble(), styles.body(Kind.ID, zebra))
                text(2, buyer.fullName, styles.body(Kind.TEXT, zebra))
                text(3, order.productName, styles.body(Kind.TEXT, zebra))
                number(4, Money.toExcelNumber(order.unitPrice), styles.body(Kind.MONEY, zebra))
                number(5, order.quantity.toDouble(), styles.body(Kind.COUNT, zebra))
                number(6, Money.toExcelNumber(order.totalAmount), styles.body(Kind.MONEY_BOLD, zebra))
                createCell(7).apply {
                    setCellValue(purchased.toLocalDate())
                    cellStyle = styles.body(Kind.DATE, zebra)
                }
                // An Excel time is the fraction of a day.
                number(8, purchased.toLocalTime().toSecondOfDay() / SECONDS_PER_DAY, styles.body(Kind.TIME, zebra))
                text(9, order.paymentMode.label, styles.body(Kind.TEXT, zebra))
                text(10, order.paymentStatus.label, styles.badge(BrandPalette.tone(order.paymentStatus)))
                text(11, order.fulfillmentStatus.label, styles.badge(BrandPalette.tone(order.fulfillmentStatus)))
            }
        }
        finishTable(sheet, ORDER_COLUMNS.size, rows.size)
    }

    // ---- Worksheet 3: Operator --------------------------------------------------------------

    private fun writeOperator(sheet: XSSFSheet, styles: Styles, operator: OperatorProfile) {
        writeHeader(sheet, styles, OPERATOR_COLUMNS, widths = listOf(26, 28, 30, 18))
        sheet.createRow(1).apply {
            text(0, operator.fullName, styles.body(Kind.TEXT, false))
            text(1, operator.storeName, styles.body(Kind.TEXT, false))
            text(2, operator.email, styles.body(Kind.TEXT, false))
            text(3, operator.phoneNumber, styles.body(Kind.TEXT, false))
        }
        sheet.createFreezePane(0, 1)
        landscapeFitToWidth(sheet)
    }

    // ---- Worksheet 4: Summary ---------------------------------------------------------------

    private fun writeSummary(sheet: XSSFSheet, styles: Styles, snapshot: ExportSnapshot) {
        sheet.setColumnWidth(0, 30 * 256)
        sheet.setColumnWidth(1, 22 * 256)
        sheet.setDisplayGridlines(false)

        val store = snapshot.operator.storeName.ifBlank { "Showroom" }
        sheet.createRow(0).apply {
            heightInPoints = 30f
            text(0, "$store - Summary", styles.title)
        }
        sheet.addMergedRegion(CellRangeAddress(0, 0, 0, 1))
        sheet.createRow(1).text(0, "Generated ${Formats.dateTime(snapshot.takenAt, zone)}", styles.subtitle)

        val headerRow = SUMMARY_HEADER_ROW
        sheet.createRow(headerRow).apply {
            heightInPoints = 22f
            text(0, "Metric", styles.header)
            text(1, "Value", styles.header)
        }
        summaryLines(snapshot.summary).forEachIndexed { index, (label, value) ->
            val zebra = index % 2 == 1
            sheet.createRow(headerRow + 1 + index).apply {
                text(0, label, styles.label(zebra))
                when (value) {
                    is SummaryValue.Count -> number(1, value.count.toDouble(), styles.body(Kind.COUNT, zebra))
                    is SummaryValue.Amount -> number(1, Money.toExcelNumber(value.amount), styles.body(Kind.MONEY_BOLD, zebra))
                }
            }
        }
        landscapeFitToWidth(sheet)
    }

    private sealed interface SummaryValue {
        data class Count(val count: Int) : SummaryValue
        data class Amount(val amount: java.math.BigDecimal) : SummaryValue
    }

    private fun summaryLines(s: ExportSummary): List<Pair<String, SummaryValue>> = listOf(
        "Total Buyers" to SummaryValue.Count(s.totalBuyers),
        "Total Orders" to SummaryValue.Count(s.totalOrders),
        "Paid Orders" to SummaryValue.Count(s.paidOrders),
        "Unpaid Orders" to SummaryValue.Count(s.unpaidOrders),
        "Pending Orders" to SummaryValue.Count(s.pendingOrders),
        "Processing Orders" to SummaryValue.Count(s.processingOrders),
        "Preparing Orders" to SummaryValue.Count(s.preparingOrders),
        "Delivered Orders" to SummaryValue.Count(s.deliveredOrders),
        "Total Recorded Sales" to SummaryValue.Amount(s.totalRecordedSales),
    )

    // ---- Shared sheet helpers ---------------------------------------------------------------

    private fun writeHeader(sheet: XSSFSheet, styles: Styles, titles: List<String>, widths: List<Int>) {
        val header = sheet.createRow(0)
        header.heightInPoints = 24f
        titles.forEachIndexed { index, title ->
            header.text(index, title, styles.header)
            sheet.setColumnWidth(index, widths[index] * 256)
        }
    }

    /** Freeze the header, add filter dropdowns and print landscape on one page width. */
    private fun finishTable(sheet: XSSFSheet, columnCount: Int, dataRows: Int) {
        sheet.createFreezePane(0, 1)
        sheet.setAutoFilter(CellRangeAddress(0, maxOf(dataRows, 1), 0, columnCount - 1))
        landscapeFitToWidth(sheet)
    }

    private fun landscapeFitToWidth(sheet: XSSFSheet) {
        sheet.printSetup.landscape = true
        sheet.fitToPage = true
        sheet.printSetup.fitWidth = 1
        sheet.printSetup.fitHeight = 0
    }

    private fun XSSFRow.text(col: Int, value: String, style: XSSFCellStyle) {
        createCell(col).apply {
            setCellValue(value)
            cellStyle = style
        }
    }

    private fun XSSFRow.number(col: Int, value: Double, style: XSSFCellStyle) {
        createCell(col).apply {
            setCellValue(value)
            cellStyle = style
        }
    }

    private enum class Kind { TEXT, ID, COUNT, MONEY, MONEY_BOLD, DATE, TIME }

    /**
     * Cell styles are workbook-wide objects with a hard cap (64,000 in .xlsx), so each distinct
     * look is created once and reused for every cell that needs it.
     */
    private class Styles(private val workbook: XSSFWorkbook) {
        private val cache = HashMap<String, XSSFCellStyle>()
        private val formats = workbook.createDataFormat()

        val title = cached("title") {
            setFont(font(size = 18, bold = true, color = BrandPalette.DEEP_MAGENTA))
            verticalAlignment = VerticalAlignment.CENTER
        }
        val subtitle = cached("subtitle") {
            setFont(font(size = 10, italic = true, color = BrandPalette.MUTED_TEXT))
        }
        val header = cached("header") {
            setFont(font(size = 11, bold = true, color = BrandPalette.WHITE))
            fill(BrandPalette.VIBRANT_ROSE)
            alignment = HorizontalAlignment.CENTER
            verticalAlignment = VerticalAlignment.CENTER
            wrapText = true
            border()
        }

        fun label(zebra: Boolean): XSSFCellStyle = cached("label-$zebra") {
            setFont(font(bold = true, color = BrandPalette.DEEP_MAGENTA))
            if (zebra) fill(BrandPalette.LAVENDER_BLUSH)
            border()
        }

        fun body(kind: Kind, zebra: Boolean): XSSFCellStyle = cached("body-$kind-$zebra") {
            setFont(font(bold = kind == Kind.MONEY_BOLD))
            if (zebra) fill(BrandPalette.LAVENDER_BLUSH)
            verticalAlignment = VerticalAlignment.CENTER
            border()
            when (kind) {
                Kind.TEXT -> Unit
                Kind.ID -> dataFormat = formats.getFormat("0")
                Kind.COUNT -> dataFormat = formats.getFormat("#,##0")
                Kind.MONEY, Kind.MONEY_BOLD -> dataFormat = formats.getFormat("\"${Money.SYMBOL}\"#,##0.00")
                Kind.DATE -> dataFormat = formats.getFormat("d mmm yyyy")
                Kind.TIME -> dataFormat = formats.getFormat("h:mm AM/PM")
            }
            if (kind == Kind.DATE || kind == Kind.TIME || kind == Kind.ID) alignment = HorizontalAlignment.LEFT
        }

        fun badge(tone: BrandPalette.Tone): XSSFCellStyle = cached("badge-${tone.foreground}-${tone.background}") {
            setFont(font(bold = true, color = tone.foreground))
            fill(tone.background)
            alignment = HorizontalAlignment.CENTER
            verticalAlignment = VerticalAlignment.CENTER
            border()
        }

        private fun cached(key: String, build: XSSFCellStyle.() -> Unit): XSSFCellStyle =
            cache.getOrPut(key) { workbook.createCellStyle().apply(build) }

        private fun font(
            size: Int = 11,
            bold: Boolean = false,
            italic: Boolean = false,
            color: Int = BrandPalette.DARK_TEXT,
        ): XSSFFont = workbook.createFont().apply {
            fontHeightInPoints = size.toShort()
            this.bold = bold
            this.italic = italic
            setColor(rgb(color))
        }

        private fun XSSFCellStyle.fill(rgb: Int) {
            setFillForegroundColor(rgb(rgb))
            fillPattern = FillPatternType.SOLID_FOREGROUND
        }

        private fun XSSFCellStyle.border() {
            val color = rgb(BrandPalette.OUTLINE_SOFT)
            borderTop = BorderStyle.THIN
            borderBottom = BorderStyle.THIN
            borderLeft = BorderStyle.THIN
            borderRight = BorderStyle.THIN
            setTopBorderColor(color)
            setBottomBorderColor(color)
            setLeftBorderColor(color)
            setRightBorderColor(color)
        }

        private fun rgb(value: Int) = XSSFColor(
            byteArrayOf((value shr 16).toByte(), (value shr 8).toByte(), value.toByte()),
            null,
        )
    }

    companion object {
        const val SHEET_BUYERS = "Buyers"
        const val SHEET_ORDERS = "Orders"
        const val SHEET_OPERATOR = "Operator"
        const val SHEET_SUMMARY = "Summary"

        /** Row index of the "Metric | Value" header on the Summary sheet (after title and timestamp). */
        const val SUMMARY_HEADER_ROW = 3

        val BUYER_COLUMNS = listOf("Buyer ID", "Full Name", "Contact Number", "Email", "Date Added", "Number of Orders")

        val ORDER_COLUMNS = listOf(
            "Order ID", "Buyer ID", "Buyer Name", "Product", "Unit Price", "Quantity", "Total Amount",
            "Purchase Date", "Purchase Time", "Payment Mode", "Payment Status", "Fulfillment Status",
        )

        val OPERATOR_COLUMNS = listOf("Operator Name", "Store Name", "Email", "Phone Number")

        private const val SECONDS_PER_DAY = 86_400.0
    }
}
