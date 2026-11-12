package fr.photosorter.ui.gallery

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items as listItems
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import fr.photosorter.domain.model.ImageItem
import fr.photosorter.ui.gallery.components.GalleryEmpty
import fr.photosorter.ui.gallery.components.GalleryGridCell
import fr.photosorter.ui.gallery.components.GalleryListRow
import fr.photosorter.ui.gallery.components.GalleryLoading

/** Rendu de la liste des images selon le mode d'affichage (grille ou liste compacte). */
@Composable
internal fun GalleryContent(
    state: GalleryUiState,
    modifier: Modifier = Modifier,
    onImageClick: (ImageItem) -> Unit,
) {
    when {
        state.isLoading -> GalleryLoading(modifier)
        state.images.isEmpty() -> GalleryEmpty(modifier)
        state.displayMode == GalleryDisplayMode.GRID -> GalleryGrid(
            images = state.images,
            selectedIds = state.selectedIds,
            modifier = modifier,
            onImageClick = onImageClick,
            onToggleSelection = { /* sélection par clic long gérée dans la cellule */ },
        )
        else -> GalleryList(
            images = state.images,
            selectedIds = state.selectedIds,
            modifier = modifier,
            onImageClick = onImageClick,
        )
    }
}

@Composable
private fun GalleryGrid(
    images: List<ImageItem>,
    selectedIds: Set<Long>,
    modifier: Modifier,
    onImageClick: (ImageItem) -> Unit,
    onToggleSelection: (Long) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(120.dp),
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(4.dp),
    ) {
        gridItems(images, key = { it.id }) { image ->
            GalleryGridCell(
                image = image,
                selected = image.id in selectedIds,
                onClick = { onImageClick(image) },
                onLongClick = { onToggleSelection(image.id) },
            )
        }
    }
}

@Composable
private fun GalleryList(
    images: List<ImageItem>,
    selectedIds: Set<Long>,
    modifier: Modifier,
    onImageClick: (ImageItem) -> Unit,
) {
    LazyColumn(modifier = modifier.fillMaxSize()) {
        listItems(images, key = { it.id }) { image ->
            GalleryListRow(
                image = image,
                selected = image.id in selectedIds,
                onClick = { onImageClick(image) },
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }
    }
}
