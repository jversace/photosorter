package fr.photosorter.domain.repository

import fr.photosorter.core.result.AppResult
import fr.photosorter.domain.model.FilterState
import fr.photosorter.domain.model.ImageItem
import fr.photosorter.domain.model.SortCriteria
import kotlinx.coroutines.flow.Flow

interface ImageRepository {
    fun observeImages(sort: SortCriteria, filter: FilterState): Flow<List<ImageItem>>
    suspend fun getImages(filter: FilterState): List<ImageItem>

    /**
     * Supprime les items. Peut renvoyer [AppResult.PendingDelete] si le scoped storage exige une
     * confirmation utilisateur : dans ce cas la DB n'est PAS nettoyée ; l'IHM lance l'IntentSender
     * puis rappelle [deleteConfirmed] avec les URIs concernées.
     */
    suspend fun delete(items: List<ImageItem>): AppResult<Unit>

    /**
     * Finalise une suppression confirmée par l'utilisateur : exécute la suppression effective sur
     * les URIs MediaStore puis nettoie la base.
     */
    suspend fun deleteConfirmed(uris: List<String>): AppResult<Unit>

    suspend fun move(items: List<ImageItem>, destUri: String): AppResult<Unit>
    suspend fun copy(items: List<ImageItem>, destUri: String): AppResult<Unit>
    suspend fun share(items: List<ImageItem>): AppResult<Unit>
    suspend fun moveConfirmed(uris: List<String>): AppResult<Unit>
}
