package fr.photosorter.ui.scan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.photosorter.domain.model.ScanProgress
import fr.photosorter.domain.repository.ScanRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel de l'écran de scan : déclenche le scan manuel et expose la progression.
 * Dépend de l'interface ScanRepository, implémentation fournie par Hilt.
 */
@HiltViewModel
class ScanViewModel @Inject constructor(
    private val scanRepository: ScanRepository,
) : ViewModel() {

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning

    val progress: StateFlow<ScanProgress> = scanRepository.progress
        .catch { e -> emit(ScanProgress.Error(e.message ?: "Erreur de scan")) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ScanProgress.Idle,
        )

    fun startScan() {
        if (_isScanning.value) return
        _isScanning.value = true
        viewModelScope.launch {
            scanRepository.launchScan().catch { /* erreurs remontées via progress */ }.collect { /* résultats affichés par la galerie */ }
            _isScanning.value = false
        }
    }
}
