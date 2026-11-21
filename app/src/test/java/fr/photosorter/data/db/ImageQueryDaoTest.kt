package fr.photosorter.data.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import fr.photosorter.data.db.entity.ImageEntity
import fr.photosorter.data.db.entity.ImageLabelEntity
import fr.photosorter.data.db.entity.LabelEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ImageQueryDaoTest {

    private lateinit var db: AppDatabase

    private val adultLabel = "adulte"

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() = db.close()

    private fun img(id: Long, name: String, format: String, size: Long, date: Long, adult: Boolean = false) =
        ImageEntity(id = id, uri = "uri/$id", name = name, format = format, sizeBytes = size,
            dateTaken = null, dateModified = date, width = 1, height = 1, isAdult = adult, thumbnailHint = null)

    @Test
    fun orderByNameAsc() = runTest {
        db.imageDao().insertAll(listOf(
            img(1, "b.jpg", "JPEG", 100, 10),
            img(2, "a.jpg", "JPEG", 200, 20)
        ))
        val rows = db.imageQueryDao().observeFiltered(
            sort = 0, asc = 1, includeAdult = 1, adultOnly = 0, adultLabel = adultLabel,
            hasFormats = 0, formats = emptyList(),
            dateFrom = null, dateTo = null, minSize = null, hasLabels = 0, labels = emptyList()
        ).first()
        assertEquals(listOf("a.jpg", "b.jpg"), rows.map { it.image.name })
    }

    @Test
    fun orderBySizeDesc() = runTest {
        db.imageDao().insertAll(listOf(
            img(1, "small.jpg", "JPEG", 100, 10),
            img(2, "big.jpg", "JPEG", 900, 20)
        ))
        val rows = db.imageQueryDao().observeFiltered(
            sort = 2, asc = 0, includeAdult = 1, adultOnly = 0, adultLabel = adultLabel,
            hasFormats = 0, formats = emptyList(),
            dateFrom = null, dateTo = null, minSize = null, hasLabels = 0, labels = emptyList()
        ).first()
        assertEquals(listOf("big.jpg", "small.jpg"), rows.map { it.image.name })
    }

    @Test
    fun filterByFormatAndMinSize() = runTest {
        db.imageDao().insertAll(listOf(
            img(1, "a.png", "PNG", 900, 10),
            img(2, "b.png", "PNG", 100, 10),
            img(3, "c.jpg", "JPEG", 900, 10)
        ))
        val rows = db.imageQueryDao().observeFiltered(
            sort = 0, asc = 1, includeAdult = 1, adultOnly = 0, adultLabel = adultLabel,
            hasFormats = 1, formats = listOf("PNG"),
            dateFrom = null, dateTo = null, minSize = 500, hasLabels = 0, labels = emptyList()
        ).first()
        assertEquals(listOf("a.png"), rows.map { it.image.name })
    }

    @Test
    fun excludesAdultWhenRequested() = runTest {
        db.imageDao().insertAll(listOf(
            img(1, "ok.jpg", "JPEG", 100, 10, adult = false),
            img(2, "nsfw.jpg", "JPEG", 100, 10, adult = true)
        ))
        val rows = db.imageQueryDao().observeFiltered(
            sort = 0, asc = 1, includeAdult = 0, adultOnly = 0, adultLabel = adultLabel,
            hasFormats = 0, formats = emptyList(),
            dateFrom = null, dateTo = null, minSize = null, hasLabels = 0, labels = emptyList()
        ).first()
        assertEquals(listOf("ok.jpg"), rows.map { it.image.name })
    }

    @Test
    fun filterByLabel() = runTest {
        db.imageDao().insertAll(listOf(
            img(1, "a.jpg", "JPEG", 100, 10),
            img(2, "b.jpg", "JPEG", 100, 10)
        ))
        db.labelDao().insert(LabelEntity(name = "vacances", isSystem = false, createdAt = 0))
        db.imageLabelDao().insertAll(listOf(ImageLabelEntity(imageId = 1, labelName = "vacances")))
        val rows = db.imageQueryDao().observeFiltered(
            sort = 0, asc = 1, includeAdult = 1, adultOnly = 0, adultLabel = adultLabel,
            hasFormats = 0, formats = emptyList(),
            dateFrom = null, dateTo = null, minSize = null, hasLabels = 1, labels = listOf("vacances")
        ).first()
        assertEquals(listOf("a.jpg"), rows.map { it.image.name })
        assertEquals(listOf("vacances"), rows.first().labels.map { it.name })
    }

    @Test
    fun adultOnlyReturnsOnlyImagesWithAdultLabel() = runTest {
        db.imageDao().insertAll(listOf(
            img(1, "adulte.jpg", "JPEG", 100, 10, adult = false),
            img(2, "normale.jpg", "JPEG", 100, 10, adult = false)
        ))
        db.labelDao().insert(DatabaseSeeder.adultLabelEntity(now = 0))
        db.imageLabelDao().insertAll(listOf(ImageLabelEntity(imageId = 1, labelName = adultLabel)))
        val rows = db.imageQueryDao().observeFiltered(
            sort = 0, asc = 1, includeAdult = 1, adultOnly = 1, adultLabel = adultLabel,
            hasFormats = 0, formats = emptyList(),
            dateFrom = null, dateTo = null, minSize = null, hasLabels = 0, labels = emptyList()
        ).first()
        assertEquals(listOf("adulte.jpg"), rows.map { it.image.name })
    }

    /**
     * insertMissingAdultLabels doit recreer le lien 'adulte' pour les images
     * is_adult = 1 qui n'en ont pas (celles hors lot courant apres re-scan),
     * sans dupliquer les liens deja presents, et sans toucher aux non-adultes.
     */
    @Test
    fun insertMissingAdultLabelsRecreatesMissingLinksOnly() = runTest {
        db.imageDao().insertAll(listOf(
            img(1, "deja_lie.jpg", "JPEG", 100, 10, adult = true),
            img(2, "adulte_sans_lien.jpg", "JPEG", 100, 10, adult = true),
            img(3, "normale.jpg", "JPEG", 100, 10, adult = false)
        ))
        db.labelDao().insert(DatabaseSeeder.adultLabelEntity(now = 0))
        // Image 1 a deja son lien ; image 2 n'en a pas encore.
        db.imageLabelDao().insertAll(listOf(ImageLabelEntity(imageId = 1, labelName = adultLabel)))

        val inserted = db.imageLabelDao().insertMissingAdultLabels(adultLabel)

        // Un seul lien manquant (image 2, adulte sans lien). L'image 1 etait deja liee,
        // l'image 3 n'est pas adulte.
        assertEquals(1L, inserted)
        val rows = db.imageQueryDao().observeFiltered(
            sort = 0, asc = 1, includeAdult = 1, adultOnly = 1, adultLabel = adultLabel,
            hasFormats = 0, formats = emptyList(),
            dateFrom = null, dateTo = null, minSize = null, hasLabels = 0, labels = emptyList()
        ).first()
        assertEquals(listOf("adulte_sans_lien.jpg", "deja_lie.jpg"), rows.map { it.image.name }.sorted())
    }

    /**
     * Idempotence : un second appel apres reconciliation complete n'insere rien.
     */
    @Test
    fun insertMissingAdultLabelsIsIdempotent() = runTest {
        db.imageDao().insertAll(listOf(
            img(1, "a.jpg", "JPEG", 100, 10, adult = true),
            img(2, "b.jpg", "JPEG", 100, 10, adult = true)
        ))
        db.labelDao().insert(DatabaseSeeder.adultLabelEntity(now = 0))

        val first = db.imageLabelDao().insertMissingAdultLabels(adultLabel)
        val second = db.imageLabelDao().insertMissingAdultLabels(adultLabel)

        assertEquals(2L, first)
        assertEquals(0L, second)
    }
}
