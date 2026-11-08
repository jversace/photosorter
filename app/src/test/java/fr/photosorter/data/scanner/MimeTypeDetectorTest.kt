package fr.photosorter.data.scanner

import fr.photosorter.domain.model.ImageFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MimeTypeDetectorTest {

    private val detector = MimeTypeDetector()

    @Test fun detectsByExtension() {
        assertEquals(ImageFormat.JPEG, detector.detectFormat("photo.JPG", null))
        assertEquals(ImageFormat.PNG, detector.detectFormat("a.png", null))
        assertEquals(ImageFormat.HEIC, detector.detectFormat("b.heic", "image/heic"))
        assertEquals(ImageFormat.RAW, detector.detectFormat("c.dng", null))
    }

    @Test fun fallsBackToMime() {
        assertEquals(ImageFormat.WEBP, detector.detectFormat("file.bin", "image/webp"))
    }

    @Test fun unknownReturnsNull() {
        assertNull(detector.detectFormat("movie.mp4", "video/mp4"))
    }
}
