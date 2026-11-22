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
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileNotFoundException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implémentation [ImageRepository] :
 * - Lecture/tri/filtre poussés en SQL via [ImageQueryDao].
 * - Actions fichiers via [FileActions] (SAF + fallback).
 * - Suppression via [MediaStoreDeleter] (scoped storage : confirmation système possible).
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

    override fun observeImages(sort: SortCriteria, filter: FilterState): Flow<List<ImageItem>> {
        android.util.Log.i(
            "GalleryFilter",
            "includeAdult=${filter.includeAdult} adultOnly=${filter.adultOnly} adultLabel='$ADULT_LABEL'"
        )
        return imageQueryDao.observeFiltered(
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
        )
            .map { rows -> rows.map { it.toDomain() } }
            .onEach { imgs ->
                android.util.Log.i("GalleryFilter", "→ résultat : ${imgs.size} image(s)")
            }
    }

    override suspend fun getImages(filter: FilterState): List<ImageItem> =
        withContext(dispatchers.io) {
            imageDao.getAllWithLabels()
                .map { it.toDomain() }
                .filter { matchesFilter(it, filter) }
        }

    override suspend fun delete(items: List<ImageItem>): AppResult<Unit> =
        withContext(dispatchers.io) {
            android.util.Log.i("ImageRepository", "delete: items=${items.size} uris=${items.map { it.uri }}")
            if (items.isEmpty()) return@withContext AppResult.Success(Unit)

            when (val req = mediaStoreDeleter.requestDeleteBatch(items.map { it.uri })) {
                is DeleteRequest.Completed -> {
                    android.util.Log.i("ImageRepository", "delete: Completed err=${req.error?.message}")
                    if (req.error != null) {
                        // Succès partiel possible : nettoyage DB basé sur l'existence réelle.
                        val gone = imageDao.getAllUris().filter { uriStr ->
                            val path = Uri.parse(uriStr).path
                            path == null || !File(path).exists()
                        }
                        if (gone.isNotEmpty()) imageDao.deleteByUris(gone)
                        AppResult.Failure(req.error)
                    } else {
                        val gone = imageDao.getAllUris().filter { uriStr ->
                            val path = Uri.parse(uriStr).path
                            path == null || !File(path).exists()
                        }
                        if (gone.isNotEmpty()) imageDao.deleteByUris(gone)
                        AppResult.Success(Unit)
                    }
                }
                is DeleteRequest.NeedsConfirmation -> {
                    android.util.Log.i("ImageRepository", "delete: NeedsConfirmation uris=${req.uris}")
                    AppResult.PendingDelete(req.intentSender, req.uris)
                }
            }
        }

    override suspend fun deleteConfirmed(uris: List<String>): AppResult<Unit> =
        withContext(dispatchers.io) {
            val err = mediaStoreDeleter.confirmDelete(uris)

            // Nettoyage DB basé sur l'existence réelle des fichiers, pas sur le retour
            // de resolver.delete (faux négatif sur API 30+ après confirmation système).
            val gone = imageDao.getAllUris().filter { uriStr ->
                val parsed = Uri.parse(uriStr)
                val path = parsed.path
                path == null || !File(path).exists()
            }
            if (gone.isNotEmpty()) imageDao.deleteByUris(gone)

            android.util.Log.i(
                "ImageRepository",
                "deleteConfirmed: demandés=${uris.size} supprimésEnDB=${gone.size} err=${err?.message}"
            )

            if (err != null && gone.isEmpty()) AppResult.Failure(err)
            else AppResult.Success(Unit)
        }

    override suspend fun move(items: List<ImageItem>, destUri: String): AppResult<Unit> =
        withContext(dispatchers.io) {
            if (items.isEmpty()) return@withContext AppResult.Success(Unit)
            android.util.Log.i("ImageRepository", "move: items=${items.size} dest=$destUri")

            // Étape 1 : copier tout ce qui peut l'être.
            val copied = mutableListOf<ImageItem>()
            var copyError: AppError? = null
            for (item in items) {
                val err = fileActions.moveToTree(item.uri, destUri)
                if (err != null) copyError = err
                else copied.add(item)
            }
            if (copied.isEmpty()) {
                return@withContext AppResult.Failure(copyError ?: AppError.Io("Aucune copie réussie"))
            }

            // Étape 2 : demander la suppression des sources (dialogue système groupé).
            when (val req = mediaStoreDeleter.requestDeleteBatch(copied.map { it.uri })) {
                is DeleteRequest.Completed -> {
                    // Suppression directe réussie : nettoyage DB des sources disparues.
                    val gone = imageDao.getAllUris().filter { uriStr ->
                        val path = Uri.parse(uriStr).path
                        path == null || !File(path).exists()
                    }
                    if (gone.isNotEmpty()) imageDao.deleteByUris(gone)
                    if (req.error != null) AppResult.Failure(req.error) else AppResult.Success(Unit)
                }
                is DeleteRequest.NeedsConfirmation ->
                    AppResult.PendingDelete(req.intentSender, req.uris, AppResult.PendingAction.MOVE_SOURCE)
            }
        }

    override suspend fun moveConfirmed(uris: List<String>): AppResult<Unit> =
        deleteConfirmed(uris)

    override suspend fun copy(items: List<ImageItem>, destUri: String): AppResult<Unit> =
        withContext(dispatchers.io) {
            var lastError: AppError? = null
            for (item in items) {
                // FileActions.copyToTree renvoie null en cas de succès, sinon l'erreur
                // (même contrat que moveToTree).
                val err = fileActions.copyToTree(item.uri, destUri)
                if (err != null) lastError = err
            }
            if (lastError != null) AppResult.Failure(lastError!!) else AppResult.Success(Unit)
        }

    override suspend fun share(items: List<ImageItem>): AppResult<Unit> =
        withContext(dispatchers.io) {
            if (items.isEmpty()) return@withContext AppResult.Success(Unit)
            val uris = items.map { Uri.parse(it.uri) }
            val intent = if (uris.size == 1) {
                Intent(Intent.ACTION_SEND).apply {
                    type = context.contentResolver.getType(uris.first()) ?: "image/*"
                    putExtra(Intent.EXTRA_STREAM, uris.first())
                }
            } else {
                Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                    type = "image/*"
                    putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
                }
            }.apply {
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            runCatching {
                context.startActivity(Intent.createChooser(intent, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                AppResult.Success(Unit)
            }.getOrElse { AppResult.Failure(AppError.Io("Partage impossible : ${it.message}")) }
        }

    /**
     * Indique si la source d'un déplacement existe encore après la tentative de suppression.
     * - `file://` (URI issues du scan) : test direct sur le système de fichiers.
     * - `content://` : tentative d'ouverture via le ContentResolver ; une entrée absente lève
     *   [FileNotFoundException], interprétée comme « source supprimée ».
     */
    private fun sourceStillExists(uri: String): Boolean {
        val parsed = Uri.parse(uri)
        return when (parsed.scheme) {
            "file" -> parsed.path?.let { File(it).exists() } ?: false
            "content" -> try {
                context.contentResolver.openInputStream(parsed)?.use { true } ?: false
            } catch (e: FileNotFoundException) {
                false
            } catch (e: SecurityException) {
                // On ne peut pas conclure : par prudence, on considère la source encore présente.
                true
            }
            else -> parsed.path?.let { File(it).exists() } ?: false
        }
    }

    private fun matchesFilter(item: ImageItem, filter: FilterState): Boolean {
        if (!filter.includeAdult && item.isAdult) return false
        if (filter.formats.isNotEmpty() && item.format !in filter.formats) return false
        val minSize = filter.minSizeBytes
        if (minSize != null && minSize > 0 && item.sizeBytes < minSize) return false
        return true
    }
}

/**
 * Code de tri attendu par [ImageQueryDao.observeFiltered] (0=NAME, 1=DATE, 2=SIZE, 3=FORMAT, 4=LABEL).
 */
private fun SortField.toSortCode(): Int = when (this) {
    SortField.NAME -> 0
    SortField.DATE -> 1
    SortField.SIZE -> 2
    SortField.FORMAT -> 3
    SortField.LABEL -> 4
}
