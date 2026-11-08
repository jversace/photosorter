package fr.photosorter.data.db.entity

import androidx.room.Embedded
import androidx.room.Junction
import androidx.room.Relation

/** Image + ses libellés (collection). */
data class ImageWithLabels(
    @Embedded val image: ImageEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "name",
        associateBy = Junction(ImageLabelEntity::class, parentColumn = "image_id", entityColumn = "label_name")
    )
    val labels: List<LabelEntity>
)
