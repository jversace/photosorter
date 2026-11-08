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
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
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
    private val loading = MutableStateFlow(true)
    private val error = MutableStateFlow<String?>(null)

    /** Flux de la liste filtrée/triée provenant du repository. */
    private val repositoryImages = combine3(sort, filter, selectedIds).let { _ ->
        sort.flatMapLatest { s ->
            filter.flatMapLatest { f ->
                imageRepository.observeImages(s, f)
                    .catch { e -> error.value = e.message }
            }
        }
    }

    val uiState: StateFlow<GalleryUiState> = combine5(
        repositoryImages, displayMode, sort, filter, selectedIds,
    ) { images, mode, s, f, sel ->
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
    ).also { state ->
        // tenue à jour de la liste courante pour les actions de masse
        viewModelScope.launch { state.collect { imagesState.value = it.images } }
    }

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

    // combine génériques (kotlinx combine 5 flux n'est pas surchargé au-delà de 5 : on reste à 5).
    private fun <T> combine3(
        a: MutableStateFlow<T>,
        b: MutableStateFlow<T>,
        c: MutableStateFlow<T>,
    ): Unit = Unit // placeholder non utilisé (voir repositoryImages)

    private fun <A, B, C, D, E, R> combine5(
        a: kotlinx.coroutines.flow.Flow<A>,
        b: kotlinx.coroutines.flow.Flow<B>,
        c: kotlinx.coroutines.flow.Flow<C>,
        d: kotlinx.coroutines.flow.Flow<D>,
        e: kotlinx.coroutines.flow.Flow<E>,
        transform: suspend (A, B, C, D, E) -> R,
    ): kotlinx.coroutines.flow.Flow<R> = kotlinx.coroutines.flow.combine(a, b, c, d, e, transform)
}
