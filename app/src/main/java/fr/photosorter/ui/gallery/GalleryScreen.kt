package fr.photosorter.ui.gallery

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.photosorter.R
import fr.photosorter.ui.gallery.components.DeleteConfirmDialog
import fr.photosorter.ui.gallery.components.GalleryActionBar
import fr.photosorter.ui.gallery.components.GalleryFilterDialog
import fr.photosorter.ui.gallery.components.GalleryTopBar
import fr.photosorter.ui.gallery.components.LabelDialog

/** Action à exécuter après le choix d'un répertoire destination via SAF. */
private enum class PendingDestAction { MOVE, COPY }

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
    val context = LocalContext.current
    var showDelete by remember { mutableStateOf(false) }
    var showFilters by remember { mutableStateOf(false) }
    var labelAssign by remember { mutableStateOf<Boolean?>(null) }
    var pendingDestAction by remember { mutableStateOf<PendingDestAction?>(null) }

    val openTreeLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { treeUri ->
        val action = pendingDestAction
        pendingDestAction = null
        if (treeUri != null && action != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    treeUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            } catch (_: SecurityException) {
                // Permission non persistable : on continue avec l'URI fourni.
            }
            when (action) {
                PendingDestAction.MOVE -> viewModel.moveSelected(treeUri.toString())
                PendingDestAction.COPY -> viewModel.copySelected(treeUri.toString())
            }
        }
    }

    // Launcher pour la confirmation système de suppression scoped storage (API >= 29).
    val deleteIntentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        val pending = state.pendingDelete
        if (result.resultCode == Activity.RESULT_OK && pending != null) {
            viewModel.onDeleteConfirmed(pending.uris)
        } else {
            viewModel.onDeleteCancelled()
        }
    }

    // Observation de la demande de confirmation système : on lance l'IntentSender fourni.
    LaunchedEffect(state.pendingDelete) {
        val pending = state.pendingDelete ?: return@LaunchedEffect
        val sender = pending.intentSender
        if (sender != null) {
            deleteIntentLauncher.launch(IntentSenderRequest.Builder(sender).build())
        } else {
            // Repli API 29 : rien à lancer, on valide directement le nettoyage DB.
            viewModel.onDeleteConfirmed(pending.uris)
        }
    }

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
                    onMove = {
                        pendingDestAction = PendingDestAction.MOVE
                        openTreeLauncher.launch(null)
                    },
                    onCopy = {
                        pendingDestAction = PendingDestAction.COPY
                        openTreeLauncher.launch(null)
                    },
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
    if (showFilters) GalleryFilterDialog(state.filter, state.availableLabels, { showFilters = false }, { f -> showFilters = false; viewModel.setFilter(f) })
    labelAssign?.let { assign ->
        LabelDialog(assign, { name -> labelAssign = null; if (assign) viewModel.assignLabel(name) else viewModel.unassignLabel(name) }, { labelAssign = null })
    }
}
