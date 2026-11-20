package fr.photosorter.ui.scan

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.photosorter.R
import fr.photosorter.domain.model.ScanProgress

private val requiredPermissions: Array<String> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        arrayOf(Manifest.permission.READ_MEDIA_IMAGES)
    } else {
        arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

private fun hasScanPermission(context: android.content.Context): Boolean =
    requiredPermissions.all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanScreen(
    onBack: () -> Unit,
    viewModel: ScanViewModel = hiltViewModel(),
) {
    val progress by viewModel.progress.collectAsStateWithLifecycle()
    val isScanning by viewModel.isScanning.collectAsStateWithLifecycle()

    val context = LocalContext.current
    var permissionGranted by remember { mutableStateOf(hasScanPermission(context)) }
    var permissionRequested by rememberSaveable { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        permissionRequested = true
        permissionGranted = requiredPermissions.all { result[it] == true } || hasScanPermission(context)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.screen_scan_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (!permissionGranted) {
                Text(
                    text = stringResource(R.string.scan_permission_required),
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedButton(
                    onClick = { permissionLauncher.launch(requiredPermissions) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.action_grant_permission))
                }
                if (permissionRequested) {
                    Text(
                        text = stringResource(R.string.scan_permission_denied),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            ScanStatus(progress = progress, modifier = Modifier.fillMaxWidth())

            Button(
                onClick = viewModel::startScan,
                enabled = !isScanning && permissionGranted,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    imageVector = Icons.Filled.Search,
                    contentDescription = null,
                )
                Text(
                    text = stringResource(R.string.action_start_scan),
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun ScanStatus(progress: ScanProgress, modifier: Modifier = Modifier) {
    when (progress) {
        is ScanProgress.Idle -> Text(
            text = stringResource(R.string.scan_idle),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            modifier = modifier,
        )

        is ScanProgress.Running -> Column(
            modifier = modifier,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = if (progress.total > 0) {
                    stringResource(R.string.scan_running_with_total, progress.done, progress.total)
                } else {
                    stringResource(R.string.scan_running, progress.done)
                },
                style = MaterialTheme.typography.bodyLarge,
            )
            if (progress.total > 0) {
                LinearProgressIndicator(
                    progress = { progress.done.toFloat() / progress.total },
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            Text(
                text = progress.current,
                style = MaterialTheme.typography.bodySmall,
            )
        }

        is ScanProgress.Completed -> Text(
            text = stringResource(R.string.scan_completed, progress.count),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            modifier = modifier,
        )

        is ScanProgress.Error -> Text(
            text = stringResource(R.string.scan_error, progress.message),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center,
            modifier = modifier,
        )
    }
}
