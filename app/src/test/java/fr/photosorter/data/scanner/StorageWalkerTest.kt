package fr.photosorter.data.scanner

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

@RunWith(RobolectricTestRunner::class)
class StorageWalkerTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val walker = StorageWalker(MimeTypeDetector())

    private fun touch(file: File): File {
        file.parentFile?.mkdirs()
        file.writeBytes(byteArrayOf(1, 2, 3))
        return file
    }

    private suspend fun walkAll(root: File, cancelled: AtomicBoolean = AtomicBoolean(false)): List<ScannedFile> {
        val out = mutableListOf<ScannedFile>()
        walker.walk(listOf(root), cancelled) { out.add(it) }
        return out
    }

    @Test
    fun collectsImagesRecursively() = runTest {
        val root = tmp.root
        touch(File(root, "a.jpg"))
        touch(File(root, "sub/b.png"))
        touch(File(root, "sub/deep/c.webp"))

        val names = walkAll(root).map { File(it.uri.path!!).name }.sorted()
        assertEquals(listOf("a.jpg", "b.png", "c.webp"), names)
    }

    @Test
    fun skipsNonImageExtensions() = runTest {
        val root = tmp.root
        touch(File(root, "video.mp4"))
        touch(File(root, "notes.txt"))
        touch(File(root, "ok.jpg"))

        val names = walkAll(root).map { File(it.uri.path!!).name }
        assertEquals(listOf("ok.jpg"), names)
    }

    @Test
    fun excludesSystemAndroidDirectories() = runTest {
        val root = tmp.root
        touch(File(root, "good.jpg"))
        val sys = File(root, "Android/data/com.app/files")
        touch(File(sys, "secret.jpg"))

        val names = walkAll(root).map { File(it.uri.path!!).name }
        assertTrue("good.jpg attendu", names.contains("good.jpg"))
        assertTrue("secret.jpg doit être exclu", !names.contains("secret.jpg"))
    }

    @Test
    fun stopsImmediatelyWhenAlreadyCancelled() = runTest {
        val root = tmp.root
        touch(File(root, "a.jpg"))
        val out = walkAll(root, cancelled = AtomicBoolean(true))
        assertTrue("aucun fichier si annulé avant", out.isEmpty())
    }

    @Test
    fun nonexistentRootIsIgnored() = runTest {
        val root = File(tmp.root, "nope")
        assertTrue(walkAll(root).isEmpty())
    }
}
