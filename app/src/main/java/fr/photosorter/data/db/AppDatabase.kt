package fr.photosorter.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import fr.photosorter.data.db.dao.ImageDao
import fr.photosorter.data.db.dao.ImageLabelDao
import fr.photosorter.data.db.dao.ImageQueryDao
import fr.photosorter.data.db.dao.LabelDao
import fr.photosorter.data.db.entity.ImageEntity
import fr.photosorter.data.db.entity.ImageLabelEntity
import fr.photosorter.data.db.entity.LabelEntity
import fr.photosorter.data.db.entity.ScanStateEntity

@Database(
    entities = [
        ImageEntity::class,
        LabelEntity::class,
        ImageLabelEntity::class,
        ScanStateEntity::class
    ],
    version = 1,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun imageDao(): ImageDao
    abstract fun labelDao(): LabelDao
    abstract fun imageLabelDao(): ImageLabelDao
    abstract fun imageQueryDao(): ImageQueryDao
}
