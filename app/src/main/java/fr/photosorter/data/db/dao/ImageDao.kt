package fr.photosorter.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import fr.photosorter.data.db.entity.ImageEntity
import fr.photosorter.data.db.entity.ImageWithLabels
import kotlinx.coroutines.flow.Flow

@Dao
interface ImageDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(images: List<ImageEntity>): List<Long>

    @Update
    suspend fun update(image: ImageEntity)

    @Query("DELETE FROM images")
    suspend fun clear()

    @Query("DELETE FROM images WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM images WHERE uri IN (:uris)")
    suspend fun deleteByUris(uris: List<String>)

    @Query("SELECT uri FROM images")
    suspend fun getAllUris(): List<String>

    @Query("SELECT * FROM images WHERE id = :id")
    suspend fun getById(id: Long): ImageEntity?

    @Transaction
    @Query("SELECT * FROM images ORDER BY name ASC")
    suspend fun getAllWithLabels(): List<ImageWithLabels>
}
