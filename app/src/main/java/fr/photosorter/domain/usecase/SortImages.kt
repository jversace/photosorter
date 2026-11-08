package fr.photosorter.domain.usecase

import fr.photosorter.domain.model.ImageItem
import fr.photosorter.domain.model.SortCriteria
import fr.photosorter.domain.model.SortField
import javax.inject.Inject

/** Trie en mémoire une liste d'images selon [SortCriteria]. */
class SortImages @Inject constructor() {

    operator fun invoke(items: List<ImageItem>, sort: SortCriteria): List<ImageItem> {
        val comparator: Comparator<ImageItem> = when (sort.field) {
            SortField.NAME -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.name }
            SortField.DATE -> compareBy { it.dateTaken ?: it.dateModified }
            SortField.SIZE -> compareBy { it.sizeBytes }
            SortField.FORMAT -> compareBy<ImageItem> { it.format.name }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.name }
            SortField.LABEL -> compareBy<ImageItem> { it.labels.sorted().firstOrNull() ?: "" }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { it.name }
        }
        return if (sort.ascending) items.sortedWith(comparator) else items.sortedWith(comparator.reversed())
    }
}
