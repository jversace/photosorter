package fr.photosorter.data.repository

import fr.photosorter.data.db.entity.ImageEntity
import fr.photosorter.data.db.entity.ImageWithLabels
import fr.photosorter.data.db.entity.LabelEntity
import fr.photosorter.domain.model.ADULT_LABEL
import fr.photosorter.domain.model.ImageFormat
import fr.photosorter.domain.model.ImageItem
import fr.photosorter.domain.model.Label

/** Conversions entités Room ↔ modèles du domaine (frontière data/domain). */

fun ImageEntity.toDomain(labels: Set<String>): ImageItem = ImageItem(
    id = id,
    uri = uri,
    name = name,
    format = runCatching { ImageFormat.valueOf(format) }.getOrNull() ?: ImageFormat.JPEG,
    sizeBytes = sizeBytes,
    dateTaken = dateTaken,
    dateModified = dateModified,
    width = width,
    height = height,
    labels = labels,
    isAdult = isAdult || labels.contains(ADULT_LABEL),
    thumbnailHint = null
)

fun ImageWithLabels.toDomain(): ImageItem {
    val names = labels.map { it.name }.toSet()
    return image.toDomain(names)
}

fun LabelEntity.toDomain(): Label = Label(
    name = name,
    isSystem = isSystem,
    createdAt = createdAt
)
