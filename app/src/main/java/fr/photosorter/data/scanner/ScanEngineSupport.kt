package fr.photosorter.data.scanner

import android.net.Uri
import androidx.room.withTransaction
import fr.photosorter.data.db.AppDatabase
import fr.photosorter.data.db.dao.ImageDao
import fr.photosorter.data.db.dao.ImageLabelDao
import fr.photosorter.data.db.dao.LabelDao
import fr.photosorter.data.db.entity.ImageEntity
import fr.photosorter.data.db.entity.ImageLabelEntity
import fr.photosorter.data.db.entity.LabelEntity
import fr.photosorter.data.moderation.AdultClassifier
import fr.photosorter.data.moderation.PreFilter
import fr.photosorter.data.moderation.PreFilterDecision
import fr.photosorter.domain.model.ADULT_LABEL

internal const val BATCH_SIZE = 200
internal const val MSG_ANALYZING = "Analyse du stockage..."
internal const val MSG_CANCELLED = "Scan annule"
internal const val MSG_ERROR = "Erreur de scan"

/**
 * Insere un lot d'images ET les associations au libelle systeme 'adulte' de
 * facon ATOMIQUE (meme transaction Room). Le libelle 'adulte' est cree s'il
 * n'existe pas encore, puis les liens image->adulte sont inseres.
 * @return le nombre d'images reellement inserees.
 */
internal suspend fun flushImages(
    batch: List<ImageEntity>,
    db: AppDatabase,
    imageDao: ImageDao,
    imageLabelDao: ImageLabelDao,
    labelDao: LabelDao
): Int {
    if (batch.isEmpty()) return 0
    var inserted = 0
    db.withTransaction {
        val ids = imageDao.insertAll(batch)
        val adultIds = ArrayList<Long>()
        for (i in batch.indices) {
            val id = ids.getOrNull(i) ?: continue
            if (id > 0L && batch[i].isAdult) adultIds.add(id)
        }
        inserted = ids.count { it > 0L }
        if (adultIds.isNotEmpty()) {
            if (labelDao.getByName(ADULT_LABEL) == null) {
                labelDao.insert(LabelEntity(name = ADULT_LABEL, isSystem = true))
            }
            imageLabelDao.insertAll(
                adultIds.map { ImageLabelEntity(imageId = it, labelName = ADULT_LABEL) }
            )
        }
    }
    return inserted
}

/**
 * Determine si une image est adulte : pre-filtre (screenshots/documents =
 * court-circuit) puis classifieur TFLite. Un resultat indisponible est traite
 * comme non-adulte (conservateur) ; le compteur est gere par le classifieur.
 */
internal fun detectAdult(uri: Uri, name: String, preFilter: PreFilter, classifier: AdultClassifier): Boolean {
    when (preFilter.decide(uri.toString(), name)) {
        PreFilterDecision.Skip -> return false
        PreFilterDecision.Classify -> Unit
    }
    return when (val r = classifier.classify(uri)) {
        is AdultClassifier.Result.Scored -> r.isAdult
        AdultClassifier.Result.Unavailable -> false
    }
}
