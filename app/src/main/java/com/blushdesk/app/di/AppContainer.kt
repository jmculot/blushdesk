package com.blushdesk.app.di

import android.content.Context
import com.blushdesk.app.data.local.ShowroomDatabase
import com.blushdesk.app.data.repository.ShowroomRepository
import com.blushdesk.app.data.repository.ShowroomRepositoryImpl
import com.blushdesk.app.data.storage.DownloadsSaver
import com.blushdesk.app.data.storage.PhotoStorage
import com.blushdesk.app.export.AndroidDocumentService
import com.blushdesk.app.export.DocumentService
import com.blushdesk.app.export.ExcelExporter
import com.blushdesk.app.export.PdfReceiptGenerator

/**
 * Hand-rolled dependency container, created once by [com.blushdesk.app.BlushDeskApp].
 *
 * A dependency-injection framework would add a code generator for what is, here, six objects.
 * Everything is `lazy`, so nothing is built (and the database is not opened) until first use.
 */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val database: ShowroomDatabase by lazy { ShowroomDatabase.create(appContext) }

    val photoStorage: PhotoStorage by lazy { PhotoStorage(appContext) }

    val repository: ShowroomRepository by lazy {
        ShowroomRepositoryImpl(database.dao(), photoStorage)
    }

    val documents: DocumentService by lazy {
        AndroidDocumentService(
            context = appContext,
            repository = repository,
            excel = ExcelExporter(),
            pdf = PdfReceiptGenerator(),
            downloads = DownloadsSaver(appContext),
        )
    }
}
