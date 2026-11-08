package fr.photosorter.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import fr.photosorter.data.db.entity.ImageLabelEntity

@Dao
interface ImageLabelDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(links: List<ImageLabelEntity>)

    @Query("DELETE FROM image_labels WHERE image_id IN (:imageIds) AND label_name = :label")
    suspend fun unassign(imageIds: List<Long>, label: String)

    @Query("DELETE FROM image_labels WHERE image_id = :imageId")
    suspend fun clearForImage(imageId: Long)
}
