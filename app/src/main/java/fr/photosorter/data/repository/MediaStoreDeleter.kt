package fr.photosorter.data.repository

import android.app.RecoverableSecurityException
import android.content.ContentResolver
import android.content.Context
import android.content.IntentSender
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import fr.photosorter.core.result.AppError
import java.io.File

/**
 * Résultat d'une demande de suppression.
 * - [Completed] : suppression tentée immédiatement (succès si [error] == null).
 * - [NeedsConfirmation] : l'utilisateur doit confirmer via [intentSender] (API 30+) ou une
 *   récupération (API 29) ; une fois confirmé, rappeler [MediaStoreDeleter.confirmDelete] sur [uris].
 */
sealed interface DeleteRequest {
    data class Completed(val error: AppError?) : DeleteRequest
    data class NeedsConfirmation(val intentSender: IntentSender?, val uris: List<String>) : DeleteRequest
}

/**
 * Suppression d'images compatible scoped storage (API 29+).
 *
 * En scoped storage, supprimer une image non possédée par l'app exige une confirmation
 * utilisateur : MediaStore.createDeleteRequest (API 30+) ou RecoverableSecurityException (API 29).
 * [requestDelete] produit alors un [DeleteRequest.NeedsConfirmation] ; l'IHM lance [intentSender]
 * (startIntentSenderForResult) puis rappelle [confirmDelete] sur les URIs renvoyées.
 *
 * La résolution file:// -> content:// (MediaStore _data) reste ici, jamais dans l'IHM.
 */
class MediaStoreDeleter(private val context: Context) {

    private val resolver: ContentResolver get() = context.contentResolver

    /** Prépare la suppression d'une image (file:// du scan ou content:// media). */
    fun requestDelete(imageUri: String): DeleteRequest {
        val uri = Uri.parse(imageUri)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val mediaUri = toMediaContentUri(uri)
            if (mediaUri != null) return deleteViaMediaStore(mediaUri, imageUri)
        }
        // API < 29 ou fichier hors MediaStore : suppression directe (fichier possédé par l'app).
        return DeleteRequest.Completed(deleteDirect(uri))
    }

    /** Exécute la suppression effective après confirmation utilisateur. null si succès. */
    fun confirmDelete(uris: List<String>): AppError? {
        var lastError: AppError? = null
        for (s in uris) {
            val uri = Uri.parse(s)
            val err: AppError? = if (uri.scheme == ContentResolver.SCHEME_CONTENT) {
                val deleted = runCatching { resolver.delete(uri, null, null) }.getOrDefault(0)
                if (deleted > 0) null else AppError.Io("Suppression refusée : $s")
            } else {
                deleteDirect(uri)
            }
            if (err != null) lastError = err
        }
        return lastError
    }

    // --- interne ----------------------------------------------------------

    private fun deleteViaMediaStore(mediaUri: Uri, original: String): DeleteRequest {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val sender = runCatching {
                MediaStore.createDeleteRequest(resolver, listOf(mediaUri)).intentSender
            }.getOrNull()
            if (sender != null) return DeleteRequest.NeedsConfirmation(sender, listOf(mediaUri.toString()))
            return DeleteRequest.Completed(deleteDirect(Uri.parse(original)))
        }
        // API 29 : tentative directe ; RecoverableSecurityException -> confirmation.
        return try {
            val deleted = resolver.delete(mediaUri, null, null)
            if (deleted > 0) DeleteRequest.Completed(null)
            else DeleteRequest.Completed(AppError.Io("Suppression impossible : $original"))
        } catch (e: RecoverableSecurityException) {
            DeleteRequest.NeedsConfirmation(e.userAction?.actionIntent, listOf(mediaUri.toString()))
        } catch (e: SecurityException) {
            DeleteRequest.NeedsConfirmation(null, listOf(mediaUri.toString()))
        } catch (e: Exception) {
            DeleteRequest.Completed(AppError.Io(e.message ?: "Suppression impossible"))
        }
    }

    /** Convertit une URI (file:// ou content://) en URI MediaStore dédiée à ce fichier. */
    private fun toMediaContentUri(uri: Uri): Uri? {
        if (uri.scheme == ContentResolver.SCHEME_CONTENT) {
            val auth = uri.authority ?: return null
            return if (auth == MediaStore.AUTHORITY || auth.startsWith("media")) uri else null
        }
        if (uri.scheme != ContentResolver.SCHEME_FILE) return null
        val path = uri.path ?: return null
        return queryMediaUriForPath(path)
    }

    /** Retrouve l'URI MediaStore d'un fichier via son chemin [_data]. */
    private fun queryMediaUriForPath(path: String): Uri? {
        val collection = mediaFilesCollection()
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

    /** Uniformité de la collection MediaStore entre API 26-28 et 29+. */
    private fun mediaFilesCollection(): Uri =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)
        else @Suppress("DEPRECATION") MediaStore.Files.getContentUri("external")

    /** Suppression directe d'un fichier (file:// ou content:// possédé par l'app). */
    private fun deleteDirect(uri: Uri): AppError? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && uri.scheme == ContentResolver.SCHEME_CONTENT) {
            val deleted = runCatching { resolver.delete(uri, null, null) }.getOrDefault(0)
            if (deleted > 0) return null
        }
        val path = uri.path ?: return AppError.NotFound("URI non exploitable : $uri")
        if (uri.scheme != ContentResolver.SCHEME_FILE) return AppError.NotFound("URI non exploitable : $uri")
        val file = File(path)
        if (!file.exists()) return AppError.NotFound("Fichier introuvable : ${file.name}")
        return if (runCatching { file.delete() }.getOrDefault(false)) null
        else AppError.Io("Suppression impossible : ${file.name}")
    }
}
