package fr.photosorter.ui.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.photosorter.domain.model.FilterState
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
 * ViewModel de l'écran plein écran. Observe la liste des images (ordre par défaut de la
 * galerie : date décroissante) et se positionne sur l'image demandée par la route. La
 * navigation précédent/suivant parcourt cette liste.
 */
@HiltViewModel
class DetailViewModel @Inject constructor(
    imageRepository: ImageRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val initialImageId: Long =
        savedStateHandle.get<Long>(Destinations.DETAIL_ARG_IMAGE_ID) ?: -1L

    private val sort = SortCriteria(SortField.DATE, ascending = false)

    /** Index de navigation explicite ; -1 tant que l'utilisateur n'a pas navigué. */
    private val navigatedIndex = MutableStateFlow(-1)

    private val repositoryImages = imageRepository
        .observeImages(sort, FilterState())
        .catch { emit(emptyList()) }

    val uiState: StateFlow<DetailUiState> = combine(
        repositoryImages,
        navigatedIndex,
    ) { items, index ->
        val resolved = when {
            items.isEmpty() -> -1
            index in items.indices -> index
            else -> items.indexOfFirst { it.id == initialImageId }.coerceAtLeast(0)
        }
        DetailUiState(items = items, currentIndex = resolved, isLoading = false)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = DetailUiState(isLoading = true),
    )

    fun showNext() {
        val state = uiState.value
        if (state.hasNext) navigatedIndex.value = state.currentIndex + 1
    }

    fun showPrevious() {
        val state = uiState.value
        if (state.hasPrevious) navigatedIndex.value = state.currentIndex - 1
    }
}
