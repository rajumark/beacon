package io.github.rajumark.hoverfly.beacon

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The public API: examples from the README, edge cases and latency. */
class BeaconTest {
    private val beacon = ParityTest.testBeacon()

    @Test
    fun readmeExamples() {
        val cases = mapOf(
            "Kal milte hain bhai" to "hin_Latn",
            "Enna panra da" to "tam_Latn",
            "நான் வீட்டுக்கு போறேன்" to "tam_Taml",
            "Nos vemos mañana" to "spa_Latn",
            "Where are you?" to "eng_Latn",
        )
        for ((text, want) in cases) {
            val r = beacon.detect(text)
            println("$text -> ${r.label} (${r.name}) ${"%.2f".format(r.confidence)}")
            assertEquals(text, want, r.label)
        }
    }

    @Test
    fun fields() {
        val r = beacon.detect("नमस्ते, आप कैसे हैं?")
        assertEquals("hin_Deva", r.label)
        assertEquals("hin", r.language)
        assertEquals("Deva", r.script)
        assertTrue(r.isReliable)
    }

    @Test
    fun noLetters() {
        for (t in listOf("", "   ", "😀 123", "https://example.com")) {
            assertEquals(DetectedLanguage.UNDETERMINED, beacon.detect(t))
            assertTrue(beacon.candidates(t).isEmpty())
        }
        assertFalse(DetectedLanguage.UNDETERMINED.isReliable)
    }

    @Test
    fun candidates() {
        val c = beacon.candidates("Kal milte hain bhai", limit = 5)
        assertEquals(5, c.size)
        for (k in 1 until c.size) assertTrue(c[k - 1].confidence >= c[k].confidence)
        assertTrue(c.sumOf { it.confidence.toDouble() } <= 1.0001)
        assertEquals(beacon.supportedLabels.size, beacon.candidates("hello", limit = 10_000).size)
    }

    @Test(expected = IllegalStateException::class)
    fun closed() {
        val b = ParityTest.testBeacon()
        b.close()
        b.detect("hello")
    }

    @Test
    fun latency() {
        val texts = listOf("Kal milte hain bhai", "Where are you?", "Nos vemos mañana", "நான் வீட்டுக்கு போறேன்")
        repeat(3000) { beacon.detect(texts[it % texts.size]) }
        val n = 20_000
        val t0 = System.nanoTime()
        repeat(n) { beacon.detect(texts[it % texts.size]) }
        val us = (System.nanoTime() - t0) / 1e3 / n
        println("JVM latency: ${"%.1f".format(us)} µs per text, ${beacon.supportedLabels.size} labels")
        assertTrue("too slow: $us µs", us < 2000)
    }
}
