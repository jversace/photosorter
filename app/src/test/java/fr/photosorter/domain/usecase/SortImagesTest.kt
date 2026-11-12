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

    private fun names(items: List<ImageItem>) = items.map { it.name }

    @Test
    fun byNameAscendingIsCaseInsensitive() {
        val input = listOf(item(1, "b.jpg"), item(2, "A.jpg"), item(3, "c.jpg"))
        val out = sort(input, SortCriteria(SortField.NAME, ascending = true))
        assertEquals(listOf("A.jpg", "b.jpg", "c.jpg"), names(out))
    }

    @Test
    fun bySizeDescending() {
        val input = listOf(item(1, "small", size = 10), item(2, "big", size = 900))
        val out = sort(input, SortCriteria(SortField.SIZE, ascending = false))
        assertEquals(listOf("big", "small"), names(out))
    }

    @Test
    fun byDateUsesModifiedWhenTakenNull() {
        val input = listOf(item(1, "recent", date = 200), item(2, "old", date = 100))
        val out = sort(input, SortCriteria(SortField.DATE, ascending = true))
        assertEquals(listOf("old", "recent"), names(out))
    }

    @Test
    fun byFormatThenName() {
        val input = listOf(
            item(1, "z.png", format = ImageFormat.PNG),
            item(2, "a.jpg", format = ImageFormat.JPEG),
            item(3, "b.png", format = ImageFormat.PNG)
        )
        val out = sort(input, SortCriteria(SortField.FORMAT, ascending = true))
        // JPEG < PNG (ordre enum), puis nom ascendant dans PNG
        assertEquals(listOf("a.jpg", "b.png", "z.png"), names(out))
    }

    @Test
    fun byLabelUsesFirstSortedLabel() {
        val input = listOf(
            item(1, "noLabel"),
            item(2, "famille", labels = setOf("famille")),
            item(3, "travail", labels = setOf("travail"))
        )
        val out = sort(input, SortCriteria(SortField.LABEL, ascending = true))
        // vide < "famille" < "travail"
        assertEquals(listOf("noLabel", "famille", "travail"), names(out))
    }

    @Test
    fun descendingReversesNameOrder() {
        val input = listOf(item(1, "a"), item(2, "b"), item(3, "c"))
        val out = sort(input, SortCriteria(SortField.NAME, ascending = false))
        assertEquals(listOf("c", "b", "a"), names(out))
    }
}
