package fr.photosorter.data.moderation

import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import fr.photosorter.testutil.TestDispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Vérifie la dégradation propre sans modèle embarqué : en environnement de test,
 * `app/assets/model.tflite` n'est pas garanti présent. Le contrat est :
 * - `isReady` = false avant/après init si le modèle ne charge pas,
 * - `classify` renvoie [AdultClassifier.Result.Unavailable] (jamais d'exception),
 * - `ensureInitialized()` est idempotent (reste cohérent sur appels répétés).
 */
@RunWith(RobolectricTestRunner::class)
class AdultClassifierTest {

    private fun newClassifier() = AdultClassifier(
        ApplicationProvider.getApplicationContext(), TestDispatchers()
    )

    @Test
    fun notReadyByDefaultBeforeInit() {
        val c = newClassifier()
        assertFalse(c.isReady)
    }

    @Test
    fun ensureInitializedIsIdempotent() {
        val c = newClassifier()
        c.ensureInitialized()
        val afterFirst = c.isReady
        c.ensureInitialized()
        assertTrue(afterFirst == c.isReady)
    }

    @Test
    fun classifyWithoutModelIsUnavailable() = runTest {
        val c = newClassifier()
        val result = c.classify(Uri.parse("content://x/nope.jpg"))
        assertTrue(result is AdultClassifier.Result.Unavailable)
    }
}
