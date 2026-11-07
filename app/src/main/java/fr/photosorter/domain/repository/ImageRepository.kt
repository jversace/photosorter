package fr.photosorter.domain.repository

import fr.photosorter.core.result.AppResult
import fr.photosorter.domain.model.FilterState
import fr.photosorter.domain.model.ImageItem
import fr.photosorter.domain.model.SortCriteria
import kotlinx.coroutines.flow.Flow

interface ImageRepository {
    fun observeImages(sort: SortCriteria, filter: FilterState): Flow<List<ImageItem>>
    suspend fun getImages(filter: FilterState): List<ImageItem>
    suspend fun delete(items: List<ImageItem>): AppResult<Unit>
    suspend fun move(items: List<ImageItem>, destUri: String): AppResult<Unit>
    suspend fun copy(items: List<ImageItem>, destUri: String): AppResult<Unit>
    suspend fun share(items: List<ImageItem>): AppResult<Unit>
}
