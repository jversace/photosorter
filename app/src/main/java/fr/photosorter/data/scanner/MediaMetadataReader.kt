package fr.photosorter.data.scanner

import android.content.ContentResolver
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import java.net.URLConnection

data class FileMetadata(
    val name: String,
    val sizeBytes: Long,
    val dateModified: Long,
    val mimeType: String?,
    val width: Int,
    val height: Int
)

/**
 * Lecture des metadonnees d'une image.
 *
 * Sous scoped storage (API >= 29) les Uri produites par [StorageWalker] sont de type
 * file:// (Uri.fromFile). contentResolver.query/getType ne sont PAS fiables sur file://.
 * On construit donc nom/taille/date DIRECTEMENT depuis le File (garanti), et on ne
 * sollicite le contentResolver que de facon BEST-EFFORT (MIME), en tolerant file://.
 */
class MediaMetadataReader(private val contentResolver: ContentResolver) {

    fun read(uri: Uri): FileMetadata? {
        val path = uri.path
        val file = path?.let { File(it) }

        var name: String? = null
        var size = -1L
        var modified = 0L

        // 1) Source primaire : le File (fiable pour file://).
        if (file != null && file.exists()) {
            name = file.name
            size = file.length()
            modified = file.lastModified()
        }

        // 2) Repli best-effort via contentResolver (utile pour content://).
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
                // file:// non interrogeable : on tolere, le File a deja fourni l'essentiel.
            }
        }

        val resolved = name
        if (resolved.isNullOrBlank()) return null

        // MIME best-effort : contentResolver puis repli par extension.
        val mime = resolveMime(uri, resolved, file)
        val (w, h) = readDimensions(path)

        return FileMetadata(resolved, size.coerceAtLeast(0L), modified, mime, w, h)
    }

    private fun resolveMime(uri: Uri, name: String, file: File?): String? {
        if (uri.scheme != "file") {
            try {
                contentResolver.getType(uri)?.let { return it }
            } catch (_: Exception) {
            }
        }
        if (file != null) {
            try {
                URLConnection.guessContentTypeFromName(name)?.let { return it }
            } catch (_: Throwable) {
            }
        }
        return null
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
