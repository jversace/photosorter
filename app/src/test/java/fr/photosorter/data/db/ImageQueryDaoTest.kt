package fr.photosorter.data.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import fr.photosorter.data.db.entity.ImageEntity
import fr.photosorter.data.db.entity.ImageLabelEntity
import fr.photosorter.data.db.entity.LabelEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ImageQueryDaoTest {

    private lateinit var db: AppDatabase

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() = db.close()

    private fun img(id: Long, name: String, format: String, size: Long, date: Long, adult: Boolean = false) =
        ImageEntity(id = id, uri = "uri/$id", name = name, format = format, sizeBytes = size,
            dateTaken = null, dateModified = date, width = 1, height = 1, isAdult = adult, thumbnailHint = null)

    @Test
    fun orderByNameAsc() = runTest {
        db.imageDao().insertAll(listOf(
            img(1, "b.jpg", "JPEG", 100, 10),
            img(2, "a.jpg", "JPEG", 200, 20)
        ))
        val rows = db.imageQueryDao().observeFiltered(
            sort = 0, asc = 1, includeAdult = 1, hasFormats = 0, formats = emptyList(),
            dateFrom = null, dateTo = null, minSize = null, hasLabels = 0, labels = emptyList()
        ).first()
        assertEquals(listOf("a.jpg", "b.jpg"), rows.map { it.image.name })
    }

    @Test
    fun orderBySizeDesc() = runTest {
        db.imageDao().insertAll(listOf(
            img(1, "small.jpg", "JPEG", 100, 10),
            img(2, "big.jpg", "JPEG", 900, 20)
        ))
        val rows = db.imageQueryDao().observeFiltered(
            sort = 2, asc = 0, includeAdult = 1, hasFormats = 0, formats = emptyList(),
            dateFrom = null, dateTo = null, minSize = null, hasLabels = 0, labels = emptyList()
        ).first()
        assertEquals(listOf("big.jpg", "small.jpg"), rows.map { it.image.name })
    }

    @Test
    fun filterByFormatAndMinSize() = runTest {
        db.imageDao().insertAll(listOf(
            img(1, "a.png", "PNG", 900, 10),
            img(2, "b.png", "PNG", 100, 10),
            img(3, "c.jpg", "JPEG", 900, 10)
        ))
        val rows = db.imageQueryDao().observeFiltered(
            sort = 0, asc = 1, includeAdult = 1, hasFormats = 1, formats = listOf("PNG"),
            dateFrom = null, dateTo = null, minSize = 500, hasLabels = 0, labels = emptyList()
        ).first()
        assertEquals(listOf("a.png"), rows.map { it.image.name })
    }

    @Test
    fun excludesAdultWhenRequested() = runTest {
        db.imageDao().insertAll(listOf(
            img(1, "ok.jpg", "JPEG", 100, 10, adult = false),
            img(2, "nsfw.jpg", "JPEG", 100, 10, adult = true)
        ))
        val rows = db.imageQueryDao().observeFiltered(
            sort = 0, asc = 1, includeAdult = 0, hasFormats = 0, formats = emptyList(),
            dateFrom = null, dateTo = null, minSize = null, hasLabels = 0, labels = emptyList()
        ).first()
        assertEquals(listOf("ok.jpg"), rows.map { it.image.name })
    }

    @Test
    fun filterByLabel() = runTest {
        db.imageDao().insertAll(listOf(
            img(1, "a.jpg", "JPEG", 100, 10),
            img(2, "b.jpg", "JPEG", 100, 10)
        ))
        db.labelDao().insert(LabelEntity(name = "vacances", isSystem = false, createdAt = 0))
        db.imageLabelDao().insertAll(listOf(ImageLabelEntity(imageId = 1, labelName = "vacances")))
        val rows = db.imageQueryDao().observeFiltered(
            sort = 0, asc = 1, includeAdult = 1, hasFormats = 0, formats = emptyList(),
            dateFrom = null, dateTo = null, minSize = null, hasLabels = 1, labels = listOf("vacances")
        ).first()
        assertEquals(listOf("a.jpg"), rows.map { it.image.name })
        assertEquals(listOf("vacances"), rows.first().labels.map { it.name })
    }

    @Test
    fun adultLabelIsNotDeletable() = runTest {
        db.labelDao().insert(DatabaseSeeder.adultLabelEntity(now = 0))
        val deleted = db.labelDao().deleteNonSystem("adulte")
        assertEquals(0, deleted)
        assertTrue(db.labelDao().getByName("adulte")!!.isSystem)
    }
}
