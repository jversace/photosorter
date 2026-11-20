package fr.photosorter.domain.model

data class FilterState(
    val formats: Set<ImageFormat> = emptySet(),
    val dateFrom: Long? = null,
    val dateTo: Long? = null,
    val minSizeBytes: Long? = null,
    val labels: Set<String> = emptySet(),
    val includeAdult: Boolean = true,
    /**
     * N'afficher QUE les images portant le libellé système adulte (ADULT_LABEL).
     * Contrainte ET indépendante des [labels] (qui restent en OU).
     * S'appuie sur la table image_labels, pas sur le cache images.is_adult.
     */
    val adultOnly: Boolean = false
)
