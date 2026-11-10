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
 * - SHARE : URI FileProvider en lecture seule (le fichier matérialisé doit survivre).
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
        if (!file.file.exists()) return AppError.NotFound("Fichier introuvable : ${file.file.name}")
        return if (runCatching { file.file.delete() }.getOrDefault(false)) null
        else AppError.Io("Suppression impossible : ${file.file.name}")
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
            // repli copie + suppression
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
            // Le fichier matérialisé en cache n'a servi qu'à lire la source : on le supprime.
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

    /**
     * Résultat de la résolution : le [file] exploitable et, s'il s'agit d'une copie
     * temporaire en cache de l'app, un moyen de la [cleanup] une fois l'opération finie.
     */
    private class ResolvedFile(val file: File, private val temp: Boolean) {
        fun cleanup() {
            if (temp) runCatching { file.delete() }
        }
    }

    /**
     * Résout une URI en fichier exploitable.
     * @param materializeIfNeeded si vrai, matérialise une copie en cache quand le chemin réel
     *   est introuvable sur un `content://`. Utilisé par copy/share.
     */
    private fun resolveFile(uri: Uri, materializeIfNeeded: Boolean): ResolvedFile? {
        return when (uri.scheme) {
            ContentResolver.SCHEME_FILE -> uri.path?.let { ResolvedFile(File(it), false) }
            ContentResolver.SCHEME_CONTENT -> {
                queryDataPath(uri)?.let { return ResolvedFile(it, false) }
                if (materializeIfNeeded) materializeFromStream(uri) else null
            }
            else -> uri.path?.let { path ->
                val f = File(path)
                if (f.exists() || materializeIfNeeded) null else ResolvedFile(f, false)
            }
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
                        if (!path.isNullOrBlank()) return@use File(path).takeIf { it.exists() }
                    }
                }
                null
            }
        }.getOrNull()
    }

    /** Matérialise une copie du flux dans le cache de l'app ; le fichier est temporaire. */
    private fun materializeFromStream(uri: Uri): ResolvedFile? {
        return runCatching {
            val name = queryDisplayName(uri) ?: "shared_${System.currentTimeMillis()}"
            val dir = File(context.cacheDir, "shared").apply { mkdirs() }
            val out = File(dir, name)
            resolver.openInputStream(uri)?.use { input ->
                out.outputStream().use { output -> input.copyTo(output) }
            } ?: return@runCatching null
            if (out.length() <= 0L && out.exists()) {
                out.delete()
                return@runCatching null
            }
            ResolvedFile(out, true)
        }.getOrNull()
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

    /** Crée le dossier cible si nécessaire ; renvoie une erreur si impossible. */
    private fun ensureDir(dir: File): AppError? {
        if (dir.isDirectory) return null
        val ok = runCatching { dir.mkdirs() && dir.isDirectory }.getOrDefault(false)
        return if (ok) null else AppError.Io("Dossier de destination inaccessible : ${dir.absolutePath}")
    }

    /** Compare deux chemins (canonical si possible, repli absolu). */
    private fun samePath(a: File, b: File): Boolean {
        val pa = runCatching { a.canonicalPath }.getOrDefault(a.absolutePath)
        val pb = runCatching { b.canonicalPath }.getOrDefault(b.absolutePath)
        return pa == pb
    }

    /**
     * [AppError.Io] explicite pour un échec copie/déplacement. Sur API >= 29, la cause la plus
     * fréquente est une cible hors de la zone autorisée de l'app (scoped storage).
     */
    private fun scopedIoError(action: String, src: File): AppError {
        val detail = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            " (cible probablement hors de la zone de l'app — scoped storage)"
        else ""
        return AppError.Io("$action échoué : ${src.name}$detail")
    }

    /** Copie binaire simple ; la cible garde le nom de la source. */
    private fun copyFile(src: File, target: File): Boolean {
        return runCatching {
            src.inputStream().use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            }
            true
        }.getOrDefault(false)
    }
}
