package fr.photosorter.domain.usecase

import fr.photosorter.domain.model.ImageItem
import fr.photosorter.domain.repository.ScanRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/** Lance un scan manuel et renvoie les images résultantes. */
class StartScan @Inject constructor(
    private val repository: ScanRepository
) {
    suspend operator fun invoke(): Flow<List<ImageItem>> = repository.launchScan()
}
