package com.blushdesk.app.ui

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import com.blushdesk.app.data.storage.AppFiles
import java.io.File

/**
 * Hands generated files to other apps. A file is never put on an intent directly: it is exposed as
 * a content:// URI through FileProvider, and the receiving app is granted read access to just that
 * one URI for the duration of the intent.
 */
object Sharing {

    /** Opens the system share sheet (email, Drive, Files, ...) for [file]. Returns false if none can handle it. */
    fun share(context: Context, file: File, mimeType: String, subject: String, chooserTitle: String): Boolean {
        val uri = AppFiles.uriFor(context, file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, subject)
            // The ClipData is what lets the chooser itself (and the target it launches) read the URI.
            clipData = ClipData.newRawUri(file.name, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return start(context, Intent.createChooser(send, chooserTitle))
    }

    /** Opens [file] in whichever viewer the user has installed. Returns false if there is none. */
    fun view(context: Context, file: File, mimeType: String): Boolean {
        val uri = AppFiles.uriFor(context, file)
        val view = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return start(context, view)
    }

    private fun start(context: Context, intent: Intent): Boolean = try {
        context.startActivity(intent)
        true
    } catch (_: ActivityNotFoundException) {
        false
    }
}
