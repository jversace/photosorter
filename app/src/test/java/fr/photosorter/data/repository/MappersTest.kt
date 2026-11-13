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

    private fun entity(
        format: String = "JPEG",
        isAdult: Boolean = false
    ) = ImageEntity(
        id = 1, uri = "uri/1", name = "a.jpg", format = format, sizeBytes = 100,
        dateTaken = null, dateModified = 10, width = 2, height = 3,
        isAdult = isAdult, thumbnailHint = null
    )

    @Test
    fun domainMappingCopiesFields() {
        val item = entity().toDomain(emptySet())
        assertEquals(1L, item.id)
        assertEquals("uri/1", item.uri)
        assertEquals("a.jpg", item.name)
        assertEquals(ImageFormat.JPEG, item.format)
        assertEquals(100L, item.sizeBytes)
        assertEquals(2, item.width)
        assertEquals(3, item.height)
        assertFalse(item.isAdult)
    }

    @Test
    fun unknownFormatFallsBackToJpeg() {
        val item = entity(format = "XYZ").toDomain(emptySet())
        assertEquals(ImageFormat.JPEG, item.format)
    }

    @Test
    fun adultFlagFromEntityOrFromLabel() {
        assertTrue(entity(isAdult = true).toDomain(emptySet()).isAdult)
        assertTrue(entity(isAdult = false).toDomain(setOf(ADULT_LABEL)).isAdult)
        assertFalse(entity(isAdult = false).toDomain(setOf("famille")).isAdult)
    }

    @Test
    fun withLabelsMappingCollectsLabelNames() {
        val withLabels = ImageWithLabels(
            image = entity(isAdult = false),
            labels = listOf(
                LabelEntity(name = "famille", isSystem = false, createdAt = 0),
                LabelEntity(name = ADULT_LABEL, isSystem = true, createdAt = 0)
            )
        )
        val item = withLabels.toDomain()
        assertEquals(setOf("famille", ADULT_LABEL), item.labels)
        assertTrue(item.isAdult)
    }

    @Test
    fun labelEntityMapping() {
        val label = LabelEntity(name = "x", isSystem = true, createdAt = 42).toDomain()
        assertEquals("x", label.name)
        assertTrue(label.isSystem)
        assertEquals(42L, label.createdAt)
    }
}
