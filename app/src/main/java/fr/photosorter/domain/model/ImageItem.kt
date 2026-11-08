package fr.photosorter.domain.model

data class ImageItem(
    val id: Long,
    val uri: String,
    val name: String,
    val format: ImageFormat,
    val sizeBytes: Long,
    val dateTaken: Long?,
    val dateModified: Long,
    val width: Int,
    val height: Int,
    val labels: Set<String>,
    val isAdult: Boolean,
    val thumbnailHint: String?
)
