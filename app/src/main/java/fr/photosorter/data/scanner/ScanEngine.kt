package fr.photosorter.data.scanner

import android.content.Context
import android.net.Uri
import android.os.SystemClock
import fr.photosorter.core.dispatchers.DispatchersProvider
import fr.photosorter.data.db.dao.ImageDao
import fr.photosorter.data.db.entity.ImageEntity
import fr.photosorter.data.moderation.AdultClassifier
import fr.photosorter.data.moderation.PreFilter
import fr.photosorter.data.moderation.PreFilterDecision
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
 * 1) Enumère les racines de stockage (interne + carte SD).
 * 2) Parcourt récursivement via [StorageWalker] (exclusions système).
 * 3) Lit les métadonnées (taille, date, format, dimensions, MIME).
 * 4) Modération adulte : pré-filtre screenshots/documents puis classifieur TFLite.
 * 5) Écrit dans Room par lots (batch).
 * 6) Émet [ScanProgress] en temps réel sur un Flow.
 *
 * 100% local : aucun envoi de données à l'extérieur.
 */
@Singleton
class ScanEngine @Inject constructor(
    private val context: Context,
    private val dispatchers: DispatchersProvider,
    private val imageDao: ImageDao,
    private val mimeDetector: MimeTypeDetector,
    private val storageRootProvider: StorageRootProvider,
    private val metadataReaderFactory: (Context) -> MediaMetadataReader,
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

    /** Lance un scan complet. Renvoie l'état final. Idempotent : ignoré si déjà en cours. */
    suspend fun scan(): ScanProgress = withContext(dispatchers.io) {
        if (running) return@withContext _progress.value
        running = true
        cancelled.set(false)
        val startedAt = SystemClock.elapsedRealtime()
        val reader = metadataReaderFactory(context)
        val buffer = ArrayList<ImageEntity>(BATCH_SIZE)
        var written = 0

        // Initialise le classifieur (dégrade proprement si modèle absent).
        adultClassifier.ensureInitialized()

        try {
            val roots = storageRootProvider.roots()
            // Passe 1 : collecter les fichiers (total connu pour la progression).
            val files = ArrayList<ScannedFile>(1024)
            _progress.value = ScanProgress.Running(done = 0, total = 0, current = "Analyse du stockage…")
            walker.walk(roots, cancelled) { files.add(it) }
            val total = files.size

            // Passe 2 : métadonnées + modération + écriture par lots.
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
                ScanProgress.Error("Scan annulé")
            } else {
                ScanProgress.Completed(count = written, durationMs = duration)
            }
            _progress.value = result
            result
        } catch (t: Throwable) {
            val result = ScanProgress.Error(t.message ?: "Erreur de scan")
            _progress.value = result
            result
        } finally {
            running = false
        }
    }

    /**
     * Détermine si une image est adulte.
     * 1) Pré-filtre : screenshots/documents → non adulte (révisable par l'utilisateur).
     * 2) Sinon classifieur TFLite ; si indisponible, non adulte.
     */
    private suspend fun detectAdult(uri: Uri, name: String): Boolean {
        return when (preFilter.decide(uri.path, name)) {
            PreFilterDecision.SKIP -> false
            PreFilterDecision.ANALYZE -> when (val r = adultClassifier.classify(uri)) {
                is AdultClassifier.Result.Scored -> r.isAdult
                AdultClassifier.Result.Unavailable -> false
            }
        }
    }

    private suspend fun flush(buffer: MutableList<ImageEntity>): Int {
        if (buffer.isEmpty()) return 0
        val batch = ArrayList(buffer)
        buffer.clear()
        val ids = imageDao.insertAll(batch)
        return ids.count { it > 0 }
    }

    companion object {
        const val BATCH_SIZE = 100
    }
}
