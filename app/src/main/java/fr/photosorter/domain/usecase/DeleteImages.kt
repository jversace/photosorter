package fr.photosorter.domain.usecase

import fr.photosorter.core.result.AppResult
import fr.photosorter.domain.model.ImageItem
import fr.photosorter.domain.repository.ImageRepository
import javax.inject.Inject

/** Supprime des images (avec confirmation gérée côté IHM). */
class DeleteImages @Inject constructor(
    private val repository: ImageRepository
) {
    suspend operator fun invoke(items: List<ImageItem>): AppResult<Unit> =
        repository.delete(items)
}
