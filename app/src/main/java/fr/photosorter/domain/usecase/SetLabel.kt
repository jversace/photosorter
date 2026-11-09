package fr.photosorter.domain.usecase

import fr.photosorter.core.result.AppResult
import fr.photosorter.domain.model.ImageItem
import fr.photosorter.domain.repository.LabelRepository
import javax.inject.Inject

/** Affecte un libellé à une sélection d'images. */
class SetLabel @Inject constructor(
    private val repository: LabelRepository
) {
    suspend operator fun invoke(items: List<ImageItem>, label: String): AppResult<Unit> =
        repository.assign(items, label)
}
