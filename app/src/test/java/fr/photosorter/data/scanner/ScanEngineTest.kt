package fr.photosorter.data.scanner

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import fr.photosorter.data.db.dao.ImageDao
import fr.photosorter.data.moderation.AdultClassifier
import fr.photosorter.data.moderation.PreFilter
import fr.photosorter.data.moderation.PreFilterDecision
import fr.photosorter.domain.model.ScanProgress
import fr.photosorter.testutil.TestDispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class ScanEngineTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val imageDao: ImageDao = mock()
    private val storageRootProvider: StorageRootProvider = mock()
    private val preFilter: PreFilter = mock()
    private val adultClassifier: AdultClassifier = mock()

    private fun engine(): ScanEngine {
        whenever(preFilter.decide(any(), any())).thenReturn(PreFilterDecision.SKIP)
        return ScanEngine(
            context = context,
            dispatchers = TestDispatchers(),
            imageDao = imageDao,
            mimeDetector = MimeTypeDetector(),
            storageRootProvider = storageRootProvider,
            metadataReaderFactory = { ctx -> MediaMetadataReader(ctx.contentResolver) },
            preFilter = preFilter,
            adultClassifier = adultClassifier
        )
    }

    private fun touch(image: File): File {
        image.parentFile?.mkdirs()
        image.writeBytes(ByteArray(16) { 1 })
        return image
    }

    @Test
    fun emitsRunningThenCompletedAndWritesBatch() = runTest {
        touch(File(tmp.root, "a.jpg"))
        touch(File(tmp.root, "b.png"))
        whenever(storageRootProvider.roots()).thenReturn(listOf(tmp.root))
        whenever(imageDao.insertAll(any())).thenReturn(listOf(1L, 2L))

        val engine = engine()

        engine.progress.test {
            assertEquals(ScanProgress.Idle, awaitItem())
            val finalState = engine.scan()
            assertTrue("état final = Completed", finalState is ScanProgress.Completed)
            assertEquals(2, (finalState as ScanProgress.Completed).count)
            cancelAndIgnoreRemainingEvents()
        }

        verify(adultClassifier).ensureInitialized()
        verify(imageDao).insertAll(any())
    }

    @Test
    fun emptyRootCompletesWithZero() = runTest {
        whenever(storageRootProvider.roots()).thenReturn(listOf(tmp.root))
        whenever(imageDao.insertAll(any())).thenReturn(emptyList())
        val engine = engine()
        val result = engine.scan()
        assertTrue(result is ScanProgress.Completed)
        assertEquals(0, (result as ScanProgress.Completed).count)
    }

    @Test
    fun preFilterSkipMeansNotAdult() = runTest {
        touch(File(tmp.root, "Screenshot_1.jpg"))
        whenever(storageRootProvider.roots()).thenReturn(listOf(tmp.root))
        whenever(imageDao.insertAll(any())).thenReturn(listOf(1L))
        whenever(preFilter.decide(any(), any())).thenReturn(PreFilterDecision.SKIP)

        val engine = engine()
        val result = engine.scan()
        assertEquals(1, (result as ScanProgress.Completed).count)
        // Le classifieur IA ne doit jamais être sollicité pour un screenshot.
        verify(adultClassifier, org.mockito.kotlin.never()).classify(any())
    }
}
