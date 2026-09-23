package io.github.rajumark.hoverfly.beacon.internal

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.exp
import kotlin.math.sqrt

/**
 * The Beacon network in plain Kotlin (same math as beacon/runtime.py):
 *
 *   5 feature groups (char 1..4-grams, words) -> mean of int8 embeddings per group
 *   -> LayerNorm -> Linear -> ReLU -> Linear -> softmax over the labels
 *
 * Stateless after loading apart from per-thread scratch buffers, so one instance can serve
 * several threads.
 */
internal class Network(bytes: ByteArray) {

    class Label(val label: String, val name: String)

    private val buckets: IntArray
    private val seeds: IntArray
    private val dim: Int
    private val hidden: Int
    private val maxFeats: Int
    private val emb: Array<ByteArray>       // int8 rows, per group
    private val embScale: Array<FloatArray> // one scale per row
    private val gamma: FloatArray
    private val beta: FloatArray
    private val w1: FloatArray
    private val b1: FloatArray
    private val w2: FloatArray
    private val b2: FloatArray
    val labels: List<Label>

    init {
        val bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val magic = ByteArray(4).also { bb.get(it) }
        require(String(magic, Charsets.US_ASCII) == "BCN1") { "not a .beacon file" }
        bb.int // version
        val g = bb.int; dim = bb.int; hidden = bb.int
        val nl = bb.int; maxFeats = bb.int
        buckets = IntArray(g) { bb.int }
        seeds = IntArray(g) { bb.int }
        // per group: int8 rows, then one f16 scale per row
        val e = ArrayList<ByteArray>(g)
        val s = ArrayList<FloatArray>(g)
        for (k in 0 until g) {
            e.add(ByteArray(buckets[k] * dim).also { bb.get(it) })
            s.add(FloatArray(buckets[k]) { halfToFloat(bb.short) })
        }
        emb = e.toTypedArray()
        embScale = s.toTypedArray()
        val d = g * dim
        gamma = FloatArray(d) { bb.float }
        beta = FloatArray(d) { bb.float }
        w1 = readQ(bb, hidden, d)
        b1 = FloatArray(hidden) { bb.float }
        w2 = readQ(bb, nl, hidden)
        b2 = FloatArray(nl) { bb.float }
        val js = ByteArray(bb.int).also { bb.get(it) }
        labels = parseLabels(String(js, Charsets.UTF_8))
        require(labels.size == nl) { "label count mismatch" }
    }

    private class Scratch(groups: Int, maxFeats: Int) {
        val ids = Array(groups) { IntArray(maxFeats) }
    }

    // ThreadLocal.withInitial needs API 26.
    private val scratch = object : ThreadLocal<Scratch>() {
        override fun initialValue() = Scratch(buckets.size, maxFeats)
    }

    /** Feature ids of normalized text, one "a,b,c" list per group joined by "|" (for the parity test). */
    fun featureIds(norm: String): String {
        val ids = scratch.get()!!.ids
        val counts = Featurizer.features(norm, buckets, seeds, maxFeats, ids)
        return buckets.indices.joinToString("|") { g -> (0 until counts[g]).joinToString(",") { ids[g][it].toString() } }
    }

    /** Probability for every label (same order as [labels]); [norm] must be non-empty. */
    fun probs(norm: String): FloatArray {
        val ids = scratch.get()!!.ids
        val counts = Featurizer.features(norm, buckets, seeds, maxFeats, ids)
        val nGroups = buckets.size
        val d = nGroups * dim
        val h = FloatArray(d)
        for (g in 0 until nGroups) {
            val e = emb[g]; val sc = embScale[g]; val n = counts[g]
            if (n == 0) continue
            val o = g * dim
            for (k in 0 until n) {
                val r = ids[g][k]
                val s = sc[r]
                val base = r * dim
                for (j in 0 until dim) h[o + j] += e[base + j] * s
            }
            val inv = 1f / n
            for (j in 0 until dim) h[o + j] *= inv
        }
        // LayerNorm
        var mu = 0f
        for (v in h) mu += v
        mu /= d
        var varSum = 0f
        for (v in h) { val x = v - mu; varSum += x * x }
        val inv = 1f / sqrt(varSum / d + 1e-5f)
        for (j in 0 until d) h[j] = (h[j] - mu) * inv * gamma[j] + beta[j]
        // hidden layer
        val z = FloatArray(hidden)
        for (i in 0 until hidden) {
            var a = b1[i]
            val o = i * d
            for (j in 0 until d) a += w1[o + j] * h[j]
            z[i] = if (a > 0f) a else 0f
        }
        // logits -> softmax
        val nl = labels.size
        val out = FloatArray(nl)
        var mx = Float.NEGATIVE_INFINITY
        for (i in 0 until nl) {
            var a = b2[i]
            val o = i * hidden
            for (j in 0 until hidden) a += w2[o + j] * z[j]
            out[i] = a
            if (a > mx) mx = a
        }
        var sum = 0f
        for (i in 0 until nl) { out[i] = exp(out[i] - mx); sum += out[i] }
        for (i in 0 until nl) out[i] /= sum
        return out
    }

    private companion object {
        /** int8 matrix [rows x cols] followed by one f32 scale per row -> dequantized floats */
        fun readQ(bb: ByteBuffer, rows: Int, cols: Int): FloatArray {
            val q = ByteArray(rows * cols).also { bb.get(it) }
            val s = FloatArray(rows) { bb.float }
            return FloatArray(rows * cols) { q[it] * s[it / cols] }
        }

        fun halfToFloat(h: Short): Float {
            val bits = h.toInt() and 0xFFFF
            val sign = bits ushr 15
            val exp = (bits ushr 10) and 0x1F
            val mant = bits and 0x3FF
            val v = when (exp) {
                0 -> mant / 1024f * 6.1035156e-5f
                31 -> if (mant == 0) Float.POSITIVE_INFINITY else Float.NaN
                else -> Float.fromBits(((exp - 15 + 127) shl 23) or (mant shl 13))
            }
            return if (sign == 1) -v else v
        }

        /** minimal parser for [{"label":"..","name":".."},...] (no JSON dependency) */
        fun parseLabels(js: String): List<Label> {
            val out = ArrayList<Label>()
            // `}` is escaped too: Android's ICU regex engine (unlike desktop Java) rejects a bare `}`.
            val re = Regex("\\{\"label\":\"((?:[^\"\\\\]|\\\\.)*)\",\"name\":\"((?:[^\"\\\\]|\\\\.)*)\"\\}")
            for (m in re.findAll(js)) out.add(Label(unescape(m.groupValues[1]), unescape(m.groupValues[2])))
            return out
        }

        fun unescape(s: String): String {
            if (!s.contains('\\')) return s
            val sb = StringBuilder()
            var i = 0
            while (i < s.length) {
                val c = s[i]
                if (c == '\\' && i + 1 < s.length) {
                    when (val n = s[i + 1]) {
                        'u' -> { sb.append(s.substring(i + 2, i + 6).toInt(16).toChar()); i += 6; continue }
                        'n' -> sb.append('\n')
                        't' -> sb.append('\t')
                        else -> sb.append(n)
                    }
                    i += 2
                } else { sb.append(c); i++ }
            }
            return sb.toString()
        }
    }
}
