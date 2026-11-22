package fr.photosorter.data.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import fr.photosorter.data.db.dao.ImageDao
import fr.photosorter.data.db.dao.ImageLabelDao
import fr.photosorter.data.db.dao.ImageQueryDao
import fr.photosorter.data.db.entity.ImageEntity
import fr.photosorter.data.db.entity.ImageWithLabels
import fr.photosorter.data.scanner.ScanEngine
import fr.photosorter.domain.model.FilterState
import fr.photosorter.domain.model.ImageFormat
import fr.photosorter.domain.model.ScanProgress
import fr.photosorter.domain.model.SortCriteria
import fr.photosorter.domain.model.SortField
import fr.photosorter.testutil.TestDispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ImageRepositoryFlowTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val imageDao: ImageDao = mock()
    private val labelDao: ImageLabelDao = mock()
    private val queryDao: ImageQueryDao = mock()
    private val fileActions: FileActions = mock()

    private fun entity(id: Long) = ImageEntity(
        id = id, uri = "uri/$id", name = "n$id.jpg", format = ImageFormat.JPEG.name,
        sizeBytes = 1, dateTaken = null, dateModified = 0, width = 1, height = 1,
        thumbnailHint = null
    )

    private fun imageRepo() = ImageRepositoryImpl(context, imageDao, labelDao, queryDao, fileActions, TestDispatchers())

    @Test
    fun observeImagesEmitsMappedList() = runTest {
        whenever(
            queryDao.observeFiltered(any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
        ).thenReturn(flowOf(listOf(ImageWithLabels(entity(1), emptyList()))))

        imageRepo().observeImages(SortCriteria(SortField.NAME, true), FilterState()).test {
            val items = awaitItem()
            assertEquals(1, items.size)
            assertEquals(1L, items.first().id)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun scanProgressIsPropagated() = runTest {
        val engine: ScanEngine = mock()
        whenever(engine.progress).thenReturn(flowOf(ScanProgress.Completed(count = 3, durationMs = 5)))
        val scanRepo = ScanRepositoryImpl(engine, imageDao, TestDispatchers())

        scanRepo.progress.test {
            assertEquals(3, (awaitItem() as ScanProgress.Completed).count)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
