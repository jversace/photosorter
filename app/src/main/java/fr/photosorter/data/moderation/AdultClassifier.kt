package fr.photosorter.data.moderation

import android.content.Context
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
 * cle scale). En l'absence de config, DEFAULT_INPUT_SCALE.
 *
 * Instrumentation : chaque inference journalise (tag AdultClassifier) l'echelle active
 * et les scores bruts par label.
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
                ZERO_ONE      -> v / 255f
                ZERO_255      -> v
                MINUS_ONE_ONE -> (v / 127.5f) - 1f
            }
        }

        companion object {
            fun parse(raw: String?): InputScale? = when (raw?.trim()?.uppercase()) {
                "ZERO_ONE"      -> ZERO_ONE
                "ZERO_255"      -> ZERO_255
                "MINUS_ONE_ONE" -> MINUS_ONE_ONE
                else            -> null
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
            }.onFailure { e ->
                Log.w(TAG, "Modele TFLite indisponible : ${e.javaClass.simpleName}: ${e.message}")
            }.getOrNull()
            interpreter?.let { interp ->
                val shape = interp.getInputTensor(0).shape()
                if (shape.size == 4 && shape[1] > 0) inputSize = shape[1]
            }
            Log.i(TAG, "InputScale=$inputScale inputSize=$inputSize modele=${if (isReady) "ok" else "KO"}")
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
            Log.w(TAG, "Inference echouee : ${t.javaClass.simpleName}: ${t.message}")
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
                "-> ${if (adultScore >= ADULT_THRESHOLD) "ADULTE" else "normale"}"
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
                reader.readLines()
                    .firstOrNull { it.trim().startsWith("scale") }
                    ?.substringAfter("=", "")
                    ?.let { InputScale.parse(it) }
            }
        }.getOrNull() ?: run {
            Log.w(TAG, "Pas de config d'echelle : defaut $DEFAULT_INPUT_SCALE (a confirmer)")
            DEFAULT_INPUT_SCALE
        }
    }

    /**
     * Charge model.tflite depuis les assets dans un DirectByteBuffer.
     *
     * Essai 1 : AssetManager.openFd() + FileChannel.map() — necessite que l'asset
     *   soit stocke non compresse dans l'APK (noCompress += "tflite" dans build.gradle).
     *
     * Essai 2 : lecture octet par octet via InputStream dans un DirectByteBuffer.
     *   Fonctionne meme si l'asset est compresse (AGP < 3.6 ou config manquante).
     *   TFLite exige un DirectByteBuffer ; un HeapByteBuffer (wrap/allocate)
     *   leve IllegalArgumentException a l'initialisation de l'Interpreter.
     */
    private fun loadModelFile(): ByteBuffer {
        // Essai 1 : mmap via openFd (zero-copy, prefere)
        runCatching {
            val afd = context.assets.openFd(MODEL_FILE)
            val fis = FileInputStream(afd.fileDescriptor)
            val mapped = fis.channel.map(
                FileChannel.MapMode.READ_ONLY,
                afd.startOffset,
                afd.declaredLength
            )
            fis.close()
            afd.close()
            Log.i(TAG, "Modele charge via openFd (mmap)")
            return mapped
        }.onFailure {
            Log.w(TAG, "openFd echoue, repli InputStream->DirectByteBuffer : ${it.message}")
        }

        // Essai 2 : InputStream -> DirectByteBuffer
        // IMPORTANT : ByteBuffer.allocateDirect() obligatoire ; wrap() retourne
        // un HeapByteBuffer que TFLite refuse (IllegalArgumentException).
        val bytes = context.assets.open(MODEL_FILE).use { it.readBytes() }
        val direct = ByteBuffer.allocateDirect(bytes.size).order(ByteOrder.nativeOrder())
        direct.put(bytes)
        direct.rewind()
        Log.i(TAG, "Modele charge via InputStream->DirectByteBuffer (${bytes.size} octets)")
        return direct
    }

    private fun decodeScaled(uri: Uri): Bitmap? = runCatching {
        val opts = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        openStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
        opts.inSampleSize = calcSampleSize(opts.outWidth, opts.outHeight, inputSize)
        opts.inJustDecodeBounds = false
        openStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
    }.getOrNull()

    private fun openStream(uri: Uri) = when (uri.scheme) {
        "file" -> java.io.FileInputStream(uri.path!!)
        else   -> context.contentResolver.openInputStream(uri)
    }

    private fun calcSampleSize(w: Int, h: Int, target: Int): Int {
        var size = 1
        while (w / size > target * 2 || h / size > target * 2) size *= 2
        return size
    }

    private fun toInputBuffer(bitmap: Bitmap): ByteBuffer {
        val scaled = Bitmap.createScaledBitmap(bitmap, inputSize, inputSize, true)
        val buf = ByteBuffer.allocateDirect(1 * inputSize * inputSize * 3 * 4)
            .order(ByteOrder.nativeOrder())
        val pixels = IntArray(inputSize * inputSize)
        scaled.getPixels(pixels, 0, inputSize, 0, 0, inputSize, inputSize)
        for (px in pixels) {
            buf.putFloat(inputScale.apply((px shr 16) and 0xFF))
            buf.putFloat(inputScale.apply((px shr 8)  and 0xFF))
            buf.putFloat(inputScale.apply( px         and 0xFF))
        }
        buf.rewind()
        if (scaled !== bitmap) scaled.recycle()
        return buf
    }

    private fun normalize(raw: FloatArray): FloatArray {
        val max = raw.max()
        if (max <= 0f) return raw.copyOf()
        val expSum = raw.sumOf { Math.exp((it - max).toDouble()) }
        return FloatArray(raw.size) { i ->
            (Math.exp((raw[i] - max).toDouble()) / expSum).toFloat()
        }
    }

    companion object {
        private const val TAG            = "AdultClassifier"
        private const val MODEL_FILE     = "model.tflite"
        private const val CONFIG_FILE    = "model.config"
        private const val LABEL_COUNT    = 4
        private const val DEFAULT_INPUT_SIZE  = 224
        private const val ADULT_THRESHOLD    = 0.5f
        private const val THREADS       = 2
        private val DEFAULT_INPUT_SCALE = InputScale.ZERO_ONE
    }
}
