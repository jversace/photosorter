package fr.photosorter.data.moderation

import android.content.Context
import android.content.res.AssetFileDescriptor
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import fr.photosorter.core.dispatchers.DispatchersProvider
import kotlinx.coroutines.withContext
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Classifieur de contenu adulte basé sur un modèle TFLite embarqué
 * (`app/assets/model.tflite`, labels `app/assets/labels.txt`).
 *
 * Labels (ordre du fichier) : drawings, neutral, porn, sexy.
 * On considère une image comme "adulte" si (porn + sexy) >= [adultThreshold].
 *
 * 100% local : aucun envoi de données à l'extérieur.
 *
 * Dégradation propre : si le modèle est absent ou invalide, [isReady] reste
 * false et [classify] renvoie [Result.Unavailable] (l'image est considérée
 * non-adulte, révisable par l'utilisateur).
 */
@Singleton
class AdultClassifier @Inject constructor(
    private val context: Context,
    private val dispatchers: DispatchersProvider
) {

    sealed class Result {
        data class Scored(val isAdult: Boolean) : Result()
        object Unavailable : Result()
    }

    @Volatile private var interpreter: Interpreter? = null
    @Volatile private var inputSize: Int = DEFAULT_INPUT_SIZE
    @Volatile private var initTried = false

    val isReady: Boolean get() = interpreter != null

    fun ensureInitialized() {
        if (initTried) return
        synchronized(this) {
            if (initTried) return
            initTried = true
            interpreter = runCatching {
                val model = loadModelFile()
                Interpreter(model, Interpreter.Options().apply { setNumThreads(THREADS) })
            }.onFailure { Log.w(TAG, "Modèle TFLite indisponible : ${it.message}") }.getOrNull()
            interpreter?.let { interp ->
                val shape = interp.getInputTensor(0).shape() // [1, H, W, 3]
                if (shape.size == 4 && shape[1] > 0) inputSize = shape[1]
            }
        }
    }

    suspend fun classify(uri: Uri): Result = withContext(dispatchers.io) {
        ensureInitialized()
        val interp = interpreter ?: return@withContext Result.Unavailable
        val bitmap = decodeScaled(uri) ?: return@withContext Result.Unavailable
        try {
            val input = toInputBuffer(bitmap)
            val output = Array(1) { FloatArray(LABEL_COUNT) }
            interp.run(input, output)
            val probs = normalize(output[0])
            val adultScore = (probs[2] + probs[3]).coerceIn(0f, 1f)
            Result.Scored(isAdult = adultScore >= adultThreshold)
        } catch (t: Throwable) {
            Log.w(TAG, "Inférence échouée : ${t.message}")
            Result.Unavailable
        } finally {
            bitmap.recycle()
        }
    }

    // --- helpers ---

    private fun decodeScaled(uri: Uri): Bitmap? = try {
        context.contentResolver.openInputStream(uri)?.use { input ->
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeStream(input, null, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
            val sample = calculateSampleSize(bounds.outWidth, bounds.outHeight, inputSize)
            context.contentResolver.openInputStream(uri)?.use { input2 ->
                val opts = BitmapFactory.Options().apply { inSampleSize = sample }
                BitmapFactory.decodeStream(input2, null, opts)
            }?.let { Bitmap.createScaledBitmap(it, inputSize, inputSize, true).also { scaled ->
                if (scaled !== it) it.recycle()
            } }
        }
    } catch (t: Throwable) {
        Log.w(TAG, "Décodage échoué : ${t.message}")
        null
    }

    private fun calculateSampleSize(w: Int, h: Int, target: Int): Int {
        var sample = 1
        var minSide = minOf(w, h)
        while (minSide / sample > target * 2) sample *= 2
        return sample
    }

    private fun toInputBuffer(bitmap: Bitmap): ByteBuffer {
        val buffer = ByteBuffer.allocateDirect(inputSize * inputSize * 3 * 4)
            .order(ByteOrder.nativeOrder())
        val pixels = IntArray(inputSize * inputSize)
        bitmap.getPixels(pixels, 0, inputSize, 0, 0, inputSize, inputSize)
        for (p in pixels) {
            buffer.putFloat(((p shr 16) and 0xFF) / 255f)
            buffer.putFloat(((p shr 8) and 0xFF) / 255f)
            buffer.putFloat((p and 0xFF) / 255f)
        }
        buffer.rewind()
        return buffer
    }

    private fun normalize(raw: FloatArray): FloatArray {
        var sum = 0f
        for (v in raw) sum += v
        // Modèle déjà softmaxé (somme ~1) : on renvoie tel quel.
        if (sum in 0.9f..1.1f) return raw
        // Sinon on applique une softmax stable.
        val max = raw.maxOrNull() ?: 0f
        val exps = FloatArray(raw.size) { kotlin.math.exp((raw[it] - max).toDouble()).toFloat() }
        val s = exps.sum().coerceAtLeast(1e-6f)
        return FloatArray(exps.size) { exps[it] / s }
    }

    private fun loadModelFile(): MappedByteBuffer {
        context.assets.openFd(MODEL_FILE).use { afd: AssetFileDescriptor ->
            FileInputStream(afd.fileDescriptor).use { fis: FileInputStream ->
                return fis.channel.map(
                    FileChannel.MapMode.READ_ONLY,
                    afd.startOffset,
                    afd.declaredLength
                )
            }
        }
    }

    private companion object {
        const val TAG = "AdultClassifier"
        const val MODEL_FILE = "model.tflite"
        const val LABEL_COUNT = 4 // drawings, neutral, porn, sexy
        const val DEFAULT_INPUT_SIZE = 224
        const val THREADS = 2
        const val adultThreshold = 0.5f
    }
}
