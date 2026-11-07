package fr.photosorter.core.ext

import fr.photosorter.domain.model.ImageFormat

/** Mappe une extension de nom de fichier vers ImageFormat (null si non supporté). */
fun String.toImageFormatOrNull(): ImageFormat? = when (lowercase()) {
    "jpg", "jpeg" -> ImageFormat.JPEG
    "png" -> ImageFormat.PNG
    "heic", "heif" -> ImageFormat.HEIC
    "webp" -> ImageFormat.WEBP
    "gif" -> ImageFormat.GIF
    "bmp" -> ImageFormat.BMP
    "cr2", "dng", "nef" -> ImageFormat.RAW
    else -> null
}
