package fr.photosorter.ui.detail

import fr.photosorter.domain.model.ImageItem

/** État de l'écran plein écran. */
data class DetailUiState(
    val items: List<ImageItem> = emptyList(),
    val currentIndex: Int = -1,
    val isLoading: Boolean = false,
) {
    val current: ImageItem? get() = items.getOrNull(currentIndex)
    val hasPrevious: Boolean get() = currentIndex > 0
    val hasNext: Boolean get() = currentIndex in 0 until items.lastIndex
    val positionLabel: String get() =
        if (currentIndex >= 0) "${currentIndex + 1} / ${items.size}" else ""
}
