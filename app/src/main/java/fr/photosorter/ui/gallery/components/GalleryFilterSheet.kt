package fr.photosorter.ui.gallery.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import fr.photosorter.R
import fr.photosorter.domain.model.FilterState
import fr.photosorter.domain.model.ImageFormat

private const val BYTES_PER_MB = 1024L * 1024L

/**
 * Dialogue de filtrage de la galerie (tâche e93e44ff).
 *
 * Toutes les modifications sont locales (brouillon) jusqu'à validation via [onApply].
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun GalleryFilterSheet(
    initial: FilterState,
    labels: Set<String>,
    onDismiss: () -> Unit,
    onApply: (FilterState) -> Unit,
) {
    var formats by remember { mutableStateOf(initial.formats) }
    var labelsSelected by remember { mutableStateOf(initial.labels) }
    var includeAdult by remember { mutableStateOf(initial.includeAdult) }
    var minSizeMb by remember {
        mutableStateOf(initial.minSizeBytes?.let { (it / BYTES_PER_MB).toString() } ?: "")
    }

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
                Text(
                    stringResource(R.string.filters_formats),
                    fontWeight = FontWeight.SemiBold,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ImageFormat.entries.forEach { format ->
                        FilterChip(
                            selected = format in formats,
                            onClick = {
                                formats = if (format in formats) formats - format else formats + format
                            },
                            label = { Text(format.name) },
                        )
                    }
                }

                OutlinedTextField(
                    value = minSizeMb,
                    onValueChange = { value ->
                        if (value.all { it.isDigit() } && value.length <= 6) minSizeMb = value
                    },
                    label = { Text(stringResource(R.string.filters_min_size)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )

                if (labels.isNotEmpty()) {
                    Text(
                        stringResource(R.string.filters_labels),
                        fontWeight = FontWeight.SemiBold,
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        labels.sorted().forEach { label ->
                            FilterChip(
                                selected = label in labelsSelected,
                                onClick = {
                                    labelsSelected =
                                        if (label in labelsSelected) labelsSelected - label
                                        else labelsSelected + label
                                },
                                label = { Text(label) },
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(R.string.filters_include_adult))
                    Switch(
                        checked = includeAdult,
                        onCheckedChange = { includeAdult = it },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onApply(
                        initial.copy(
                            formats = formats,
                            labels = labelsSelected,
                            includeAdult = includeAdult,
                            minSizeBytes = minSizeMb.toLongOrNull()?.let { it * BYTES_PER_MB },
                        ),
                    )
                },
            ) { Text(stringResource(R.string.filters_apply)) }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { onApply(initial.copy(formats = emptySet(), labels = emptySet(), minSizeBytes = null, includeAdult = true)) }) {
                    Text(stringResource(R.string.filters_clear))
                }
                OutlinedButton(onClick = onDismiss) {
                    Text(stringResource(R.string.action_back))
                }
            }
        },
    )
}
