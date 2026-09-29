package io.github.rajumark.hoverfly.beacon

import io.github.rajumark.hoverfly.beacon.internal.Featurizer
import io.github.rajumark.hoverfly.beacon.internal.TestData
import io.github.rajumark.hoverfly.beacon.internal.decodeChunks
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Checks the Kotlin port against Python on testvectors.tsv (written by
 * beacon/scripts/export_testvectors.py): identical normalization and feature ids, and the same
 * label with the same probability as the Python reference. Runs on every target.
 */
class ParityTest {
    private val beacon = testBeacon()

    private val vectors = decodeChunks(TestData.files.getValue("testvectors.tsv")).decodeToString()
        .lineSequence().filter { it.isNotEmpty() }.map { it.split("\t") }.toList()

    @Test
    fun normalizationMatchesPython() {
        var bad = 0
        for (v in vectors) {
            val norm = Featurizer.normalize(unescape(v[0]))
            if (norm != unescape(v[1])) { bad++; println("NORM MISMATCH: '${v[0]}' kt='$norm' py='${v[1]}'") }
        }
        println("normalization: ${vectors.size - bad}/${vectors.size} identical")
        assertEquals(958, vectors.size)
        assertEquals(0, bad)
    }

    @Test
    fun featureIdsMatchPython() {
        var bad = 0
        for (v in vectors) if (beacon.featureIds(unescape(v[1])) != v[2]) { bad++; println("IDS MISMATCH: '${v[0]}'") }
        println("feature ids: ${vectors.size - bad}/${vectors.size} identical")
        assertEquals(0, bad)
    }

    @Test
    fun predictionsMatchPython() {
        var bad = 0
        var maxDiff = 0f
        for (v in vectors) {
            val r = beacon.detect(unescape(v[0]))
            val p = v[4].toFloat()
            if (r.label != v[3]) {
                // allowed only when the reference itself was a near-tie
                if (abs(r.confidence - p) > 1e-3f) { bad++; println("LABEL MISMATCH: '${v[0]}' kt=${r.label} py=${v[3]}") }
            } else {
                maxDiff = maxOf(maxDiff, abs(r.confidence - p))
            }
        }
        println("predictions: ${vectors.size - bad}/${vectors.size} match, max prob diff $maxDiff")
        assertEquals(0, bad)
        assertTrue(maxDiff < 1e-3f, "probability drift $maxDiff")
    }

    companion object {
        private var shared: Beacon? = null

        /** One instance per test run: loading is the slow part on the native and web targets. */
        fun testBeacon(): Beacon = shared ?: Beacon().also { shared = it }

        /** The vectors escape tabs and newlines inside texts. */
        fun unescape(s: String) = buildString {
            var i = 0
            while (i < s.length) {
                if (s[i] == '\\' && i + 1 < s.length) {
                    append(when (s[i + 1]) { 't' -> '\t'; 'n' -> '\n'; else -> s[i + 1] }); i += 2
                } else { append(s[i]); i++ }
            }
        }
    }
}
