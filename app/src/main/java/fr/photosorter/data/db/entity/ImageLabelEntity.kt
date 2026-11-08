package fr.photosorter.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "image_labels",
    primaryKeys = ["image_id", "label_name"],
    foreignKeys = [
        ForeignKey(
            entity = ImageEntity::class,
            parentColumns = ["id"],
            childColumns = ["image_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = LabelEntity::class,
            parentColumns = ["name"],
            childColumns = ["label_name"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("image_id"), Index("label_name")]
)
data class ImageLabelEntity(
    @ColumnInfo(name = "image_id") val imageId: Long,
    @ColumnInfo(name = "label_name") val labelName: String
)
