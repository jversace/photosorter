package fr.photosorter.domain.model

sealed interface ScanProgress {
    data object Idle : ScanProgress
    data class Running(val done: Int, val total: Int, val current: String) : ScanProgress
    data class Completed(val count: Int, val durationMs: Long) : ScanProgress
    data class Error(val message: String) : ScanProgress
}
