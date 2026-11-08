package fr.photosorter.data.scanner

/**
 * Répertoires système Android à exclure du scan (assets d'apps, données privées).
 * La comparaison se fait sur un chemin relatif normalisé (segments séparés par '/').
 */
object SystemExclusions {

    private val excludedSegments = listOf("android/data", "android/obb", "android/media")

    /**
     * @param normalizedPath chemin relatif en minuscules avec '/' comme séparateur
     *        (ex. "dcim/camera/photo.jpg")
     */
    fun isExcluded(normalizedPath: String): Boolean {
        val path = "/" + normalizedPath.trim('/') + "/"
        return excludedSegments.any { seg ->
            path.contains("/$seg/") || path.endsWith("/$seg/")
        }
    }

    /** Normalise un chemin relatif (minuscules, séparateurs unifiés, sans slash initial/terminal). */
    fun normalize(path: String): String =
        path.replace('\\', '/').trim('/').lowercase()
}
