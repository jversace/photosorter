package fr.photosorter.data.repository

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.OpenableColumns
import fr.photosorter.core.result.AppError
import java.io.File

/**
 * Opérations fichier bas niveau sur une image, en tenant compte du scoped storage :
 * - DELETE : sur API >= 29, tentative ContentResolver.delete (respecte les autorisations
 *   MediaStore), fallback File.delete() sur le chemin réel résolu depuis l'URI.
 * - MOVE/COPY : via [File] (renameTo puis copie+suppression) sur le chemin réel résolu.
 * - SHARE : URI FileProvider en lecture seule.
 *
 * La résolution URI content://MediaStore -> File s'appuie sur la colonne `_data`
 * (OpenableColumns.DATA) et non sur le `path` de l'URI (qui est un identifiant interne).
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
        // 2) Fallback fichier (chemin réel résolu depuis l'URI).
        val file = resolveFile(uri)
            ?: return AppError.NotFound("URI non exploitable : $imageUri")
        if (!file.exists()) return AppError.NotFound("Fichier introuvable : ${file.name}")
        return if (runCatching { file.delete() }.getOrDefault(false)) null
        else AppError.Io("Suppression impossible : ${file.name}")
    }

    fun move(imageUri: String, destDir: File): AppError? {
        val src = resolveFile(Uri.parse(imageUri))
            ?: return AppError.NotFound("URI non exploitable : $imageUri")
        if (!src.exists()) return AppError.NotFound("Fichier introuvable : ${src.name}")
        if (!ensureDir(destDir)) return AppError.Io("Dossier de destination inaccessible : ${destDir.name}")
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
        val src = resolveFile(Uri.parse(imageUri))
            ?: return AppError.NotFound("URI non exploitable : $imageUri")
        if (!src.exists()) return AppError.NotFound("Fichier introuvable : ${src.name}")
        if (!ensureDir(destDir)) return AppError.Io("Dossier de destination inaccessible : ${destDir.name}")
        val target = File(destDir, src.name)
        return if (copyFile(src, target)) null else AppError.Io("Copie échouée : ${src.name}")
    }

    /** URI partageable via FileProvider (autorisation en lecture seule). */
    fun shareUri(imageUri: String): Uri? {
        val file = resolveFile(Uri.parse(imageUri)) ?: return null
        if (!file.exists()) return null
        return runCatching {
            androidx.core.content.FileProvider.getUriForFile(
                context, "${context.packageName}.fileprovider", file
            )
        }.getOrNull()
    }

    private fun ensureDir(dir: File): Boolean = runCatching {
        dir.exists() || dir.mkdirs()
    }.getOrDefault(false)

    private fun copyFile(src: File, target: File): Boolean = runCatching {
        src.inputStream().use { input -> target.outputStream().use { input.copyTo(it) } }
        true
    }.getOrDefault(false)

    /**
     * Résout une URI (file:// ou content://) en [File].
     * Pour content://, interroge la colonne `_data` ; échoue proprement si absente
     * (accès par MediaStore non exposé en chemin).
     */
    private fun resolveFile(uri: Uri): File? {
        when (uri.scheme) {
            ContentResolver.SCHEME_FILE -> return uri.path?.let(::File)
            ContentResolver.SCHEME_CONTENT -> {
                // Colonne _data = chemin réel du média.
                runCatching {
                    resolver.query(uri, arrayOf(OpenableColumns.DATA), null, null, null)
                }.getOrNull()?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val idx = cursor.getColumnIndex(OpenableColumns.DATA)
                        if (idx >= 0 && !cursor.isNull(idx)) {
                            return cursor.getString(idx)?.let(::File)
                        }
                    }
                }
                return null
            }
            else -> return uri.path?.let(::File)
        }
    }
}
