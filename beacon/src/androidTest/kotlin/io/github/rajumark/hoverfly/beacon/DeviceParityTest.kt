package io.github.rajumark.hoverfly.beacon

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.rajumark.hoverfly.beacon.internal.Featurizer
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Same check as the JVM ParityTest, on a real device: Android's ICU-backed Unicode tables and
 * regex engine must give Python's normalization, feature ids and labels on every vector.
 */
@RunWith(AndroidJUnit4::class)
class DeviceParityTest {
    @Test
    fun matchesPythonOnDevice() {
        val inst = InstrumentationRegistry.getInstrumentation()
        val lines = inst.context.assets.open("testvectors.tsv").bufferedReader().readLines().filter { it.isNotEmpty() }
        val t0 = System.nanoTime()
        val beacon = Beacon(inst.targetContext)
        val loadMs = (System.nanoTime() - t0) / 1e6
        var norm = 0
        var ids = 0
        var labels = 0
        for (line in lines) {
            val c = line.split('\t')
            val text = unescape(c[0])
            val n = Featurizer.normalize(text)
            if (n == unescape(c[1])) norm++ else println("NORM DIFF: ${c[0]}")
            if (beacon.featureIds(n) == c[2]) ids++
            if (beacon.detect(text).label == c[3]) labels++ else println("LABEL DIFF: ${c[0]}")
        }
        val texts = lines.map { unescape(it.substringBefore('\t')) }
        repeat(1000) { beacon.detect(texts[it % texts.size]) }
        val n = 5000
        val s0 = System.nanoTime()
        repeat(n) { beacon.detect(texts[it % texts.size]) }
        val ms = (System.nanoTime() - s0) / 1e6 / n
        println("BEACON_DEVICE norm $norm ids $ids labels $labels of ${lines.size} load=${"%.0f".format(loadMs)}ms latency=${"%.3f".format(ms)}ms")
        assertEquals(lines.size, norm)
        assertEquals(lines.size, ids)
        assertEquals(lines.size, labels)
    }

    private fun unescape(s: String) = buildString {
        var i = 0
        while (i < s.length) {
            if (s[i] == '\\' && i + 1 < s.length) {
                append(when (s[i + 1]) { 't' -> '\t'; 'n' -> '\n'; else -> s[i + 1] }); i += 2
            } else { append(s[i]); i++ }
        }
    }
}
