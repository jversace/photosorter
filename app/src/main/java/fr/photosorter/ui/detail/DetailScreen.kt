package fr.photosorter.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import fr.photosorter.R
import fr.photosorter.domain.model.ImageItem

@Composable
fun DetailScreen(
    onBack: () -> Unit,
    viewModel: DetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    DetailContent(
        state = state,
        onBack = onBack,
        onPrevious = viewModel::previous,
        onNext = viewModel::next,
    )
}

@Composable
private fun DetailContent(
    state: DetailUiState,
    onBack: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    var showInfo by remember { mutableStateOf(false) }
    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        state.current?.let { item ->
            ZoomableImage(item = item, modifier = Modifier.fillMaxSize())
        }
        if (showInfo) {
            state.current?.let { item ->
                ExifPanel(
                    item = item,
                    modifier = Modifier.align(Alignment.BottomCenter)
                        .fillMaxWidth().padding(8.dp),
                )
            }
        }
        Row(
            modifier = Modifier.align(Alignment.TopStart).fillMaxWidth().padding(8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.KeyboardArrowLeft, stringResource(R.string.action_back), tint = Color.White)
            }
            IconButton(onClick = { showInfo = !showInfo }) {
                Icon(
                    Icons.Filled.Info,
                    stringResource(R.string.detail_toggle_info),
                    tint = if (showInfo) MaterialTheme.colorScheme.primary else Color.White,
                )
            }
        }
        if (state.hasPrevious) {
            FilledTonalIconButton(
                onClick = onPrevious,
                modifier = Modifier.align(Alignment.CenterStart).padding(8.dp),
            ) {
                Icon(Icons.Filled.KeyboardArrowLeft, stringResource(R.string.detail_previous))
            }
        }
        if (state.hasNext) {
            FilledTonalIconButton(
                onClick = onNext,
                modifier = Modifier.align(Alignment.CenterEnd).padding(8.dp),
            ) {
                Icon(Icons.Filled.KeyboardArrowRight, stringResource(R.string.detail_next))
            }
        }
    }
}

@Composable
private fun ZoomableImage(item: ImageItem, modifier: Modifier = Modifier) {
    var scale by remember(item.id) { mutableFloatStateOf(1f) }
    var offsetX by remember(item.id) { mutableFloatStateOf(0f) }
    var offsetY by remember(item.id) { mutableFloatStateOf(0f) }
    AsyncImage(
        model = item.uri,
        contentDescription = item.name,
        contentScale = ContentScale.Fit,
        modifier = modifier
            .pointerInput(item.id) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale = (scale * zoom).coerceIn(1f, 5f)
                    if (scale > 1f) {
                        offsetX += pan.x
                        offsetY += pan.y
                    } else {
                        offsetX = 0f
                        offsetY = 0f
                    }
                }
            }
            .graphicsLayer(
                scaleX = scale, scaleY = scale,
                translationX = offsetX, translationY = offsetY,
            ),
    )
}
