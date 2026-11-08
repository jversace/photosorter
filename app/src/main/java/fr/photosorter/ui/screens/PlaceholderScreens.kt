package fr.photosorter.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import fr.photosorter.R

/**
 * Écrans provisoires pour le socle de navigation (tâche 4060ffc0).
 * Remplacés par les vraies implémentations des tâches galerie/détail/scan/libellés.
 */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlaceholderScaffold(
    titleRes: Int,
    onBack: (() -> Unit)?,
    content: @Composable () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(titleRes)) },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.action_back),
                            )
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            content()
        }
    }
}

@Composable
fun GalleryPlaceholderScreen(
    onOpenDetail: (Long) -> Unit,
    onOpenScan: () -> Unit,
    onOpenLabels: () -> Unit,
) {
    PlaceholderScaffold(titleRes = R.string.screen_gallery_title, onBack = null) {
        Icon(
            imageVector = Icons.Filled.Photo,
            contentDescription = null, // décoratif : le texte ci-dessous porte le sens
        )
        Text(
            text = stringResource(R.string.screen_gallery_placeholder),
            style = MaterialTheme.typography.bodyLarge,
        )
        Button(onClick = onOpenScan) { Text(stringResource(R.string.action_open_scan)) }
        Button(onClick = onOpenLabels) { Text(stringResource(R.string.action_open_labels)) }
        // Bouton temporaire pour valider la navigation vers le détail (imageId fictif = 0)
        Button(onClick = { onOpenDetail(0L) }) { Text(stringResource(R.string.action_open_detail)) }
    }
}

@Composable
fun ImageDetailPlaceholderScreen(imageId: Long, onBack: () -> Unit) {
    PlaceholderScaffold(titleRes = R.string.screen_detail_title, onBack = onBack) {
        Text(
            text = stringResource(R.string.screen_detail_image_id, imageId),
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

@Composable
fun ScanPlaceholderScreen(onBack: () -> Unit) {
    PlaceholderScaffold(titleRes = R.string.screen_scan_title, onBack = onBack) {
        Text(
            text = stringResource(R.string.screen_scan_placeholder),
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

@Composable
fun LabelPlaceholderScreen(onBack: () -> Unit) {
    PlaceholderScaffold(titleRes = R.string.screen_labels_title, onBack = onBack) {
        Text(
            text = stringResource(R.string.screen_labels_placeholder),
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}
