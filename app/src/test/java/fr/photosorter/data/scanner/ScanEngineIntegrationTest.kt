package fr.photosorter.data.scanner

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import fr.photosorter.core.dispatchers.DispatchersProvider
import fr.photosorter.data.db.AppDatabase
import fr.photosorter.data.moderation.AdultClassifier
import fr.photosorter.data.moderation.PreFilter
import fr.photosorter.domain.model.ScanProgress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class ScanEngineIntegrationTest {

    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var tempDir: File

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries().build()
        tempDir = File(context.cacheDir, "scan-it").apply { mkdirs() }
    }

    @After
    fun tearDown() {
        db.close()
        tempDir.deleteRecursively()
    }

    private class FakeDispatchers : DispatchersProvider {
        override val io = Dispatchers.Unconfined
        override val main = Dispatchers.Unconfined
        override val default = Dispatchers.Unconfined
    }

    @Test
    fun scanFindsAndInsertsImagesFromFileUris() = runTest {
        File(tempDir, "photo1.jpg").writeBytes(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 1, 2, 3))
        File(tempDir, "photo2.png").writeBytes(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 1, 2))

        val engine = ScanEngine(
            context = context,
            dispatchers = FakeDispatchers(),
            imageDao = db.imageDao(),
            mimeDetector = MimeTypeDetector(),
            storageRootProvider = { listOf(tempDir) },
            metadataReaderFactory = { MediaMetadataReader(it) },
            preFilter = PreFilter(),
            adultClassifier = AdultClassifier(context, FakeDispatchers())
        )

        val result = engine.scan()

        assertTrue("attendu Completed, obtenu $result", result is ScanProgress.Completed)
        val completed = result as ScanProgress.Completed
        assertTrue("count attendu > 0, obtenu ${completed.count}", completed.count > 0)
        assertTrue("images persistées attendues > 0", db.imageDao().getAllWithLabels().isNotEmpty())
    }
}
