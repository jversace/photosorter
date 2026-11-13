package fr.photosorter.ui.labels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.photosorter.domain.model.Label
import fr.photosorter.domain.repository.LabelRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LabelViewModel @Inject constructor(
    private val repository: LabelRepository
) : ViewModel() {

    val labels: StateFlow<List<Label>> = repository.observeLabels()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun create(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch { repository.createLabel(trimmed) }
    }

    fun rename(label: Label, newName: String) {
        if (label.isSystem) return
        val trimmed = newName.trim()
        if (trimmed.isEmpty() || trimmed == label.name) return
        viewModelScope.launch { repository.renameLabel(label.name, trimmed) }
    }

    fun delete(label: Label) {
        if (label.isSystem) return
        viewModelScope.launch { repository.deleteLabel(label.name) }
    }
}
