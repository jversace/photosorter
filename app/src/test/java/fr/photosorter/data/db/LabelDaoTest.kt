package fr.photosorter.data.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import fr.photosorter.data.db.entity.ImageEntity
import fr.photosorter.data.db.entity.ImageLabelEntity
import fr.photosorter.data.db.entity.LabelEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LabelDaoTest {

    private lateinit var db: AppDatabase

    @Before fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(), AppDatabase::class.java
        ).allowMainThreadQueries().build()
    }

    @After fun tearDown() = db.close()

    @Test fun cascadeDeleteLinksWhenImageDeleted() = runTest {
        db.imageDao().insertAll(listOf(
            ImageEntity(id = 1, uri = "u/1", name = "a.jpg", format = "JPEG", sizeBytes = 1,
                dateTaken = null, dateModified = 0, width = 1, height = 1, isAdult = false, thumbnailHint = null)
        ))
        db.labelDao().insert(LabelEntity(name = "x", isSystem = false, createdAt = 0))
        db.imageLabelDao().insertAll(listOf(ImageLabelEntity(imageId = 1, labelName = "x")))
        db.imageDao().clear()
        val rows = db.imageQueryDao().observeFiltered(
            sort = 0, asc = 1, includeAdult = 1, hasFormats = 0, formats = emptyList(),
            dateFrom = null, dateTo = null, minSize = null, hasLabels = 1, labels = listOf("x")
        ).let { kotlinx.coroutines.flow.first(it) }
        assertEquals(0, rows.size)
    }

    @Test fun deleteNonSystemLabelWorks() = runTest {
        db.labelDao().insert(LabelEntity(name = "temp", isSystem = false, createdAt = 0))
        val deleted = db.labelDao().deleteNonSystem("temp")
        assertEquals(1, deleted)
    }
}
