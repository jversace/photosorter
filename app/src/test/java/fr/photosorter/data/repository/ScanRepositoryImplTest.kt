package fr.photosorter.data.repository

import fr.photosorter.data.db.dao.ImageDao
import fr.photosorter.data.db.entity.ImageEntity
import fr.photosorter.data.db.entity.ImageWithLabels
import fr.photosorter.data.scanner.ScanEngine
import fr.photosorter.domain.model.ImageFormat
import fr.photosorter.domain.model.ScanProgress
import fr.photosorter.testutil.TestDispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class ScanRepositoryImplTest {

    private val scanEngine: ScanEngine = mock()
    private val imageDao: ImageDao = mock()
    private val repo = ScanRepositoryImpl(scanEngine, imageDao, TestDispatchers())

    private fun entity(id: Long) = ImageEntity(
        id = id, uri = "uri/$id", name = "n$id.jpg", format = ImageFormat.JPEG.name,
        sizeBytes = 1, dateTaken = null, dateModified = 0, width = 1, height = 1,
        thumbnailHint = null
    )

    @Test
    fun progressDelegatesToEngine() {
        whenever(scanEngine.progress).thenReturn(flowOf(ScanProgress.Running(1, 10, "a.jpg")))
        // construction déjà faite avant ce stub : on reconstruit pour refléter la délégation
        val r = ScanRepositoryImpl(scanEngine, imageDao, TestDispatchers())
        assertEquals(scanEngine.progress, r.progress)
    }

    @Test
    fun launchScanReturnsCachedImagesOnSuccess() = runTest {
        whenever(scanEngine.scan()).thenReturn(ScanProgress.Completed(count = 1, durationMs = 10))
        whenever(imageDao.getAllWithLabels()).thenReturn(
            listOf(ImageWithLabels(entity(1), emptyList()))
        )

        val flow = repo.launchScan()
        var list: List<fr.photosorter.domain.model.ImageItem>? = null
        flow.collect { list = it }

        assertEquals(1, list?.size)
        assertEquals(1L, list?.first()?.id)
    }

    @Test
    fun launchScanThrowsOnEngineError() = runTest {
        whenever(scanEngine.scan()).thenReturn(ScanProgress.Error("boom"))
        assertThrows(IllegalStateException::class.java) {
            kotlinx.coroutines.runBlocking { repo.launchScan() }
        }
    }
}
