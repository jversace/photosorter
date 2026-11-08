package fr.photosorter.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/** Association N-N entre images et libellés. */
@Entity(
    tableName = "image_label_cross_ref",
    primaryKeys = ["imageId", "labelName"],
    foreignKeys = [
        ForeignKey(
            entity = ImageEntity::class,
            parentColumns = ["id"],
            childColumns = ["imageId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = LabelEntity::class,
            parentColumns = ["name"],
            childColumns = ["labelName"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("labelName")]
)
data class ImageLabelCrossRef(
    val imageId: Long,
    val labelName: String
)
