package fr.photosorter.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import fr.photosorter.data.db.entity.LabelEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LabelDao {

    @Query("SELECT * FROM labels ORDER BY is_system DESC, name ASC")
    fun observeLabels(): Flow<List<LabelEntity>>

    @Query("SELECT * FROM labels ORDER BY is_system DESC, name ASC")
    suspend fun getLabels(): List<LabelEntity>

    @Query("SELECT * FROM labels WHERE name = :name")
    suspend fun getByName(name: String): LabelEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(label: LabelEntity)

    @Update
    suspend fun update(label: LabelEntity)

    /** Supprime un libellé non-système uniquement (cascade sur image_labels). */
    @Query("DELETE FROM labels WHERE name = :name AND is_system = 0")
    suspend fun deleteNonSystem(name: String): Int
}
