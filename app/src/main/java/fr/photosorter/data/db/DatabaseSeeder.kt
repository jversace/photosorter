package fr.photosorter.data.db

import fr.photosorter.data.db.entity.LabelEntity
import fr.photosorter.domain.model.ADULT_LABEL

/** Création/garantie du label système « adulte » (isSystem=true, non supprimable). */
object DatabaseSeeder {

    fun adultLabelEntity(now: Long = System.currentTimeMillis()): LabelEntity =
        LabelEntity(name = ADULT_LABEL, isSystem = true, createdAt = now)
}
