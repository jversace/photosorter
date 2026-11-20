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
 * Image consideree adulte si (porn + sexy) >= adultThreshold.
 *
 * 100% local.
 *
 * Normalisation d'entree PILOTEE PAR LA CONFIG du modele (app/assets/model.config,
 * cle scale), jamais codee en dur. Valeurs : ZERO_ONE, ZERO_255, MINUS_ONE_ONE.
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

    /** Nombre d'images non classees (Unavailable) depuis le dernier reset. Exploitable par le rapport de scan. */
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
                reader.readLines()
                    .firstOrNull { it.trim().startsWith("scale") }
                    ?.substringAfter("=", "")
                    ?.let { InputScale.parse(it) }
            }
        }.getOrNull()?.also { Log.i(TAG, "InputScale lu depuis $CONFIG_FILE : $it") }
            ?: DEFAULT_INPUT_SCALE.also { Log.w(TAG, "Pas de config d'echelle : defaut $it (a confirmer)") }
    }

    private fun decodeScaled(uri: Uri): Bitmap? {
        val fromResolver = runCatching { decodeFromStream(uri) }.getOrNull()
        if (fromResolver != null) return fromResolver
        if (uri.scheme == "file") {
            val path = uri.path
            if (!path.isNullOrBlank()) {
                return runCatching {
                    FileInputStream(File(path)).use { decodeFromStream(it) }
                }.onFailure { Log.w(TAG, "Repli FileInputStream echoue : ${it.message}") }.getOrNull()
            }
        }
        return null
    }

    private fun decodeFromStream(uri: Uri): Bitmap? =
        context.contentResolver.openInputStream(uri)?.use { decodeFromStream(it) }

    private fun decodeFromStream(input: java.io.InputStream): Bitmap? {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeStream(input, null, opts)
        if (opts.outWidth <= 0 || opts.outHeight <= 0) return null
        val sample = calculateSampleSize(opts.outWidth, opts.outHeight)
        val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sample }
        return BitmapFactory.decodeStream(input, null, decodeOpts)
    }

    private fun calculateSampleSize(width: Int, height: Int): Int {
        var sample = 1
        while (width / sample > inputSize || height / sample > inputSize) sample *= 2
        return sample
    }

    private fun toInputBuffer(bitmap: Bitmap): ByteBuffer {
        val scaled = Bitmap.createScaledBitmap(bitmap, inputSize, inputSize, true)
        val buffer = ByteBuffer.allocateDirect(4 * inputSize * inputSize * 3).order(ByteOrder.nativeOrder())
        val pixels = IntArray(inputSize * inputSize)
        scaled.getPixels(pixels, 0, inputSize, 0, 0, inputSize, inputSize)
        for (pixel in pixels) {
            buffer.putFloat(inputScale.apply((pixel shr 16) and 0xFF))
            buffer.putFloat(inputScale.apply((pixel shr 8) and 0xFF))
            buffer.putFloat(inputScale.apply(pixel and 0xFF))
        }
        buffer.rewind()
        if (scaled !== bitmap) scaled.recycle()
        return buffer
    }

    private fun normalize(raw: FloatArray): FloatArray {
        val sum = raw.sum()
        return if (sum in 0.9f..1.1f && raw.all { it in 0f..1f }) raw
        else {
            val exp = raw.map { kotlin.math.exp(it.toDouble()) }
            val total = exp.sum()
            FloatArray(raw.size) { (exp[it] / total).toFloat() }
        }
    }

    private fun loadModelFile(): MappedByteBuffer {
        val afd: AssetFileDescriptor = context.assets.openFd(MODEL_FILE)
        FileInputStream(afd.fileDescriptor).use { fis ->
            return fis.channel.map(FileChannel.MapMode.READ_ONLY, afd.startOffset, afd.declaredLength)
        }
    }

    companion object {
        private const val TAG = "AdultClassifier"
        private const val MODEL_FILE = "model.tflite"
        private const val CONFIG_FILE = "model.config"
        private const val LABEL_COUNT = 4
        private const val DEFAULT_INPUT_SIZE = 224
        private const val THREADS = 2
        private const val adultThreshold = 0.5f

        /** A confirmer avec la config d'entrainement reelle du modele (reponse utilisateur en attente). */
        val DEFAULT_INPUT_SCALE: InputScale = InputScale.ZERO_ONE
    }
}
