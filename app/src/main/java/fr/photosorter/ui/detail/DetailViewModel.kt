package fr.photosorter.ui.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.photosorter.domain.model.FilterState
import fr.photosorter.domain.model.ImageItem
import fr.photosorter.domain.model.SortCriteria
import fr.photosorter.domain.model.SortField
import fr.photosorter.domain.repository.ImageRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Position courante dans la liste observée. */
data class DetailUiState(
    val current: ImageItem? = null,
    val hasPrevious: Boolean = false,
    val hasNext: Boolean = false,
    val index: Int = 0,
    val total: Int = 0,
)

@HiltViewModel
class DetailViewModel @Inject constructor(
    repository: ImageRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val startImageId: Long =
        savedStateHandle.get<Long>(ARG_IMAGE_ID) ?: -1L

    private val currentId = MutableStateFlow(startImageId)

    private val images = repository.observeImages(
        SortCriteria(SortField.DATE, ascending = false),
        FilterState(),
    )

    private var cachedList: List<ImageItem> = emptyList()

    val state: StateFlow<DetailUiState> = combine(images, currentId) { list, id ->
        if (list.isEmpty()) {
            DetailUiState()
        } else {
            val idx = list.indexOfFirst { it.id == id }.takeIf { it >= 0 } ?: 0
            DetailUiState(
                current = list.getOrNull(idx),
                hasPrevious = idx > 0,
                hasNext = idx < list.lastIndex,
                index = idx,
                total = list.size,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DetailUiState())

    init {
        viewModelScope.launch {
            images.collect { cachedList = it }
        }
    }

    fun previous() {
        val s = state.value
        if (s.index > 0) navigateTo(s.index - 1)
    }

    fun next() {
        val s = state.value
        if (s.index < s.total - 1) navigateTo(s.index + 1)
    }

    private fun navigateTo(targetIndex: Int) {
        val list = cachedList
        if (targetIndex in list.indices) {
            currentId.value = list[targetIndex].id
        }
    }

    companion object {
        const val ARG_IMAGE_ID = "imageId"
    }
}
