package fr.photosorter.data.scanner

import android.content.ContentResolver
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File

/** Métadonnées brutes extraites d'un fichier image. */
data class FileMetadata(
    val name: String,
    val sizeBytes: Long,
    val dateModified: Long,
    val mimeType: String?,
    val width: Int,
    val height: Int
)

/**
 * Extraction locale des métadonnées : nom, taille, date, MIME, dimensions.
 * - Dimensions via BitmapFactory (bounds only, pas de décodage complet en mémoire).
 * - Pour les fichiers inaccessibles en décodage (RAW), dimensions = 0.
 */
class MediaMetadataReader(private val contentResolver: ContentResolver) {

    fun read(uri: Uri): FileMetadata? {
        var name: String? = null
        var size = -1L
        var modified = 0L

        try {
            contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
                null, null, null
            )?.use { c ->
                if (c.moveToFirst()) {
                    val nameIdx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIdx = c.getColumnIndex(OpenableColumns.SIZE)
                    if (nameIdx >= 0) name = c.getString(nameIdx)
                    if (sizeIdx >= 0 && !c.isNull(sizeIdx)) size = c.getLong(sizeIdx)
                }
            }
        } catch (_: Exception) {
            // ignore : on retombera sur les infos de fichier
        }

        val mime = contentResolver.getType(uri)

        val path = uri.path
        if (path != null) {
            val f = File(path)
            if (f.exists()) {
                if (name.isNullOrBlank()) name = f.name
                if (size < 0) size = f.length()
                modified = f.lastModified()
            }
        }
        if (name.isNullOrBlank()) return null

        val (w, h) = readDimensions(uri)
        return FileMetadata(name = name!!, sizeBytes = size.coerceAtLeast(0L), dateModified = modified,
            mimeType = mime, width = w, height = h)
    }

    private fun readDimensions(uri: Uri): Pair<Int, Int> {
        return try {
            contentResolver.openInputStream(uri)?.use { input ->
                val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeStream(input, null, opts)
                opts.outWidth.coerceAtLeast(0) to opts.outHeight.coerceAtLeast(0)
            } ?: (0 to 0)
        } catch (_: Throwable) {
            0 to 0
        }
    }
}
