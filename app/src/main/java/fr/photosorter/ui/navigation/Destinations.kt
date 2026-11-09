package fr.photosorter.ui.navigation

/**
 * Destinations de l'application (navigation Compose mono-activity).
 */
object Destinations {
    const val GALLERY = "gallery"
    const val SCAN = "scan"
    const val LABELS = "labels"

    const val DETAIL_ROUTE = "detail/{imageId}"
    const val DETAIL_ARG_IMAGE_ID = "imageId"

    fun detail(imageId: Long): String = "detail/$imageId"
}
