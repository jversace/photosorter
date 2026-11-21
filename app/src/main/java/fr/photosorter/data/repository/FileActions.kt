package fr.photosorter.data.repository

import android.app.RecoverableSecurityException
import android.content.ContentResolver
import android.content.Context
import android.content.IntentSender
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.documentfile.provider.DocumentFile
import fr.photosorter.core.result.AppError
import java.io.File

/**
 * Résultat d'une demande de suppression.
 * - [Completed] : la suppression a été tentée immédiatement (succès si [error] == null).
 * - [NeedsConfirmation] : l'utilisateur doit confirmer via [intentSender] (API 30+) ou une
 *   récupération (API 29) ; une fois confirmé, rappeler [FileActions.confirmDelete] sur [uris].
 */
sealed interface DeleteRequest {
    data class Completed(val error: AppError?) : DeleteRequest
    data class NeedsConfirmation(val intentSender: IntentSender?, val uris: List<String>) : DeleteRequest
}

/**
 * Opérations fichier bas niveau sur une image, en tenant compte du scoped storage.
 *
 * Résolution d'URI :
 * - [ContentResolver.SCHEME_FILE] : le chemin est directement exploitable.
 * - [ContentResolver.SCHEME_CONTENT] : le champ [Uri.getPath] est un identifiant interne
 *   MediaStore et NON un chemin de fichier. On résout le chemin réel via [MediaStore.MediaColumns.DATA].
 *
 * Suppression : voir [requestDelete]/[confirmDelete]. En scoped storage (API 29+), supprimer une
 * image ne possédant pas l'app exige une confirmation utilisateur (MediaStore.createDeleteRequest
 * sur API 30+, RecoverableSecurityException sur API 29) : [requestDelete] produit alors un
 * [DeleteRequest.NeedsConfirmation], et l'IHM rappelle [confirmDelete] après confirmation.
 *
 * La permission runtime WRITE_EXTERNAL_STORAGE (API 26-28) relève de l'IHM, pas de cette classe.
 */
class FileActions(private val context: Context) {

    private val resolver: ContentResolver get() = context.contentResolver

    /**
     * Prépare la suppression d'une image. Renvoie soit une suppression immédiate terminée,
     * soit une demande de confirmation utilisateur à faire aboutir via [confirmDelete].
     */
    fun requestDelete(imageUri: String): DeleteRequest {
        val uri = Uri.parse(imageUri)
        // Chemin MediaStore pour un fichier du stockage (file:// issu du scan, ou content:// media).
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val mediaUri = toMediaContentUri(uri)
            if (mediaUri != null) return deleteViaMediaStore(mediaUri, imageUri)
        }
        // API < 29 ou fichier hors MediaStore : suppression directe (fichier possédé par l'app).
        return DeleteRequest.Completed(delete(imageUri))
    }

    /**
     * Exécute la suppression effective après confirmation utilisateur, sur les URIs renvoyées
     * par [requestDelete] (content:// MediaStore). Renvoie null si succès, sinon l'erreur.
     */
    fun confirmDelete(uris: List<String>): AppError? {
        var lastError: AppError? = null
        for (s in uris) {
            val uri = Uri.parse(s)
            val err: AppError? = if (uri.scheme == ContentResolver.SCHEME_CONTENT) {
                val deleted = runCatching { resolver.delete(uri, null, null) }.getOrDefault(0)
                if (deleted > 0) null
                else refreshMediaStoreIfNeeded(uri).let { err2 ->
                    if (err2 != null) AppError.Io("Suppression refusée : $s") else null
                }
            } else {
                delete(s)
            }
            if (err != null) lastError = err
        }
        return lastError
    }

    /** Tentative de suppression directe (best-effort). Renvoie null si succès. */
    fun delete(imageUri: String): AppError? {
        val uri = Uri.parse(imageUri)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && uri.scheme == ContentResolver.SCHEME_CONTENT) {
            val deleted = runCatching { resolver.delete(uri, null, null) }.getOrDefault(0)
            if (deleted > 0) return null
        }
        val file = resolveFile(uri, materializeIfNeeded = false)
            ?: return AppError.NotFound("URI non exploitable : $imageUri")
        if (!file.file.exists()) return AppError.NotFound("Fichier introuvable : ${file.file.name}")
        return if (runCatching { file.file.delete() }.getOrDefault(false)) null
        else AppError.Io("Suppression impossible : ${file.file.name}")
    }

    // --- Suppression MediaStore --------------------------------------------

    /** Supprime une URI MediaStore via le flux adapté à la version d'API. */
    private fun deleteViaMediaStore(mediaUri: Uri, original: String): DeleteRequest {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // API 30+ : la confirmation (même pour fichiers app) passe par createDeleteRequest.
            val sender = runCatching {
                MediaStore.createDeleteRequest(resolver, listOf(mediaUri)).intentSender
            }.getOrNull()
            if (sender != null) {
                return DeleteRequest.NeedsConfirmation(sender, listOf(mediaUri.toString()))
            }
            // Repli (rare) : tentative directe.
            return DeleteRequest.Completed(delete(original))
        }
        // API 29 : tentative directe ; RecoverableSecurityException -> confirmation.
        return try {
            val deleted = resolver.delete(mediaUri, null, null)
            if (deleted > 0) DeleteRequest.Completed(null)
            else DeleteRequest.Completed(AppError.Io("Suppression impossible : $original"))
        } catch (e: RecoverableSecurityException) {
            val sender = e.userAction?.actionIntent
            DeleteRequest.NeedsConfirmation(sender, listOf(mediaUri.toString()))
        } catch (e: SecurityException) {
            DeleteRequest.NeedsConfirmation(null, listOf(mediaUri.toString()))
        } catch (e: Exception) {
            DeleteRequest.Completed(AppError.Io(e.message ?: "Suppression impossible"))
        }
    }

    /**
     * Convertit une URI (file:// ou content://) en URI MediaStore content://image/media/id.
     * Renvoie null si l'URI n'appartient pas à MediaStore (fichier hors index).
     */
    private fun toMediaContentUri(uri: Uri): Uri? {
        // content:// déjà MediaStore : on le garde tel quel.
        if (uri.scheme == ContentResolver.SCHEME_CONTENT) {
            return if (uri.authority == MediaStore.AUTHORITY || uri.authority?.startsWith("media") == true) uri else null
        }
        if (uri.scheme != ContentResolver.SCHEME_FILE) return null
        val path = uri.path ?: return null
        return queryMediaUriForPath(path)
    }

    /** Retrouve l'URI MediaStore d'un fichier à partir de son chemin [_data]. */
    private fun queryMediaUriForPath(path: String): Uri? {
        val collection = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)
        val projection = arrayOf(MediaStore.MediaColumns._ID)
        val selection = "${MediaStore.MediaColumns.DATA} = ?"
        return runCatching {
            resolver.query(collection, projection, selection, arrayOf(path), null)?.use { c ->
                if (c.moveToFirst()) {
                    val id = c.getLong(0)
                    if (id > 0) Uri.withAppendedPath(collection, id.toString()) else null
                } else null
            }
        }.getOrNull()
    }

    /** Force un rafraîchissement MediaStore après suppression (renvoie null si OK). */
    private fun refreshMediaStoreIfNeeded(uri: Uri): AppError? = null

    fun move(imageUri: String, destDir: File): AppError? {
        val resolved = resolveFile(Uri.parse(imageUri), materializeIfNeeded = false)
            ?: return AppError.NotFound("URI non exploitable : $imageUri")
        val src = resolved.file
        try {
            if (!src.exists()) return AppError.NotFound("Fichier introuvable : ${src.name}")
            ensureDir(destDir)?.let { return it }
            val target = File(destDir, src.name)
            if (samePath(src, target)) return null
            if (src.renameTo(target)) return null
            if (!copyFile(src, target)) return scopedIoError("Déplacement", src)
            if (!runCatching { src.delete() }.getOrDefault(false)) {
                return AppError.Io("Déplacement partiel (copie ok, source non supprimée) : ${src.name}")
            }
            return null
        } finally {
            resolved.cleanup()
        }
    }

    fun copy(imageUri: String, destDir: File): AppError? {
        val resolved = resolveFile(Uri.parse(imageUri), materializeIfNeeded = true)
            ?: return AppError.NotFound("URI non exploitable : $imageUri")
        val src = resolved.file
        try {
            if (!src.exists()) return AppError.NotFound("Fichier introuvable : ${src.name}")
            ensureDir(destDir)?.let { return it }
            val target = File(destDir, src.name)
            return if (copyFile(src, target)) null else scopedIoError("Copie", src)
        } finally {
            resolved.cleanup()
        }
    }

    /** Déplace [imageUri] dans l'arborescence SAF [treeUri]. Renvoie null si succès. */
    fun moveToTree(imageUri: String, treeUri: String): AppError? {
        val copyError = copyToTreeInternal(imageUri, treeUri, materialize = false)
        if (copyError != null) return copyError
        return delete(imageUri)
    }

    /** Copie [imageUri] dans l'arborescence SAF [treeUri]. Renvoie null si succès. */
    fun copyToTree(imageUri: String, treeUri: String): AppError? =
        copyToTreeInternal(imageUri, treeUri, materialize = true)

    private fun copyToTreeInternal(imageUri: String, treeUri: String, materialize: Boolean): AppError? {
        val tree = runCatching { DocumentFile.fromTreeUri(context, Uri.parse(treeUri)) }.getOrNull()
            ?: return AppError.Io("Arborescence destination inaccessible : $treeUri")
        if (!tree.isDirectory) return AppError.Io("Destination invalide (pas un dossier) : $treeUri")

        val resolved = resolveFile(Uri.parse(imageUri), materializeIfNeeded = materialize)
            ?: return AppError.NotFound("URI non exploitable : $imageUri")
        val src = resolved.file
        try {
            if (!src.exists()) return AppError.NotFound("Fichier introuvable : ${src.name}")
            val displayName = src.name
            val mime = resolver.getType(Uri.parse(imageUri)) ?: guessMime(displayName)
            val target = tree.createFile(mime, displayName)
                ?: return AppError.Io("Création du fichier destination impossible : $displayName")
            val copied = runCatching {
                resolver.openOutputStream(target.uri)?.use { output ->
                    src.inputStream().use { input -> input.copyTo(output) }
                } != null
            }.getOrDefault(false)
            if (!copied) {
                runCatching { target.delete() }
                return AppError.Io("Copie échouée : $displayName")
            }
            return null
        } finally {
            resolved.cleanup()
        }
    }

    /** URI partageable via FileProvider. Le fichier matérialisé n'est PAS supprimé ici. */
    fun shareUri(imageUri: String): Uri? {
        val uri = Uri.parse(imageUri)
        val resolved = resolveFile(uri, materializeIfNeeded = true) ?: return null
        if (!resolved.file.exists()) return null
        return runCatching {
            androidx.core.content.FileProvider.getUriForFile(
                context, "${context.packageName}.fileprovider", resolved.file
            )
        }.getOrNull()
    }

    // --- Résolution d'URI ---------------------------------------------------

    private class ResolvedFile(val file: File, private val temp: Boolean) {
        fun cleanup() {
            if (temp) runCatching { file.delete() }
        }
    }

    private fun resolveFile(uri: Uri, materializeIfNeeded: Boolean): ResolvedFile? {
        return when (uri.scheme) {
            ContentResolver.SCHEME_FILE -> uri.path?.let { ResolvedFile(File(it), false) }
            ContentResolver.SCHEME_CONTENT -> {
                queryDataPath(uri)?.let { return ResolvedFile(it, false) }
                if (materializeIfNeeded) materializeFromStream(uri) else null
            }
            else -> uri.path?.let { path ->
                val f = File(path)
                if (f.exists()) ResolvedFile(f, false) else null
            }
        }
    }

    private fun queryDataPath(uri: Uri): File? {
        return runCatching {
            resolver.query(uri, arrayOf(MediaStore.MediaColumns.DATA), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(MediaStore.MediaColumns.DATA)
                    if (idx >= 0 && !cursor.isNull(idx)) {
                        val path = cursor.getString(idx)
                        if (!path.isNullOrBlank()) return@use File(path).takeIf { it.exists() }
                    }
                }
                null
            }
        }.getOrNull()
    }

    private fun materializeFromStream(uri: Uri): ResolvedFile? {
        return runCatching {
            purgeStaleSharedCache()
            val name = queryDisplayName(uri) ?: "shared_${System.currentTimeMillis()}"
            val dir = File(context.cacheDir, "shared").apply { mkdirs() }
            val out = File(dir, name)
            resolver.openInputStream(uri)?.use { input ->
                out.outputStream().use { output -> input.copyTo(output) }
            } ?: return@runCatching null
            if (out.length() <= 0L) {
                out.delete()
                return@runCatching null
            }
            ResolvedFile(out, true)
        }.getOrNull()
    }

    private fun purgeStaleSharedCache() {
        runCatching {
            val dir = File(context.cacheDir, "shared")
            val cutoff = System.currentTimeMillis() - SHARED_CACHE_TTL_MS
            dir.listFiles()?.forEach { f ->
                if (f.lastModified() < cutoff) runCatching { f.delete() }
            }
        }
    }

    private fun queryDisplayName(uri: Uri): String? {
        return runCatching {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0 && !c.isNull(idx)) c.getString(idx) else null
                } else null
            }
        }.getOrNull()
    }

    // --- Helpers ------------------------------------------------------------

    private fun guessMime(name: String): String {
        return when (name.substringAfterLast('.', "").lowercase()) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "heic", "heif" -> "image/heic"
            "webp" -> "image/webp"
            "gif" -> "image/gif"
            "bmp" -> "image/bmp"
            "dng" -> "image/x-adobe-dng"
            "cr2" -> "image/x-canon-cr2"
            "nef" -> "image/x-nikon-nef"
            else -> "application/octet-stream"
        }
    }

    private fun ensureDir(dir: File): AppError? {
        if (dir.isDirectory) return null
        val ok = runCatching { dir.mkdirs() && dir.isDirectory }.getOrDefault(false)
        return if (ok) null else AppError.Io("Dossier de destination inaccessible : ${dir.absolutePath}")
    }

    private fun samePath(a: File, b: File): Boolean {
        val ca = runCatching { a.canonicalPath }.getOrDefault(a.absolutePath)
        val cb = runCatching { b.canonicalPath }.getOrDefault(b.absolutePath)
        return ca == cb
    }

    private fun copyFile(src: File, target: File): Boolean {
        return runCatching {
            src.inputStream().use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            }
            true
        }.getOrDefault(false)
    }

    private fun scopedIoError(action: String, src: File): AppError =
        AppError.Io("$action impossible en scoped storage : ${src.name}")

    private companion object {
        const val SHARED_CACHE_TTL_MS = 15 * 60 * 1000L
    }
}
