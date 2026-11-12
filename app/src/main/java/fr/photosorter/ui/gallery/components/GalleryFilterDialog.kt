package fr.photosorter.ui.gallery.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
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
import fr.photosorter.R
import fr.photosorter.domain.model.FilterState
import fr.photosorter.domain.model.ImageFormat

private const val MB = 1024L * 1024L

@Composable
internal fun GalleryFilterDialog(
    initialFilter: FilterState,
    availableLabels: List<String>,
    onDismiss: () -> Unit,
    onApply: (FilterState) -> Unit,
) {
    var formats by remember { mutableStateOf(initialFilter.formats) }
    var labels by remember { mutableStateOf(initialFilter.labels) }
    var includeAdult by remember { mutableStateOf(initialFilter.includeAdult) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.filters_title)) },
        text = {
            Column {
                ImageFormat.entries.forEach { format ->
                    ToggleRow(format.name, format in formats) { on ->
                        formats = if (on) formats + format else formats - format
                    }
                }
                ToggleRow(stringResource(R.string.filters_include_adult), includeAdult) { includeAdult = it }
                availableLabels.forEach { label ->
                    ToggleRow(label, label in labels) { on ->
                        labels = if (on) labels + label else labels - label
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onApply(FilterState(formats = formats, labels = labels, includeAdult = includeAdult))
            }) { Text(stringResource(R.string.filters_apply)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel_selection)) }
        },
    )
}

@Composable
private fun ToggleRow(text: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Checkbox(checked = checked, onCheckedChange = onChange)
        Text(text)
    }
}
