package fr.photosorter.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import fr.photosorter.data.db.entity.ImageWithLabels
import kotlinx.coroutines.flow.Flow

/**
 * Requêtes dynamiques de tri/filtre.
 *
 * Room ne permet pas d'ordonner par un paramètre : on utilise un ORDER BY
 * construit à partir d'un code de tri numérique (paramètre :sort) et d'un
 * drapeau ascendant (:asc). Les valeurs de tri viennent d'un mapping figé
 * côté repository (aucune concaténation de l'extérieur, donc pas d'injection SQL).
 *
 * Codes de tri (sort) :
 *   0 = NAME, 1 = DATE, 2 = SIZE, 3 = FORMAT, 4 = LABEL
 *
 * Direction : on trie d'abord par un bloc CASE actif uniquement pour l'ordre
 * demandé (ASC si :asc = 1, DESC sinon) ; l'autre bloc vaut NULL pour toutes
 * les lignes et n'a donc aucun effet.
 */
@Dao
interface ImageQueryDao {

    /**
     * Filtres : formats (liste vide = tous), plage de dates [dateFrom, dateTo],
     * taille min, libellés (OR : image ayant AU MOINS un des libellés demandés),
     * includeAdult = 0 exclut les images adultes.
     */
    @Transaction
    @Query(
        "SELECT * FROM images i " +
        "WHERE (:includeAdult = 1 OR i.is_adult = 0) " +
        "AND (:hasFormats = 0 OR i.format IN (:formats)) " +
        "AND (:dateFrom IS NULL OR i.date_modified >= :dateFrom) " +
        "AND (:dateTo IS NULL OR i.date_modified <= :dateTo) " +
        "AND (:minSize IS NULL OR i.size_bytes >= :minSize) " +
        "AND (:hasLabels = 0 OR EXISTS (SELECT 1 FROM image_labels il WHERE il.image_id = i.id AND il.label_name IN (:labels))) " +
        "ORDER BY " +
        // Ordre ASCENDANT (actif seulement si :asc = 1)
        "CASE WHEN :asc = 1 THEN " +
        "  CASE :sort " +
        "    WHEN 0 THEN i.name COLLATE NOCASE " +
        "    WHEN 3 THEN i.format COLLATE NOCASE " +
        "    WHEN 1 THEN i.date_modified " +
        "    WHEN 2 THEN i.size_bytes " +
        "    WHEN 4 THEN (SELECT MIN(il2.label_name) FROM image_labels il2 WHERE il2.image_id = i.id) COLLATE NOCASE " +
        "  END " +
        "END ASC, " +
        // Ordre DESCENDANT (actif seulement si :asc = 0)
        "CASE WHEN :asc = 0 THEN " +
        "  CASE :sort " +
        "    WHEN 0 THEN i.name COLLATE NOCASE " +
        "    WHEN 3 THEN i.format COLLATE NOCASE " +
        "    WHEN 1 THEN i.date_modified " +
        "    WHEN 2 THEN i.size_bytes " +
        "    WHEN 4 THEN (SELECT MIN(il2.label_name) FROM image_labels il2 WHERE il2.image_id = i.id) COLLATE NOCASE " +
        "  END " +
        "END DESC, " +
        // Départage stable, toujours par nom croissant
        "i.name COLLATE NOCASE ASC"
    )
    fun observeFiltered(
        sort: Int,
        asc: Int,
        includeAdult: Int,
        hasFormats: Int,
        formats: List<String>,
        dateFrom: Long?,
        dateTo: Long?,
        minSize: Long?,
        hasLabels: Int,
        labels: List<String>
    ): Flow<List<ImageWithLabels>>
}
