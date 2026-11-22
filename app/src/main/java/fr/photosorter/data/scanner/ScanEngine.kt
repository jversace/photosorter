package fr.photosorter.data.scanner

import android.content.Context
import android.os.SystemClock
import fr.photosorter.core.dispatchers.DispatchersProvider
import fr.photosorter.data.db.AppDatabase
import fr.photosorter.data.db.dao.ImageDao
import fr.photosorter.data.db.dao.ImageLabelDao
import fr.photosorter.data.db.dao.LabelDao
import fr.photosorter.data.db.entity.ImageEntity
import fr.photosorter.data.moderation.AdultClassifier
import fr.photosorter.data.moderation.PreFilter
import fr.photosorter.domain.model.ScanProgress
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import android.content.ContentUris
import android.provider.MediaStore
import android.os.Build

/**
 * Moteur de scan : enumere les racines de stockage, parcourt recursivement
 * (exclusions systeme), lit les metadonnees, applique la moderation adulte
 * (pre-filtre + classifieur TFLite) puis ecrit dans Room par lots atomiques :
 * images ET libelle systeme 'adulte' (image_labels) dans la meme transaction.
 * En fin de scan, une reconciliation idempotente des liens 'adulte' garantit
 * la coherence apres re-scan (REPLACE + FK CASCADE).
 * 100% local. La logique de lot et de detection est dans ScanEngineSupport.kt.
 */
@Singleton
class ScanEngine @Inject constructor(
    private val context: Context,
    private val dispatchers: DispatchersProvider,
    private val db: AppDatabase,
    private val imageDao: ImageDao,
    private val imageLabelDao: ImageLabelDao,
    private val labelDao: LabelDao,
    private val mimeDetector: MimeTypeDetector,
    private val storageRootProvider: StorageRootProvider,
    private val metadataReaderFactory: @JvmSuppressWildcards (Context) -> MediaMetadataReader,
    private val preFilter: PreFilter,
    private val adultClassifier: AdultClassifier
) {

    private val _progress = MutableStateFlow<ScanProgress>(ScanProgress.Idle)
    val progress: Flow<ScanProgress> = _progress.asStateFlow()

    private val cancelled = AtomicBoolean(false)

    @Volatile private var running = false

    private val walker = StorageWalker(mimeDetector)

    fun cancel() {
        cancelled.set(true)
    }

    suspend fun scan(): ScanProgress = withContext(dispatchers.io) {
        if (running) return@withContext _progress.value
        running = true
        cancelled.set(false)
        val startedAt = SystemClock.elapsedRealtime()
        val reader = metadataReaderFactory(context)
        val buffer = ArrayList<ImageEntity>(BATCH_SIZE)
        var written = 0

        adultClassifier.ensureInitialized()
        adultClassifier.resetUnavailableCount()

        try {
            val roots = storageRootProvider.roots()
            android.util.Log.i("ScanEngine", "roots=${roots.map { it.absolutePath }}")
            val files = ArrayList<ScannedFile>(1024)
            _progress.value = ScanProgress.Running(done = 0, total = 0, current = MSG_ANALYZING)
            walker.walk(roots, cancelled) { files.add(it) }

            // Compléter avec MediaStore : couvre la carte SD (inaccessible via File sur API 30+)
            // et tout fichier indexé absent du parcours File. Déduplication par chemin physique :
            // si un fichier est déjà trouvé par le walker (file://), on garde le walker (pas de régression).
            val seenPaths = files.mapNotNullTo(HashSet()) { it.uri.path }
            val before = files.size
            for (sf in queryMediaStoreImages(context)) {
                if (seenPaths.add(sf.normalizedPath)) {
                    files.add(sf)
                }
            }
            android.util.Log.i(
                "ScanEngine",
                "files total=${files.size} (walker=$before, MediaStore ajoutés=${files.size - before})"
            )

            val total = files.size

            for ((index, file) in files.withIndex()) {
                if (cancelled.get()) break
                val meta = reader.read(file.uri) ?: continue
                val format = mimeDetector.detectFormat(meta.name, meta.mimeType) ?: continue
                val isAdult = detectAdult(file.uri, meta.name, preFilter, adultClassifier)
                buffer.add(
                    ImageEntity(
                        uri = file.uri.toString(),
                        name = meta.name,
                        format = format.name,
                        sizeBytes = meta.sizeBytes,
                        dateTaken = null,
                        dateModified = meta.dateModified,
                        width = meta.width,
                        height = meta.height,
                        isAdult = isAdult,
                        thumbnailHint = null
                    )
                )
                if (buffer.size >= BATCH_SIZE) written += flushImages(buffer, db, imageDao, imageLabelDao, labelDao)
                _progress.value = ScanProgress.Running(done = index + 1, total = total, current = meta.name)
            }
            if (buffer.isNotEmpty()) written += flushImages(buffer, db, imageDao, imageLabelDao, labelDao)

            // Reconciliation idempotente des liens 'adulte' pour TOUTES les images
            // is_adult = 1 (y compris celles hors lot courant) : INSERT OR IGNORE ...
            // SELECT. Compense la perte de liens due au REPLACE + FK CASCADE au re-scan.
            reconcileAdultLabels(db, imageDao, imageLabelDao, labelDao)

            val duration = SystemClock.elapsedRealtime() - startedAt
            val result = if (cancelled.get()) {
                ScanProgress.Error(MSG_CANCELLED)
            } else {
                ScanProgress.Completed(count = written, durationMs = duration, unclassifiedCount = adultClassifier.unavailableCount)
            }
            _progress.value = result
            result
        } catch (t: Throwable) {
            val result = ScanProgress.Error(t.message ?: MSG_ERROR)
            _progress.value = result
            result
        } finally {
            running = false
            buffer.clear()
        }
    }

    /**
     * Récupère les images indexées par MediaStore (couvre le stockage interne ET la
     * carte SD externe). Indispensable sur API 30+ : la carte SD n'est pas accessible
     * via File API (canRead() renvoie false silencieusement), donc StorageWalker ne
     * peut pas la parcourir. MediaStore est la seule voie conforme au scoped storage.
     */
    private fun queryMediaStoreImages(context: Context): List<ScannedFile> {
        val result = mutableListOf<ScannedFile>()
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            @Suppress("DEPRECATION")
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }
        val projection = arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DATA)
        runCatching {
            context.contentResolver.query(collection, projection, null, null, null)?.use { c ->
                val idCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                val dataCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DATA)
                while (c.moveToNext()) {
                    val id = c.getLong(idCol)
                    val data = c.getString(dataCol) ?: continue
                    val contentUri = ContentUris.withAppendedId(collection, id)
                    result.add(ScannedFile(contentUri, data))
                }
            }
        }.onFailure {
            android.util.Log.w("ScanEngine", "MediaStoreQuery échouée: ${it.message}")
        }
        android.util.Log.i("ScanEngine", "MediaStoreQuery: ${result.size} image(s) indexée(s)")
        return result
    }
}
