package fr.photosorter.domain.usecase

import fr.photosorter.domain.model.ImageFormat
import fr.photosorter.domain.model.ImageItem
import fr.photosorter.domain.model.SortCriteria
import fr.photosorter.domain.model.SortField
import org.junit.Assert.assertEquals
import org.junit.Test

class SortImagesTest {

    private val useCase = SortImages()

    private fun img(name: String, size: Long, date: Long, format: ImageFormat = ImageFormat.JPEG, labels: Set<String> = emptySet()) =
        ImageItem(id = name.hashCode().toLong(), uri = "u/$name", name = name, format = format,
            sizeBytes = size, dateTaken = null, dateModified = date, width = 1, height = 1,
            labels = labels, isAdult = false, thumbnailHint = null)

    private val a = img("b.jpg", 300, 20)
    private val b = img("a.jpg", 100, 30)
    private val c = img("c.png", 200, 10, ImageFormat.PNG, setOf("z"))
    private val list = listOf(a, b, c)

    @Test fun byNameAscIsCaseInsensitive() {
        val sorted = useCase(list, SortCriteria(SortField.NAME, ascending = true))
        assertEquals(listOf("a.jpg", "b.jpg", "c.png"), sorted.map { it.name })
    }

    @Test fun byNameDesc() {
        val sorted = useCase(list, SortCriteria(SortField.NAME, ascending = false))
        assertEquals(listOf("c.png", "b.jpg", "a.jpg"), sorted.map { it.name })
    }

    @Test fun bySizeAsc() {
        val sorted = useCase(list, SortCriteria(SortField.SIZE, ascending = true))
        assertEquals(listOf(100L, 200L, 300L), sorted.map { it.sizeBytes })
    }

    @Test fun byDateAsc() {
        val sorted = useCase(list, SortCriteria(SortField.DATE, ascending = true))
        assertEquals(listOf(10L, 20L, 30L), sorted.map { it.dateModified })
    }

    @Test fun byFormatGroupsAndNameWithin() {
        val sorted = useCase(list, SortCriteria(SortField.FORMAT, ascending = true))
        // JPEG (a,b) avant PNG (c)
        assertEquals(listOf("b.jpg", "a.jpg", "c.png"), sorted.map { it.name })
    }

    @Test fun byLabelUsesFirstSortedLabel() {
        val sorted = useCase(list, SortCriteria(SortField.LABEL, ascending = true))
        // Les images sans label ("") passent avant celles avec "z"
        assertEquals("c.png", sorted.last().name)
    }
}
