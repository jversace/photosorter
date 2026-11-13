package fr.photosorter.data.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import fr.photosorter.core.result.AppResult
import fr.photosorter.data.db.dao.ImageDao
import fr.photosorter.data.db.entity.ImageEntity
import fr.photosorter.data.repository.LabelRepositoryImpl
import fr.photosorter.domain.model.ADULT_LABEL
import fr.photosorter.domain.model.ImageFormat
import fr.photosorter.domain.model.ImageItem
import fr.photosorter.testutil.TestDispatchers
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LabelRepositoryImplTest {

    private lateinit var db: AppDatabase
    private lateinit var repo: LabelRepositoryImpl

    @Before fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(), AppDatabase::class.java
        ).allowMainThreadQueries().build()
        db.labelDao().insert(
            fr.photosorter.data.db.entity.LabelEntity(ADULT_LABEL, isSystem = true, createdAt = 0)
        )
        repo = LabelRepositoryImpl(db.labelDao(), db.imageLabelDao(), TestDispatchers())
    }

    @After fun tearDown() = db.close()

    private suspend fun seedImage(): Long =
        db.imageDao().insertAll(
            listOf(
                ImageEntity(
                    uri = "uri/1", name = "a.jpg", format = ImageFormat.JPEG.name,
                    sizeBytes = 1, dateTaken = null, dateModified = 0, width = 1, height = 1,
                    thumbnailHint = null
                )
            )
        ).first()

    private fun imageItem(id: Long) = ImageItem(
        id = id, uri = "uri/$id", name = "a.jpg", format = ImageFormat.JPEG,
        sizeBytes = 1, dateTaken = null, dateModified = 0, width = 1, height = 1,
        labels = emptySet(), isAdult = false, thumbnailHint = null
    )

    @Test fun createLabelSucceeds() = runTest {
        val result = repo.createLabel("  famille  ")
        assertTrue(result is AppResult.Success)
        assertEquals("famille", (result as AppResult.Success).data.name)
    }

    @Test fun createEmptyLabelFails() = runTest {
        assertTrue(repo.createLabel("   ") is AppResult.Failure)
    }

    @Test fun createDuplicateLabelFails() = runTest {
        repo.createLabel("famille")
        assertTrue(repo.createLabel("famille") is AppResult.Failure)
    }

    @Test fun cannotDeleteSystemLabel() = runTest {
        assertTrue(repo.deleteLabel(ADULT_LABEL) is AppResult.Failure)
    }

    @Test fun renameAndDeleteCustomLabel() = runTest {
        repo.createLabel("tmp")
        assertTrue(repo.renameLabel("tmp", "travail") is AppResult.Success)
        assertTrue(repo.deleteLabel("travail") is AppResult.Success)
    }

    @Test fun assignThenUnassign() = runTest {
        repo.createLabel("famille")
        val id = seedImage()
        assertTrue(repo.assign(listOf(imageItem(id)), "famille") is AppResult.Success)
        assertTrue(repo.unassign(listOf(imageItem(id)), "famille") is AppResult.Success)
    }
}
