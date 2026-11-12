package fr.photosorter.ui.gallery.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import fr.photosorter.R
import fr.photosorter.domain.model.FilterState
import fr.photosorter.domain.model.ImageFormat

/**
 * Dialogue de filtres de la galerie (e93e44ff).
 * Modifie une copie de [initialFilter] puis renvoie le résultat via [onApply].
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun GalleryFilterDialog(
    initialFilter: FilterState,
    availableLabels: List<String>,
    onDismiss: () -> Unit,
    onApply: (FilterState) -> Unit,
) {
    var formats by remember { mutableStateOf(initialFilter.formats) }
    var includeAdult by remember { mutableStateOf(initialFilter.includeAdult) }
    var minSizeMb by remember {
        mutableStateOf(
            initialFilter.minSizeBytes?.let { (it / (1024L * 1024L)).toString() } ?: "",
        )
    }
    var selectedLabels by remember { mutableStateOf(initialFilter.labels) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.filters_title)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(stringResource(R.string.filters_formats))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ImageFormat.entries.forEach { format ->
                        val selected = format in formats
                        FilterChip(
                            selected = selected,
                            onClick = {
                                formats = if (selected) formats - format else formats + format
                            },
                            label = { Text(format.name) },
                        )
                    }
                }

                Text(stringResource(R.string.filters_labels))
                if (availableLabels.isEmpty()) {
                    Text("—")
                } else {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        availableLabels.forEach { label ->
                            val selected = label in selectedLabels
                            FilterChip(
                                selected = selected,
                                onClick = {
                                    selectedLabels =
                                        if (selected) selectedLabels - label else selectedLabels + label
                                },
                                label = { Text(label) },
                            )
                        }
                    }
                }

                RowSwitch(
                    label = stringResource(R.string.filters_include_adult),
                    checked = includeAdult,
                    onCheckedChange = { includeAdult = it },
                )

                OutlinedTextField(
                    value = minSizeMb,
                    onValueChange = { input ->
                        if (input.isEmpty() || input.all { it.isDigit() }) minSizeMb = input
                    },
                    label = { Text(stringResource(R.string.filters_min_size)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onApply(
                        initialFilter.copy(
                            formats = formats,
                            includeAdult = includeAdult,
                            labels = selectedLabels,
                            minSizeBytes = minSizeMb.toLongOrNull()?.let { it * 1024L * 1024L },
                        ),
                    )
                },
            ) { Text(stringResource(R.string.filters_apply)) }
        },
        dismissButton = {
            TextButton(
                onClick = { onApply(FilterState()) },
            ) { Text(stringResource(R.string.filters_clear)) }
        },
    )
}

@Composable
private fun RowSwitch(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    androidx.compose.foundation.layout.Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, modifier = Modifier.weight(1f))
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
    }
}
