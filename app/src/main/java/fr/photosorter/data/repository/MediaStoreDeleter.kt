package fr.photosorter.data.repository

import android.app.RecoverableSecurityException
import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.content.IntentSender
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import fr.photosorter.core.result.AppError
import java.io.File

/**
 * Résultat d'une demande de suppression.
 * - [Completed] : suppression tentée immédiatement ([error] == null si succès).
 * - [NeedsConfirmation] : confirmation utilisateur requise ; l'IHM lance [intentSender]
 *   (API 30+) ou gère la récupération (API 29), puis rappelle [MediaStoreDeleter.confirmDelete]
 *   sur [uris].
 */
sealed interface DeleteRequest {
    data class Completed(val error: AppError?) : DeleteRequest
    data class NeedsConfirmation(val intentSender: IntentSender?, val uris: List<String>) : DeleteRequest
}

/**
 * Suppression d'images tenant compte du scoped storage (API 29+).
 *
 * Le scan produit des URI `file://` ; en scoped storage, supprimer un fichier qui n'appartient
 * pas à l'app exige une confirmation utilisateur :
 * - API 30+ : [MediaStore.createDeleteRequest] -> [IntentSender] que l'IHM lance.
 * - API 29  : [ContentResolver.delete] peut lever [RecoverableSecurityException] ; on en extrait
 *   l'[IntentSender] de récupération.
 *
 * API 26-28 : suppression directe du fichier (permission WRITE_EXTERNAL_STORAGE côté IHM).
 * La résolution `file://` -> `content://` MediaStore reste ici, jamais côté IHM.
 */
class MediaStoreDeleter(private val context: Context) {

    private val resolver: ContentResolver get() = context.contentResolver

    /**
     * Prépare la suppression d'une image. Renvoie une suppression immédiate terminée, ou une
     * demande de confirmation à faire aboutir via [confirmDelete].
     */
    fun requestDelete(imageUri: String): DeleteRequest {
        val uri = Uri.parse(imageUri)

        // API < 29 : pas de scoped storage, suppression fichier directe.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return DeleteRequest.Completed(deleteFileDirect(uri))
        }

        // API >= 29 : on cible l'URI MediaStore pour profiter du contrôle d'accès.
        val mediaUri = toMediaContentUri(uri) ?: return DeleteRequest.Completed(deleteFileDirect(uri))

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // API 30+ : demande de suppression groupée -> IntentSender à lancer par l'IHM.
            runCatching {
                android.util.Log.i("MediaStoreDeleter", "requestDelete mediaUri=$mediaUri sdk=${Build.VERSION.SDK_INT}")
                val pending = MediaStore.createDeleteRequest(resolver, listOf(mediaUri))
                DeleteRequest.NeedsConfirmation(pending.intentSender, listOf(mediaUri.toString()))
            }.getOrElse { DeleteRequest.Completed(AppError.Io("Demande de suppression impossible : ${it.message}")) }
        } else {
            // API 29 : tentative directe, échec de sécurité traduit en demande de confirmation.
            try {
                val deleted = resolver.delete(mediaUri, null, null)
                if (deleted > 0) DeleteRequest.Completed(null)
                else DeleteRequest.Completed(deleteFileDirect(uri))
            } catch (e: RecoverableSecurityException) {
                DeleteRequest.NeedsConfirmation(
                    e.userAction.actionIntent.intentSender,
                    listOf(mediaUri.toString())
                )
            } catch (e: SecurityException) {
                DeleteRequest.Completed(AppError.PermissionDenied(e.message))
            }
        }
    }

    /**
     * Exécute la suppression effective après confirmation utilisateur, sur les URIs renvoyées
     * par [requestDelete]. Renvoie null si succès, sinon l'erreur.
     */
    fun confirmDelete(uris: List<String>): AppError? {
        var lastError: AppError? = null
        for (s in uris) {
            val uri = Uri.parse(s)
            val err: AppError? = when (uri.scheme) {
                ContentResolver.SCHEME_CONTENT -> {
                    try {
                        val deleted = resolver.delete(uri, null, null)
                        android.util.Log.i("MediaStoreDeleter", "confirmDelete uri=$uri deleted=$deleted")
                        if (deleted > 0) {
                            null
                        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            // API 30+ : le système a déjà supprimé après confirmation du dialogue.
                            // resolver.delete retourne 0 mais c'est un succès.
                            null
                        } else {
                            AppError.Io("Suppression refusée : $s")
                        }
                    } catch (e: SecurityException) {
                        AppError.PermissionDenied(e.message)
                    } catch (e: Exception) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) null
                        else AppError.Io("Suppression impossible : ${e.message}")
                    }
                }
                else -> deleteFileDirect(uri)
            }
            if (err != null) lastError = err
        }
        return lastError
    }

    // --- Helpers ------------------------------------------------------------

    /** Supprime directement le fichier sous-jacent à l'URI, hors MediaStore. */
    private fun deleteFileDirect(uri: Uri): AppError? {
        val file = uriToFile(uri) ?: return AppError.NotFound("URI non exploitable : $uri")
        if (!file.exists()) return AppError.NotFound("Fichier introuvable : ${file.name}")
        return if (runCatching { file.delete() }.getOrDefault(false)) null
        else AppError.Io("Suppression impossible : ${file.name}")
    }

    /**
     * Convertit une URI (`file://` ou `content://`) en URI `content://` MediaStore.
     * - `content://` MediaStore : renvoyée telle quelle.
     * - `file://` : on retrouve l'id MediaStore via la colonne `_data`.
     */
    private fun toMediaContentUri(uri: Uri): Uri? {
        if (uri.scheme == ContentResolver.SCHEME_CONTENT) return uri
        if (uri.scheme != ContentResolver.SCHEME_FILE) return null
        val path = uri.path ?: return null

        // API 29+ : les collections "media" (images/) sont requises par createDeleteRequest.
        // Sur API 30+, une URI files/ fait afficher le dialogue MAIS n'exécute pas la suppression.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val imagesBase = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
            queryMediaUri(imagesBase, path)?.let { return it }
            // Fallback pour les fichiers qui ne seraient pas indexés comme images (rare)
            val filesBase = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)
            return queryMediaUri(filesBase, path)
        }

        // API 26-28 : résolution sur la collection Files (l'appelant fera deleteFileDirect de toute façon)
        val filesBase = MediaStore.Files.getContentUri("external")
        return queryMediaUri(filesBase, path)
    }

    private fun queryMediaUri(base: Uri, path: String): Uri? = runCatching {
        resolver.query(
            base,
            arrayOf(MediaStore.MediaColumns._ID),
            "${MediaStore.MediaColumns.DATA}=?",
            arrayOf(path),
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID))
                ContentUris.withAppendedId(base, id)
            } else null
        }
    }.getOrNull()

    /** Retrouve le fichier sous-jacent à l'URI (file:// direct, ou _data sur content://). */
    private fun uriToFile(uri: Uri): File? {
        if (uri.scheme == ContentResolver.SCHEME_FILE) {
            return uri.path?.let { File(it) }
        }
        if (uri.scheme == ContentResolver.SCHEME_CONTENT) {
            return runCatching {
                resolver.query(uri, arrayOf(MediaStore.MediaColumns.DATA), null, null, null)
                    ?.use { c ->
                        if (c.moveToFirst()) {
                            val idx = c.getColumnIndex(MediaStore.MediaColumns.DATA)
                            if (idx >= 0 && !c.isNull(idx)) {
                                val p = c.getString(idx)
                                if (!p.isNullOrBlank()) File(p) else null
                            } else null
                        } else null
                    }
            }.getOrNull()
        }
        return null
    }
}
