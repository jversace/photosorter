package fr.photosorter.ui.gallery

import androidx.annotation.StringRes
import fr.photosorter.R
import fr.photosorter.domain.model.FilterState
import fr.photosorter.domain.model.ImageItem
import fr.photosorter.domain.model.SortCriteria
import fr.photosorter.domain.model.SortField

/** Mode d'affichage de la galerie. */
enum class GalleryDisplayMode { LIST, GRID }

/** Retour utilisateur après une action fichiers/libellé (mappé en string par l'UI). */
enum class GalleryFeedback {
    DELETED, SHARED, MOVED, COPIED, LABEL_ASSIGNED, LABEL_REMOVED,
}

/** Ressource texte associée à un retour d'action. */
@StringRes
internal fun feedbackRes(feedback: GalleryFeedback): Int = when (feedback) {
    GalleryFeedback.DELETED -> R.string.feedback_deleted
    GalleryFeedback.SHARED -> R.string.feedback_shared
    GalleryFeedback.MOVED -> R.string.feedback_moved
    GalleryFeedback.COPIED -> R.string.feedback_copied
    GalleryFeedback.LABEL_ASSIGNED -> R.string.feedback_label_assigned
    GalleryFeedback.LABEL_REMOVED -> R.string.feedback_label_removed
}

/**
 * État complet de l'écran galerie.
 * Pas d'erreur réseau : l'app est 100% locale, on ne gère que les erreurs métier locales.
 */
data class GalleryUiState(
    val images: List<ImageItem> = emptyList(),
    val displayMode: GalleryDisplayMode = GalleryDisplayMode.GRID,
    val sort: SortCriteria = SortCriteria(SortField.DATE, ascending = false),
    val filter: FilterState = FilterState(),
    val selectedIds: Set<Long> = emptySet(),
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val feedback: GalleryFeedback? = null,
    val actionsBusy: Boolean = false,
) {
    /** Vrai en mode sélection multiple actif (au moins un élément sélectionné). */
    val isSelectionMode: Boolean get() = selectedIds.isNotEmpty()

    val allSelected: Boolean get() = images.isNotEmpty() && images.all { it.id in selectedIds }
}
