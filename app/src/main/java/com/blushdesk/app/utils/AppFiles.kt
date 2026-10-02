package com.blushdesk.app.utils

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.core.net.toUri
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

    /**
     * The file behind an image URI stored in the database (`file:///data/.../photos/x.jpg`), or null
     * for null, blank or non-file URIs. Plain absolute paths are accepted too.
     */
    fun localFile(imageUri: String?): File? {
        if (imageUri.isNullOrBlank()) return null
        if (imageUri.startsWith("/")) return File(imageUri)
        val uri = imageUri.toUri()
        return if (uri.scheme == "file") uri.path?.let(::File) else null
    }

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
