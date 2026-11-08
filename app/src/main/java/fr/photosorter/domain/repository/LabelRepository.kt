package fr.photosorter.domain.repository

import fr.photosorter.core.result.AppResult
import fr.photosorter.domain.model.ImageItem
import fr.photosorter.domain.model.Label
import kotlinx.coroutines.flow.Flow

interface LabelRepository {
    fun observeLabels(): Flow<List<Label>>
    suspend fun createLabel(name: String): AppResult<Label>
    suspend fun renameLabel(oldName: String, newName: String): AppResult<Unit>
    suspend fun deleteLabel(name: String): AppResult<Unit>
    suspend fun assign(items: List<ImageItem>, label: String): AppResult<Unit>
    suspend fun unassign(items: List<ImageItem>, label: String): AppResult<Unit>
}
