package fr.photosorter.data.repository

import fr.photosorter.data.db.entity.ImageEntity
import fr.photosorter.data.db.entity.ImageWithLabels
import fr.photosorter.data.db.entity.LabelEntity
import fr.photosorter.domain.model.ADULT_LABEL
import fr.photosorter.domain.model.ImageFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MappersTest {

    private fun entity(format: String = "JPEG", isAdult: Boolean = false) = ImageEntity(
        id = 1, uri = "u/1", name = "a.jpg", format = format, sizeBytes = 42,
        dateTaken = 10, dateModified = 20, width = 3, height = 4,
        isAdult = isAdult, thumbnailHint = null
    )

    @Test fun mapsAllFields() {
        val item = entity().toDomain(setOf("x"))
        assertEquals(1L, item.id)
        assertEquals("a.jpg", item.name)
        assertEquals(ImageFormat.JPEG, item.format)
        assertEquals(42L, item.sizeBytes)
        assertEquals(10L, item.dateTaken)
        assertEquals(setOf("x"), item.labels)
    }

    @Test fun unknownFormatFallsBackToJpeg() {
        assertEquals(ImageFormat.JPEG, entity(format = "BOGUS").toDomain(emptySet()).format)
    }

    @Test fun isAdultTrueWhenLabelPresent() {
        val item = entity(isAdult = false).toDomain(setOf(ADULT_LABEL))
        assertTrue(item.isAdult)
    }

    @Test fun isAdultTrueWhenColumnTrue() {
        val item = entity(isAdult = true).toDomain(emptySet())
        assertTrue(item.isAdult)
    }

    @Test fun isAdultFalseWhenNeither() {
        val item = entity(isAdult = false).toDomain(setOf("x"))
        assertFalse(item.isAdult)
    }

    @Test fun imageWithLabelsCollectsLabelNames() {
        val withLabels = ImageWithLabels(
            image = entity(),
            labels = listOf(
                LabelEntity(name = "a", isSystem = false, createdAt = 0),
                LabelEntity(name = ADULT_LABEL, isSystem = true, createdAt = 0)
            )
        )
        val item = withLabels.toDomain()
        assertEquals(setOf("a", ADULT_LABEL), item.labels)
        assertTrue(item.isAdult)
    }

    @Test fun labelEntityToDomain() {
        val label = LabelEntity(name = "z", isSystem = true, createdAt = 5).toDomain()
        assertEquals("z", label.name)
        assertTrue(label.isSystem)
        assertEquals(5L, label.createdAt)
    }
}
