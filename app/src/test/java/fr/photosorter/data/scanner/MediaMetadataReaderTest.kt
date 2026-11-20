package fr.photosorter.data.scanner

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
class MediaMetadataReaderTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val reader = MediaMetadataReader(context.contentResolver)

    @Test
    @Config(sdk = [29])
    fun readsNameSizeAndDateFromFileUri() {
        val file = File(tmp.root, "photo.jpg")
        file.writeBytes(ByteArray(512) { 1 })
        file.setLastModified(1_600_000_000_000L)
        val meta = reader.read(Uri.fromFile(file))
        assertNotNull(meta)
        assertEquals("photo.jpg", meta!!.name)
        assertEquals(512L, meta.sizeBytes)
        assertEquals(1_600_000_000_000L, meta.dateModified)
    }

    @Test
    @Config(sdk = [29])
    fun existsAgainAfterReading() {
        val file = File(tmp.root, "a.png")
        file.writeBytes(ByteArray(10))
        assertNotNull(reader.read(Uri.fromFile(file)))
        assertNotNull(reader.read(Uri.fromFile(file)))
    }

    @Test
    fun returnsNullForMissingFile() {
        val file = File(tmp.root, "absent.jpg")
        assertNull(reader.read(Uri.fromFile(file)))
    }
}
