package fr.photosorter.data.scanner

import android.net.Uri
import kotlinx.coroutines.yield
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/** Un fichier candidat au scan, avec son chemin normalisé. */
data class ScannedFile(val uri: Uri, val normalizedPath: String)

/**
 * Parcours récursif du stockage (interne + carte SD) à partir de racines données.
 * - Exclut les répertoires système via [SystemExclusions].
 * - Ne renvoie que les fichiers dont l'extension est une image connue (filtre rapide ;
 *   l'affinage MIME est fait en aval par MimeTypeDetector).
 * - Annulable (cancelled) et coopératif (yield) pour la progression.
 */
class StorageWalker(
    private val mimeDetector: MimeTypeDetector,
    private val maxDepth: Int = 40
) {

    suspend fun walk(
        roots: List<File>,
        cancelled: AtomicBoolean,
        onFile: suspend (ScannedFile) -> Unit
    ) {
        for (root in roots) {
            if (cancelled.get()) return
            if (!root.exists() || !root.canRead()) continue
            walkDir(root, root, depth = 0, cancelled, onFile)
        }
    }

    private suspend fun walkDir(
        root: File,
        dir: File,
        depth: Int,
        cancelled: AtomicBoolean,
        onFile: suspend (ScannedFile) -> Unit
    ) {
        if (cancelled.get() || depth > maxDepth) return
        val children = dir.listFiles() ?: return
        for (child in children) {
            if (cancelled.get()) return
            val relative = child.absolutePath.removePrefix(root.absolutePath)
            val normalized = SystemExclusions.normalize(relative)
            if (child.isDirectory) {
                if (SystemExclusions.isExcluded(normalized)) continue
                walkDir(root, child, depth + 1, cancelled, onFile)
            } else if (isCandidateImage(child.name)) {
                onFile(ScannedFile(Uri.fromFile(child), normalized))
                yield()
            }
        }
    }

    private fun isCandidateImage(name: String): Boolean =
        mimeDetector.detectFormat(name, null) != null
}
