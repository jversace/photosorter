package fr.photosorter.data.repository

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.OpenableColumns
import fr.photosorter.core.result.AppError
import java.io.File

/**
 * Opérations fichier bas niveau sur une image, en tenant compte du scoped storage.
 *
 * Résolution d'URI :
 * - [ContentResolver.SCHEME_FILE] : le chemin est directement exploitable.
 * - [ContentResolver.SCHEME_CONTENT] : le champ [Uri.getPath] est un identifiant interne
 *   MediaStore (ex. `images/media/1234`) et NON un chemin de fichier. On résout le chemin
 *   réel via la colonne [OpenableColumns.DATA] (`_data`). En repli, si `_data` est absent
 *   ou vide (typique sur API >= 29 / scoped storage), on matérialise une copie dans le
 *   cache de l'app à partir du flux du resolver, nommée d'après [OpenableColumns.DISPLAY_NAME].
 *
 * Politique par opération :
 * - DELETE : sur API >= 29, tentative ContentResolver.delete (respecte l'autorisation
 *   MediaStore), puis repli fichier via [resolveFile].
 * - MOVE/COPY : via [File] (renameTo, puis copie + suppression en repli).
 * - SHARE : URI FileProvider en lecture seule.
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
            // si 0, on tente le fichier réel (repli)
        }
        // 2) Repli fichier.
        val file = resolveFile(uri, materializeIfNeeded = false)
            ?: return AppError.NotFound("URI non exploitable : $imageUri")
        if (!file.exists()) return AppError.NotFound("Fichier introuvable : ${file.name}")
        return if (runCatching { file.delete() }.getOrDefault(false)) null
        else AppError.Io("Suppression impossible : ${file.name}")
    }

    fun move(imageUri: String, destDir: File): AppError? {
        val src = resolveFile(Uri.parse(imageUri), materializeIfNeeded = false)
            ?: return AppError.NotFound("URI non exploitable : $imageUri")
        if (!src.exists()) return AppError.NotFound("Fichier introuvable : ${src.name}")
        val target = File(destDir, src.name)
        if (src.renameTo(target)) return null
        // repli copie + suppression
        if (!copyFile(src, target)) return AppError.Io("Déplacement échoué : ${src.name}")
        if (!runCatching { src.delete() }.getOrDefault(false)) {
            return AppError.Io("Déplacement partiel (copie ok, source non supprimée) : ${src.name}")
        }
        return null
    }

    fun copy(imageUri: String, destDir: File): AppError? {
        val uri = Uri.parse(imageUri)
        val src = resolveFile(uri, materializeIfNeeded = true)
            ?: return AppError.NotFound("URI non exploitable : $imageUri")
        if (!src.exists()) return AppError.NotFound("Fichier introuvable : ${src.name}")
        val target = File(destDir, src.name)
        return if (copyFile(src, target)) null else AppError.Io("Copie échouée : ${src.name}")
    }

    /** URI partageable via FileProvider (autorisation en lecture seule). */
    fun shareUri(imageUri: String): Uri? {
        val uri = Uri.parse(imageUri)
        val file = resolveFile(uri, materializeIfNeeded = true) ?: return null
        if (!file.exists()) return null
        return runCatching {
            androidx.core.content.FileProvider.getUriForFile(
                context, "${context.packageName}.fileprovider", file
            )
        }.getOrNull()
    }

    /**
     * Résout une URI en [File] exploitable.
     *
     * @param materializeIfNeeded si vrai et que le chemin réel est introuvable sur un
     *   `content://`, copie le flux dans le cache de l'app (nom issu de DISPLAY_NAME) et
     *   renvoie ce fichier. Utilisé par copy/share. Pour delete/move, on reste en repli
     *   conservateur : on ne fabrique pas de copie, on renvoie null si le chemin réel est
     *   introuvable.
     */
    private fun resolveFile(uri: Uri, materializeIfNeeded: Boolean): File? {
        return when (uri.scheme) {
            ContentResolver.SCHEME_FILE -> uri.path?.let(::File)?.takeIf { it.exists() }
                ?: uri.path?.let(::File)
            ContentResolver.SCHEME_CONTENT -> {
                queryDataPath(uri)
                    ?: if (materializeIfNeeded) materializeFromStream(uri) else null
            }
            else -> uri.path?.let(::File)?.takeIf { it.exists() }
                ?: if (materializeIfNeeded) materializeFromStream(uri) else null
        }
    }

    /** Interroge la colonne `_data` ([OpenableColumns.DATA]) d'une URI content://. */
    private fun queryDataPath(uri: Uri): File? {
        return runCatching {
            resolver.query(uri, arrayOf(OpenableColumns.DATA), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.DATA)
                    if (idx >= 0) {
                        val path = cursor.getString(idx)
                        if (!path.isNullOrBlank()) {
                            val f = File(path)
                            if (f.exists()) return@use f
                        }
                    }
                }
                null
            }
        }.getOrNull()
    }

    /**
     * Matérialise une URI content:// en fichier du cache de l'app, nommé d'après
     * [OpenableColumns.DISPLAY_NAME]. La zone `cache-path` est déclarée dans file_paths.xml.
     */
    private fun materializeFromStream(uri: Uri): File? {
        return runCatching {
            val name = queryDisplayName(uri) ?: "img_${System.currentTimeMillis()}"
            val cacheDir = File(context.cacheDir, "shared").apply { mkdirs() }
            val out = File(cacheDir, name)
            resolver.openInputStream(uri)?.use { input ->
                out.outputStream().use { output -> input.copyTo(output) }
            } ?: return null
            if (out.exists() && out.length() > 0) out else null
        }.getOrNull()
    }

    private fun queryDisplayName(uri: Uri): String? = runCatching {
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) cursor.getString(idx) else null
            } else null
        }
    }.getOrNull()

    private fun copyFile(src: File, target: File): Boolean = runCatching {
        src.inputStream().use { input -> target.outputStream().use { input.copyTo(it) } }
        true
    }.getOrDefault(false)
}
