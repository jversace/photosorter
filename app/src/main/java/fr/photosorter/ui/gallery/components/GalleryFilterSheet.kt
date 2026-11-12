package fr.photosorter.ui.gallery.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import fr.photosorter.R
import fr.photosorter.domain.model.FilterState
import fr.photosorter.domain.model.ImageFormat

/**
 * Feuille modale de filtres de la galerie. Travaille sur une copie locale de l'état de filtre,
 * appliquée uniquement via [onApply] (confirmation explicite).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun GalleryFilterSheet(
    initialFilter: FilterState,
    availableLabels: Set<String>,
    onDismiss: () -> Unit,
    onApply: (FilterState) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var selectedFormats by remember { mutableStateOf(initialFilter.formats) }
    var selectedLabels by remember { mutableStateOf(initialFilter.labels) }
    var includeAdult by remember { mutableStateOf(initialFilter.includeAdult) }
    var minSizeMb by remember {
        mutableStateOf(((initialFilter.minSizeBytes ?: 0L) / (1024L * 1024L)).toInt())
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(stringResource(R.string.filters_title), style = MaterialTheme.typography.titleLarge)

            // Formats
            Text(stringResource(R.string.filters_formats), style = MaterialTheme.typography.titleSmall)
            androidx.compose.foundation.layout.FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ImageFormat.entries.forEach { format ->
                    val selected = format in selectedFormats
                    FilterChip(
                        selected = selected,
                        onClick = {
                            selectedFormats = if (selected) selectedFormats - format else selectedFormats + format
                        },
                        label = { Text(format.name) },
                        leadingIcon = if (selected) {
                            { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
                        } else null,
                    )
                }
            }

            // Libellés
            Text(stringResource(R.string.filters_labels), style = MaterialTheme.typography.titleSmall)
            if (availableLabels.isEmpty()) {
                Text(
                    stringResource(R.string.filters_labels) + " :",
                    style = MaterialTheme.typography.bodySmall,
                )
            } else {
                androidx.compose.foundation.layout.FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    availableLabels.forEach { label ->
                        val selected = label in selectedLabels
                        FilterChip(
                            selected = selected,
                            onClick = {
                                selectedLabels = if (selected) selectedLabels - label else selectedLabels + label
                            },
                            label = { Text(label) },
                        )
                    }
                }
            }

            // Inclure adulte
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(stringResource(R.string.filters_include_adult))
                Switch(checked = includeAdult, onCheckedChange = { includeAdult = it })
            }

            // Taille minimale
            Text(
                text = stringResource(R.string.filters_min_size_value, minSizeMb),
                style = MaterialTheme.typography.titleSmall,
            )
            Slider(
                value = minSizeMb.toFloat(),
                onValueChange = { minSizeMb = it.toInt() },
                valueRange = 0f..50f,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = {
                    selectedFormats = emptySet()
                    selectedLabels = emptySet()
                    includeAdult = true
                    minSizeMb = 0
                }) { Text(stringResource(R.string.filters_clear)) }
                TextButton(onClick = {
                    onApply(
                        FilterState(
                            formats = selectedFormats,
                            labels = selectedLabels,
                            includeAdult = includeAdult,
                            minSizeBytes = if (minSizeMb <= 0) null else minSizeMb.toLong() * 1024L * 1024L,
                        ),
                    )
                }) { Text(stringResource(R.string.filters_apply)) }
            }
        }
    }
}
