package com.blushdesk.app.export

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.blushdesk.app.TestData
import kotlinx.coroutines.runBlocking
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.Instant
import java.util.zip.ZipFile

/**
 * Apache POI is a desktop-Java library. The JVM unit test proves the workbook's content; this one
 * proves the same code actually runs on Android's runtime (ART), which lacks java.awt, StAX and
 * other classes POI normally takes for granted.
 */
@RunWith(AndroidJUnit4::class)
class ExcelExporterAndroidTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    private fun exportSample(): File {
        val target = File(context.cacheDir, "instrumented-export.xlsx")
        return runBlocking {
            ExcelExporter().export(target, TestData.operator, TestData.sampleExport(), Instant.parse("2026-10-02T07:45:00Z"))
        }
    }

    @Test
    fun writes_a_real_xlsx_package() {
        val file = exportSample()
        assertTrue(file.length() > 2_000)
        ZipFile(file).use { zip ->
            val entries = zip.entries().asSequence().map { it.name }.toSet()
            assertTrue("workbook part", "xl/workbook.xml" in entries)
            assertTrue("styles part", "xl/styles.xml" in entries)
            assertTrue("three worksheets", (1..3).all { "xl/worksheets/sheet$it.xml" in entries })
        }
    }

    @Test
    fun reads_back_with_the_expected_sheets_and_values() {
        val file = exportSample()
        file.inputStream().use { input ->
            XSSFWorkbook(input).use { workbook ->
                assertEquals(listOf("Summary", "Orders", "Buyers"), (0 until workbook.numberOfSheets).map { workbook.getSheetName(it) })

                val orders = workbook.getSheet("Orders")
                assertEquals(3, orders.lastRowNum)
                assertEquals("Velvet Sofa", orders.getRow(1).getCell(4).stringCellValue)
                assertEquals(25_001.00, orders.getRow(1).getCell(7).numericCellValue, 0.0)
                assertEquals("Paid", orders.getRow(1).getCell(10).stringCellValue)
                assertEquals("Delivered", orders.getRow(1).getCell(11).stringCellValue)
                // Same purchase time for all three, so rows follow order id: 10, 11, 12.
                assertEquals("Preparing", orders.getRow(2).getCell(11).stringCellValue)
                assertEquals("Processing", orders.getRow(3).getCell(11).stringCellValue)

                val buyers = workbook.getSheet("Buyers")
                assertEquals(2, buyers.lastRowNum)
                assertEquals("Ana Reyes", buyers.getRow(1).getCell(1).stringCellValue)
            }
        }
    }

    @Test
    fun the_peso_sign_survives_into_the_number_format() {
        exportSample().inputStream().use { input ->
            XSSFWorkbook(input).use { workbook ->
                val format = workbook.getSheet("Orders").getRow(1).getCell(5).cellStyle.dataFormatString
                assertTrue(format, format.contains("₱"))
            }
        }
    }

    @Test
    fun exporting_twice_replaces_the_file_and_leaves_no_partial_copy() {
        exportSample()
        val file = exportSample()
        assertTrue(file.exists())
        assertTrue(!File(file.parentFile, file.name + ".part").exists())
    }
}
