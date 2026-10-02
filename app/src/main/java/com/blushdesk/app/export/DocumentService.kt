package com.blushdesk.app.export

import android.content.Context
import android.util.Log
import com.blushdesk.app.data.repository.ShowroomRepository
import com.blushdesk.app.data.storage.AppFiles
import com.blushdesk.app.data.storage.DownloadsSaver
import com.blushdesk.app.domain.Formats
import kotlinx.coroutines.CancellationException
import java.io.File
import java.time.Instant

/** A generated receipt: the cache file to open or share, and where its Downloads copy landed. */
data class ReceiptDocument(
    val file: File,
    val displayName: String,
    /** "Download/BlushDesk/<name>", or null if the copy to Downloads failed (the cache file is still good). */
    val savedTo: String?,
)

data class ExportDocument(val file: File, val displayName: String)

/**
 * Produces the two files the app creates. Kept behind an interface so the ViewModel can be tested
 * without a device: the real implementation needs Android's PdfDocument and MediaStore.
 */
interface DocumentService {
    /** Creates the PDF receipt for a paid order and saves a copy to Downloads. */
    suspend fun createReceipt(orderId: Long): ReceiptDocument

    /** Creates the .xlsx with every buyer and order in the database. */
    suspend fun createWorkbook(): ExportDocument
}

class AndroidDocumentService(
    private val context: Context,
    private val repository: ShowroomRepository,
    private val excel: ExcelExporter,
    private val pdf: PdfReceiptGenerator,
    private val downloads: DownloadsSaver,
) : DocumentService {

    override suspend fun createReceipt(orderId: Long): ReceiptDocument {
        val order = repository.getOrder(orderId) ?: error("That order no longer exists")
        check(order.paymentStatus.isPaid) { "Mark the order as paid first" }
        val buyer = repository.getBuyer(order.buyerId) ?: error("That buyer no longer exists")
        val operator = repository.getOperator()

        val folder = AppFiles.cacheDir(context, AppFiles.RECEIPTS_DIR)
        val name = "Receipt_${Formats.orderNumber(order.id)}_${Formats.fileSafe(buyer.fullName)}.pdf"
        val file = pdf.generate(File(folder, name), operator, buyer, order)

        // The receipt is already good in the cache; failing to copy it to Downloads must not lose it.
        val savedTo = try {
            downloads.save(file, name, AppFiles.MIME_PDF)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Could not copy receipt to Downloads", e)
            null
        }
        AppFiles.prune(folder, keep = 20)
        return ReceiptDocument(file, name, savedTo)
    }

    override suspend fun createWorkbook(): ExportDocument {
        val buyers = repository.getAllBuyersWithOrders()
        check(buyers.isNotEmpty()) { "There is nothing to export yet. Add a buyer first." }
        val operator = repository.getOperator()

        val now = Instant.now()
        val folder = AppFiles.cacheDir(context, AppFiles.EXPORTS_DIR)
        val name = "BlushDesk_Export_${Formats.fileStamp(now)}.xlsx"
        val file = excel.export(File(folder, name), operator, buyers, now)
        AppFiles.prune(folder, keep = 5)
        return ExportDocument(file, name)
    }

    private companion object {
        const val TAG = "DocumentService"
    }
}
