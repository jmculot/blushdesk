package com.blushdesk.app.data.storage

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

/**
 * Where the app keeps files that leave its sandbox. The directory names here must match the
 * `<cache-path>` entries in res/xml/file_paths.xml: FileProvider refuses to hand out a URI for any
 * file outside those folders, which is what keeps the rest of the cache private.
 */
object AppFiles {
    const val RECEIPTS_DIR = "receipts"
    const val EXPORTS_DIR = "exports"
    const val CAMERA_DIR = "camera"

    const val MIME_PDF = "application/pdf"
    const val MIME_XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"

    /** A cache sub-folder, created on demand. */
    fun cacheDir(context: Context, name: String): File =
        File(context.cacheDir, name).also { it.mkdirs() }

    /** Matches `android:authorities` of the provider in AndroidManifest.xml. */
    fun authority(context: Context): String = "${context.packageName}.fileprovider"

    /** A content:// URI another app can read once we grant it permission on the intent. */
    fun uriFor(context: Context, file: File): Uri =
        FileProvider.getUriForFile(context, authority(context), file)

    /**
     * Keeps only the [keep] most recently written files in [dir]. Receipts and exports are
     * regenerated on demand, so old cache copies are clutter, not records.
     */
    fun prune(dir: File, keep: Int) {
        dir.listFiles { f -> f.isFile }
            ?.sortedByDescending { it.lastModified() }
            ?.drop(keep)
            ?.forEach { it.delete() }
    }
}
