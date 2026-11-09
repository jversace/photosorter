package fr.photosorter.ui.gallery.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import fr.photosorter.R
import fr.photosorter.domain.model.SortField
import fr.photosorter.ui.gallery.GalleryDisplayMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun GalleryTopBar(
    displayMode: GalleryDisplayMode,
    selectionMode: Boolean,
    selectedCount: Int,
    sortField: SortField,
    onToggleDisplayMode: () -> Unit,
    onSelectAll: () -> Unit,
    onClearSelection: () -> Unit,
    onSortSelected: (SortField) -> Unit,
    onOpenScan: () -> Unit,
    onOpenLabels: () -> Unit,
) {
    if (selectionMode) {
        TopAppBar(
            title = { Text(stringResource(R.string.gallery_selected_count, selectedCount)) },
            navigationIcon = {
                IconButton(onClick = onClearSelection) {
                    Icon(Icons.Filled.Close, stringResource(R.string.action_cancel_selection))
                }
            },
            actions = {
                IconButton(onClick = onSelectAll) {
                    Icon(Icons.Filled.SelectAll, stringResource(R.string.action_select_all))
                }
            },
        )
    } else {
        var sortMenuOpen by remember { mutableStateOf(false) }
        TopAppBar(
            title = { Text(stringResource(R.string.gallery_title)) },
            actions = {
                IconButton(onClick = onToggleDisplayMode) {
                    Icon(
                        imageVector = if (displayMode == GalleryDisplayMode.GRID) Icons.Filled.ViewList
                        else Icons.Filled.GridView,
                        contentDescription = stringResource(R.string.action_toggle_display_mode),
                    )
                }
                IconButton(onClick = { sortMenuOpen = true }) {
                    Icon(Icons.AutoMirrored.Filled.Sort, stringResource(R.string.action_sort))
                }
                DropdownMenu(expanded = sortMenuOpen, onDismissRequest = { sortMenuOpen = false }) {
                    SortField.entries.forEach { field ->
                        DropdownMenuItem(
                            text = { Text(sortLabel(field)) },
                            onClick = { sortMenuOpen = false; onSortSelected(field) },
                        )
                    }
                }
            },
        )
    }
}

@Composable
private fun sortLabel(field: SortField): String = stringResource(
    when (field) {
        SortField.NAME -> R.string.sort_name
        SortField.DATE -> R.string.sort_date
        SortField.SIZE -> R.string.sort_size
        SortField.FORMAT -> R.string.sort_format
        SortField.LABEL -> R.string.sort_label
    }
)
