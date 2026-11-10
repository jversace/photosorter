package fr.photosorter.data.repository

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import fr.photosorter.core.result.AppError
import java.io.File

/**
 * Opérations fichier bas niveau sur une image, en tenant compte du scoped storage :
 * - DELETE : sur API >= 29, tentative ContentResolver.delete (respecte les autorisations
 *   MediaStore), fallback File.delete() sur le chemin réel.
 * - MOVE/COPY : via [File] (renameTo puis copie+suppression). Scoped storage limite les
 *   écritures hors de la zone de l'app ; en cas d'échec, on renvoie un [AppError.Io] clair.
 * - SHARE : URI FileProvider valide.
 *
 * La résolution d'URI se fait via [resolveFile] : pour une URI `content://` (MediaStore
 * notamment) la colonne [OpenableColumns.DATA] (`_data`) donne le chemin réel ; on ne se
 * fie plus à [Uri.getPath] qui renvoie un identifiant interne (bug 79d94669).
 */
class FileActions(private val context: Context) {

    private val resolver: ContentResolver get() = context.contentResolver

    /** Supprime le fichier et renvoie null si succès, sinon l'erreur. */
    fun delete(imageUri: String): AppError? {
        val uri = Uri.parse(imageUri)
        // 1) Tentative via MediaStore (scoped storage) : gère l'autorisation utilisateur.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && uri.scheme == ContentResolver.SCHEME_CONTENT) {
            val deleted = runCatching { resolver.delete(uri, null, null) }.getOrDefault(0)
            if (deleted > 0) return null
            // si 0, on tente le fichier réel (fallback)
        }
        // 2) Fallback fichier réel.
        val file = resolveFile(uri, allowCacheFallback = false)
            ?: return AppError.NotFound("URI non exploitable : $imageUri")
        if (!file.exists()) return AppError.NotFound("Fichier introuvable : ${file.name}")
        return if (runCatching { file.delete() }.getOrDefault(false)) null
        else AppError.Io("Suppression impossible : ${file.name}")
    }

    fun move(imageUri: String, destDir: File): AppError? {
        val src = resolveFile(Uri.parse(imageUri), allowCacheFallback = false)
            ?: return AppError.NotFound("URI non exploitable : $imageUri")
        if (!src.exists()) return AppError.NotFound("Fichier introuvable : ${src.name}")
        ensureDir(destDir)?.let { return it }
        val target = File(destDir, src.name)
        if (src.renameTo(target)) return null
        // fallback copie + suppression
        if (!copyFile(src, target)) return AppError.Io("Déplacement échoué : ${src.name}")
        if (!runCatching { src.delete() }.getOrDefault(false)) {
            return AppError.Io("Déplacement partiel (copie ok, source non supprimée) : ${src.name}")
        }
        return null
    }

    fun copy(imageUri: String, destDir: File): AppError? {
        val src = resolveFile(Uri.parse(imageUri), allowCacheFallback = true)
            ?: return AppError.NotFound("URI non exploitable : $imageUri")
        if (!src.exists()) return AppError.NotFound("Fichier introuvable : ${src.name}")
        ensureDir(destDir)?.let { return it }
        val target = File(destDir, src.name)
        return if (copyFile(src, target)) null else AppError.Io("Copie échouée : ${src.name}")
    }

    /** Renvoie une URI FileProvider partageable, ou null si impossible. */
    fun shareUri(imageUri: String): Uri? {
        val file = resolveFile(Uri.parse(imageUri), allowCacheFallback = true) ?: return null
        if (!file.exists()) return null
        return runCatching {
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        }.getOrNull()
    }

    // --- Résolution d'URI ---------------------------------------------------

    /**
     * Résout une URI en [File] réel.
     * - `file://` : chemin direct.
     * - `content://` : colonne `_data` ([OpenableColumns.DATA]). Si le fichier existe, on le
     *   renvoie. Sinon, si [allowCacheFallback] est vrai (copy/share), on matérialise une copie
     *   dans le cache de l'app à partir du flux, nommée d'après [OpenableColumns.DISPLAY_NAME].
     * - sinon null.
     */
    private fun resolveFile(uri: Uri, allowCacheFallback: Boolean): File? {
        return when (uri.scheme) {
            ContentResolver.SCHEME_FILE -> {
                uri.path?.let { File(it) }
            }
            ContentResolver.SCHEME_CONTENT -> {
                val dataPath = queryString(uri, OpenableColumns.DATA)
                if (dataPath != null) {
                    val f = File(dataPath)
                    if (f.exists()) return f
                }
                if (allowCacheFallback) materializeToCache(uri) else null
            }
            else -> null
        }
    }

    /** Copie le contenu de l'URI dans le cache de l'app et renvoie le [File]. */
    private fun materializeToCache(uri: Uri): File? {
        val name = queryString(uri, OpenableColumns.DISPLAY_NAME)
            ?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
            ?: "shared_${System.currentTimeMillis()}"
        val dest = File(context.cacheDir, name)
        return runCatching {
            resolver.openInputStream(uri)?.use { input ->
                dest.outputStream().use { output -> input.copyTo(output) }
            } ?: return null
            dest
        }.getOrNull()
    }

    private fun queryString(uri: Uri, column: String): String? {
        return runCatching {
            resolver.query(uri, arrayOf(column), null, null, null)?.use { cursor ->
                val idx = cursor.getColumnIndex(column)
                if (idx >= 0 && cursor.moveToFirst()) {
                    if (cursor.isNull(idx)) null else cursor.getString(idx)
                } else null
            }
        }.getOrNull()
    }

    // --- Utilitaires --------------------------------------------------------

    private fun ensureDir(dir: File): AppError? {
        if (dir.exists()) {
            return if (dir.isDirectory) null else AppError.Io("Chemin cible invalide : ${dir.name}")
        }
        return if (dir.mkdirs()) null else AppError.Io("Création du dossier cible impossible : ${dir.name}")
    }

    private fun copyFile(src: File, target: File): Boolean {
        return runCatching {
            src.inputStream().use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            }
            true
        }.getOrDefault(false)
    }
}
