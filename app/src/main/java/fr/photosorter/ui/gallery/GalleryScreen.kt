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
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.photosorter.R
import fr.photosorter.domain.model.ImageItem
import fr.photosorter.ui.gallery.components.DeleteConfirmDialog
import fr.photosorter.ui.gallery.components.DestinationDialog
import fr.photosorter.ui.gallery.components.GalleryActionBar
import fr.photosorter.ui.gallery.components.GalleryEmpty
import fr.photosorter.ui.gallery.components.GalleryFilterDialog
import fr.photosorter.ui.gallery.components.GalleryGridCell
import fr.photosorter.ui.gallery.components.GalleryListRow
import fr.photosorter.ui.gallery.components.GalleryLoading
import fr.photosorter.ui.gallery.components.GalleryTopBar
import fr.photosorter.ui.gallery.components.LabelDialog

private enum class ActionDialog { NONE, DELETE, MOVE, COPY, LABEL_ASSIGN, LABEL_REMOVE }

@Composable
fun GalleryScreen(
    onOpenDetail: (Long) -> Unit,
    onOpenScan: () -> Unit,
    onOpenLabels: () -> Unit,
    viewModel: GalleryViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showFilters by remember { mutableStateOf(false) }
    var dialog by remember { mutableStateOf(ActionDialog.NONE) }
    val snackbar = remember { SnackbarHostState() }

    val feedbackText = state.feedback?.let { stringResource(feedbackRes(it)) }
    LaunchedEffect(state.feedback) {
        if (feedbackText != null) {
            snackbar.showSnackbar(feedbackText)
            viewModel.consumeFeedback()
        }
    }
    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let { snackbar.showSnackbar(it) }
    }

    val selectedCount = state.selectedIds.size

    Scaffold(
        topBar = {
            GalleryTopBar(
                displayMode = state.displayMode,
                selectionMode = state.isSelectionMode,
                selectedCount = selectedCount,
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
        bottomBar = {
            if (state.isSelectionMode) {
                GalleryActionBar(
                    enabled = !state.actionsBusy,
                    onDelete = { dialog = ActionDialog.DELETE },
                    onShare = viewModel::shareSelected,
                    onMove = { dialog = ActionDialog.MOVE },
                    onCopy = { dialog = ActionDialog.COPY },
                    onLabel = { dialog = ActionDialog.LABEL_ASSIGN },
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        GalleryContent(
            state = state,
            modifier = Modifier.fillMaxSize().padding(padding),
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
            onApply = { newFilter -> viewModel.setFilter(newFilter); showFilters = false },
        )
    }

    when (dialog) {
        ActionDialog.DELETE -> DeleteConfirmDialog(
            count = selectedCount,
            onConfirm = { dialog = ActionDialog.NONE; viewModel.deleteSelected() },
            onDismiss = { dialog = ActionDialog.NONE },
        )
        ActionDialog.MOVE -> DestinationDialog(
            title = stringResource(R.string.dialog_move_title),
            onConfirm = { uri -> dialog = ActionDialog.NONE; viewModel.moveSelected(uri) },
            onDismiss = { dialog = ActionDialog.NONE },
        )
        ActionDialog.COPY -> DestinationDialog(
            title = stringResource(R.string.dialog_copy_title),
            onConfirm = { uri -> dialog = ActionDialog.NONE; viewModel.copySelected(uri) },
            onDismiss = { dialog = ActionDialog.NONE },
        )
        ActionDialog.LABEL_ASSIGN -> LabelDialog(
            assign = true,
            onConfirm = { name -> dialog = ActionDialog.NONE; viewModel.assignLabel(name) },
            onDismiss = { dialog = ActionDialog.NONE },
        )
        ActionDialog.LABEL_REMOVE -> LabelDialog(
            assign = false,
            onConfirm = { name -> dialog = ActionDialog.NONE; viewModel.unassignLabel(name) },
            onDismiss = { dialog = ActionDialog.NONE },
        )
        ActionDialog.NONE -> Unit
    }
}

private fun feedbackRes(f: GalleryFeedback): Int = when (f) {
    GalleryFeedback.DELETED -> R.string.feedback_deleted
    GalleryFeedback.SHARED -> R.string.feedback_shared
    GalleryFeedback.MOVED -> R.string.feedback_moved
    GalleryFeedback.COPIED -> R.string.feedback_copied
    GalleryFeedback.LABEL_ASSIGNED -> R.string.feedback_label_assigned
    GalleryFeedback.LABEL_REMOVED -> R.string.feedback_label_removed
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
        state.errorMessage != null -> GalleryEmpty(modifier = modifier, message = state.errorMessage)
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
