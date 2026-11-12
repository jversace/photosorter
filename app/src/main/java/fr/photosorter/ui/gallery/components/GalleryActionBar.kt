package fr.photosorter.ui.gallery.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import fr.photosorter.R

/** Barre d'actions affichée en bas quand une sélection multiple est active. */
@Composable
internal fun GalleryActionBar(
    enabled: Boolean,
    onDelete: () -> Unit,
    onShare: () -> Unit,
    onMove: () -> Unit,
    onCopy: () -> Unit,
    onLabel: () -> Unit,
) {
    Surface {
        androidx.compose.foundation.layout.Row(
            modifier = androidx.compose.ui.Modifier
                .fillMaxWidthCompat()
                .paddingHorizontal(),
        ) {
            ActionButton(Icons.Filled.Share, R.string.action_share, enabled, onShare)
            ActionButton(Icons.Filled.DriveFileMove, R.string.action_move, enabled, onMove)
            ActionButton(Icons.Filled.ContentCopy, R.string.action_copy, enabled, onCopy)
            ActionButton(Icons.Filled.Label, R.string.action_label, enabled, onLabel)
            ActionButton(Icons.Filled.Delete, R.string.action_delete, enabled, onDelete)
        }
    }
}

@Composable
private fun ActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    labelRes: Int,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    IconButton(onClick = onClick, enabled = enabled) {
        Icon(icon, contentDescription = stringResource(labelRes))
    }
}

private fun androidx.compose.ui.Modifier.fillMaxWidthCompat() =
    this.then(androidx.compose.foundation.layout.fillMaxWidth())

private fun androidx.compose.ui.Modifier.paddingHorizontal() =
    this.then(androidx.compose.foundation.layout.padding(
        horizontal = androidx.compose.ui.unit.dp_hack(),
    ))
