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
import java.io.FileOutputStream

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
 */
class FileActions(private val context: Context) {

    private val resolver: ContentResolver get() = context.contentResolver

    fun requestDelete(imageUri: String): DeleteRequest {
        val uri = Uri.parse(imageUri)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val mediaUri = toMediaContentUri(uri)
            if (mediaUri != null) return deleteViaMediaStore(mediaUri, imageUri)
        }
        return DeleteRequest.Completed(delete(imageUri))
    }

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

    /** Indique si la source existe encore après une tentative de suppression. */
    private fun stillExists(imageUri: String): Boolean {
        val uri = Uri.parse(imageUri)
        return when (uri.scheme) {
            ContentResolver.SCHEME_FILE -> uri.path?.let { File(it).exists() } ?: false
            ContentResolver.SCHEME_CONTENT -> runCatching {
                resolver.openFileDescriptor(uri, "r")?.use { true } ?: false
            }.getOrDefault(false)
            else -> false
        }
    }

    // --- Suppression MediaStore --------------------------------------------

    private fun deleteViaMediaStore(mediaUri: Uri, original: String): DeleteRequest {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val sender = runCatching {
                MediaStore.createDeleteRequest(resolver, listOf(mediaUri)).intentSender
            }.getOrNull()
            if (sender != null) {
                return DeleteRequest.NeedsConfirmation(sender, listOf(mediaUri.toString()))
            }
            return DeleteRequest.Completed(delete(original))
        }
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

    private fun toMediaContentUri(uri: Uri): Uri? {
        if (uri.scheme == ContentResolver.SCHEME_CONTENT) {
            return if (uri.authority == MediaStore.AUTHORITY || uri.authority?.startsWith("media") == true) uri else null
        }
        if (uri.scheme != ContentResolver.SCHEME_FILE) return null
        val path = uri.path ?: return null
        return queryMediaUriForPath(path)
    }

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

    /**
     * Déplace [imageUri] dans l'arborescence SAF [treeUri]. Renvoie null si succès (copie + source
     * réellement supprimée). En cas d'échec de suppression après copie, remonte une erreur claire
     * (la copie reste en destination mais l'appelant est informé — pas de succès trompeur).
     */
    fun moveToTree(imageUri: String, treeUri: String): AppError? {
        val copyError = copyToTreeInternal(imageUri, treeUri, materialize = false)
        if (copyError != null) return copyError

        // Suppression de la source : essayer le flux MediaStore (gère scoped storage).
        when (val req = requestDelete(imageUri)) {
            is DeleteRequest.Completed -> {
                if (req.error != null) {
                    return AppError.Io("Déplacement : copie effectuée mais original non supprimé (${req.error.message ?: "erreur"})")
                }
                if (stillExists(imageUri)) {
                    return AppError.Io("Déplacement : copie effectuée mais original toujours présent")
                }
                return null
            }
            is DeleteRequest.NeedsConfirmation -> {
                // La suppression requiert une confirmation interactive indisponible dans un déplacement synchrone.
                return AppError.Io("Déplacement : copie effectuée mais suppression de l'original nécessitant confirmation. Utilisez Supprimer pour retirer l'original.")
            }
        }
    }

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
            ContentResolver.SCHEME_FILE -> uri.path?.let { ResolvedFile(File(it), temp = false) }
            ContentResolver.SCHEME_CONTENT -> resolveContentFile(uri, materializeIfNeeded)
            else -> null
        }
    }

    private fun resolveContentFile(uri: Uri, materializeIfNeeded: Boolean): ResolvedFile? {
        // 1) Chemin réel via DATA (souvent accessible selon OEM/API).
        val dataPath = queryDataColumn(uri)
        if (dataPath != null) {
            val f = File(dataPath)
            if (f.exists()) return ResolvedFile(f, temp = false)
        }
        // 2) Repli : matérialiser le contenu dans un fichier temporaire.
        if (!materializeIfNeeded) return null
        val name = queryDisplayName(uri) ?: "img_${System.currentTimeMillis()}"
        val tmp = File(context.cacheDir, "saf_${System.currentTimeMillis()}_$name")
        return runCatching {
            resolver.openInputStream(uri)?.use { input ->
                FileOutputStream(tmp).use { output -> input.copyTo(output) }
            } ?: return null
            ResolvedFile(tmp, temp = true)
        }.getOrNull()
    }

    private fun queryDataColumn(uri: Uri): String? = runCatching {
        resolver.query(uri, arrayOf(MediaStore.MediaColumns.DATA), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull()

    private fun queryDisplayName(uri: Uri): String? = runCatching {
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull()

    // --- Helpers ------------------------------------------------------------

    private fun ensureDir(dir: File): AppError? {
        if (dir.exists()) return if (dir.isDirectory) null else AppError.Io("Destination invalide : ${dir.name}")
        return if (dir.mkdirs() || dir.isDirectory) null else AppError.Io("Création du dossier impossible : ${dir.name}")
    }

    private fun samePath(a: File, b: File): Boolean =
        runCatching { a.canonicalPath == b.canonicalPath }.getOrDefault(a.absolutePath == b.absolutePath)

    private fun copyFile(src: File, target: File): Boolean = runCatching {
        src.inputStream().use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        }
        true
    }.getOrDefault(false)

    private fun scopedIoError(op: String, src: File): AppError =
        AppError.Io("$op impossible (accès restreint) : ${src.name}")

    private fun guessMime(name: String): String {
        val ext = name.substringAfterLast('.', "").lowercase()
        return when (ext) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "webp" -> "image/webp"
            "gif" -> "image/gif"
            "bmp" -> "image/bmp"
            "heic", "heif" -> "image/heic"
            "dng" -> "image/x-adobe-dng"
            "cr2" -> "image/x-canon-cr2"
            "nef" -> "image/x-nikon-nef"
            else -> "image/*"
        }
    }
}
