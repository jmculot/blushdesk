package com.blushdesk.app.export

import com.blushdesk.app.data.local.OrderStatus
import com.blushdesk.app.data.local.PaymentMode
import com.blushdesk.app.data.local.PaymentStatus
import com.blushdesk.app.data.local.entity.Buyer
import com.blushdesk.app.data.local.entity.OperatorProfile
import com.blushdesk.app.data.local.entity.Order
import com.blushdesk.app.data.local.relation.BuyerWithOrders
import com.blushdesk.app.domain.BrandPalette
import com.blushdesk.app.domain.Formats
import com.blushdesk.app.domain.Money
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
import java.time.Instant
import java.time.ZoneId

/**
 * Writes every buyer, product, amount, payment status and fulfillment stage to a styled .xlsx.
 *
 * Apache POI is a desktop-Java library, so a few of its habits are avoided here on purpose:
 *  - No `autoSizeColumn`: it measures text with java.awt.font, which does not exist on Android.
 *    Column widths are fixed instead.
 *  - No formulas: workbooks written by POI carry no cached results, so previewers that do not
 *    recalculate (mail and Drive viewers) would show blanks. Totals are written as plain numbers.
 *  - The stream is written to a `.part` file and renamed, so a failure never leaves a truncated
 *    workbook that could be shared as if it were good.
 *
 * Nothing here touches Android APIs, so the same class runs in plain JVM unit tests.
 */
class ExcelExporter(private val zone: ZoneId = ZoneId.systemDefault()) {

    /**
     * Builds the workbook into [destination] on the IO dispatcher and returns it.
     * Sheets: Summary (showroom details and totals), Orders (one row per order), Buyers.
     */
    suspend fun export(
        destination: File,
        operator: OperatorProfile,
        buyers: List<BuyerWithOrders>,
        generatedAt: Instant = Instant.now(),
    ): File = withContext(Dispatchers.IO) {
        destination.absoluteFile.parentFile?.mkdirs()
        val partial = File(destination.absoluteFile.parentFile, destination.name + ".part")
        try {
            XSSFWorkbook().use { workbook ->
                val styles = Styles(workbook)
                val rows = buyers
                    .flatMap { entry -> entry.orders.map { OrderRow(entry.buyer, it) } }
                    .sortedWith(compareBy({ it.order.purchasedAt }, { it.order.id }))

                writeSummary(workbook.createSheet("Summary"), styles, operator, buyers, rows, generatedAt)
                writeOrders(workbook.createSheet("Orders"), styles, rows)
                writeBuyers(workbook.createSheet("Buyers"), styles, buyers)

                workbook.properties.coreProperties.apply {
                    creator = "BlushDesk"
                    title = "${operator.storeName.ifBlank { "Showroom" }} export"
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

    private data class OrderRow(val buyer: Buyer, val order: Order)

    // ---- Summary ----------------------------------------------------------------------------

    private fun writeSummary(
        sheet: XSSFSheet,
        styles: Styles,
        operator: OperatorProfile,
        buyers: List<BuyerWithOrders>,
        rows: List<OrderRow>,
        generatedAt: Instant,
    ) {
        sheet.setColumnWidth(0, 30 * 256)
        sheet.setColumnWidth(1, 26 * 256)
        sheet.setColumnWidth(2, 14 * 256)
        sheet.setColumnWidth(3, 20 * 256)
        sheet.setDisplayGridlines(false)

        var r = 0
        sheet.createRow(r).apply {
            heightInPoints = 30f
            cell(0, "${operator.storeName.ifBlank { "Showroom" }}  -  Sales export", styles.title)
        }
        sheet.addMergedRegion(CellRangeAddress(r, r, 0, 3))
        r++
        sheet.createRow(r++).cell(0, "Generated ${Formats.dateTime(generatedAt, zone)}", styles.subtitle)
        r++

        fun section(title: String) {
            val row = sheet.createRow(r++)
            for (c in 0..3) row.cell(c, if (c == 0) title else "", styles.header)
            row.heightInPoints = 20f
        }

        fun line(label: String, value: String) {
            val row = sheet.createRow(r++)
            row.cell(0, label, styles.label)
            row.cell(1, value, styles.value)
        }

        fun count(label: String, value: Int) {
            val row = sheet.createRow(r++)
            row.cell(0, label, styles.label)
            row.number(1, value.toDouble(), styles.body(Kind.INT, false))
        }

        fun money(label: String, minor: Long, count: Int? = null) {
            val row = sheet.createRow(r++)
            row.cell(0, label, styles.label)
            row.number(1, Money.toExcelNumber(minor), styles.body(Kind.MONEY_BOLD, false))
            if (count != null) row.number(2, count.toDouble(), styles.body(Kind.INT, false))
        }

        section("Showroom")
        line("Operator", operator.fullName.ifBlank { "-" })
        line("Store", operator.storeName.ifBlank { "-" })
        line("Email", operator.email.ifBlank { "-" })
        line("Phone", operator.phone.ifBlank { "-" })
        r++

        val orders = rows.map { it.order }
        val billed = orders.sumOf { it.totalMinor }
        val paid = orders.filter { it.paymentStatus.isPaid }.sumOf { it.totalMinor }
        section("Totals")
        count("Buyers", buyers.size)
        count("Orders", orders.size)
        money("Total billed", billed)
        money("Collected (paid)", paid)
        money("Outstanding (pending + unpaid)", billed - paid)
        r++

        section("By fulfillment stage")
        OrderStatus.entries.forEach { stage ->
            val matching = orders.filter { it.orderStatus == stage }
            money(stage.label, matching.sumOf { it.totalMinor }, matching.size)
        }
        r++

        section("By payment status")
        PaymentStatus.entries.forEach { status ->
            val matching = orders.filter { it.paymentStatus == status }
            money(status.label, matching.sumOf { it.totalMinor }, matching.size)
        }
        r++

        section("By payment mode")
        PaymentMode.entries.forEach { mode ->
            val matching = orders.filter { it.paymentMode == mode }
            money(mode.label, matching.sumOf { it.totalMinor }, matching.size)
        }

        landscapeFitToWidth(sheet)
    }

    // ---- Orders -----------------------------------------------------------------------------

    private fun writeOrders(sheet: XSSFSheet, styles: Styles, rows: List<OrderRow>) {
        val columns = listOf(
            Column("Order #", 12), Column("Buyer", 24), Column("Contact", 16), Column("Email", 28),
            Column("Product", 32), Column("Unit price", 15), Column("Qty", 7), Column("Total", 16),
            Column("Purchased", 22), Column("Payment mode", 16), Column("Payment status", 16),
            Column("Fulfillment stage", 18),
        )
        writeHeader(sheet, styles, columns)

        rows.forEachIndexed { index, (buyer, order) ->
            val zebra = index % 2 == 1
            val row = sheet.createRow(index + 1)
            row.cell(0, Formats.orderNumber(order.id), styles.body(Kind.TEXT, zebra))
            row.cell(1, buyer.fullName, styles.body(Kind.TEXT, zebra))
            row.cell(2, buyer.contact, styles.body(Kind.TEXT, zebra))
            row.cell(3, buyer.email, styles.body(Kind.TEXT, zebra))
            row.cell(4, order.productName, styles.body(Kind.TEXT, zebra))
            row.number(5, Money.toExcelNumber(order.unitPriceMinor), styles.body(Kind.MONEY, zebra))
            row.number(6, order.quantity.toDouble(), styles.body(Kind.INT, zebra))
            row.number(7, Money.toExcelNumber(order.totalMinor), styles.body(Kind.MONEY_BOLD, zebra))
            row.createCell(8).apply {
                setCellValue(order.purchasedAt.atZone(zone).toLocalDateTime())
                cellStyle = styles.body(Kind.DATE_TIME, zebra)
            }
            row.cell(9, order.paymentMode.label, styles.body(Kind.TEXT, zebra))
            row.cell(10, order.paymentStatus.label, styles.badge(BrandPalette.tone(order.paymentStatus)))
            row.cell(11, order.orderStatus.label, styles.badge(BrandPalette.tone(order.orderStatus)))
        }
        finishTable(sheet, columns.size, rows.size)
    }

    // ---- Buyers -----------------------------------------------------------------------------

    private fun writeBuyers(sheet: XSSFSheet, styles: Styles, buyers: List<BuyerWithOrders>) {
        val columns = listOf(
            Column("Buyer ID", 10), Column("Name", 26), Column("Contact", 16), Column("Email", 28),
            Column("Date added", 16), Column("Orders", 9), Column("Total billed", 16),
            Column("Paid", 16), Column("Outstanding", 16),
        )
        writeHeader(sheet, styles, columns)

        buyers.forEachIndexed { index, entry ->
            val zebra = index % 2 == 1
            val billed = entry.orders.sumOf { it.totalMinor }
            val paid = entry.orders.filter { it.paymentStatus.isPaid }.sumOf { it.totalMinor }
            val row = sheet.createRow(index + 1)
            row.number(0, entry.buyer.id.toDouble(), styles.body(Kind.INT, zebra))
            row.cell(1, entry.buyer.fullName, styles.body(Kind.TEXT, zebra))
            row.cell(2, entry.buyer.contact, styles.body(Kind.TEXT, zebra))
            row.cell(3, entry.buyer.email, styles.body(Kind.TEXT, zebra))
            row.createCell(4).apply {
                setCellValue(entry.buyer.dateAdded.atZone(zone).toLocalDateTime())
                cellStyle = styles.body(Kind.DATE, zebra)
            }
            row.number(5, entry.orders.size.toDouble(), styles.body(Kind.INT, zebra))
            row.number(6, Money.toExcelNumber(billed), styles.body(Kind.MONEY, zebra))
            row.number(7, Money.toExcelNumber(paid), styles.body(Kind.MONEY, zebra))
            row.number(8, Money.toExcelNumber(billed - paid), styles.body(Kind.MONEY_BOLD, zebra))
        }
        finishTable(sheet, columns.size, buyers.size)
    }

    // ---- Shared sheet helpers ---------------------------------------------------------------

    private data class Column(val title: String, val widthChars: Int)

    private fun writeHeader(sheet: XSSFSheet, styles: Styles, columns: List<Column>) {
        val header = sheet.createRow(0)
        header.heightInPoints = 24f
        columns.forEachIndexed { index, column ->
            header.cell(index, column.title, styles.header)
            sheet.setColumnWidth(index, column.widthChars * 256)
        }
    }

    /** Freeze the header, add filter dropdowns and make it print landscape on one page width. */
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

    private fun XSSFRow.cell(col: Int, text: String, style: XSSFCellStyle) {
        createCell(col).apply {
            setCellValue(text)
            cellStyle = style
        }
    }

    private fun XSSFRow.number(col: Int, value: Double, style: XSSFCellStyle) {
        createCell(col).apply {
            setCellValue(value)
            cellStyle = style
        }
    }

    private enum class Kind { TEXT, INT, MONEY, MONEY_BOLD, DATE_TIME, DATE }

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
            setFont(font(size = 10, italic = true, color = BrandPalette.MUTED))
        }
        val header = cached("header") {
            setFont(font(size = 11, bold = true, color = BrandPalette.WHITE))
            fill(BrandPalette.VIBRANT_ROSE)
            alignment = HorizontalAlignment.CENTER
            verticalAlignment = VerticalAlignment.CENTER
            wrapText = true
            border()
        }
        val label = cached("label") {
            setFont(font(bold = true, color = BrandPalette.DEEP_MAGENTA))
            border()
        }
        val value = cached("value") {
            setFont(font())
            border()
        }

        fun body(kind: Kind, zebra: Boolean): XSSFCellStyle = cached("body-$kind-$zebra") {
            setFont(font(bold = kind == Kind.MONEY_BOLD))
            if (zebra) fill(BrandPalette.LAVENDER_BLUSH)
            verticalAlignment = VerticalAlignment.CENTER
            border()
            when (kind) {
                Kind.TEXT -> Unit
                Kind.INT -> dataFormat = formats.getFormat("#,##0")
                Kind.MONEY, Kind.MONEY_BOLD -> dataFormat = formats.getFormat("\"${Money.SYMBOL}\"#,##0.00")
                Kind.DATE_TIME -> dataFormat = formats.getFormat("d mmm yyyy  h:mm AM/PM")
                Kind.DATE -> dataFormat = formats.getFormat("d mmm yyyy")
            }
            if (kind == Kind.DATE_TIME || kind == Kind.DATE) alignment = HorizontalAlignment.LEFT
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
            color: Int = BrandPalette.INK,
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
            val color = rgb(BrandPalette.OUTLINE)
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
}
