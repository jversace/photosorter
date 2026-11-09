package fr.photosorter.domain.usecase

import fr.photosorter.core.result.AppResult
import fr.photosorter.domain.model.ImageItem
import fr.photosorter.domain.repository.ImageRepository
import javax.inject.Inject

/** Déplace des images vers un dossier destination. */
class MoveImages @Inject constructor(
    private val repository: ImageRepository
) {
    suspend operator fun invoke(items: List<ImageItem>, destUri: String): AppResult<Unit> =
        repository.move(items, destUri)
}
