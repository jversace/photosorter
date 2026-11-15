package fr.photosorter.data.moderation

import org.junit.Assert.assertEquals
import org.junit.Test

class PreFilterTest {

    private val preFilter = PreFilter()

    @Test
    fun screenshotIsSkipped() {
        assertEquals(
            PreFilterDecision.SKIP,
            preFilter.decide("/storage/emulated/0/DCIM/Screenshots/img.png", "img.png")
        )
    }

    @Test
    fun documentIsSkipped() {
        assertEquals(
            PreFilterDecision.SKIP,
            preFilter.decide("/storage/emulated/0/Documents/scan_001.jpg", "scan_001.jpg")
        )
    }

    @Test
    fun invoiceHintIsSkipped() {
        assertEquals(
            PreFilterDecision.SKIP,
            preFilter.decide("/x/facture.jpg", "facture.jpg")
        )
    }

    @Test
    fun regularPhotoIsAnalyzed() {
        assertEquals(
            PreFilterDecision.ANALYZE,
            preFilter.decide("/storage/emulated/0/DCIM/Camera/IMG_0001.jpg", "IMG_0001.jpg")
        )
    }

    @Test
    fun blankInputIsAnalyzed() {
        assertEquals(PreFilterDecision.ANALYZE, preFilter.decide(null, null))
        assertEquals(PreFilterDecision.ANALYZE, preFilter.decide("  ", ""))
    }

    @Test
    fun caseInsensitive() {
        assertEquals(
            PreFilterDecision.SKIP,
            preFilter.decide("/Screenshots/A.PNG", "SCREENSHOT_1.PNG")
        )
    }
}
