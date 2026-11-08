package fr.photosorter.data.scanner

import android.os.Environment
import java.io.File

/**
 * Fournit les racines de stockage à scanner : stockage interne + carte SD externe
 * lorsqu'elle est montée. Les chemins non accessibles sont ignorés en aval.
 */
interface StorageRootProvider {
    fun roots(): List<File>
}

class DefaultStorageRootProvider : StorageRootProvider {
    override fun roots(): List<File> {
        val result = mutableListOf<File>()
        val primary = Environment.getExternalStorageDirectory()
        if (primary != null) result.add(primary)
        val secondary = System.getenv("SECONDARY_STORAGE") // souvent ':'-séparé
        if (!secondary.isNullOrBlank()) {
            secondary.split(File.pathSeparator, ":")
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .map { File(it) }
                .forEach { if (it.absolutePath != primary?.absolutePath) result.add(it) }
        }
        // Carte SD exposée via getExternalFilesDirs (second élément = externe)
        runCatching {
            val dirs = android.os.Environment.getExternalStorageDirectory()
            // fallback : chemin standard de carte SD
            val sd = File("/storage")
            if (sd.exists()) {
                sd.listFiles()?.forEach { mount ->
                    if (mount.isDirectory && mount.canRead() &&
                        mount.name != "emulated" && mount.name != "self"
                    ) {
                        if (result.none { it.absolutePath == mount.absolutePath }) result.add(mount)
                    }
                }
            }
            dirs
        }
        return result.distinctBy { it.absolutePath }
    }
}
