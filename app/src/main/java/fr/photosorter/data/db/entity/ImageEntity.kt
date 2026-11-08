package fr.photosorter.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "images",
    indices = [
        Index("format"),
        Index("sizeBytes"),
        Index("dateModified"),
        Index("isAdult")
    ]
)
data class ImageEntity(
    @PrimaryKey val id: Long,
    val uri: String,
    val name: String,
    val format: String,
    val sizeBytes: Long,
    val dateTaken: Long?,
    val dateModified: Long,
    val width: Int,
    val height: Int,
    val isAdult: Boolean,
    val thumbnailHint: String?
)
