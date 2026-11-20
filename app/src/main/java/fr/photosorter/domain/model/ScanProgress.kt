package fr.photosorter.domain.model

sealed interface ScanProgress {
    data object Idle : ScanProgress
    data class Running(val done: Int, val total: Int, val current: String) : ScanProgress

    /**
     * Scan termine.
     * @param count nombre d'images ecrites/triees.
     * @param durationMs duree du scan.
     * @param unclassifiedCount nombre d'images NON CLASSÉES par la moderation
     *   (classifieur indisponible, decodage/inference en echec).
     *   ATTENTION : "non classée" ne signifie PAS "normale" : ces images n'ont
     *   pas pu etre evaluees et ne sont donc ni confirmees saines ni adultes.
     *   Valeur par defaut 0 pour ne pas casser les consommateurs existants.
     */
    data class Completed(
        val count: Int,
        val durationMs: Long,
        val unclassifiedCount: Int = 0
    ) : ScanProgress

    data class Error(val message: String) : ScanProgress
}
