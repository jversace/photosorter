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
import fr.photosorter.ui.gallery.components.GalleryEmpty
import fr.photosorter.ui.gallery.components.GalleryGridCell
import fr.photosorter.ui.gallery.components.GalleryListRow
import fr.photosorter.ui.gallery.components.GalleryLoading

/** Rendu de la liste des images selon le mode d'affichage (grille ou liste compacte). */
@Composable
internal fun GalleryContent(
    state: GalleryUiState,
    onImageClick: (Long) -> Unit,
    onToggleSelection: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    when {
        state.isLoading -> GalleryLoading(modifier)
        state.images.isEmpty() -> GalleryEmpty(modifier)
        state.displayMode == GalleryDisplayMode.GRID -> LazyVerticalGrid(
            columns = GridCells.Adaptive(120.dp),
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(4.dp),
        ) {
            gridItems(state.images, key = { it.id }) { image ->
                GalleryGridCell(
                    item = image,
                    selected = image.id in state.selectedIds,
                    selectionMode = state.isSelectionMode,
                    onClick = { onImageClick(image.id) },
                    onLongClick = { onToggleSelection(image.id) },
                )
            }
        }
        else -> LazyColumn(modifier = modifier.fillMaxSize()) {
            listItems(state.images, key = { it.id }) { image ->
                GalleryListRow(
                    item = image,
                    selected = image.id in state.selectedIds,
                    selectionMode = state.isSelectionMode,
                    onClick = { onImageClick(image.id) },
                    onLongClick = { onToggleSelection(image.id) },
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                )
            }
        }
    }
}
