package fr.photosorter.ui.gallery

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items as listItems
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.photosorter.domain.model.ImageItem
import fr.photosorter.ui.gallery.components.GalleryEmpty
import fr.photosorter.ui.gallery.components.GalleryFilterDialog
import fr.photosorter.ui.gallery.components.GalleryGridCell
import fr.photosorter.ui.gallery.components.GalleryListRow
import fr.photosorter.ui.gallery.components.GalleryLoading
import fr.photosorter.ui.gallery.components.GalleryTopBar

@Composable
fun GalleryScreen(
    onOpenDetail: (Long) -> Unit,
    onOpenScan: () -> Unit,
    onOpenLabels: () -> Unit,
    viewModel: GalleryViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showFilters by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            GalleryTopBar(
                displayMode = state.displayMode,
                selectionMode = state.isSelectionMode,
                selectedCount = state.selectedIds.size,
                sortField = state.sort.field,
                onToggleDisplayMode = {
                    viewModel.setDisplayMode(
                        if (state.displayMode == GalleryDisplayMode.GRID) GalleryDisplayMode.LIST
                        else GalleryDisplayMode.GRID,
                    )
                },
                onSelectAll = viewModel::toggleSelectAll,
                onClearSelection = viewModel::clearSelection,
                onSortSelected = viewModel::setSort,
                onOpenFilters = { showFilters = true },
                onOpenScan = onOpenScan,
                onOpenLabels = onOpenLabels,
            )
        },
    ) { padding ->
        GalleryContent(
            state = state,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            onClick = { item ->
                if (state.isSelectionMode) viewModel.toggleSelection(item.id)
                else onOpenDetail(item.id)
            },
            onLongClick = { item -> viewModel.toggleSelection(item.id) },
        )
    }

    if (showFilters) {
        GalleryFilterDialog(
            initialFilter = state.filter,
            availableLabels = state.images.flatMap { it.labels }.distinct().sorted(),
            onDismiss = { showFilters = false },
            onApply = { newFilter ->
                viewModel.setFilter(newFilter)
                showFilters = false
            },
        )
    }
}

@Composable
private fun GalleryContent(
    state: GalleryUiState,
    modifier: Modifier,
    onClick: (ImageItem) -> Unit,
    onLongClick: (ImageItem) -> Unit,
) {
    when {
        state.isLoading -> GalleryLoading(modifier)
        state.errorMessage != null -> GalleryEmpty(
            modifier = modifier,
            message = state.errorMessage,
        )
        state.images.isEmpty() -> GalleryEmpty(modifier = modifier)
        else -> when (state.displayMode) {
            GalleryDisplayMode.GRID -> LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 96.dp),
                modifier = modifier,
                contentPadding = PaddingValues(2.dp),
            ) {
                gridItems(state.images, key = { it.id }) { item ->
                    GalleryGridCell(
                        item = item,
                        selected = item.id in state.selectedIds,
                        selectionMode = state.isSelectionMode,
                        onClick = { onClick(item) },
                        onLongClick = { onLongClick(item) },
                    )
                }
            }

            GalleryDisplayMode.LIST -> LazyColumn(modifier = modifier) {
                listItems(state.images, key = { it.id }) { item ->
                    GalleryListRow(
                        item = item,
                        selected = item.id in state.selectedIds,
                        selectionMode = state.isSelectionMode,
                        onClick = { onClick(item) },
                        onLongClick = { onLongClick(item) },
                    )
                }
            }
        }
    }
}
