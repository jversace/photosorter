package fr.photosorter.data.repository

import fr.photosorter.core.dispatchers.DispatchersProvider
import fr.photosorter.core.result.AppError
import fr.photosorter.core.result.AppResult
import fr.photosorter.data.db.DatabaseSeeder
import fr.photosorter.data.db.dao.ImageLabelDao
import fr.photosorter.data.db.dao.LabelDao
import fr.photosorter.data.db.entity.ImageLabelEntity
import fr.photosorter.data.db.entity.LabelEntity
import fr.photosorter.domain.model.ADULT_LABEL
import fr.photosorter.domain.model.ImageItem
import fr.photosorter.domain.model.Label
import fr.photosorter.domain.repository.LabelRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implémentation [LabelRepository] :
 * - CRUD libellés via [LabelDao] (le libellé système « adulte » est protégé en SQL).
 * - Affectation/retrait via [ImageLabelDao] (table image_labels).
 *
 * Contrat DAO : image_labels(label_name) est une FK vers labels(name). Il faut donc
 * garantir l'existence du libellé (même nom exact/casse) AVANT tout insert dans image_labels,
 * sinon SQLite lève « FOREIGN KEY constraint failed ».
 */
@Singleton
class LabelRepositoryImpl @Inject constructor(
    private val labelDao: LabelDao,
    private val imageLabelDao: ImageLabelDao,
    private val dispatchers: DispatchersProvider
) : LabelRepository {

    override fun observeLabels(): Flow<List<Label>> =
        labelDao.observeLabels().map { labels -> labels.map { it.toDomain() } }

    override suspend fun createLabel(name: String): AppResult<Label> =
        withContext(dispatchers.io) {
            val trimmed = name.trim()
            if (trimmed.isEmpty()) return@withContext AppResult.Failure(AppError.Unsupported("Nom vide"))
            if (labelDao.getByName(trimmed) != null) {
                return@withContext AppResult.Failure(AppError.Unsupported("Libellé déjà existant"))
            }
            val entity = LabelEntity(name = trimmed, isSystem = false, createdAt = System.currentTimeMillis())
            labelDao.insert(entity)
            AppResult.Success(entity.toDomain())
        }

    override suspend fun renameLabel(oldName: String, newName: String): AppResult<Unit> =
        withContext(dispatchers.io) {
            val trimmed = newName.trim()
            if (trimmed.isEmpty()) return@withContext AppResult.Failure(AppError.Unsupported("Nom vide"))
            val existing = labelDao.getByName(oldName)
                ?: return@withContext AppResult.Failure(AppError.NotFound("Libellé introuvable"))
            if (existing.isSystem) {
                return@withContext AppResult.Failure(AppError.Unsupported("Libellé système non renommable"))
            }
            if (labelDao.getByName(trimmed) != null) {
                return@withContext AppResult.Failure(AppError.Unsupported("Nom déjà utilisé"))
            }
            labelDao.update(existing.copy(name = trimmed))
            AppResult.Success(Unit)
        }

    override suspend fun deleteLabel(name: String): AppResult<Unit> =
        withContext(dispatchers.io) {
            val deleted = labelDao.deleteNonSystem(name)
            if (deleted == 0) AppResult.Failure(AppError.Unsupported("Suppression refusée (système ou inexistant)"))
            else AppResult.Success(Unit)
        }

    override suspend fun assign(items: List<ImageItem>, label: String): AppResult<Unit> =
        withContext(dispatchers.io) {
            if (items.isEmpty()) return@withContext AppResult.Success(Unit)
            ensureLabelExists(label)
            val links = items.map { ImageLabelEntity(imageId = it.id, labelName = label) }
            imageLabelDao.insertAll(links)
            AppResult.Success(Unit)
        }

    override suspend fun unassign(items: List<ImageItem>, label: String): AppResult<Unit> =
        withContext(dispatchers.io) {
            if (items.isEmpty()) return@withContext AppResult.Success(Unit)
            imageLabelDao.unassign(items.map { it.id }, label)
            AppResult.Success(Unit)
        }

    /**
     * Garantit l'existence d'une ligne labels(name = [name]) pour satisfaire la FK de
     * image_labels(label_name). Idempotent : ne fait rien si le libellé existe déjà
     * (comparaison sur le nom exact, casse comprise). Pour le label système « adulte »
     * on réutilise l'entité dédiée du seeder (isSystem = true).
     */
    private suspend fun ensureLabelExists(name: String) {
        if (labelDao.getByName(name) != null) return
        val entity = if (name == ADULT_LABEL) {
            DatabaseSeeder.adultLabelEntity()
        } else {
            LabelEntity(name = name, isSystem = false, createdAt = System.currentTimeMillis())
        }
        labelDao.insert(entity)
    }
}
