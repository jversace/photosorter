package fr.photosorter.data.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import fr.photosorter.core.result.AppError
import fr.photosorter.core.result.AppResult
import fr.photosorter.data.db.dao.ImageDao
import fr.photosorter.data.db.dao.ImageLabelDao
import fr.photosorter.data.db.dao.ImageQueryDao
import fr.photosorter.data.db.entity.ImageEntity
import fr.photosorter.data.db.entity.ImageWithLabels
import fr.photosorter.domain.model.FilterState
import fr.photosorter.domain.model.ImageFormat
import fr.photosorter.domain.model.ImageItem
import fr.photosorter.domain.model.SortCriteria
import fr.photosorter.domain.model.SortField
import fr.photosorter.testutil.TestDispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ImageRepositoryImplTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val imageDao: ImageDao = mock()
    private val labelDao: ImageLabelDao = mock()
    private val queryDao: ImageQueryDao = mock()
    private val fileActions: FileActions = mock()

    private fun repo() = ImageRepositoryImpl(
        context, imageDao, labelDao, queryDao, fileActions, TestDispatchers()
    )

    private fun entity(id: Long) = ImageEntity(
        id = id, uri = "uri/$id", name = "n$id.jpg", format = ImageFormat.JPEG.name,
        sizeBytes = 1, dateTaken = null, dateModified = 0, width = 1, height = 1,
        thumbnailHint = null
    )

    private fun item(id: Long) = ImageItem(
        id = id, uri = "uri/$id", name = "n$id.jpg", format = ImageFormat.JPEG,
        sizeBytes = 1, dateTaken = null, dateModified = 0, width = 1, height = 1,
        labels = emptySet(), isAdult = false, thumbnailHint = null
    )

    @Test
    fun observeImagesMapsRows() = runTest {
        whenever(
            queryDao.observeFiltered(any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
        ).thenReturn(flowOf(listOf(ImageWithLabels(entity(1), emptyList()))))

        val result = repo().observeImages(
            SortCriteria(SortField.NAME, true), FilterState()
        )
        var emitted: List<ImageItem>? = null
        result.collect { emitted = it }

        assertEquals(1, emitted?.size)
        assertEquals(1L, emitted?.first()?.id)
    }

    @Test
    fun deleteSuccessCleansAndRemovesRow() = runTest {
        whenever(fileActions.delete(eq("uri/1"))).thenReturn(null)
        val result = repo().delete(listOf(item(1)))
        assertTrue(result is AppResult.Success)
        verify(labelDao).clearForImage(1L)
        verify(imageDao).deleteById(1L)
    }

    @Test
    fun deleteFailureKeepsRowAndReturnsFailure() = runTest {
        whenever(fileActions.delete(eq("uri/1"))).thenReturn(AppError.NotFound("nope"))
        val result = repo().delete(listOf(item(1)))
        assertTrue(result is AppResult.Failure)
        verify(labelDao, never()).clearForImage(any())
        verify(imageDao, never()).deleteById(any())
    }

    @Test
    fun deleteReportsFirstErrorAmongItems() = runTest {
        whenever(fileActions.delete(eq("uri/1"))).thenReturn(AppError.Io("e1"))
        whenever(fileActions.delete(eq("uri/2"))).thenReturn(null)
        val result = repo().delete(listOf(item(1), item(2)))
        assertTrue(result is AppResult.Failure)
        // item 2 a bien été supprimé malgré l'échec du 1
        verify(imageDao).deleteById(2L)
    }
}
