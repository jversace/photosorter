package fr.photosorter.domain.repository

import fr.photosorter.domain.model.ImageItem
import fr.photosorter.domain.model.ScanProgress
import kotlinx.coroutines.flow.Flow

/**
 * Orchestration du scan : l'IHM observe la progression et déclenche un scan manuel.
 * (Contrat indicatif — à affiner avec Zoé si besoin lors de la tâche 68b19a34.)
 */
interface ScanRepository {
    val progress: Flow<ScanProgress>
    suspend fun launchScan(): Flow<List<ImageItem>>
    suspend fun getCachedImages(): List<ImageItem>
}
