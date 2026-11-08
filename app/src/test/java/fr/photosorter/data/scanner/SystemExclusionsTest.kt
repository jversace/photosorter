package fr.photosorter.data.scanner

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SystemExclusionsTest {

    @Test fun excludesAndroidData() {
        assertTrue(SystemExclusions.isExcluded("android/data/com.foo/files/x.jpg"))
    }

    @Test fun excludesAndroidObb() {
        assertTrue(SystemExclusions.isExcluded("android/obb/com.foo/x.obb"))
    }

    @Test fun keepsNormalPhotos() {
        assertFalse(SystemExclusions.isExcluded("dcim/camera/photo.jpg"))
        assertFalse(SystemExclusions.isExcluded("pictures/foo/bar.png"))
    }

    @Test fun normalizeSlashes() {
        assertTrue(SystemExclusions.isExcluded(SystemExclusions.normalize("/Android\\Data\\com.foo/")))
    }
}
