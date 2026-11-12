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
    private val feedback = MutableStateFlow<String?>(null)
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

    fun setDisplayMode(mode: GalleryDisplayMode) { displayMode.value = mode }

    fun setSort(field: SortField) {
        val current = sort.value
        sort.value = SortCriteria(field, if (current.field == field) !current.ascending else true)
    }

    fun setFilter(newFilter: FilterState) { filter.value = newFilter }

    fun toggleSelection(id: Long) {
        selectedIds.value = selectedIds.value.let { if (id in it) it - id else it + id }
    }

    fun toggleSelectAll() {
        val all = imagesState.value.map { it.id }.toSet()
        val cur = selectedIds.value
        selectedIds.value = if (cur.containsAll(all) && all.isNotEmpty()) emptySet() else all
    }

    fun clearSelection() { selectedIds.value = emptySet() }

    fun consumeFeedback() { feedback.value = null }

    private fun selectedItems(): List<ImageItem> {
        val sel = selectedIds.value
        return imagesState.value.filter { it.id in sel }
    }

    private fun runAction(successMsg: String, block: suspend () -> AppResult<Unit>) {
        if (busy.value) return
        viewModelScope.launch {
            busy.value = true
            when (val r = block()) {
                is AppResult.Success -> { feedback.value = successMsg; clearSelection() }
                is AppResult.Failure -> errorMessage.value = r.error.message
            }
            busy.value = false
        }
    }

    fun deleteSelected() = runAction("SUPPRIME_OK") {
        imageRepository.delete(selectedItems())
    }

    fun shareSelected() = runAction("PARTAGE_OK") {
        imageRepository.share(selectedItems())
    }

    fun moveSelected(destUri: String) = runAction("DEPLACE_OK") {
        imageRepository.move(selectedItems(), destUri)
    }

    fun copySelected(destUri: String) = runAction("COPIE_OK") {
        imageRepository.copy(selectedItems(), destUri)
    }

    fun assignLabel(label: String) = runAction("LIBELLE_ASSIGN_OK") {
        labelRepository.assign(selectedItems(), label)
    }

    fun unassignLabel(label: String) = runAction("LIBELLE_RETIRE_OK") {
        labelRepository.unassign(selectedItems(), label)
    }
}
