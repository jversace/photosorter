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
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Classifieur de contenu adulte base sur un modele TFLite embarque
 * (app/assets/model.tflite, labels app/assets/labels.txt).
 *
 * Labels (ordre) : drawings, neutral, porn, sexy.
 * Image consideree "adulte" si (porn + sexy) >= adultThreshold.
 *
 * 100% local.
 *
 * Normalisation d'entree PILOTEE PAR LA CONFIG du modele (app/assets/model.config,
 * cle 'scale'), jamais codee en dur. Valeurs : ZERO_ONE, ZERO_255, MINUS_ONE_ONE.
 * En l'absence de config, on retombe sur DEFAULT_INPUT_SCALE (hypothese a confirmer
 * avec la config d'entrainement reelle du modele).
 */
@Singleton
class AdultClassifier @Inject constructor(
    private val context: Context,
    private val dispatchers: DispatchersProvider
) {

    enum class InputScale {
        ZERO_ONE,
        ZERO_255,
        MINUS_ONE_ONE;

        fun apply(channel: Int): Float {
            val v = channel.toFloat()
            return when (this) {
                ZERO_ONE -> v / 255f
                ZERO_255 -> v
                MINUS_ONE_ONE -> (v / 127.5f) - 1f
            }
        }

        companion object {
            fun parse(raw: String?): InputScale? = when (raw?.trim()?.uppercase()) {
                "ZERO_ONE" -> ZERO_ONE
                "ZERO_255" -> ZERO_255
                "MINUS_ONE_ONE" -> MINUS_ONE_ONE
                else -> null
            }
        }
    }

    sealed class Result {
        data class Scored(val isAdult: Boolean) : Result()
        object Unavailable : Result()
    }

    @Volatile private var interpreter: Interpreter? = null
    @Volatile private var inputSize: Int = DEFAULT_INPUT_SIZE
    @Volatile private var inputScale: InputScale = DEFAULT_INPUT_SCALE
    @Volatile private var initTried = false

    private val unavailableCounter = AtomicInteger(0)
    val unavailableCount: Int get() = unavailableCounter.get()
    fun resetUnavailableCount() { unavailableCounter.set(0) }

    val isReady: Boolean get() = interpreter != null
    val currentInputScale: InputScale get() = inputScale

    fun ensureInitialized() {
        if (initTried) return
        synchronized(this) {
            if (initTried) return
            initTried = true
            inputScale = loadInputScale()
            interpreter = runCatching {
                val model = loadModelFile()
                Interpreter(model, Interpreter.Options().apply { setNumThreads(THREADS) })
            }.onFailure { Log.w(TAG, "Modele TFLite indisponible : ${it.message}") }.getOrNull()
            interpreter?.let { interp ->
                val shape = interp.getInputTensor(0).shape()
                if (shape.size == 4 && shape[1] > 0) inputSize = shape[1]
            }
            Log.i(TAG, "InputScale=$inputScale inputSize=$inputSize")
        }
    }

    suspend fun classify(uri: Uri): Result = withContext(dispatchers.io) {
        ensureInitialized()
        val interp = interpreter ?: return@withContext markUnavailable("modele indisponible")
        val bitmap = decodeScaled(uri) ?: return@withContext markUnavailable("decodage echoue: $uri")
        try {
            val input = toInputBuffer(bitmap)
            val output = Array(1) { FloatArray(LABEL_COUNT) }
            interp.run(input, output)
            val probs = normalize(output[0])
            val adultScore = (probs[2] + probs[3]).coerceIn(0f, 1f)
            Result.Scored(isAdult = adultScore >= adultThreshold)
        } catch (t: Throwable) {
            Log.w(TAG, "Inference echouee : ${t.message}")
            markUnavailable("inference: ${t.message}")
        } finally {
            bitmap.recycle()
        }
    }

    private fun markUnavailable(reason: String): Result.Unavailable {
        unavailableCounter.incrementAndGet()
        Log.w(TAG, "Image non classee (Unavailable) : $reason")
        return Result.Unavailable
    }

    private fun loadInputScale(): InputScale {
        return runCatching {
            context.assets.open(CONFIG_FILE).bufferedReader().use { reader ->
                reader.readLines().firstOrNull { it.trim().startsWith("scale") }
                    ?.substringAfter("=", "")?.let { InputScale.parse(it) }
            }
        }.getOrNull()?.also { Log.i(TAG, "InputScale lu depuis $CONFIG_FILE : $it") }
            ?: DEFAULT_INPUT_SCALE.also { Log.w(TAG, "Pas de config d'echelle : defaut $it (a confirmer)") }
    }

    private fun decodeScaled(uri: Uri): Bitmap? = try {
        decodeFromStream(uri) ?: decodeFromFile(uri)
    } catch (t: Throwable) {
        Log.w(TAG, "Decodage echoue ($uri) : ${t.message}")
        try { decodeFromFile(uri) } catch (t2: Throwable) { null }
    }

    private fun decodeFromStream(uri: Uri): Bitmap? = context.contentResolver.openInputStream(uri)?.use { input ->
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

    private fun decodeFromFile(uri: Uri): Bitmap? {
        if (uri.scheme != "file") return null
        val path = uri.path ?: return null
        val file = File(path)
        if (!file.exists()) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val sample = calculateSampleSize(bounds.outWidth, bounds.outHeight, inputSize)
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val decoded = BitmapFactory.decodeFile(path, opts) ?: return null
        return Bitmap.createScaledBitmap(decoded, inputSize, inputSize, true).also { scaled ->
            if (scaled !== decoded) decoded.recycle()
        }
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
        val scale = inputScale
        for (p in pixels) {
            buffer.putFloat(scale.apply((p shr 16) and 0xFF))
            buffer.putFloat(scale.apply((p shr 8) and 0xFF))
            buffer.putFloat(scale.apply(p and 0xFF))
        }
        buffer.rewind()
        return buffer
    }

    private fun normalize(raw: FloatArray): FloatArray {
        var sum = 0f
        for (v in raw) sum += v
        if (sum in 0.9f..1.1f) return raw
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
        const val CONFIG_FILE = "model.config"
        val DEFAULT_INPUT_SCALE = InputScale.ZERO_ONE
        const val LABEL_COUNT = 4
        const val DEFAULT_INPUT_SIZE = 224
        const val THREADS = 2
        const val adultThreshold = 0.5f
    }
}
