package fr.photosorter.ui.gallery.components

import java.text.DateFormat
import java.util.Date
import java.util.concurrent.TimeUnit

/** Helpers d'affichage pour la galerie (taille lisible, date lisible). */
internal object GalleryFormatting {

    fun formatSize(bytes: Long): String {
        if (bytes < 1024) return "$bytes o"
        val units = listOf("Ko", "Mo", "Go", "To")
        var value = bytes.toDouble()
        var unitIndex = -1
        while (value >= 1024 && unitIndex < units.lastIndex) {
            value /= 1024
            unitIndex++
        }
        return if (unitIndex < 0) "$bytes o" else String.format("%.1f %s", value, units[unitIndex])
    }

    fun formatDate(epochMillis: Long): String =
        DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(epochMillis))
}
