package fr.photosorter.ui.gallery

import androidx.compose.foundation.layout.padding
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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.photosorter.R
import fr.photosorter.ui.gallery.components.DeleteConfirmDialog
import fr.photosorter.ui.gallery.components.DestinationDialog
import fr.photosorter.ui.gallery.components.GalleryActionBar
import fr.photosorter.ui.gallery.components.GalleryFilterDialog
import fr.photosorter.ui.gallery.components.GalleryTopBar
import fr.photosorter.ui.gallery.components.LabelDialog

@Composable
fun GalleryRoute(
    onOpenImage: (Long) -> Unit,
    onOpenScan: () -> Unit,
    onOpenLabels: () -> Unit,
    viewModel: GalleryViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    GalleryScreen(state, onOpenImage, onOpenScan, onOpenLabels, viewModel)
}

@Composable
internal fun GalleryScreen(
    state: GalleryUiState,
    onOpenImage: (Long) -> Unit,
    onOpenScan: () -> Unit,
    onOpenLabels: () -> Unit,
    viewModel: GalleryViewModel,
) {
    val snackbar = remember { SnackbarHostState() }
    var showDelete by remember { mutableStateOf(false) }
    var destMove by remember { mutableStateOf(false) }
    var destCopy by remember { mutableStateOf(false) }
    var showFilters by remember { mutableStateOf(false) }
    var labelAssign by remember { mutableStateOf<Boolean?>(null) }

    state.feedback?.let { fb ->
        val msg = stringResource(feedbackRes(fb))
        LaunchedEffect(fb) { snackbar.showSnackbar(msg); viewModel.consumeFeedback() }
    }

    Scaffold(
        topBar = {
            GalleryTopBar(
                displayMode = state.displayMode,
                selectionMode = state.isSelectionMode,
                selectedCount = state.selectedCount,
                sortField = state.sort.field,
                onToggleDisplayMode = {
                    val next = if (state.displayMode == GalleryDisplayMode.GRID) GalleryDisplayMode.LIST else GalleryDisplayMode.GRID
                    viewModel.setDisplayMode(next)
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
                    onDelete = { showDelete = true },
                    onShare = viewModel::shareSelected,
                    onMove = { destMove = true },
                    onCopy = { destCopy = true },
                    onLabel = { labelAssign = true },
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        GalleryContent(
            state = state,
            onImageClick = { id -> if (state.isSelectionMode) viewModel.toggleSelection(id) else onOpenImage(id) },
            onToggleSelection = viewModel::toggleSelection,
            modifier = Modifier.padding(padding),
        )
    }

    if (showDelete) DeleteConfirmDialog(state.selectedCount, { showDelete = false; viewModel.deleteSelected() }, { showDelete = false })
    if (destMove) DestinationDialog(stringResource(R.string.action_move), { uri -> destMove = false; viewModel.moveSelected(uri) }, { destMove = false })
    if (destCopy) DestinationDialog(stringResource(R.string.action_copy), { uri -> destCopy = false; viewModel.copySelected(uri) }, { destCopy = false })
    if (showFilters) GalleryFilterDialog(state.filter, state.availableLabels, { showFilters = false }, { f -> showFilters = false; viewModel.setFilter(f) })
    labelAssign?.let { assign ->
        LabelDialog(assign, { name -> labelAssign = null; if (assign) viewModel.assignLabel(name) else viewModel.unassignLabel(name) }, { labelAssign = null })
    }
}
