package fr.photosorter.data.scanner

import android.content.ContentResolver
import android.webkit.MimeTypeMap
import fr.photosorter.domain.model.ImageFormat

/**
 * Détection de format d'image par extension puis type MIME.
 * Aucune dépendance réseau ; tout est local.
 */
class MimeTypeDetector {

    fun detectFormat(fileName: String, mimeType: String?): ImageFormat? {
        extensionToFormat(fileName.substringAfterLast('.', ""))?.let { return it }
        if (!mimeType.isNullOrBlank()) {
            mimeToFormat(mimeType)?.let { return it }
        }
        return null
    }

    private fun extensionToFormat(ext: String): ImageFormat? = when (ext.lowercase()) {
        "jpg", "jpeg", "jpe", "jfif" -> ImageFormat.JPEG
        "png" -> ImageFormat.PNG
        "heic", "heif" -> ImageFormat.HEIC
        "webp" -> ImageFormat.WEBP
        "gif" -> ImageFormat.GIF
        "bmp", "dib" -> ImageFormat.BMP
        "cr2", "dng", "nef", "arw", "orf", "rw2", "raf", "tiff", "tif" -> ImageFormat.RAW
        else -> null
    }

    private fun mimeToFormat(mime: String): ImageFormat? = when (mime.lowercase()) {
        "image/jpeg", "image/jpg", "image/pjpeg" -> ImageFormat.JPEG
        "image/png" -> ImageFormat.PNG
        "image/heic", "image/heif", "image/heic-sequence", "image/heif-sequence" -> ImageFormat.HEIC
        "image/webp" -> ImageFormat.WEBP
        "image/gif" -> ImageFormat.GIF
        "image/bmp", "image/x-ms-bmp" -> ImageFormat.BMP
        "image/x-adobe-dng", "image/x-canon-cr2", "image/x-nikon-nef", "image/tiff" -> ImageFormat.RAW
        else -> null
    }

    fun mimeFromFile(fileName: String): String? =
        MimeTypeMap.getSingleton().getMimeTypeFromExtension(fileName.substringAfterLast('.', "").lowercase())
        ?: ContentResolver.MIME_TYPE_DEFAULT.takeIf { it.isNotBlank() }
}
