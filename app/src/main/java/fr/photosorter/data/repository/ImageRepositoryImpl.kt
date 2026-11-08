package fr.photosorter.data.repository

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import fr.photosorter.core.dispatchers.DispatchersProvider
import fr.photosorter.core.result.AppError
import fr.photosorter.core.result.AppResult
import fr.photosorter.core.result.map
import fr.photosorter.data.db.dao.ImageDao
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
 * - Actions fichiers (delete/move/copy) via [File] sur le chemin réel de l'URI.
 * - Partage via [Intent.ACTION_SEND] (FileProvider pour les fichiers privatisés).
 */
@Singleton
class ImageRepositoryImpl @Inject constructor(
    private val context: Context,
    private val imageDao: ImageDao,
    private val imageQueryDao: ImageQueryDao,
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
                val file = item.uri.toFileOrNull()
                if (file == null || !file.exists()) {
                    failure = AppError.NotFound("Fichier introuvable : ${item.name}")
                    continue
                }
                val deleted = runCatching { file.delete() }.getOrDefault(false)
                if (!deleted) {
                    failure = AppError.Io("Suppression impossible : ${item.name}")
                } else {
                    imageDao.clear?  
                }
            }
            if (failure == null) AppResult.Success(Unit) else AppResult.Failure(failure)
        }

    override suspend fun move(items: List<ImageItem>, destUri: String): AppResult<Unit> =
        withContext(dispatchers.io) {
            val destDir = destUri.toFileOrNull()
                ?: return@withContext AppResult.Failure(AppError.NotFound("Dossier destination invalide"))
            if (!destDir.isDirectory && !destDir.mkdirs()) {
                return@withContext AppResult.Failure(AppError.Io("Impossible de créer le dossier destination"))
            }
            runAction(items) { item ->
                val src = item.uri.toFileOrNull() ?: return@runAction AppError.NotFound(item.name)
                val target = File(destDir, src.name)
                if (!src.renameTo(target)) {
                    // fallback copie + suppression
                    if (!copyFile(src, target)) return@runAction AppError.Io("Déplacement échoué : ${item.name}")
                    if (!src.delete()) return@runAction AppError.Io("Suppression source échouée : ${item.name}")
                }
                null
            }
        }

    override suspend fun copy(items: List<ImageItem>, destUri: String): AppResult<Unit> =
        withContext(dispatchers.io) {
            val destDir = destUri.toFileOrNull()
                ?: return@withContext AppResult.Failure(AppError.NotFound("Dossier destination invalide"))
            if (!destDir.isDirectory && !destDir.mkdirs()) {
                return@withContext AppResult.Failure(AppError.Io("Impossible de créer le dossier destination"))
            }
            runAction(items) { item ->
                val src = item.uri.toFileOrNull() ?: return@runAction AppError.NotFound(item.name)
                val target = File(destDir, src.name)
                if (!copyFile(src, target)) AppError.Io("Copie échouée : ${item.name}") else null
            }
        }

    override suspend fun share(items: List<ImageItem>): AppResult<Unit> =
        withContext(dispatchers.main) {
            if (items.isEmpty()) return@withContext AppResult.Success(Unit)
            val uris = ArrayList<Uri>(items.size)
            for (item in items) {
                val file = item.uri.toFileOrNull()
                if (file == null || !file.exists()) {
                    return@withContext AppResult.Failure(AppError.NotFound(item.name))
                }
                val uri = runCatching {
                    FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        file
                    )
                }.getOrNull() ?: return@withContext AppResult.Failure(AppError.Io("FileProvider non configuré"))
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

    private fun runAction(
        items: List<ImageItem>,
        action: (ImageItem) -> AppError?
    ): AppResult<Unit> {
        var firstError: AppError? = null
        for (item in items) {
            val err = action(item)
            if (err != null && firstError == null) firstError = err
        }
        return if (firstError == null) AppResult.Success(Unit) else AppResult.Failure(firstError)
    }

    private fun copyFile(src: File, target: File): Boolean = runCatching {
        src.inputStream().use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        }
        true
    }.getOrDefault(false)

    private fun String.toFileOrNull(): File? = runCatching {
        val u = Uri.parse(this)
        if (u.scheme == "file") u.path?.let(::File) else u.path?.let(::File)
    }.getOrNull()

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
