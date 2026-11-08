package fr.photosorter.ui.gallery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.photosorter.domain.model.FilterState
import fr.photosorter.domain.model.ImageItem
import fr.photosorter.domain.model.SortCriteria
import fr.photosorter.domain.model.SortField
import fr.photosorter.domain.repository.ImageRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel de la galerie. Dépend de l'interface ImageRepository (implémentation injectée par Hilt
 * une fois fusionnée depuis agent/léna). L'UI n'a pas besoin de connaître l'implémentation.
 */
@HiltViewModel
class GalleryViewModel @Inject constructor(
    private val imageRepository: ImageRepository,
) : ViewModel() {

    private val query = MutableStateFlow(GalleryQuery())

    /** État exposé à l'UI. */
    val uiState: StateFlow<GalleryUiState> = query
        .flatMapLatest { q ->
            imageRepository.observeImages(q.sort, q.filter)
                .mapToImages(q)
        }
        .catch { e ->
            emit(GalleryUiState(isLoading = false, errorMessage = e.message))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = GalleryUiState(isLoading = true),
        )

    private fun kotlinx.coroutines.flow.Flow<List<ImageItem>>.mapToImages(q: GalleryQuery) =
        kotlinx.coroutines.flow.map { images ->
            GalleryUiState(
                images = images,
                displayMode = q.displayMode,
                sort = q.sort,
                filter = q.filter,
                selectedIds = q.selectedIds.filterTo(LinkedHashSet()) { id -> images.any { it.id == id } },
                isLoading = false,
            )
        }

    fun setDisplayMode(mode: GalleryDisplayMode) = update { it.copy(displayMode = mode) }

    fun setSort(field: SortField) = update {
        val ascending = if (it.sort.field == field) !it.sort.ascending else true
        it.copy(sort = SortCriteria(field, ascending))
    }

    fun setFilter(filter: FilterState) = update { it.copy(filter = filter) }

    fun toggleSelection(id: Long) = update {
        val next = it.selectedIds.toMutableSet()
        if (!next.add(id)) next.remove(id)
        it.copy(selectedIds = next)
    }

    fun selectOnly(id: Long) = update { it.copy(selectedIds = setOf(id)) }

    fun clearSelection() = update { it.copy(selectedIds = emptySet()) }

    fun toggleSelectAll() = update {
        if (it.selectedIds.size == currentImages.value.size) it.copy(selectedIds = emptySet())
        else it.copy(selectedIds = currentImages.value.map { img -> img.id }.toSet())
    }

    fun deleteSelected() {
        val selected = currentImages.value.filter { it.id in query.value.selectedIds }
        if (selected.isEmpty()) return
        viewModelScope.launch {
            imageRepository.delete(selected)
            update { it.copy(selectedIds = emptySet()) }
        }
    }

    private val currentImages = MutableStateFlow<List<ImageItem>>(emptyList())

    private inline fun update(transform: (GalleryQuery) -> GalleryQuery) {
        query.value = transform(query.value)
    }

    /** Regroupe l'état interne (requête + sélection + mode). */
    internal data class GalleryQuery(
        val displayMode: GalleryDisplayMode = GalleryDisplayMode.GRID,
        val sort: SortCriteria = SortCriteria(SortField.DATE, ascending = false),
        val filter: FilterState = FilterState(),
        val selectedIds: Set<Long> = emptySet(),
    )
}
