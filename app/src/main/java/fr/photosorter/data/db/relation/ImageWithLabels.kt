package fr.photosorter.data.db.relation

import androidx.room.Embedded
import androidx.room.Junction
import androidx.room.Relation
import fr.photosorter.data.db.entity.ImageEntity
import fr.photosorter.data.db.entity.ImageLabelCrossRef
import fr.photosorter.data.db.entity.LabelEntity

data class ImageWithLabels(
    @Embedded val image: ImageEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "name",
        associateBy = Junction(ImageLabelCrossRef::class, parentColumn = "imageId", entityColumn = "labelName")
    )
    val labels: List<LabelEntity>
)
