package fr.photosorter.data.repository

import android.content.Context
import android.content.Intent
import android.net.Uri
import fr.photosorter.core.dispatchers.DispatchersProvider
import fr.photosorter.core.result.AppError
import fr.photosorter.core.result.AppResult
import fr.photosorter.data.db.dao.ImageDao
import fr.photosorter.data.db.dao.ImageLabelDao
import fr.photosorter.data.db.dao.ImageQueryDao
import fr.photosorter.domain.model.FilterState
import fr.photosorter.domain.model.ImageItem
import fr.photosorter.domain.model.SortCriteria
import fr.photosorter.domain.model.SortField
import fr.photosorter.domain.repository.ImageRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implémentation [ImageRepository] :
 * - Lecture/tri/filtre poussés en SQL via [ImageQueryDao].
 * - Actions fichiers via [FileActions] (scoped storage + fallback).
 * - Partage via [Intent.ACTION_SEND] et FileProvider.
 */
@Singleton
class ImageRepositoryImpl @Inject constructor(
    private val context: Context,
    private val imageDao: ImageDao,
    private val imageLabelDao: ImageLabelDao,
    private val imageQueryDao: ImageQueryDao,
    private val fileActions: FileActions,
    private val dispatchers: DispatchersProvider
) : ImageRepository {

    override fun observeImages(sort: SortCriteria, filter: FilterState): Flow<List<ImageItem>> =
        imageQueryDao.observeFiltered(
            sort = sort.field.toSortCode(),
            asc = if (sort.ascending) 1 else 0,
            includeAdult = if (filter.includeAdult) 1 else 0,
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
            var failure: AppError? = null
            for (item in items) {
                val err = fileActions.delete(item.uri)
                if (err != null) {
                    if (failure == null) failure = err
                } else {
                    imageLabelDao.clearForImage(item.id)
                    imageDao.deleteById(item.id)
                }
            }
            if (failure == null) AppResult.Success(Unit) else AppResult.Failure(failure)
        }

    override suspend fun move(items: List<ImageItem>, destUri: String): AppResult<Unit> =
        withContext(dispatchers.io) {
            val destDir = ensureDestDir(destUri)
                ?: return@withContext AppResult.Failure(AppError.NotFound("Dossier destination invalide"))
            runAction(items) { fileActions.move(it.uri, destDir) }
        }

    override suspend fun copy(items: List<ImageItem>, destUri: String): AppResult<Unit> =
        withContext(dispatchers.io) {
            val destDir = ensureDestDir(destUri)
                ?: return@withContext AppResult.Failure(AppError.NotFound("Dossier destination invalide"))
            runAction(items) { fileActions.copy(it.uri, destDir) }
        }

    override suspend fun share(items: List<ImageItem>): AppResult<Unit> =
        withContext(dispatchers.main) {
            if (items.isEmpty()) return@withContext AppResult.Success(Unit)
            val uris = ArrayList<Uri>(items.size)
            for (item in items) {
                val uri = fileActions.shareUri(item.uri)
                    ?: return@withContext AppResult.Failure(AppError.NotFound("Partage impossible : ${item.name}"))
                uris.add(uri)
            }
            val intent = if (uris.size == 1) {
                Intent(Intent.ACTION_SEND).apply {
                    type = "image/*"
                    putExtra(Intent.EXTRA_STREAM, uris.first())
                }
            } else {
                Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                    type = "image/*"
                    putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                }
            }.apply {
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(intent, null).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
            AppResult.Success(Unit)
        }

    // --- helpers ---

    private fun ensureDestDir(destUri: String): File? {
        val dir = runCatching { Uri.parse(destUri).path?.let(::File) }.getOrNull() ?: return null
        if (!dir.isDirectory && !dir.mkdirs() && !dir.isDirectory) return null
        return dir
    }

    private fun runAction(items: List<ImageItem>, action: (ImageItem) -> AppError?): AppResult<Unit> {
        var firstError: AppError? = null
        for (item in items) {
            val err = action(item)
            if (err != null && firstError == null) firstError = err
        }
        return if (firstError == null) AppResult.Success(Unit) else AppResult.Failure(firstError)
    }

    private fun matchesFilter(item: ImageItem, filter: FilterState): Boolean {
        if (!filter.includeAdult && item.isAdult) return false
        if (filter.formats.isNotEmpty() && item.format !in filter.formats) return false
        if (filter.dateFrom != null && item.dateModified < filter.dateFrom) return false
        if (filter.dateTo != null && item.dateModified > filter.dateTo) return false
        if (filter.minSizeBytes != null && item.sizeBytes < filter.minSizeBytes) return false
        if (filter.labels.isNotEmpty() && item.labels.none { it in filter.labels }) return false
        return true
    }
}

private fun SortField.toSortCode(): Int = when (this) {
    SortField.NAME -> 0
    SortField.DATE -> 1
    SortField.SIZE -> 2
    SortField.FORMAT -> 3
    SortField.LABEL -> 4
}
