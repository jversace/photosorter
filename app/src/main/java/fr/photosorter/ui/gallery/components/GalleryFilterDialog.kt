package fr.photosorter.ui.gallery.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
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

private enum class AdultMode(val includeAdult: Boolean, val adultOnly: Boolean) {
    SHOW_ALL(includeAdult = true, adultOnly = false),
    HIDE_ADULT(includeAdult = false, adultOnly = false),
    ADULT_ONLY(includeAdult = false, adultOnly = true);

    companion object {
        fun fromFilter(f: FilterState): AdultMode = when {
            f.adultOnly -> ADULT_ONLY
            !f.includeAdult -> HIDE_ADULT
            else -> SHOW_ALL
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun GalleryFilterDialog(
    initialFilter: FilterState,
    availableLabels: List<String>,
    onDismiss: () -> Unit,
    onApply: (FilterState) -> Unit,
) {
    var formats by remember { mutableStateOf(initialFilter.formats) }
    var labels by remember { mutableStateOf(initialFilter.labels) }
    var adultMode by remember { mutableStateOf(AdultMode.fromFilter(initialFilter)) }
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
                Text(stringResource(R.string.filters_adult_section))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = adultMode == AdultMode.SHOW_ALL,
                        onClick = { adultMode = AdultMode.SHOW_ALL },
                        label = { Text(stringResource(R.string.filters_adult_show_all)) },
                    )
                    FilterChip(
                        selected = adultMode == AdultMode.HIDE_ADULT,
                        onClick = { adultMode = AdultMode.HIDE_ADULT },
                        label = { Text(stringResource(R.string.filters_adult_hide)) },
                    )
                    FilterChip(
                        selected = adultMode == AdultMode.ADULT_ONLY,
                        onClick = { adultMode = AdultMode.ADULT_ONLY },
                        label = { Text(stringResource(R.string.filters_adult_only)) },
                    )
                }
                NumField(from, { from = it }, stringResource(R.string.filters_date_from))
                NumField(to, { to = it }, stringResource(R.string.filters_date_to))
                NumField(size, { size = it }, stringResource(R.string.filters_min_size))
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onApply(
                    FilterState(
                        formats = formats,
                        dateFrom = from.toLongOrNull(),
                        dateTo = to.toLongOrNull(),
                        minSizeBytes = size.toLongOrNull()?.times(MB),
                        labels = labels,
                        includeAdult = adultMode.includeAdult,
                        adultOnly = adultMode.adultOnly,
                    )
                )
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
