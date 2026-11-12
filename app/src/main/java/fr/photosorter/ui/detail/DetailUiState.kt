package fr.photosorter.ui.detail

import fr.photosorter.domain.model.ImageItem

/**
 * État de l'écran de visualisation plein écran.
 *
 * @property items liste courante (ordre d'affichage de la galerie) dans laquelle on navigue.
 * @property currentIndex index de l'image affichée, ou -1 si absente.
 * @property isLoading chargement de la liste en cours.
 */
data class DetailUiState(
    val items: List<ImageItem> = emptyList(),
    val currentIndex: Int = -1,
    val isLoading: Boolean = true,
) {
    val current: ImageItem? get() = items.getOrNull(currentIndex)
    val hasPrevious: Boolean get() = currentIndex > 0
    val hasNext: Boolean get() = currentIndex in 0 until items.lastIndex
    val positionLabel: String
        get() = if (currentIndex >= 0 && items.isNotEmpty()) "${currentIndex + 1} / ${items.size}" else ""
}
