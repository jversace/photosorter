package fr.photosorter.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "labels")
data class LabelEntity(
    @PrimaryKey val name: String,
    val isSystem: Boolean,
    val createdAt: Long
)
