package fr.photosorter.data.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import fr.photosorter.core.result.AppError
import fr.photosorter.core.result.AppResult
import fr.photosorter.data.db.dao.ImageDao
import fr.photosorter.data.db.dao.ImageLabelDao
import fr.photosorter.data.db.dao.ImageQueryDao
import fr.photosorter.domain.model.ImageFormat
import fr.photosorter.domain.model.ImageItem
import fr.photosorter.testutil.TestDispatchers
import kotlinx.coroutines.test.runTest
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
    private val imageLabelDao: ImageLabelDao = mock()
    private val imageQueryDao: ImageQueryDao = mock()
    private val fileActions: FileActions = mock()

    private fun repo() = ImageRepositoryImpl(
        context, imageDao, imageLabelDao, imageQueryDao, fileActions, TestDispatchers()
    )

    private fun item(id: Long) = ImageItem(
        id = id, uri = "uri/$id", name = "n$id.jpg", format = ImageFormat.JPEG,
        sizeBytes = 1, dateTaken = null, dateModified = 0, width = 1, height = 1,
        labels = emptySet(), isAdult = false, thumbnailHint = null
    )

    private val treeUri =
        "content://com.android.externalstorage.documents/tree/primary%3APhotos"

    @Test
    fun deleteSuccessCleansAndRemovesRow() = runTest {
        whenever(fileActions.delete(eq("uri/1"))).thenReturn(null)
        val result = repo().delete(listOf(item(1)))
        assertTrue(result is AppResult.Success)
        verify(imageLabelDao).clearForImage(1L)
        verify(imageDao).deleteById(1L)
    }

    @Test
    fun deleteFailureKeepsRowAndReturnsFailure() = runTest {
        whenever(fileActions.delete(eq("uri/1"))).thenReturn(AppError.NotFound("nope"))
        val result = repo().delete(listOf(item(1)))
        assertTrue(result is AppResult.Failure)
        verify(imageLabelDao, never()).clearForImage(any())
        verify(imageDao, never()).deleteById(any())
    }

    @Test
    fun copyDelegatesToCopyToTreeWithTreeUri() = runTest {
        whenever(fileActions.copyToTree(eq("uri/1"), eq(treeUri))).thenReturn(null)
        whenever(fileActions.copyToTree(eq("uri/2"), eq(treeUri))).thenReturn(null)

        val result = repo().copy(listOf(item(1), item(2)), treeUri)

        assertTrue(result is AppResult.Success)
        verify(fileActions).copyToTree(eq("uri/1"), eq(treeUri))
        verify(fileActions).copyToTree(eq("uri/2"), eq(treeUri))
    }

    @Test
    fun copyFailureReturnsFailure() = runTest {
        whenever(fileActions.copyToTree(eq("uri/1"), eq(treeUri)))
            .thenReturn(AppError.Io("copie impossible"))

        val result = repo().copy(listOf(item(1)), treeUri)

        assertTrue(result is AppResult.Failure)
    }

    @Test
    fun moveDelegatesToMoveToTreeWithTreeUri() = runTest {
        whenever(fileActions.moveToTree(eq("uri/1"), eq(treeUri))).thenReturn(null)

        val result = repo().move(listOf(item(1)), treeUri)

        assertTrue(result is AppResult.Success)
        verify(fileActions).moveToTree(eq("uri/1"), eq(treeUri))
    }

    @Test
    fun moveFailureReturnsFailure() = runTest {
        whenever(fileActions.moveToTree(eq("uri/1"), eq(treeUri)))
            .thenReturn(AppError.Io("deplacement impossible"))

        val result = repo().move(listOf(item(1)), treeUri)

        assertTrue(result is AppResult.Failure)
    }

    @Test
    fun copyReportsFirstErrorAmongItems() = runTest {
        whenever(fileActions.copyToTree(eq("uri/1"), eq(treeUri)))
            .thenReturn(AppError.Io("e1"))
        whenever(fileActions.copyToTree(eq("uri/2"), eq(treeUri))).thenReturn(null)

        val result = repo().copy(listOf(item(1), item(2)), treeUri)

        assertTrue(result is AppResult.Failure)
        verify(fileActions).copyToTree(eq("uri/2"), eq(treeUri))
    }
}
