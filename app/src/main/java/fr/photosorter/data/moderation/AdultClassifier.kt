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
 * cle scale). En l'absence de config, DEFAULT_INPUT_SCALE (hypothese a confirmer).
 *
 * Instrumentation : chaque inference journalise (tag AdultClassifier) l'echelle active
 * et les scores bruts par label, pour diagnostiquer sur appareil l'echelle attendue.
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
            Log.i(TAG, "InputScale=$inputScale inputSize=$inputSize modele=${if (isReady) \"ok\" else \"KO\"}")
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
            logScores(uri, output[0])
            val probs = normalize(output[0])
            val adultScore = (probs[2] + probs[3]).coerceIn(0f, 1f)
            Result.Scored(isAdult = adultScore >= ADULT_THRESHOLD)
        } catch (t: Throwable) {
            Log.w(TAG, "Inference echouee : ${t.message}")
            markUnavailable("inference: ${t.message}")
        } finally {
            bitmap.recycle()
        }
    }

    private fun logScores(uri: Uri, raw: FloatArray) {
        val probs = normalize(raw)
        val adultScore = (probs[2] + probs[3]).coerceIn(0f, 1f)
        Log.d(
            TAG,
            "Scores[$uri] scale=$inputScale raw=[${raw.joinToString(", ") { fmt(it) }}] " +
                "probs=[${probs.joinToString(", ") { fmt(it) }}] adult=$adultScore " +
                "-> ${if (adultScore >= ADULT_THRESHOLD) \"ADULTE\" else \"normale\"}"
        )
    }

    private fun fmt(v: Float): String = String.format(java.util.Locale.US, "%.4f", v)

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
        }.getOrNull() ?: DEFAULT_INPUT_SCALE
    }

    private fun loadModelFile(): ByteBuffer {
        // Essai 1 : openFd (fonctionne si tflite non compresse dans l'APK)
        runCatching {
            val afd: AssetFileDescriptor = context.assets.openFd(MODEL_FILE)
            FileInputStream(afd.fileDescriptor).use { fis ->
                fis.channel.map(FileChannel.MapMode.READ_ONLY, afd.startOffset, afd.declaredLength)
            }
        }.onSuccess { return it }
        // Repli : lecture complete en ByteBuffer (asset compresse ou openFd indisponible)
        val bytes = context.assets.open(MODEL_FILE).use { it.readBytes() }
        return ByteBuffer.allocateDirect(bytes.size).apply {
            order(ByteOrder.nativeOrder())
            put(bytes)
            rewind()
        }
    }

    private fun decodeScaled(uri: Uri): Bitmap? {
        val opts = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        openStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
            ?: return null
        opts.inSampleSize = computeSampleSize(opts.outWidth, opts.outHeight, inputSize)
        opts.inJustDecodeBounds = false
        val sampled = openStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
            ?: return null
        return Bitmap.createScaledBitmap(sampled, inputSize, inputSize, true)
            .also { if (it !== sampled) sampled.recycle() }
    }

    private fun openStream(uri: Uri): java.io.InputStream? {
        // Essai contentResolver
        runCatching {
            context.contentResolver.openInputStream(uri)
        }.onSuccess { stream -> if (stream != null) return stream }
        // Repli file:// direct
        if (uri.scheme == "file") {
            runCatching {
                val path = uri.path ?: return null
                FileInputStream(File(path))
            }.onSuccess { return it }
        }
        return null
    }

    private fun computeSampleSize(w: Int, h: Int, target: Int): Int {
        var size = 1
        while ((w / size) > target * 2 && (h / size) > target * 2) size *= 2
        return size
    }

    private fun toInputBuffer(bitmap: Bitmap): ByteBuffer {
        val buf = ByteBuffer.allocateDirect(1 * inputSize * inputSize * 3 * 4)
        buf.order(ByteOrder.nativeOrder())
        val pixels = IntArray(inputSize * inputSize)
        bitmap.getPixels(pixels, 0, inputSize, 0, 0, inputSize, inputSize)
        for (px in pixels) {
            buf.putFloat(inputScale.apply((px shr 16) and 0xFF))
            buf.putFloat(inputScale.apply((px shr 8) and 0xFF))
            buf.putFloat(inputScale.apply(px and 0xFF))
        }
        buf.rewind()
        return buf
    }

    private fun normalize(raw: FloatArray): FloatArray {
        val sum = raw.sum()
        // Si les logits ne sont pas deja normalises (softmax absent ou somme != 1)
        if (sum < 0.9f || sum > 1.1f) {
            val expArr = raw.map { kotlin.math.exp(it.toDouble()).toFloat() }.toFloatArray()
            val expSum = expArr.sum()
            return if (expSum > 0f) FloatArray(expArr.size) { expArr[it] / expSum } else raw
        }
        return raw
    }

    companion object {
        private const val TAG = "AdultClassifier"
        private const val MODEL_FILE = "model.tflite"
        private const val CONFIG_FILE = "model.config"
        private const val LABEL_COUNT = 4
        private const val DEFAULT_INPUT_SIZE = 224
        private const val THREADS = 2
        private const val ADULT_THRESHOLD = 0.5f
        private val DEFAULT_INPUT_SCALE = InputScale.ZERO_ONE
    }
}
