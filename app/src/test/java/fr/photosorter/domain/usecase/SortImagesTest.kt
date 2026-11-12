package fr.photosorter.domain.usecase

import fr.photosorter.domain.model.ImageFormat
import fr.photosorter.domain.model.ImageItem
import fr.photosorter.domain.model.SortCriteria
import fr.photosorter.domain.model.SortField
import org.junit.Assert.assertEquals
import org.junit.Test

class SortImagesTest {

    private val sort = SortImages()

    private fun item(
        id: Long,
        name: String,
        format: ImageFormat = ImageFormat.JPEG,
        size: Long = 100,
        date: Long = 0,
        labels: Set<String> = emptySet()
    ) = ImageItem(
        id = id, uri = "uri/$id", name = name, format = format, sizeBytes = size,
        dateTaken = null, dateModified = date, width = 1, height = 1,
        labels = labels, isAdult = false, thumbnailHint = null
    )

    private val a = item(1, "b.jpg", ImageFormat.PNG, size = 300, date = 20, labels = setOf("zeta"))
    private val b = item(2, "A.jpg", ImageFormat.JPEG, size = 100, date = 10, labels = setOf("alpha"))
    private val c = item(3, "c.jpg", ImageFormat.JPEG, size = 200, date = 30, labels = emptySet())

    private fun ids(items: List<ImageItem>) = items.map { it.id }

    @Test
    fun byNameAscendingIsCaseInsensitive() {
        val result = sort(listOf(a, b, c), SortCriteria(SortField.NAME, ascending = true))
        assertEquals(listOf(2L, 1L, 3L), ids(result)) // A.jpg, b.jpg, c.jpg
    }

    @Test
    fun byNameDescending() {
        val result = sort(listOf(a, b, c), SortCriteria(SortField.NAME, ascending = false))
        assertEquals(listOf(3L, 1L, 2L), ids(result))
    }

    @Test
    fun bySizeAscending() {
        val result = sort(listOf(a, b, c), SortCriteria(SortField.SIZE, ascending = true))
        assertEquals(listOf(2L, 3L, 1L), ids(result))
    }

    @Test
    fun byDateDescending() {
        val result = sort(listOf(a, b, c), SortCriteria(SortField.DATE, ascending = false))
        assertEquals(listOf(3L, 1L, 2L), ids(result))
    }

    @Test
    fun byFormatThenName() {
        val result = sort(listOf(a, b, c), SortCriteria(SortField.FORMAT, ascending = true))
        // JPEG (b, c triés par nom -> A.jpg=b, c.jpg=c) puis PNG (a)
        assertEquals(listOf(2L, 3L, 1L), ids(result))
    }

    @Test
    fun byLabelUsesFirstSortedLabelEmptyFirst() {
        val result = sort(listOf(a, b, c), SortCriteria(SortField.LABEL, ascending = true))
        // c (pas de label -> ""), b (alpha), a (zeta)
        assertEquals(listOf(3L, 2L, 1L), ids(result))
    }

    @Test
    fun emptyListStaysEmpty() {
        assertEquals(emptyList<ImageItem>(), sort(emptyList(), SortCriteria(SortField.NAME, true)))
    }
}
