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

    /**
     * Réconciliation idempotente du libellé système 'adulte' : insère un lien
     * (image_id, label_name) pour TOUTES les images marquées is_adult = 1 qui n'en ont
     * pas encore, en ignorant les liens déjà présents (INSERT OR IGNORE).
     *
     * Corrige la perte de liens provoquée par ImageDao.insertAll en REPLACE + FK CASCADE :
     * après un re-scan, les images adultes issues d'un scan antérieur doivent voir leur
     * lien recréé même si elles ne font pas partie du lot courant (bug c7681760).
     *
     * Room exige Long (ou Unit) pour un INSERT ... SELECT, pas Int (bug 768b171a).
     *
     * @return le nombre de liens effectivement insérés (0 si tout était déjà réconcilié).
     */
    @Query(
        "INSERT OR IGNORE INTO image_labels (image_id, label_name) " +
            "SELECT id, :adultLabel FROM images WHERE is_adult = 1"
    )
    suspend fun insertMissingAdultLabels(adultLabel: String): Long
}
