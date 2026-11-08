package fr.photosorter.domain.model

data class Label(
    val name: String,
    val isSystem: Boolean,
    val createdAt: Long
)

/** Nom du libellé système désignant le contenu adulte. */
const val ADULT_LABEL = "adulte"
