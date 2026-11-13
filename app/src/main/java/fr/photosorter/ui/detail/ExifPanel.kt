package fr.photosorter.ui.detail

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import fr.photosorter.R
import fr.photosorter.domain.model.ImageItem
import fr.photosorter.ui.gallery.components.GalleryFormatting

/** Panneau des métadonnées EXIF/infos image affiché en bas de l'écran détail. */
@Composable
internal fun ExifPanel(item: ImageItem, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        tonalElevation = 6.dp,
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(item.name, style = MaterialTheme.typography.titleMedium)
            ExifRow(R.string.detail_exif_format, item.format.name)
            ExifRow(R.string.detail_exif_size, GalleryFormatting.formatSize(item.sizeBytes))
            ExifRow(R.string.detail_exif_dimensions, "${item.width} × ${item.height}")
            ExifRow(R.string.detail_exif_date_modified, GalleryFormatting.formatDate(item.dateModified))
            item.dateTaken?.let {
                ExifRow(R.string.detail_exif_date_taken, GalleryFormatting.formatDate(it))
            }
            if (item.labels.isNotEmpty()) {
                ExifRow(R.string.detail_exif_labels, item.labels.joinToString(", "))
            }
        }
    }
}

@Composable
private fun ExifRow(labelRes: Int, value: String) {
    Text(
        text = stringResource(labelRes) + " : " + value,
        style = MaterialTheme.typography.bodySmall,
    )
}
