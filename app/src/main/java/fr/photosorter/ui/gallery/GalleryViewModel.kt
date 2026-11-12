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
    private val sort = MutableStateFlow(SortCriteria(SortField.DATE, ascending = false))
    private val filter = MutableStateFlow(FilterState())
    private val selectedIds = MutableStateFlow<Set<Long>>(emptySet())
    private val imagesState = MutableStateFlow<List<ImageItem>>(emptyList())
    private val errorMessage = MutableStateFlow<String?>(null)
    private val feedback = MutableStateFlow<GalleryFeedback?>(null)
    private val busy = MutableStateFlow(false)

    private val repositoryImages = combine(sort, filter) { s, f -> s to f }
        .flatMapLatest { (s, f) ->
            imageRepository.observeImages(s, f)
                .catch { e -> errorMessage.value = e.message; emit(emptyList()) }
        }

    val uiState: StateFlow<GalleryUiState> =
        combine(
            repositoryImages, displayMode, sort, filter, selectedIds,
            combine(errorMessage, feedback, busy) { e, f, b -> Triple(e, f, b) },
        ) { images, mode, s, f, sel, extra ->
            imagesState.value = images
            GalleryUiState(
                images = images,
                displayMode = mode,
                sort = s,
                filter = f,
                selectedIds = sel.filterTo(LinkedHashSet()) { id -> images.any { it.id == id } },
                isLoading = false,
                errorMessage = extra.first,
                feedback = extra.second,
                actionsBusy = extra.third,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = GalleryUiState(isLoading = true),
        )

    private fun selectedItems(): List<ImageItem> =
        imagesState.value.filter { it.id in selectedIds.value }

    fun setDisplayMode(mode: GalleryDisplayMode) { displayMode.value = mode }

    fun setSort(field: SortField) {
        sort.value = if (sort.value.field == field) sort.value.copy(ascending = !sort.value.ascending)
        else SortCriteria(field, ascending = true)
    }

    fun setFilter(newFilter: FilterState) { filter.value = newFilter }

    fun toggleSelection(id: Long) {
        selectedIds.value = selectedIds.value.let { if (id in it) it - id else it + id }
    }

    fun clearSelection() { selectedIds.value = emptySet() }

    fun toggleSelectAll() {
        selectedIds.value = if (uiState.value.allSelected) emptySet()
        else imagesState.value.mapTo(LinkedHashSet()) { it.id }
    }

    fun consumeFeedback() { feedback.value = null }

    private fun runAction(success: GalleryFeedback, block: suspend () -> AppResult<Unit>) {
        val items = selectedItems()
        if (items.isEmpty()) return
        busy.value = true
        viewModelScope.launch {
            when (val r = block()) {
                is AppResult.Success -> { feedback.value = success; selectedIds.value = emptySet() }
                is AppResult.Failure -> errorMessage.value = r.error.message
            }
            busy.value = false
        }
    }

    fun deleteSelected() = runAction(GalleryFeedback.DELETED) { imageRepository.delete(selectedItems()) }
    fun shareSelected() = runAction(GalleryFeedback.SHARED) { imageRepository.share(selectedItems()) }
    fun moveSelected(destUri: String) = runAction(GalleryFeedback.MOVED) { imageRepository.move(selectedItems(), destUri) }
    fun copySelected(destUri: String) = runAction(GalleryFeedback.COPIED) { imageRepository.copy(selectedItems(), destUri) }

    fun assignLabel(label: String) =
        runAction(GalleryFeedback.LABEL_ASSIGNED) { labelRepository.assign(selectedItems(), label) }

    fun unassignLabel(label: String) =
        runAction(GalleryFeedback.LABEL_REMOVED) { labelRepository.unassign(selectedItems(), label) }
}
