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

        // Cartes SD secondaires : souvent exposées via SECONDARY_STORAGE (' : '-séparé)
        val secondary = System.getenv("SECONDARY_STORAGE")
        if (!secondary.isNullOrBlank()) {
            secondary.split(File.pathSeparator, ":")
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .map { File(it) }
                .forEach { if (it.absolutePath != primary?.absolutePath) result.add(it) }
        }

        // Fallback : points de montage sous /storage (hors emulated/self)
        runCatching {
            val storage = File("/storage")
            if (storage.exists()) {
                storage.listFiles()?.forEach { mount ->
                    if (mount.isDirectory && mount.canRead() &&
                        mount.name != "emulated" && mount.name != "self"
                    ) {
                        if (result.none { it.absolutePath == mount.absolutePath }) result.add(mount)
                    }
                }
            }
        }
        return result.distinctBy { it.absolutePath }
    }
}
