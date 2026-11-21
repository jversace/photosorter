package fr.photosorter.data.repository

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.documentfile.provider.DocumentFile
import fr.photosorter.core.result.AppError
import java.io.File

/**
 * Opérations fichier bas niveau sur une image, en tenant compte du scoped storage.
 *
 * Résolution d'URI :
 * - [ContentResolver.SCHEME_FILE] : le chemin est directement exploitable.
 * - [ContentResolver.SCHEME_CONTENT] : le champ [Uri.getPath] est un identifiant interne
 *   MediaStore et NON un chemin de fichier. On résout via la colonne [MediaStore.MediaColumns.DATA]
 *   (`_data`). En repli, si `_data` est absent (typique API >= 29 / scoped storage), on matérialise
 *   une copie dans le cache de l'app.
 *
 * Destination :
 * - [move]/[copy] ciblent un dossier [File].
 * - [moveToTree]/[copyToTree] ciblent une destination SAF (tree URI) via [DocumentFile].
 *
 * La permission runtime WRITE_EXTERNAL_STORAGE (API 26-28) relève de l'IHM.
 */
class FileActions(private val context: Context) {

    private val resolver: ContentResolver get() = context.contentResolver

    /** Supprime le fichier et renvoie null si succès, sinon l'erreur. */
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
    fun stillExists(imageUri: String): Boolean {
        val uri = Uri.parse(imageUri)
        return when (uri.scheme) {
            ContentResolver.SCHEME_FILE -> uri.path?.let { File(it).exists() } ?: false
            ContentResolver.SCHEME_CONTENT -> runCatching {
                resolver.openFileDescriptor(uri, "r")?.use { true } ?: false
            }.getOrDefault(false)
            else -> false
        }
    }

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
     * Déplace [imageUri] dans l'arborescence SAF [treeUri]. Renvoie null si succès (copie ET source
     * réellement supprimée). En cas d'échec de suppression après copie, remonte une erreur claire :
     * la copie reste en destination mais l'appelant est informé (pas de succès trompeur).
     */
    fun moveToTree(imageUri: String, treeUri: String): AppError? {
        val copyError = copyToTreeInternal(imageUri, treeUri, materialize = false)
        if (copyError != null) return copyError

        // Copie réussie : supprimer la source. On utilise la suppression directe (delete),
        // PAS le flux de confirmation MediaStore (createDeleteRequest renvoie un IntentSender
        // inconditionnellement sur API 30+, ce qui casserait tout déplacement synchrone).
        val delError = delete(imageUri)
        if (delError != null) {
            return AppError.Io(
                "Déplacement : copie effectuée mais original non supprimé (${delError.message ?: "erreur"})"
            )
        }
        // Vérifier que la source a réellement disparu (certains OEM renvoient un faux succès).
        if (stillExists(imageUri)) {
            return AppError.Io("Déplacement : copie effectuée mais original toujours présent")
        }
        return null
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
        val dataPath = queryDataColumn(uri)
        if (dataPath != null) {
            val f = File(dataPath)
            if (f.exists()) return ResolvedFile(f, temp = false)
        }
        if (!materializeIfNeeded) return null
        return materializeContent(uri)
    }

    private fun materializeContent(uri: Uri): ResolvedFile? {
        val name = queryDisplayName(uri) ?: "img_${System.currentTimeMillis()}.bin"
        val temp = File(context.cacheDir, name)
        val ok = runCatching {
            resolver.openInputStream(uri)?.use { input ->
                temp.outputStream().use { output -> input.copyTo(output) }
            } != null
        }.getOrDefault(false)
        return if (ok) ResolvedFile(temp, temp = true) else null
    }

    private fun queryDataColumn(uri: Uri): String? = runCatching {
        resolver.query(uri, arrayOf(MediaStore.MediaColumns.DATA), null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                val idx = c.getColumnIndex(MediaStore.MediaColumns.DATA)
                if (idx >= 0) c.getString(idx)?.takeIf { it.isNotBlank() } else null
            } else null
        }
    }.getOrNull()

    private fun queryDisplayName(uri: Uri): String? = runCatching {
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) c.getString(idx) else null
            } else null
        }
    }.getOrNull()

    // --- Helpers -----------------------------------------------------------

    private fun ensureDir(dir: File): AppError? {
        if (dir.exists()) return if (dir.isDirectory) null else AppError.Io("Destination invalide : ${dir.path}")
        return if (runCatching { dir.mkdirs() }.getOrDefault(false)) null
        else AppError.Io("Création du dossier impossible : ${dir.path}")
    }

    private fun copyFile(src: File, target: File): Boolean = runCatching {
        src.inputStream().use { input -> target.outputStream().use { output -> input.copyTo(output) } }
        true
    }.getOrDefault(false)

    private fun samePath(a: File, b: File): Boolean =
        runCatching { a.canonicalPath == b.canonicalPath }.getOrDefault(a.absolutePath == b.absolutePath)

    private fun scopedIoError(op: String, src: File): AppError =
        AppError.Io("$op impossible (scoped storage) : ${src.name}")

    private fun guessMime(name: String): String = when (name.substringAfterLast('.', "").lowercase()) {
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "heic", "heif" -> "image/heic"
        "webp" -> "image/webp"
        "gif" -> "image/gif"
        "bmp" -> "image/bmp"
        else -> "application/octet-stream"
    }
}
