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
 * - MOVE/COPY : via [File] (renameTo, puis copie + suppression en repli). Si la cible est
 *   hors de la zone de l'app sur API >= 29, renvoie un [AppError.Io] explicite (scoped storage).
 * - SHARE : URI FileProvider en lecture seule.
 *
 * La permission runtime WRITE_EXTERNAL_STORAGE (API 26-28) relève de l'IHM, pas de cette classe.
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
        ensureDir(destDir)?.let { return it }
        val target = File(destDir, src.name)
        if (target.absolutePath == src.absolutePath) return null
        if (src.renameTo(target)) return null
        // repli copie + suppression
        if (!copyFile(src, target)) return scopedIoError("Déplacement", src)
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
        ensureDir(destDir)?.let { return it }
        val target = File(destDir, src.name)
        return if (copyFile(src, target)) null else scopedIoError("Copie", src)
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

    // --- Helpers ------------------------------------------------------------

    /** Crée le dossier cible si nécessaire ; renvoie une erreur claire en cas d'échec. */
    private fun ensureDir(dir: File): AppError? {
        if (dir.exists()) {
            return if (dir.isDirectory) null else AppError.Io("Cible n'est pas un dossier : ${dir.name}")
        }
        return if (runCatching { dir.mkdirs() }.getOrDefault(false) || dir.isDirectory) null
        else scopedIoError("Création du dossier", dir)
    }

    /**
     * Construit un [AppError.Io] précisant la cause probable quand la cible/source est
     * hors de la zone de l'app sur API >= 29 (scoped storage).
     */
    private fun scopedIoError(action: String, f: File): AppError.Io {
        val hint = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            " — cible probablement hors de la zone de l'app (scoped storage) ou permissions insuffisantes"
        } else {
            " — vérifier la permission WRITE_EXTERNAL_STORAGE"
        }
        return AppError.Io("$action échoué : ${f.name}$hint")
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
            ContentResolver.SCHEME_FILE -> uri.path?.let(::File)
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
                    if (idx >= 0 && !cursor.isNull(idx)) {
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
     * Matérialise une copie locale du contenu `content://` dans `cacheDir/shared/`,
     * nommée d'après [OpenableColumns.DISPLAY_NAME]. Renvoie null en cas d'échec.
     */
    private fun materializeFromStream(uri: Uri): File? {
        return runCatching {
            val name = queryString(uri, OpenableColumns.DISPLAY_NAME) ?: "shared_${System.currentTimeMillis()}"
            val outDir = File(context.cacheDir, "shared").apply { mkdirs() }
            val outFile = File(outDir, name)
            resolver.openInputStream(uri)?.use { input ->
                outFile.outputStream().use { output -> input.copyTo(output) }
            } ?: return null
            outFile.takeIf { it.exists() }
        }.getOrNull()
    }

    /** Lit une colonne texte (ex. DISPLAY_NAME) d'une URI content://. */
    private fun queryString(uri: Uri, column: String): String? {
        return runCatching {
            resolver.query(uri, arrayOf(column), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(column)
                    if (idx >= 0 && !cursor.isNull(idx)) cursor.getString(idx) else null
                } else null
            }
        }.getOrNull()
    }

    /** Copie [src] vers [target] (flux), en garantissant le dossier parent. */
    private fun copyFile(src: File, target: File): Boolean = runCatching {
        target.parentFile?.let { if (!it.exists()) it.mkdirs() }
        src.inputStream().use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        }
        true
    }.getOrDefault(false)
}
