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
    private val repository: ImageRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val startImageId: Long =
        savedStateHandle.get<Long>(ARG_IMAGE_ID) ?: -1L

    private val sort = MutableStateFlow(SortCriteria(SortField.DATE, ascending = false))
    private val filter = MutableStateFlow(FilterState())
    private val currentId = MutableStateFlow(startImageId)

    val state: StateFlow<DetailUiState> = combine(
        repository.observeImages(SortCriteria(SortField.DATE, false), FilterState()),
        currentId,
    ) { list, id -> toState(list, id) }
        // simple: on utilise le tri/filtre par défaut ; navigation basée sur cet ordre
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DetailUiState())

    private fun toState(list: List<ImageItem>, id: Long): DetailUiState {
        if (list.isEmpty()) return DetailUiState()
        val idx = list.indexOfFirst { it.id == id }.takeIf { it >= 0 } ?: 0
        return DetailUiState(
            current = list.getOrNull(idx),
            hasPrevious = idx > 0,
            hasNext = idx < list.lastIndex,
            index = idx,
            total = list.size,
        )
    }

    fun previous() {
        val s = state.value
        if (s.hasPrevious && s.index > 0) {
            // recalcule via la liste observée légèrement différée ; on s'appuie sur lastList
            moveBy(-1)
        }
    }

    fun next() {
        if (state.value.hasNext) moveBy(1)
    }

    private var lastList: List<ImageItem> = emptyList()

    private fun moveBy(delta: Int) {
        val s = state.value
        val target = s.index + delta
        // met à jour currentId depuis l'état courant
        s.current?.let { cur ->
            val list = currentListOrEmpty()
            val idx = list.indexOfFirst { it.id == cur.id }
            if (idx >= 0) {
                val t = (idx + delta).coerceIn(0, list.lastIndex)
                currentId.value = list[t].id
            }
        }
    }

    private fun currentListOrEmpty(): List<ImageItem> = lastList

    companion object {
        const val ARG_IMAGE_ID = "imageId"
    }
}
