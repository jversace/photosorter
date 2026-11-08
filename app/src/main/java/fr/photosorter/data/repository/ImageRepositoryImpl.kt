package fr.photosorter.data.repository

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import fr.photosorter.core.dispatchers.DispatchersProvider
import fr.photosorter.core.ext.runCatchingApp
import fr.photosorter.core.result.AppError
import fr.photosorter.core.result.AppResult
import fr.photosorter.data.db.dao.ImageQueryDao
import fr.photosorter.domain.model.FilterState
import fr.photosorter.domain.model.ImageFormat
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

@Singleton
class ImageRepositoryImpl @Inject constructor(
    private val appContext: Context,
    private val queryDao: ImageQueryDao,
    private val dispatchers: DispatchersProvider
) : ImageRepository {

    override fun observeImages(sort: SortCriteria, filter: FilterState): Flow<List<ImageItem>> {
        val args = QueryArgs.from(sort, filter)
        return queryDao.observeFiltered(
            sort = args.sortCode,
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
    }

    override suspend fun getImages(filter: FilterState): List<ImageItem> =
        withContext(dispatchers.io) {
            // Observe + first via le même chemin pour rester cohérent avec observeImages.
            observeImages(SortCriteria(SortField.NAME, true), filter)
                .let { flow -> kotlinx.coroutines.flow.first(flow, this) }
        }

    override suspend fun delete(items: List<ImageItem>): AppResult<Unit> =
        withContext(dispatchers.io) {
            runCatchingApp {
                val resolver = appContext.contentResolver
                var failed = false
                items.forEach { item -> if (!deleteOne(resolver, item)) failed = true }
                if (failed) throw SecurityException("Suppression partielle")
            }
        }

    override suspend fun move(items: List<ImageItem>, destUri: String): AppResult<Unit> =
        withContext(dispatchers.io) {
            runCatchingApp {
                val destDir = File(destUri)
                items.forEach { item ->
                    val src = File(Uri.parse(item.uri).path ?: error("URI invalide"))
                    val target = File(destDir, src.name)
                    if (!src.renameTo(target)) {
                        src.copyTo(target, overwrite = true)
                        src.delete()
                    }
                }
            }
        }

    override suspend fun copy(items: List<ImageItem>, destUri: String): AppResult<Unit> =
        withContext(dispatchers.io) {
            runCatchingApp {
                val destDir = File(destUri)
                items.forEach { item ->
                    val src = File(Uri.parse(item.uri).path ?: error("URI invalide"))
                    src.copyTo(File(destDir, src.name), overwrite = false)
                }
            }
        }

    override suspend fun share(items: List<ImageItem>): AppResult<Unit> =
        withContext(dispatchers.main) {
            runCatchingApp {
                if (items.isEmpty()) throw IllegalArgumentException("Aucune image à partager")
                val uris = ArrayList<Uri>(items.size)
                items.forEach { uris.add(Uri.parse(it.uri)) }
                val intent = if (uris.size == 1) {
                    Intent(Intent.ACTION_SEND).apply {
                        type = mimeOf(items.first().format)
                        putExtra(Intent.EXTRA_STREAM, uris.first())
                    }
                } else {
                    Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                        type = "image/*"
                        putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                    }
                }.apply { addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }
                val chooser = Intent.createChooser(intent, "Partager").apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                appContext.startActivity(chooser)
            }
        }

    private fun deleteOne(resolver: ContentResolver, item: ImageItem): Boolean {
        val uri = Uri.parse(item.uri)
        return try {
            if (uri.scheme == ContentResolver.SCHEME_CONTENT) {
                resolver.delete(uri, null, null) > 0
            } else {
                uri.path?.let { File(it).delete() } ?: false
            }
        } catch (_: SecurityException) {
            false
        }
    }

    private fun mimeOf(format: ImageFormat): String = when (format) {
        ImageFormat.JPEG -> "image/jpeg"
        ImageFormat.PNG -> "image/png"
        ImageFormat.HEIC -> "image/heic"
        ImageFormat.WEBP -> "image/webp"
        ImageFormat.GIF -> "image/gif"
        ImageFormat.BMP -> "image/bmp"
        ImageFormat.RAW -> "image/x-raw"
    }

    /** Mapping figé SortCriteria → code numérique attendu par ImageQueryDao. */
    private data class QueryArgs(val sortCode: Int) {
        companion object {
            fun from(sort: SortCriteria, @Suppress unusedFilter: FilterState): QueryArgs =
                QueryArgs(
                    when (sort.field) {
                        SortField.NAME -> 0
                        SortField.DATE -> 1
                        SortField.SIZE -> 2
                        SortField.FORMAT -> 3
                        SortField.LABEL -> 4
                    }
                )
        }
    }
}
