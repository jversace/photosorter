package fr.photosorter.domain.model

data class FilterState(
    val formats: Set<ImageFormat> = emptySet(),
    val dateFrom: Long? = null,
    val dateTo: Long? = null,
    val minSizeBytes: Long? = null,
    val labels: Set<String> = emptySet(),
    val includeAdult: Boolean = true
)
