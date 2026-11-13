package fr.photosorter.ui.gallery.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedTextField
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
    var from by remember { mutableStateOf(initialFilter.dateFrom?.toString() ?: "") }
    var to by remember { mutableStateOf(initialFilter.dateTo?.toString() ?: "") }
    var size by remember { mutableStateOf(initialFilter.minSizeBytes?.div(MB)?.toString() ?: "") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.filters_title)) },
        text = {
            Column {
                ImageFormat.entries.forEach { f ->
                    CheckRow(f.name, f in formats) { on -> formats = if (on) formats + f else formats - f }
                }
                availableLabels.forEach { l ->
                    CheckRow(l, l in labels) { on -> labels = if (on) labels + l else labels - l }
                }
                CheckRow(stringResource(R.string.filters_include_adult), includeAdult) { includeAdult = it }
                NumField(from, { from = it }, stringResource(R.string.filters_date_from))
                NumField(to, { to = it }, stringResource(R.string.filters_date_to))
                NumField(size, { size = it }, stringResource(R.string.filters_min_size))
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onApply(FilterState(formats, from.toLongOrNull(), to.toLongOrNull(), size.toLongOrNull()?.times(MB), labels, includeAdult))
            }) { Text(stringResource(R.string.filters_apply)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel_selection)) } },
    )
}

@Composable
private fun CheckRow(text: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Checkbox(checked = checked, onCheckedChange = onChange)
        Text(text)
    }
}

@Composable
private fun NumField(value: String, onChange: (String) -> Unit, label: String) {
    OutlinedTextField(value, onChange, singleLine = true, label = { Text(label) },
        modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
}
