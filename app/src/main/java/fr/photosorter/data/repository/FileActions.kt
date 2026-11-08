package fr.photosorter.data.repository

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import fr.photosorter.core.result.AppError
import java.io.File

/**
 * Opérations fichier bas niveau sur une image, en tenant compte du scoped storage :
 * - DELETE : sur API >= 29, tentative ContentResolver.delete (respecte les autorisations
 *   MediaStore), fallback File.delete() sur le chemin réel.
 * - MOVE/COPY : via [File] (renameTo puis copie+suppression). Scoped storage limite les
 *   écritures hors de la zone de l'app ; en cas d'échec, on renvoie un [AppError.Io] clair.
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
        // 2) Fallback fichier.
        val file = uri.toFileOrNull()
            ?: return AppError.NotFound("URI non exploitable : $imageUri")
        if (!file.exists()) return AppError.NotFound("Fichier introuvable : ${file.name}")
        return if (runCatching { file.delete() }.getOrDefault(false)) null
        else AppError.Io("Suppression impossible : ${file.name}")
    }

    fun move(imageUri: String, destDir: File): AppError? {
        val src = Uri.parse(imageUri).toFileOrNull()
            ?: return AppError.NotFound("URI non exploitable : $imageUri")
        if (!src.exists()) return AppError.NotFound("Fichier introuvable : ${src.name}")
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
        val src = Uri.parse(imageUri).toFileOrNull()
            ?: return AppError.NotFound("URI non exploitable : $imageUri")
        if (!src.exists()) return AppError.NotFound("Fichier introuvable : ${src.name}")
        val target = File(destDir, src.name)
        return if (copyFile(src, target)) null else AppError.Io("Copie échouée : ${src.name}")
    }

    /** URI partageable via FileProvider (autorisation en lecture seule). */
    fun shareUri(imageUri: String): Uri? {
        val file = Uri.parse(imageUri).toFileOrNull() ?: return null
        if (!file.exists()) return null
        return runCatching {
            androidx.core.content.FileProvider.getUriForFile(
                context, "${context.packageName}.fileprovider", file
            )
        }.getOrNull()
    }

    private fun copyFile(src: File, target: File): Boolean = runCatching {
        src.inputStream().use { input -> target.outputStream().use { input.copyTo(it) } }
        true
    }.getOrDefault(false)

    private fun Uri.toFileOrNull(): File? = runCatching {
        when (scheme) {
            ContentResolver.SCHEME_FILE -> path?.let(::File)
            ContentResolver.SCHEME_CONTENT -> path?.let(::File)
            else -> path?.let(::File)
        }
    }.getOrNull()
}
