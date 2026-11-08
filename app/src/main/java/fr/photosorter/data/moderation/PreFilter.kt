package fr.photosorter.data.moderation

/**
 * Pré-filtre heuristique exécuté AVANT le classifieur TFLite.
 *
 * Objectif : éviter de faire tourner le modèle sur des images qui ne sont pas
 * des photos (screenshots, documents scannés). Ces catégories sont détectées
 * via le chemin et le nom de fichier (aucune analyse de pixels, donc très rapide
 * et 100% local).
 *
 * Résultat possible :
 * - SKIP    : inutile d'appeler l'IA, on considère "non adulte par défaut"
 *             (un screenshot peut être révisé manuellement par l'utilisateur).
 * - ANALYZE : il faut appeler le classifieur TFLite.
 */
enum class PreFilterDecision { SKIP, ANALYZE }

class PreFilter {

    fun decide(path: String?, displayName: String?): PreFilterDecision {
        val haystack = listOfNotNull(path, displayName)
            .joinToString(" ")
            .lowercase()
        if (haystack.isBlank()) return PreFilterDecision.ANALYZE

        if (SCREENSHOT_HINTS.any { haystack.contains(it) }) return PreFilterDecision.SKIP
        if (DOCUMENT_HINTS.any { haystack.contains(it) }) return PreFilterDecision.SKIP
        return PreFilterDecision.ANALYZE
    }

    private companion object {
        // Chemins/dossiers ou préfixes de noms typiques de captures d'écran.
        val SCREENSHOT_HINTS = listOf(
            "/screenshots/",
            "/screen_shots/",
            "screenshot",
            "screen_shot",
            "capture d'écran",
            "/dcim/screenshots"
        )

        // Documents scannés : souvent des images "aplaties" (texte blanc sur fond).
        val DOCUMENT_HINTS = listOf(
            "/documents/",
            "/scans/",
            "/scanned/",
            "scan_",
            "_scan",
            "document_",
            "_document",
            "receipt",
            "facture",
            "invoice"
        )
    }
}
