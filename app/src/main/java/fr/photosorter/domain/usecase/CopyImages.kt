package fr.photosorter.domain.usecase

import fr.photosorter.core.result.AppResult
import fr.photosorter.domain.model.ImageItem
import fr.photosorter.domain.repository.ImageRepository
import javax.inject.Inject

/** Copie des images vers un dossier destination. */
class CopyImages @Inject constructor(
    private val repository: ImageRepository
) {
    suspend operator fun invoke(items: List<ImageItem>, destUri: String): AppResult<Unit> =
        repository.copy(items, destUri)
}
