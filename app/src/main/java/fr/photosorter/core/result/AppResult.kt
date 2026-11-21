package fr.photosorter.core.result

import android.content.IntentSender

/**
 * Résultat d'une opération métier : succès typé ou erreur.
 * Utilisé par les repositories (pas d'exceptions qui traversent les frontières).
 */
sealed interface AppResult<out T> {
    data class Success<T>(val data: T) : AppResult<T>
    data class Failure(val error: AppError) : AppResult<Nothing>

    /**
     * Suppression suspendue à une confirmation utilisateur (scoped storage).
     *
     * L'IHM doit lancer [intentSender] (API 30+, via MediaStore.createDeleteRequest) ou
     * gérer la récupération (API 29, RecoverableSecurityException) ; une fois la confirmation
     * obtenue, l'IHM rappelle la suppression confirmée sur [uris].
     *
     * @param intentSender écran système de confirmation à lancer, ou null si la confirmation
     *   doit être obtenue par un autre moyen (récupération API 29).
     * @param uris URIs MediaStore (content://) concernées par la suppression.
     */
    data class PendingDelete(
        val intentSender: IntentSender?,
        val uris: List<String>
    ) : AppResult<Nothing>
}

/** Erreurs applicatives (pas d'erreurs réseau : l'app est 100% locale). */
sealed interface AppError {
    val message: String?

    data class PermissionDenied(override val message: String? = null) : AppError
    data class NotFound(override val message: String? = null) : AppError
    data class Io(override val message: String? = null) : AppError
    data class Unsupported(override val message: String? = null) : AppError
    data class Unknown(override val message: String? = null, val cause: Throwable? = null) : AppError
}

inline fun <T, R> AppResult<T>.map(transform: (T) -> R): AppResult<R> = when (this) {
    is AppResult.Success -> AppResult.Success(transform(data))
    is AppResult.Failure -> this
    is AppResult.PendingDelete -> this
}

fun <T> AppResult<T>.getOrNull(): T? = (this as? AppResult.Success)?.data

fun <T> AppResult<T>.getOrThrow(): T = when (this) {
    is AppResult.Success -> data
    is AppResult.Failure -> throw AppException(error)
    is AppResult.PendingDelete -> throw AppException(AppError.Io("Suppression en attente de confirmation"))
}

class AppException(val error: AppError) : Exception(error.message)
