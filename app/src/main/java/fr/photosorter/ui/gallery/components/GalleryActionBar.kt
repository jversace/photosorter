package fr.photosorter.ui.gallery.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
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
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceAround,
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
    icon: ImageVector,
    labelRes: Int,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    IconButton(onClick = onClick, enabled = enabled) {
        Icon(icon, contentDescription = stringResource(labelRes))
    }
}
