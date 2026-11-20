package fr.photosorter.data.scanner

import android.content.Context
import android.net.Uri
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
 * Moteur de scan de bout en bout :
 * 1) Enumere les racines de stockage (interne + carte SD).
 * 2) Parcourt recursivement via [StorageWalker] (exclusions systeme).
 * 3) Lit les metadonnees (taille, date, format, dimensions, MIME).
 * 4) Moderation adulte : pre-filtre screenshots/documents puis classifieur TFLite.
 * 5) Ecrit dans Room par lots (batch) de facon atomique : images + libelle systeme
 *    'adulte' (dans image_labels) sont inseres dans la MEME transaction.
 * 6) Emet [ScanProgress] en temps reel sur un Flow.
 *
 * 100% local : aucun envoi de donnees a l'exterieur.
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

    /** Lance un scan complet. Renvoie l'etat final. Idempotent : ignore si deja en cours. */
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
            _progress.value = ScanProgress.Running(done = 0, total = 0, current = "Analyse du stockage...")
            walker.walk(roots, cancelled) { files.add(it) }
            val total = files.size

            for ((index, file) in files.withIndex()) {
                if (cancelled.get()) break
                val meta = reader.read(file.uri) ?: continue
                val format = mimeDetector.detectFormat(meta.name, meta.mimeType) ?: continue

                val isAdult = detectAdult(file.uri, meta.name)

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
                if (buffer.size >= BATCH_SIZE) written += flush(buffer)
                _progress.value = ScanProgress.Running(
                    done = index + 1,
                    total = total,
                    current = meta.name
                )
            }
            if (buffer.isNotEmpty()) written += flush(buffer)

            val duration = SystemClock.elapsedRealtime() - startedAt
            val result = if (cancelled.get()) {
                ScanProgress.Error("Scan annule")
            } else {
                ScanProgress.Completed(
                    count = written,
                    durationMs = duration,
                    unclassifiedCount = adultClassifier.unavailableCount
                )
            }
            _progress.value = result
            result
        } catch (t: Throwable) {
            val result = ScanProgress.Error(t.message ?: "Erreur de scan")
            _progress.value = result
            result
        } finally {
            running = false
            buffer.clear()
        }
    }

    /**
     * Insere un lot d'images ET les associations au libelle systeme 'adulte' dans
     * une seule transaction Room : impossible d'avoir isAdult=true sans libelle.
     * Renvoie le nombre d'images reellement ecrites (ids > 0).
     */
    private suspend fun flush(buffer: MutableList<ImageEntity>): Int {
        if (buffer.isEmpty()) return 0
        val batch = ArrayList(buffer)
        buffer.clear()

        return db.withTransaction {
            val ids = imageDao.insertAll(batch)
            val adultLinks = ArrayList<ImageLabelEntity>()
            ids.forEachIndexed { index, id ->
                if (id > 0L && batch[index].isAdult) {
                    adultLinks.add(ImageLabelEntity(imageId = id, labelName = ADULT_LABEL))
                }
            }
            if (adultLinks.isNotEmpty()) {
                // FK labels.name : garantir l'existence du libelle systeme avant les liens.
                if (labelDao.getByName(ADULT_LABEL) == null) {
                    labelDao.insert(LabelEntity(name = ADULT_LABEL, isSystem = true, createdAt = System.currentTimeMillis()))
                }
                imageLabelDao.insertAll(adultLinks)
            }
            ids.count { it > 0L }
        }
    }

    /** Pre-filtre puis classifieur. Renvoie false si indisponible (aucune classification). */
    private suspend fun detectAdult(uri: Uri, name: String): Boolean {
        return when (preFilter.evaluate(name)) {
            PreFilterDecision.Adult -> true
            PreFilterDecision.Neutral -> false
            PreFilterDecision.NeedsModel -> {
                val score = adultClassifier.classify(uri)
                score?.isAdult ?: false
            }
        }
    }

    companion object {
        private const val BATCH_SIZE = 200
    }
}
