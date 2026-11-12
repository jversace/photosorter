package fr.photosorter.data.moderation

import org.junit.Assert.assertEquals
import org.junit.Test

class PreFilterTest {

    private val preFilter = PreFilter()

    @Test fun screenshotPathIsSkipped() {
        assertEquals(
            PreFilterDecision.SKIP,
            preFilter.decide("/storage/emulated/0/Pictures/Screenshots/IMG_1.png", null)
        )
    }

    @Test fun screenshotInNameIsSkipped() {
        assertEquals(
            PreFilterDecision.SKIP,
            preFilter.decide("/storage/emulated/0/DCIM/x.png", "Screenshot_20240101.png")
        )
    }

    @Test fun documentPathIsSkipped() {
        assertEquals(
            PreFilterDecision.SKIP,
            preFilter.decide("/storage/emulated/0/Documents/facture.jpg", null)
        )
    }

    @Test fun scanPrefixIsSkipped() {
        assertEquals(
            PreFilterDecision.SKIP,
            preFilter.decide(null, "scan_2024.jpg")
        )
    }

    @Test fun normalPhotoIsAnalyzed() {
        assertEquals(
            PreFilterDecision.ANALYZE,
            preFilter.decide("/storage/emulated/0/DCIM/Camera/IMG_20240101.jpg", "IMG_20240101.jpg")
        )
    }

    @Test fun emptyInputIsAnalyzed() {
        assertEquals(PreFilterDecision.ANALYZE, preFilter.decide(null, null))
        assertEquals(PreFilterDecision.ANALYZE, preFilter.decide("", "  "))
    }

    @Test fun caseInsensitiveMatch() {
        assertEquals(
            PreFilterDecision.SKIP,
            preFilter.decide("/storage/SCREENSHOTS/A.PNG", "A.PNG")
        )
    }
}
