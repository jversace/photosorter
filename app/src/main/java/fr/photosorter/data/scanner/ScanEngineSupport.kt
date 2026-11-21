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
 * n'existe pas encore, puis les liens image->adulte sont inseres (IGNORE).
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
                labelDao.insert(
                    LabelEntity(name = ADULT_LABEL, isSystem = true, createdAt = System.currentTimeMillis())
                )
            }
            imageLabelDao.insertAll(
                adultIds.map { ImageLabelEntity(imageId = it, labelName = ADULT_LABEL) }
            )
        }
    }
    return inserted
}

/**
 * Reconciliation IDEMPOTENTE des liens image_labels pour le libelle systeme
 * 'adulte', a executer en fin de scan.
 *
 * Probleme adresse (bug c7681760) : ImageDao.insertAll est en REPLACE ; la FK
 * ON DELETE CASCADE efface a chaque re-scan les liens des images existantes.
 * Ces liens ne sont recrees par flushImages que pour les images isAdult du lot
 * COURANT. Les images adultes issues d'un scan anterieur perdent donc leur lien
 * -> le filtre 'adulte uniquement' devient vide.
 *
 * Cette etape garantit l'existence du label 'adulte' puis insere (INSERT OR
 * IGNORE ... SELECT) un lien pour TOUTES les images ou images.is_adult = 1, y
 * compris celles hors du lot courant, sans dupliquer les liens deja presents.
 *
 * Room exige Long (ou Unit) pour un INSERT ... SELECT (bug 768b171a) : le type
 * suit insertMissingAdultLabels.
 *
 * @return le nombre de liens manquants effectivement inseres (0 = rien a faire).
 */
internal suspend fun reconcileAdultLabels(
    db: AppDatabase,
    imageLabelDao: ImageLabelDao,
    labelDao: LabelDao
): Long {
    var inserted = 0L
    db.withTransaction {
        if (labelDao.getByName(ADULT_LABEL) == null) {
            labelDao.insert(
                LabelEntity(name = ADULT_LABEL, isSystem = true, createdAt = System.currentTimeMillis())
            )
        }
        inserted = imageLabelDao.insertMissingAdultLabels(ADULT_LABEL)
    }
    return inserted
}

/**
 * Determine si une image est adulte : pre-filtre (screenshots/documents =
 * court-circuit SYNCHRONE) puis classifieur TFLite (classify() est suspend,
 * d'ou le detectAdult suspend ; il s'execute dans la coroutine de scan).
 * Un resultat indisponible est traite comme non-adulte (conservateur) ; le
 * compteur est gere par le classifieur.
 */
internal suspend fun detectAdult(uri: Uri, name: String, preFilter: PreFilter, classifier: AdultClassifier): Boolean {
    when (preFilter.decide(uri.toString(), name)) {
        PreFilterDecision.SKIP -> return false
        PreFilterDecision.ANALYZE -> Unit
    }
    return when (val r = classifier.classify(uri)) {
        is AdultClassifier.Result.Scored -> r.isAdult
        AdultClassifier.Result.Unavailable -> false
    }
}
