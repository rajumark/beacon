package io.github.rajumark.hoverfly.beacon.internal

/**
 * Text -> hashed feature ids. A 1:1 port of beacon/features.py (the spec); parity is checked
 * against Python-generated test vectors in ParityTest.
 *
 *  normalize: NFKC, drop URLs / e-mails / @mentions, lowercase, every char that is not a letter
 *             or mark becomes a space, collapse, trim, cut to 256 code points.
 *  features:  on " " + norm + " " (code points): char 1..4-grams (group 0..3, skipping the lone
 *             space unigram) and whole words wrapped in spaces (group 4), hashed with FNV-1a
 *             (64-bit state) over code points into each group's table. 0 is padding.
 *
 * The 1.x Android version used java.util.regex; here the same patterns are matched by hand,
 * with character classes from [UnicodeTables], so every platform gives identical results.
 */
internal object Featurizer {
    const val MAX_CHARS = 256

    /** Python's Unicode \s, spelled out (the WS class of the original regex). */
    fun isWs(cp: Int): Boolean = when (cp) {
        0x20, 0x85, 0xA0, 0x1680, 0x2028, 0x2029, 0x202F, 0x205F, 0x3000 -> true
        else -> cp in 0x09..0x0D || cp in 0x1C..0x1F || cp in 0x2000..0x200A
    }

    private fun isWord(cp: Int) = UnicodeTables.contains(UnicodeTables.WORD, cp)

    private fun isLetterOrMark(cp: Int) = UnicodeTables.contains(UnicodeTables.LETTER_OR_MARK, cp)

    fun normalize(text: String): String {
        var t = stripUrls(nfkc(text)).lowercase()
        t = collapseNonLetters(t).trim(' ')
        if (t.cpCount() > MAX_CHARS) {
            var end = 0
            repeat(MAX_CHARS) { end += cpLen(t.cpAt(end)) }
            t = t.substring(0, end).trimEnd(' ')
        }
        return t
    }

    /**
     * Same as Java's `replaceAll(" ")` with the regex
     * `(https?://|www\.)\S+ | \S+@\S+\.\S+ | @[\p{L}\p{N}_]+` (\S = not [isWs]).
     * Alternatives are tried in that order at each position, leftmost match first.
     */
    fun stripUrls(t: String): String {
        val n = t.length
        // nextAt[k] = index of the first '@' at or after k (n if none)
        val nextAt = IntArray(n + 1)
        nextAt[n] = n
        for (k in n - 1 downTo 0) nextAt[k] = if (t[k] == '@') k else nextAt[k + 1]
        val sb = StringBuilder(n)
        var runEnd = -1   // end of the \S run containing i
        var runLastDot = -1 // last '.' in that run that still has a \S after it
        var i = 0
        while (i < n) {
            val cp = t.cpAt(i)
            if (!isWs(cp)) {
                if (i >= runEnd) {
                    runEnd = i
                    while (runEnd < n) { val c = t.cpAt(runEnd); if (isWs(c)) break; runEnd += cpLen(c) }
                    runLastDot = if (runEnd - 2 >= i) t.lastIndexOf('.', runEnd - 2) else -1
                }
                // (https?://|www\.)\S+
                val prefix = when {
                    t.startsWith("https://", i) -> 8
                    t.startsWith("http://", i) -> 7
                    t.startsWith("www.", i) -> 4
                    else -> 0
                }
                if (prefix > 0 && i + prefix < runEnd) { sb.append(' '); i = runEnd; continue }
                // \S+@\S+\.\S+ : the whole run, if some '@' (not first) has a '.' at least 2 later with a \S after it
                val at = if (i + 1 <= n) nextAt[i + 1] else n
                if (at < runEnd && runLastDot >= at + 2) { sb.append(' '); i = runEnd; continue }
            }
            // @[\p{L}\p{N}_]+
            if (cp == '@'.code) {
                var j = i + 1
                while (j < n) { val c = t.cpAt(j); if (!isWord(c)) break; j += cpLen(c) }
                if (j > i + 1) { sb.append(' '); i = j; continue }
            }
            sb.appendCp(cp)
            i += cpLen(cp)
        }
        return sb.toString()
    }

    /** Same as `replaceAll("[^\\p{L}\\p{M}]+", " ")`. */
    fun collapseNonLetters(t: String): String {
        val sb = StringBuilder(t.length)
        var inGap = false
        var i = 0
        while (i < t.length) {
            val cp = t.cpAt(i)
            if (isLetterOrMark(cp)) { sb.appendCp(cp); inGap = false }
            else if (!inGap) { sb.append(' '); inGap = true }
            i += cpLen(cp)
        }
        return sb.toString()
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
        val n = norm.cpCount()
        val s = IntArray(n + 2)
        s[0] = 32; s[n + 1] = 32
        var i0 = 0
        var p = 1
        while (i0 < norm.length) {
            val cp = norm.cpAt(i0)
            s[p++] = cp
            i0 += cpLen(cp)
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
