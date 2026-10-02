package com.blushdesk.app.utils

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.blushdesk.app.TestData
import kotlinx.coroutines.runBlocking
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.zip.ZipFile

/**
 * Apache POI is a desktop-Java library. The JVM unit test proves the workbook's content; this one
 * proves the same code runs on Android's runtime (ART), which lacks java.awt, StAX and other
 * classes POI normally takes for granted.
 */
@RunWith(AndroidJUnit4::class)
class ExcelExporterAndroidTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    private fun exportSample(): File = runBlocking {
        ExcelExporter().export(File(context.cacheDir, "instrumented-export.xlsx"), TestData.sampleSnapshot())
    }

    @Test
    fun writes_a_real_xlsx_package_with_four_worksheets() {
        val file = exportSample()
        assertTrue(file.length() > 2_000)
        ZipFile(file).use { zip ->
            val entries = zip.entries().asSequence().map { it.name }.toSet()
            assertTrue("workbook part", "xl/workbook.xml" in entries)
            assertTrue("styles part", "xl/styles.xml" in entries)
            assertTrue("four worksheets", (1..4).all { "xl/worksheets/sheet$it.xml" in entries })
        }
    }

    @Test
    fun reads_back_on_android_with_the_specified_sheets_and_values() {
        exportSample().inputStream().use { input ->
            XSSFWorkbook(input).use { workbook ->
                assertEquals(listOf("Buyers", "Orders", "Operator", "Summary"), (0 until workbook.numberOfSheets).map { workbook.getSheetName(it) })

                val orders = workbook.getSheet("Orders")
                assertEquals(4, orders.lastRowNum) // one row per product line: 1 + 2 + 1
                assertEquals("Throw Pillow", orders.getRow(4).getCell(3).stringCellValue)
                assertEquals(5_300.00, orders.getRow(4).getCell(12).numericCellValue, 0.0) // that order's total
                assertEquals("Velvet Sofa", orders.getRow(1).getCell(3).stringCellValue)
                assertEquals(25_001.00, orders.getRow(1).getCell(6).numericCellValue, 0.0)
                assertEquals("Paid", orders.getRow(1).getCell(10).stringCellValue)
                assertEquals("Delivered", orders.getRow(1).getCell(11).stringCellValue)

                assertEquals("Ana Reyes", workbook.getSheet("Buyers").getRow(1).getCell(1).stringCellValue)
                assertEquals("Lia Santos", workbook.getSheet("Operator").getRow(1).getCell(0).stringCellValue)
            }
        }
    }

    @Test
    fun the_peso_sign_survives_into_the_number_format() {
        exportSample().inputStream().use { input ->
            XSSFWorkbook(input).use { workbook ->
                val format = workbook.getSheet("Orders").getRow(1).getCell(4).cellStyle.dataFormatString
                assertTrue(format, format.contains("₱"))
            }
        }
    }

    @Test
    fun exporting_twice_replaces_the_file_and_leaves_no_partial_copy() {
        exportSample()
        val file = exportSample()
        assertTrue(file.exists())
        assertFalse(File(file.parentFile, file.name + ".part").exists())
    }
}
