package fr.photosorter.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import fr.photosorter.data.db.dao.ImageDao
import fr.photosorter.data.db.dao.ImageLabelDao
import fr.photosorter.data.db.dao.ImageQueryDao
import fr.photosorter.data.db.dao.LabelDao
import fr.photosorter.data.db.entity.ImageEntity
import fr.photosorter.data.db.entity.ImageLabelEntity
import fr.photosorter.data.db.entity.LabelEntity
import fr.photosorter.data.db.entity.ScanStateEntity
import fr.photosorter.domain.model.ADULT_LABEL

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

    companion object {
        /**
         * Seed idempotent du label système « adulte ».
         * - onCreate : premier lancement (base absente)
         * - onOpen   : filet de sécurité pour les bases existantes (migration oubliée)
         * INSERT OR IGNORE → ne fait rien si le label est déjà présent.
         */
        val CALLBACK = object : Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                seedAdultLabel(db)
            }

            override fun onOpen(db: SupportSQLiteDatabase) {
                super.onOpen(db)
                seedAdultLabel(db)
            }

            private fun seedAdultLabel(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "INSERT OR IGNORE INTO labels (name, is_system, created_at) VALUES (?, 1, ?)",
                    arrayOf(ADULT_LABEL, System.currentTimeMillis())
                )
            }
        }
    }
}
