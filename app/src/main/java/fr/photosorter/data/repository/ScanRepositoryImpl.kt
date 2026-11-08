package fr.photosorter.data.repository

import fr.photosorter.core.dispatchers.DispatchersProvider
import fr.photosorter.data.db.dao.ImageDao
import fr.photosorter.data.scanner.ScanEngine
import fr.photosorter.domain.model.ImageItem
import fr.photosorter.domain.model.ScanProgress
import fr.photosorter.domain.repository.ScanRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implémentation [ScanRepository] : délègue à [ScanEngine] (parcours + écriture Room)
 * et expose les images en cache depuis Room.
 */
@Singleton
class ScanRepositoryImpl @Inject constructor(
    private val scanEngine: ScanEngine,
    private val imageDao: ImageDao,
    private val dispatchers: DispatchersProvider
) : ScanRepository {

    override val progress: Flow<ScanProgress> = scanEngine.progress

    override suspend fun launchScan(): Flow<List<ImageItem>> = withContext(dispatchers.io) {
        val finalState = scanEngine.scan()
        if (finalState is ScanProgress.Error) {
            throw IllegalStateException(finalState.message)
        }
        val cached = imageDao.getAllWithLabels().map { it.toDomain() }
        kotlinx.coroutines.flow.flowOf(cached)
    }

    override suspend fun getCachedImages(): List<ImageItem> = withContext(dispatchers.io) {
        imageDao.getAllWithLabels().map { it.toDomain() }
    }

    fun cancel() = scanEngine.cancel()
}
