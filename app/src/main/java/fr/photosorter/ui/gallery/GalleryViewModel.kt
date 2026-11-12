package fr.photosorter.ui.gallery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.photosorter.core.result.AppResult
import fr.photosorter.domain.model.FilterState
import fr.photosorter.domain.model.ImageItem
import fr.photosorter.domain.model.SortCriteria
import fr.photosorter.domain.model.SortField
import fr.photosorter.domain.repository.ImageRepository
import fr.photosorter.domain.repository.LabelRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class GalleryViewModel @Inject constructor(
    private val imageRepository: ImageRepository,
    private val labelRepository: LabelRepository,
) : ViewModel() {

    private val displayMode = MutableStateFlow(GalleryDisplayMode.GRID)
    private val sort = MutableStateFlow(SortCriteria(SortField.DATE, false))
    private val filter = MutableStateFlow(FilterState())
    private val selectedIds = MutableStateFlow<Set<Long>>(emptySet())
    private val imagesState = MutableStateFlow<List<ImageItem>>(emptyList())
    private val errorMessage = MutableStateFlow<String?>(null)
    private val feedback = MutableStateFlow<GalleryFeedback?>(null)
    private val busy = MutableStateFlow(false)

    private val images = combine(sort, filter) { s, f -> s to f }
        .flatMapLatest { (s, f) -> imageRepository.observeImages(s, f).catch { e -> errorMessage.value = e.message; emit(emptyList()) } }

    private val labels = labelRepository.observeLabels().map { l -> l.map { it.name } }.catch { emit(emptyList()) }

    val uiState: StateFlow<GalleryUiState> = combine(images, displayMode, sort, filter, selectedIds, labels) { v ->
        @Suppress("UNCHECKED_CAST")
        val imgs = v[0] as List<ImageItem>
        imagesState.value = imgs
        GalleryUiState(
            images = imgs, displayMode = v[1] as GalleryDisplayMode, sort = v[2] as SortCriteria,
            filter = v[3] as FilterState,
            selectedIds = (v[4] as Set<Long>).filterTo(LinkedHashSet()) { id -> imgs.any { it.id == id } },
            availableLabels = v[5] as List<String>, isLoading = false,
            errorMessage = errorMessage.value, feedback = feedback.value, actionsBusy = busy.value,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GalleryUiState(isLoading = true))

    private fun items(): List<ImageItem> = imagesState.value.filter { it.id in selectedIds.value }

    fun setDisplayMode(m: GalleryDisplayMode) { displayMode.value = m }
    fun setSort(f: SortField) { sort.value = if (sort.value.field == f) sort.value.copy(ascending = !sort.value.ascending) else SortCriteria(f, true) }
    fun setFilter(f: FilterState) { filter.value = f }
    fun toggleSelection(id: Long) { selectedIds.value = selectedIds.value.let { if (id in it) it - id else it + id } }
    fun clearSelection() { selectedIds.value = emptySet() }
    fun toggleSelectAll() { selectedIds.value = if (uiState.value.allSelected) emptySet() else imagesState.value.mapTo(LinkedHashSet()) { it.id } }
    fun consumeFeedback() { feedback.value = null }

    private fun run(fb: GalleryFeedback, block: suspend (List<ImageItem>) -> AppResult<Unit>) {
        val its = items(); if (its.isEmpty()) return
        busy.value = true
        viewModelScope.launch {
            when (val r = block(its)) {
                is AppResult.Success -> { feedback.value = fb; selectedIds.value = emptySet() }
                is AppResult.Failure -> errorMessage.value = r.error.message
            }
            busy.value = false
        }
    }

    fun deleteSelected() = run(GalleryFeedback.DELETED) { imageRepository.delete(it) }
    fun shareSelected() = run(GalleryFeedback.SHARED) { imageRepository.share(it) }
    fun moveSelected(dest: String) = run(GalleryFeedback.MOVED) { imageRepository.move(it, dest) }
    fun copySelected(dest: String) = run(GalleryFeedback.COPIED) { imageRepository.copy(it, dest) }
    fun assignLabel(name: String) = run(GalleryFeedback.LABEL_ASSIGNED) { labelRepository.assign(it, name) }
    fun unassignLabel(name: String) = run(GalleryFeedback.LABEL_REMOVED) { labelRepository.unassign(it, name) }
}
