package fr.photosorter.ui.labels

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.photosorter.domain.model.Label

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LabelScreen(
    onBack: () -> Unit,
    viewModel: LabelViewModel = hiltViewModel(),
) {
    val labels by viewModel.labels.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<Label?>(null) }
    var creating by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringRes("labels_title", "Libellés")) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { creating = true }) {
                Icon(Icons.Filled.Add, contentDescription = "Nouveau libellé")
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            items(labels, key = { it.name }) { label ->
                LabelRow(
                    label = label,
                    onRename = { editing = label },
                    onDelete = { viewModel.delete(label) },
                )
            }
        }
    }

    if (creating) {
        LabelNameDialog(
            title = "Nouveau libellé",
            initial = "",
            onConfirm = { name -> viewModel.create(name); creating = false },
            onDismiss = { creating = false },
        )
    }

    editing?.let { label ->
        LabelNameDialog(
            title = "Renommer",
            initial = label.name,
            onConfirm = { name -> viewModel.rename(label, name); editing = null },
            onDismiss = { editing = null },
        )
    }
}

@Composable
private fun LabelRow(label: Label, onRename: () -> Unit, onDelete: () -> Unit) {
    ListItem(
        headlineContent = { Text(label.name) },
        supportingContent = if (label.isSystem) {
            { Text("Libellé système") }
        } else null,
        trailingContent = {
            if (!label.isSystem) {
                IconButton(onClick = onRename) {
                    Icon(Icons.Filled.Edit, contentDescription = "Renommer")
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Filled.Delete, contentDescription = "Supprimer")
                }
            }
        },
    )
}

@Composable
private fun LabelNameDialog(
    title: String,
    initial: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Nom") },
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(text) },
                enabled = text.isNotBlank(),
            ) { Text("Valider") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Annuler") }
        },
    )
}

private fun stringRes(key: String, fallback: String): String = fallback
