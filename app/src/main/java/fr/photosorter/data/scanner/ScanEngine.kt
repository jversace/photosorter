package fr.photosorter.data.scanner

import android.content.Context
import android.os.SystemClock
import androidx.room.withTransaction
import fr.photosorter.core.dispatchers.DispatchersProvider
import fr.photosorter.data.db.AppDatabase
import fr.photosorter.data.db.dao.ImageDao
import fr.photosorter.data.db.dao.ImageLabelDao
import fr.photosorter.data.db.dao.LabelDao
import fr.photosorter.data.db.entity.ImageEntity
import fr.photosorter.data.db.entity.ImageLabelEntity
import fr.photosorter.data.db.entity.LabelEntity
import fr.photosorter.data.moderation.AdultClassifier
import fr.photosorter.data.moderation.AdultClassifier.Result
import fr.photosorter.data.moderation.PreFilter
import fr.photosorter.data.moderation.PreFilterDecision
import fr.photosorter.domain.model.ADULT_LABEL
import fr.photosorter.domain.model.ScanProgress
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Moteur de scan : enumere les racines de stockage, parcourt recursivement
 * (exclusions systeme), lit les metadonnees, applique la moderation adulte
 * (pre-filtre + classifieur TFLite) puis ecrit dans Room par lots atomiques :
 * images ET libelle systeme 'adulte' (image_labels) dans la meme transaction.
 * 100% local.
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

    fun cancel() { cancelled.set(true) }

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
            val files = ArrayList<ScannedFile>(1024)
            _progress.value = ScanProgress.Running(0, 0, MSG_ANALYZING)
            walker.walk(roots, cancelled) { files.add(it) }
            val total = files.size
            for ((index, file) in files.withIndex()) {
                if (cancelled.get()) break
                val meta = reader.read(file.uri) ?: continue
                val format = mimeDetector.detectFormat(meta.name, meta.mimeType) ?: continue
                val isAdult = detectAdult(file.uri.toString(), meta.name)
                buffer.add(ImageEntity(uri = file.uri.toString(), name = meta.name, format = format.name, sizeBytes = meta.sizeBytes, dateTaken = null, dateModified = meta.dateModified, width = meta.width, height = meta.height, isAdult = isAdult, thumbnailHint = null))
                if (buffer.size >= BATCH_SIZE) written += flush(buffer)
                _progress.value = ScanProgress.Running(index + 1, total, meta.name)
            }
            if (buffer.isNotEmpty()) written += flush(buffer)
            val duration = SystemClock.elapsedRealtime() - startedAt
            val result = if (cancelled.get()) ScanProgress.Error(MSG_CANCELLED) else ScanProgress.Completed(written, duration, adultClassifier.unavailableCount)
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

    /** Insere un lot d'images ET les liens au libelle 'adulte' atomiquement. */
    private suspend fun flush(buffer: MutableList<ImageEntity>): Int {
        if (buffer.isEmpty()) return 0
        val batch = ArrayList(buffer)
        buffer.clear()
        var inserted = 0
        db.withTransaction {
            val ids = imageDao.insertAll(batch)
            val adultLinks = ArrayList<ImageLabelEntity>()
            for (i in batch.indices) {
                val id = ids.getOrNull(i) ?: continue
                if (id > 0L && batch[i].isAdult) adultLinks.add(ImageLabelEntity(imageId = id, labelName = ADULT_LABEL))
            }
            if (adultLinks.isNotEmpty()) {
                if (labelDao.getByName(ADULT_LABEL) == null) labelDao.insert(LabelEntity(name = ADULT_LABEL, isSystem = true))
                imageLabelDao.insertAll(adultLinks)
            }
            inserted = ids.count { it != null && it > 0L }
        }
        return inserted
    }

    /** Moderation adulte : pre-filtre puis classifieur. Unavailable => normale (conservateur). */
    private suspend fun detectAdult(uri: String, name: String): Boolean {
        return when (preFilter.decide(uri, name)) {
            PreFilterDecision.SKIP_NOT_PHOTO -> false
            PreFilterDecision.ANALYZE -> {
                if (!adultClassifier.isReady()) false else when (val r = adultClassifier.classify(android.net.Uri.parse(uri))) {
                    is Result.Scored -> r.isAdult
                    is Result.Unavailable -> false
                }
            }
        }
    }

    companion object {
        private const val BATCH_SIZE = 50
        private const val MSG_ANALYZING = "Analyse du stockage..."
        private const val MSG_CANCELLED = "Scan annule"
        private const val MSG_ERROR = "Erreur de scan"
    }
}
