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
 * Instrumentation (instruction n°248) : chaque inference journalise (tag AdultClassifier)
 * l'echelle active, les scores bruts, les scores normalises et le score adulte, pour
 * diagnostiquer sur appareil (Logcat) ce que le modele retourne reellement.
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
            val probs = normalize(output[0])
            val adultScore = (probs[2] + probs[3]).coerceIn(0f, 1f)
            val isAdult = adultScore >= ADULT_THRESHOLD
            logScores(uri, output[0], probs, adultScore, isAdult)
            Result.Scored(isAdult = isAdult)
        } catch (t: Throwable) {
            Log.w(TAG, "Inference echouee : ${t.javaClass.simpleName}: ${t.message}")
            markUnavailable("inference: ${t.message}")
        } finally {
            bitmap.recycle()
        }
    }

    /**
     * Journalisation de diagnostic (instruction n°248) : scores bruts, scores normalises,
     * score adulte vs seuil et resultat. Permet de lire Logcat pour reperer l'echelle
     * d'entree / le mapping de sortie reellement attendus par le modele.
     */
    private fun logScores(
        uri: Uri,
        raw: FloatArray,
        probs: FloatArray,
        adultScore: Float,
        isAdult: Boolean
    ) {
        Log.d(TAG, "Classification[$uri] scale=$inputScale")
        Log.d(TAG, "Scores bruts: ${raw.toList()}")
        Log.d(TAG, "Scores normalisés: ${probs.toList()}")
        Log.d(
            TAG,
            "Score adulte (porn+sexy): $adultScore, seuil: $ADULT_THRESHOLD, " +
                "résultat: ${if (isAdult) "ADULTE" else "normale"}"
        )
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

    private fun decodeScaled(uri: Uri): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        openStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val opts = BitmapFactory.Options().apply {
            inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight)
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return openStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
            ?.let { Bitmap.createScaledBitmap(it, inputSize, inputSize, true).also { s -> if (s !== it) it.recycle() } }
    }

    private fun openStream(uri: Uri): java.io.InputStream? {
        runCatching { context.contentResolver.openInputStream(uri) }.getOrNull()?.let { return it }
        if (uri.scheme == "file") {
            val path = uri.path ?: return null
            return runCatching { FileInputStream(File(path)) }.getOrNull()
        }
        return null
    }

    private fun sampleSizeFor(width: Int, height: Int): Int {
        var sample = 1
        while (width / (sample * 2) >= inputSize && height / (sample * 2) >= inputSize) sample *= 2
        return sample
    }

    private fun toInputBuffer(bitmap: Bitmap): ByteBuffer {
        val side = inputSize
        val resized = if (bitmap.width == side && bitmap.height == side) bitmap
            else Bitmap.createScaledBitmap(bitmap, side, side, true)
        val buffer = ByteBuffer.allocateDirect(1 * side * side * 3 * 4).order(ByteOrder.nativeOrder())
        val pixels = IntArray(side * side)
        resized.getPixels(pixels, 0, side, 0, 0, side, side)
        for (p in pixels) {
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            buffer.putFloat(inputScale.apply(r))
            buffer.putFloat(inputScale.apply(g))
            buffer.putFloat(inputScale.apply(b))
        }
        if (resized !== bitmap) resized.recycle()
        buffer.rewind()
        return buffer
    }

    private fun normalize(raw: FloatArray): FloatArray {
        val sum = raw.sum()
        return if (sum in 0.9f..1.1f) raw else {
            val max = raw.maxOrNull() ?: 0f
            val exps = raw.map { kotlin.math.exp((it - max).toDouble()).toFloat() }
            val s = exps.sum()
            if (s <= 0f) raw else FloatArray(raw.size) { exps[it] / s }
        }
    }

    /**
     * Charge le modele en ByteBuffer. Cas nominal : openFd() + map (asset non compresse).
     * Si l'asset est compresse, openFd() echoue ("can not be opened as a file descriptor;
     * it is probably compressed") : repli sur une copie de l'InputStream dans un ByteBuffer
     * DIRECT (obligatoire pour TFLite, un buffer heap serait rejete).
     */
    private fun loadModelFile(): ByteBuffer {
        return try {
            context.assets.openFd(MODEL_FILE).use { afd: AssetFileDescriptor ->
                FileInputStream(afd.fileDescriptor).use { input ->
                    input.channel.map(FileChannel.MapMode.READ_ONLY, afd.startOffset, afd.declaredLength)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "openFd a echoue (${e.javaClass.simpleName}: ${e.message}), repli copie flux direct : $MODEL_FILE")
            val bytes = context.assets.open(MODEL_FILE).use { it.readBytes() }
            val direct = ByteBuffer.allocateDirect(bytes.size).order(ByteOrder.nativeOrder())
            direct.put(bytes)
            direct.rewind()
            direct
        }
    }

    private companion object {
        const val TAG = "AdultClassifier"
        const val MODEL_FILE = "model.tflite"
        const val CONFIG_FILE = "model.config"
        const val LABEL_COUNT = 4
        const val THREADS = 2
        const val DEFAULT_INPUT_SIZE = 224
        const val ADULT_THRESHOLD = 0.5f
        val DEFAULT_INPUT_SCALE = InputScale.ZERO_ONE
    }
}
