package fr.photosorter.data.repository

import android.content.Context
import android.content.Intent
import android.net.Uri
import fr.photosorter.core.dispatchers.DispatchersProvider
import fr.photosorter.core.result.AppError
import fr.photosorter.core.result.AppResult
import fr.photosorter.data.db.dao.ImageDao
import fr.photosorter.data.db.dao.ImageQueryDao
import fr.photosorter.domain.model.ADULT_LABEL
import fr.photosorter.domain.model.FilterState
import fr.photosorter.domain.model.ImageItem
import fr.photosorter.domain.model.SortCriteria
import fr.photosorter.domain.model.SortField
import fr.photosorter.domain.repository.ImageRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileNotFoundException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation [ImageRepository] :
 * - Lecture/tri/filtre pousses en SQL via [ImageQueryDao].
 * - Actions fichiers via [FileActions] (SAF + fallback).
 * - Suppression via [MediaStoreDeleter] (scoped storage : confirmation systeme possible).
 */
@Singleton
class ImageRepositoryImpl @Inject constructor(
    private val context: Context,
    private val imageDao: ImageDao,
    private val imageQueryDao: ImageQueryDao,
    private val fileActions: FileActions,
    private val mediaStoreDeleter: MediaStoreDeleter,
    private val dispatchers: DispatchersProvider
) : ImageRepository {

    override fun observeImages(sort: SortCriteria, filter: FilterState): Flow<List<ImageItem>> =
        imageQueryDao.observeFiltered(
            sort = sort.field.toSortCode(),
            asc = if (sort.ascending) 1 else 0,
            includeAdult = if (filter.includeAdult) 1 else 0,
            adultOnly = if (filter.adultOnly) 1 else 0,
            adultLabel = ADULT_LABEL,
            hasFormats = if (filter.formats.isEmpty()) 0 else 1,
            formats = filter.formats.map { it.name },
            dateFrom = filter.dateFrom,
            dateTo = filter.dateTo,
            minSize = filter.minSizeBytes,
            hasLabels = if (filter.labels.isEmpty()) 0 else 1,
            labels = filter.labels.toList()
        ).map { rows -> rows.map { it.toDomain() } }

    override suspend fun getImages(filter: FilterState): List<ImageItem> =
        withContext(dispatchers.io) {
            imageDao.getAllWithLabels()
                .map { it.toDomain() }
                .filter { matchesFilter(it, filter) }
        }

    override suspend fun delete(items: List<ImageItem>): AppResult<Unit> =
        withContext(dispatchers.io) {
            var lastError: AppError? = null
            val pendingUris = mutableListOf<String>()
            var pendingSender: android.content.IntentSender? = null

            for (item in items) {
                when (val req = mediaStoreDeleter.requestDelete(item.uri)) {
                    is DeleteRequest.Completed -> {
                        if (req.error != null) {
                            lastError = req.error
                        } else {
                            imageDao.deleteById(item.id)
                        }
                    }
                    is DeleteRequest.NeedsConfirmation -> {
                        pendingUris += req.uris
                        if (req.intentSender != null) pendingSender = req.intentSender
                    }
                }
            }

            when {
                pendingUris.isNotEmpty() -> AppResult.PendingDelete(pendingSender, pendingUris)
                lastError != null -> AppResult.Failure(lastError!!)
                else -> AppResult.Success(Unit)
            }
        }

    override suspend fun deleteConfirmed(uris: List<String>): AppResult<Unit> =
        withContext(dispatchers.io) {
            val err = mediaStoreDeleter.confirmDelete(uris)
            if (err != null) return@withContext AppResult.Failure(err)

            // Nettoyage DB : on retire les images dont le fichier n existe plus.
            val gone = imageDao.getAllUris().filter { !File(Uri.parse(it).path.orEmpty()).exists() }
            if (gone.isNotEmpty()) imageDao.deleteByUris(gone)
            AppResult.Success(Unit)
        }
