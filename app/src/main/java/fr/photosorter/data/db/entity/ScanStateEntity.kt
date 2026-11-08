package fr.photosorter.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/** État du dernier scan (cache du résultat). Ligne unique id=1. */
@Entity(tableName = "scan_state")
data class ScanStateEntity(
    @PrimaryKey @ColumnInfo(name = "id") val id: Int = SINGLETON_ID,
    @ColumnInfo(name = "last_scan_at") val lastScanAt: Long?,
    @ColumnInfo(name = "image_count") val imageCount: Int,
    @ColumnInfo(name = "duration_ms") val durationMs: Long
) {
    companion object {
        const val SINGLETON_ID = 1
    }
}
