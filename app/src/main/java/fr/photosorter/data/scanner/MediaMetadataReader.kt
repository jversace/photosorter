package fr.photosorter.data.scanner

import android.content.ContentResolver
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File

data class FileMetadata(
    val name: String,
    val sizeBytes: Long,
    val dateModified: Long,
    val mimeType: String?,
    val width: Int,
    val height: Int
)

class MediaMetadataReader(private val contentResolver: ContentResolver) {

    fun read(uri: Uri): FileMetadata? {
        var name: String? = null
        var size = -1L
        var modified = 0L

        val path = uri.path
        if (path != null) {
            val f = File(path)
            if (f.exists()) {
                name = f.name
                size = f.length()
                modified = f.lastModified()
            }
        }

        if (name.isNullOrBlank() || size < 0) {
            try {
                contentResolver.query(
                    uri,
                    arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
                    null, null, null
                )?.use { c ->
                    if (c.moveToFirst()) {
                        val ni = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        val si = c.getColumnIndex(OpenableColumns.SIZE)
                        if (name.isNullOrBlank() && ni >= 0) name = c.getString(ni)
                        if (size < 0 && si >= 0 && !c.isNull(si)) size = c.getLong(si)
                    }
                }
            } catch (_: Exception) {
            }
        }

        val resolved = name
        if (resolved.isNullOrBlank()) return null

        val mime = try { contentResolver.getType(uri) } catch (_: Exception) { null }
        val (w, h) = readDimensions(path)

        return FileMetadata(resolved, size.coerceAtLeast(0L), modified, mime, w, h)
    }

    private fun readDimensions(path: String?): Pair<Int, Int> {
        if (path != null) {
            try {
                val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(path, o)
                if (o.outWidth > 0 && o.outHeight > 0) return o.outWidth to o.outHeight
            } catch (_: Throwable) {
            }
        }
        return 0 to 0
    }
}
