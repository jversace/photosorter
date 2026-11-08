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
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel de la galerie. Dépend de l'interface ImageRepository ; l'implémentation
 * concrète est fournie par Hilt (une fois fusionnée depuis agent/léna).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class GalleryViewModel @Inject constructor(
    private val imageRepository: ImageRepository,
) : ViewModel() {

    private val displayMode = MutableStateFlow(GalleryDisplayMode.GRID)
    private val sort = MutableStateFlow(SortCriteria(SortField.DATE, ascending = false))
    private val filter = MutableStateFlow(FilterState())
    private val selectedIds = MutableStateFlow<Set<Long>>(emptySet())
    private val imagesState = MutableStateFlow<List<ImageItem>>(emptyList())

    /** Flux de la liste filtrée/triée provenant du repository. */
    private val repositoryImages = combine(sort, filter) { s, f -> s to f }
        .flatMapLatest { (s, f) ->
            imageRepository.observeImages(s, f)
                .catch { emit(emptyList()) }
        }

    val uiState: StateFlow<GalleryUiState> = kotlinx.coroutines.flow.combine(
        repositoryImages, displayMode, sort, filter, selectedIds,
    ) { images, mode, s, f, sel ->
        imagesState.value = images
        GalleryUiState(
            images = images,
            displayMode = mode,
            sort = s,
            filter = f,
            selectedIds = sel.filterTo(LinkedHashSet()) { id -> images.any { it.id == id } },
            isLoading = false,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = GalleryUiState(isLoading = true),
    )

    fun setDisplayMode(mode: GalleryDisplayMode) { displayMode.value = mode }

    fun setSort(field: SortField) {
        val current = sort.value
        sort.value = SortCriteria(field, if (current.field == field) !current.ascending else true)
    }

    fun setFilter(next: FilterState) { filter.value = next }

    fun toggleSelection(id: Long) {
        val next = selectedIds.value.toMutableSet()
        if (!next.add(id)) next.remove(id)
        selectedIds.value = next
    }

    fun selectOnly(id: Long) { selectedIds.value = setOf(id) }

    fun clearSelection() { selectedIds.value = emptySet() }

    fun toggleSelectAll() {
        val all = imagesState.value.map { it.id }.toSet()
        selectedIds.value = if (selectedIds.value == all) emptySet() else all
    }

    fun deleteSelected() {
        val selected = imagesState.value.filter { it.id in selectedIds.value }
        if (selected.isEmpty()) return
        viewModelScope.launch {
            imageRepository.delete(selected)
            selectedIds.value = emptySet()
        }
    }
}
