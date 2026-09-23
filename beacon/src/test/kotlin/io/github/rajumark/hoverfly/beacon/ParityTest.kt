package io.github.rajumark.hoverfly.beacon

import io.github.rajumark.hoverfly.beacon.internal.Featurizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.abs

/**
 * Checks the Kotlin port against Python on testvectors.tsv (written by
 * beacon/scripts/export_testvectors.py): identical normalization and feature ids, and the same
 * label with the same probability as the Python reference.
 */
class ParityTest {
    private val beacon = testBeacon()

    private val vectors = javaClass.getResourceAsStream("/testvectors.tsv")!!.bufferedReader(Charsets.UTF_8)
        .readLines().filter { it.isNotEmpty() }.map { it.split("\t") }

    @Test
    fun normalizationMatchesPython() {
        var bad = 0
        for (v in vectors) {
            val norm = Featurizer.normalize(unescape(v[0]))
            if (norm != unescape(v[1])) { bad++; println("NORM MISMATCH: '${v[0]}' kt='$norm' py='${v[1]}'") }
        }
        println("normalization: ${vectors.size - bad}/${vectors.size} identical")
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
        assertTrue("probability drift $maxDiff", maxDiff < 1e-3f)
    }

    companion object {
        const val MODEL = "src/main/assets/${Beacon.MODEL_ASSET}"
        fun testBeacon() = Beacon(File(MODEL).readBytes())

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
