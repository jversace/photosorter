package fr.photosorter.data.scanner

import android.content.ContentResolver
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.MediaStore
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
 * Lecture des métadonnées d'une image. Supporte les deux schémas :
 * - file://  : via File API (parcours StorageWalker du stockage interne)
 * - content:// : via ContentResolver (MediaStore, indispensable pour la carte SD
 *   sur API 30+ où File est inaccessible).
 */
class MediaMetadataReader(private val contentResolver: ContentResolver) {

    fun read(uri: Uri): FileMetadata? = when (uri.scheme) {
        ContentResolver.SCHEME_CONTENT -> readFromContent(uri)
        else -> readFromFile(uri)   // file:// et null couverts
    }

    // --- file:// -------------------------------------------------------------

    private fun readFromFile(uri: Uri): FileMetadata? {
        val path = uri.path ?: return null
        val file = File(path)
        if (!file.exists()) return null
        val name = file.name
        val size = file.length()
        val modified = file.lastModified()
        val mime = resolveMimeFile(name)
        val (w, h) = readDimensionsFromFile(path)
        return FileMetadata(name, size, modified, mime, w, h)
    }

    private fun resolveMimeFile(name: String): String? =
        try { URLConnection.guessContentTypeFromName(name) } catch (_: Throwable) { null }

    private fun readDimensionsFromFile(path: String): Pair<Int, Int> = try {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, o)
        if (o.outWidth > 0 && o.outHeight > 0) o.outWidth to o.outHeight else 0 to 0
    } catch (_: Throwable) { 0 to 0 }

    // --- content:// ----------------------------------------------------------

    private fun readFromContent(uri: Uri): FileMetadata? {
        var name: String? = null
        var size = -1L
        var modified = 0L

        try {
            contentResolver.query(
                uri,
                arrayOf(
                    OpenableColumns.DISPLAY_NAME,
                    OpenableColumns.SIZE,
                    MediaStore.MediaColumns.DATE_MODIFIED
                ),
                null, null, null
            )?.use { c ->
                if (c.moveToFirst()) {
                    val ni = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val si = c.getColumnIndex(OpenableColumns.SIZE)
                    val di = c.getColumnIndex(MediaStore.MediaColumns.DATE_MODIFIED)
                    if (ni >= 0) name = c.getString(ni)
                    if (si >= 0 && !c.isNull(si)) size = c.getLong(si)
                    if (di >= 0 && !c.isNull(di)) {
                        // DATE_MODIFIED est en SECONDES depuis l'epoch (pas en ms)
                        val seconds = c.getLong(di)
                        if (seconds > 0) modified = seconds * 1000L
                    }
                }
            }
        } catch (_: Exception) {
            // Certains content:// providers ne supportent pas DATE_MODIFIED : toléré.
        }

        val resolved = name ?: return null
        val mime = try { contentResolver.getType(uri) } catch (_: Exception) { null }
        val (w, h) = readDimensionsFromContent(uri)
        return FileMetadata(resolved, size.coerceAtLeast(0L), modified, mime, w, h)
    }

    private fun readDimensionsFromContent(uri: Uri): Pair<Int, Int> = try {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        contentResolver.openInputStream(uri)?.use { input ->
            BitmapFactory.decodeStream(input, null, o)
        }
        if (o.outWidth > 0 && o.outHeight > 0) o.outWidth to o.outHeight else 0 to 0
    } catch (_: Throwable) { 0 to 0 }
}