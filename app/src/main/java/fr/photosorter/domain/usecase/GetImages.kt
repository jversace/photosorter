package fr.photosorter.domain.usecase

import fr.photosorter.domain.model.FilterState
import fr.photosorter.domain.model.ImageItem
import fr.photosorter.domain.repository.ImageRepository
import javax.inject.Inject

/** Récupère la liste des images filtrées (retour ponctuel). */
class GetImages @Inject constructor(
    private val repository: ImageRepository
) {
    suspend operator fun invoke(filter: FilterState): List<ImageItem> =
        repository.getImages(filter)
}
