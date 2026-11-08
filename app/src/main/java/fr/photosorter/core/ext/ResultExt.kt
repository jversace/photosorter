package fr.photosorter.core.ext

import fr.photosorter.core.result.AppError
import fr.photosorter.core.result.AppResult
import kotlinx.coroutines.CancellationException

/**
 * Exécute un bloc et capture les exceptions dans un AppResult.
 * Les CancellationException sont propagées (ne pas les avaler).
 */
suspend fun <T> runCatchingApp(block: suspend () -> T): AppResult<T> = try {
    AppResult.Success(block())
} catch (ce: CancellationException) {
    throw ce
} catch (t: Throwable) {
    AppResult.Failure(AppError.Unknown(t.message, t))
}
