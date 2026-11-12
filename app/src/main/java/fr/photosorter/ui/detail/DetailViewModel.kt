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
import fr.photosorter.ui.navigation.Destinations
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * ViewModel de l'écran plein écran. Il observe la liste des images (même ordre par défaut
 * que la galerie : date décroissante) et se positionne sur l'image demandée par la route.
 */
@HiltViewModel
class DetailViewModel @Inject constructor(
    private val imageRepository: ImageRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val initialImageId: Long =
        savedStateHandle.get<Long>(Destinations.DETAIL_ARG_IMAGE_ID) ?: -1L

    private val sort = SortCriteria(SortField.DATE, ascending = false)

    private val imagesFlow = MutableStateFlow<List<ImageItem>>(emptyList())

    private val repositoryImages = imageRepository
        .observeImages(sort, FilterState())
        .catch { imagesFlow.value = emptyList() }

    private val currentIndex = MutableStateFlow(-1)

    val uiState: StateFlow<DetailUiState> = combine(
        repositoryImages,
        currentIndex,
    ) { items, index ->
        imagesFlow.value = items
        val resolvedIndex = when {
            index >= 0 && items.getOrNull(index)?.id != null -> index
            else -> items.indexOfFirst { it.id == initialImageId }.coerceAtLeast(0)
        }
        DetailUiState(
            items = items,
            currentIndex = if (items.isEmpty()) -1 else resolvedIndex,
            isLoading = false,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = DetailUiState(isLoading = true),
    )

    fun showNext() {
        val state = uiState.value
        if (state.hasNext) currentIndex.value = state.currentIndex + 1
    }

    fun showPrevious() {
        val state = uiState.value
        if (state.hasPrevious) currentIndex.value = state.currentIndex - 1
    }
}
