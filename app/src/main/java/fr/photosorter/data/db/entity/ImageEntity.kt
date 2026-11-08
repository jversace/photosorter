package fr.photosorter.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "images",
    indices = [
        Index("uri", unique = true),
        Index("format"),
        Index("date_modified"),
        Index("size_bytes")
    ]
)
data class ImageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "uri") val uri: String,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "format") val format: String,
    @ColumnInfo(name = "size_bytes") val sizeBytes: Long,
    @ColumnInfo(name = "date_taken") val dateTaken: Long?,
    @ColumnInfo(name = "date_modified") val dateModified: Long,
    @ColumnInfo(name = "width") val width: Int,
    @ColumnInfo(name = "height") val height: Int,
    @ColumnInfo(name = "is_adult") val isAdult: Boolean = false,
    @ColumnInfo(name = "thumbnail_hint") val thumbnailHint: String?
)
