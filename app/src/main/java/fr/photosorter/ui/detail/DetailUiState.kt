package fr.photosorter.ui.detail

import fr.photosorter.domain.model.ImageItem

/**
 * État de l'écran de visualisation plein écran (tâche 79906e61).
 * [images] est la liste courante (pour la navigation précédent/suivant),
 * [currentIndex] pointe sur l'image affichée.
 */
data class DetailUiState(
    val isLoading: Boolean = true,
    val images: List<ImageItem> = emptyList(),
    val currentIndex: Int = 0,
    val errorMessage: String? = null,
) {
    val currentImage: ImageItem?
        get() = images.getOrNull(currentIndex)

    val hasPrevious: Boolean
        get() = currentIndex > 0

    val hasNext: Boolean
        get() = currentIndex < images.lastIndex
}
