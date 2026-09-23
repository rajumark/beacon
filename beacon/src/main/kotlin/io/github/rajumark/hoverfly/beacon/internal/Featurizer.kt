package io.github.rajumark.hoverfly.beacon.internal

import java.text.Normalizer
import java.util.Locale
import java.util.regex.Pattern

/**
 * Text -> hashed feature ids. A 1:1 port of beacon/features.py (the spec); parity is checked
 * against Python-generated test vectors in ParityTest.
 *
 *  normalize: NFKC, drop URLs / e-mails / @mentions, lowercase, every char that is not a letter
 *             or mark becomes a space, collapse, trim, cut to 256 code points.
 *  features:  on " " + norm + " " (code points): char 1..4-grams (group 0..3, skipping the lone
 *             space unigram) and whole words wrapped in spaces (group 4), hashed with FNV-1a
 *             (64-bit state) over code points into each group's table. 0 is padding.
 */
internal object Featurizer {
    const val MAX_CHARS = 256
    // Python's Unicode \S and \w spelled out, so desktop Java and Android (ICU, which has no
    // UNICODE_CHARACTER_CLASS flag) match exactly the same characters.
    private const val WS = " \\t\\n\\x0B\\f\\r\\x1C-\\x1F\\u0085\\u00A0\\u1680\\u2000-\\u200A\\u2028\\u2029\\u202F\\u205F\\u3000"
    private const val NS = "[^$WS]"
    private const val W = "[\\p{L}\\p{N}_]"
    private val URL = Pattern.compile("(https?://|www\\.)$NS+|$NS+@$NS+\\.$NS+|@$W+")
    private val NON_LETTER = Pattern.compile("[^\\p{L}\\p{M}]+")

    fun normalize(text: String): String {
        var t = Normalizer.normalize(text, Normalizer.Form.NFKC)
        t = URL.matcher(t).replaceAll(" ").lowercase(Locale.ROOT)
        t = NON_LETTER.matcher(t).replaceAll(" ").trim(' ')
        val n = t.codePointCount(0, t.length)
        if (n > MAX_CHARS) t = t.substring(0, t.offsetByCodePoints(0, MAX_CHARS)).trimEnd(' ')
        return t
    }

    /** FNV-1a over code points with a 64-bit state and the 32-bit FNV constants (see features.py). */
    private fun fnv(s: IntArray, a: Int, b: Int, seed: Int): Long {
        var h = 0x811C9DC5L xor (seed.toLong() and 0xFFFFFFFFL)
        for (k in a until b) h = (h xor s[k].toLong()) * 0x01000193L
        return h
    }

    private fun bucket(h: Long, size: Int): Int = (h.toULong() % (size - 1).toULong()).toInt() + 1

    /**
     * Feature ids of a normalized string: out[g][0 until counts[g]] holds the ids of group g.
     * Returns counts (the number of ids per group); slots past the count are not cleared.
     */
    fun features(norm: String, buckets: IntArray, seeds: IntArray, maxFeats: Int, out: Array<IntArray>): IntArray {
        val counts = IntArray(buckets.size)
        if (norm.isEmpty()) return counts
        val n = norm.codePointCount(0, norm.length)
        val s = IntArray(n + 2)
        s[0] = 32; s[n + 1] = 32
        var i0 = 0
        var p = 1
        while (i0 < norm.length) {
            val cp = norm.codePointAt(i0)
            s[p++] = cp
            i0 += Character.charCount(cp)
        }
        val len = s.size
        for (g in 0 until 4) {
            val m = g + 1
            var c = 0
            var i = 0
            while (i <= len - m && c < maxFeats) {
                if (!(m == 1 && s[i] == 32)) {
                    out[g][c++] = bucket(fnv(s, i, i + m, seeds[g]), buckets[g])
                }
                i++
            }
            counts[g] = c
        }
        var c = 0
        var start = 0
        for (i in 1 until len) {
            if (s[i] == 32) {
                if (i - start > 1 && c < maxFeats) out[4][c++] = bucket(fnv(s, start, i + 1, seeds[4]), buckets[4])
                start = i
            }
        }
        counts[4] = c
        return counts
    }
}
