package io.github.rajumark.hoverfly.beacon

import io.github.rajumark.hoverfly.beacon.internal.Featurizer
import io.github.rajumark.hoverfly.beacon.internal.Network
import io.github.rajumark.hoverfly.beacon.internal.readModelFile
import kotlin.jvm.JvmOverloads

/**
 * On-device language detection for 211 languages and scripts, including romanised Indian
 * languages such as Hinglish and Tanglish.
 *
 * ```
 * Beacon().use { beacon ->
 *     beacon.detect("Kal milte hain bhai").label   // "hin_Latn"
 * }
 * ```
 *
 * Everything runs locally on Android, iOS, macOS, the JVM and the web: the ~8.5 MB model ships
 * inside the library, there is no network, no permission and no dependency. Creating an instance
 * reads the model (up to a few hundred ms), so create it off the main thread where the platform
 * has one and keep it around; [detect] takes well under a
 * millisecond and is safe to call from several threads.
 */
public class Beacon internal constructor(model: ByteArray) : AutoCloseable {

    /** Loads the model bundled in the library. */
    public constructor() : this(readModelFile(MODEL_FILE))

    private var network: Network? = Network(model)

    /** Every label the model can return, e.g. "hin_Latn", "tam_Taml", "spa_Latn". */
    public val supportedLabels: List<String> = requireNotNull(network).labels.map { it.label }

    init {
        // The first calls run interpreted; pay that here (off the UI thread) instead of on the first keystroke.
        repeat(WARM_UP) { detect("warm up the model $it") }
    }

    /** The most likely language of [text], or [DetectedLanguage.UNDETERMINED] when it has no letters. */
    public fun detect(text: String): DetectedLanguage =
        candidates(text, 1).firstOrNull() ?: DetectedLanguage.UNDETERMINED

    /**
     * The [limit] most likely languages of [text], best first. Empty when the text has no letters.
     * Useful for close pairs such as Hindi/Urdu in Latin script or Indonesian/Malay.
     */
    @JvmOverloads
    public fun candidates(text: String, limit: Int = 3): List<DetectedLanguage> {
        require(limit > 0) { "limit must be positive" }
        val net = checkNotNull(network) { "Beacon is closed" }
        val p = probabilities(text) ?: return emptyList()
        return topK(p, minOf(limit, p.size)).map { i ->
            val l = net.labels[i]
            DetectedLanguage(l.label, l.label.substringBefore('_'), l.label.substringAfter('_'), l.name, p[i])
        }
    }

    /** Releases the model (about 10 MB of heap). The instance cannot be used afterwards. */
    override fun close() {
        network = null
    }

    /** Probability for every label (same order as [supportedLabels]), or null if the text has no letters. */
    internal fun probabilities(text: String): FloatArray? {
        val net = checkNotNull(network) { "Beacon is closed" }
        val norm = Featurizer.normalize(text)
        return if (norm.isEmpty()) null else net.probs(norm)
    }

    internal fun featureIds(norm: String): String = checkNotNull(network) { "Beacon is closed" }.featureIds(norm)

    internal companion object {
        const val MODEL_FILE = "beacon.beacon"
        const val WARM_UP = 20

        /** Indices of the k largest values, best first (insertion into a small array; k << n). */
        fun topK(p: FloatArray, k: Int): List<Int> {
            val idx = IntArray(k) { -1 }
            val v = FloatArray(k) { Float.NEGATIVE_INFINITY }
            for (i in p.indices) {
                val x = p[i]
                if (x <= v[k - 1]) continue
                var j = k - 1
                while (j > 0 && v[j - 1] < x) { v[j] = v[j - 1]; idx[j] = idx[j - 1]; j-- }
                v[j] = x; idx[j] = i
            }
            return idx.filter { it >= 0 }
        }
    }
}
