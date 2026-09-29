package io.github.rajumark.hoverfly.beacon

import io.github.rajumark.hoverfly.beacon.internal.Featurizer
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.TimeSource

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
            println("$text -> ${r.label} (${r.name}) ${(r.confidence * 100).roundToInt() / 100.0}")
            assertEquals(want, r.label, text)
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

    @Test
    fun urlsMentionsAndEmailsAreIgnored() {
        assertEquals("hallo wie geht s", Featurizer.normalize("Hallo https://x.de/a?b=1 wie geht's @max_99 a.b@c.com"))
    }

    @Test
    fun closed() {
        val b = Beacon()
        b.close()
        assertFailsWith<IllegalStateException> { b.detect("hello") }
    }

    @Test
    fun latency() {
        val texts = listOf("Kal milte hain bhai", "Where are you?", "Nos vemos mañana", "நான் வீட்டுக்கு போறேன்")
        repeat(1000) { beacon.detect(texts[it % texts.size]) }
        val n = 5000
        val t0 = TimeSource.Monotonic.markNow()
        repeat(n) { beacon.detect(texts[it % texts.size]) }
        val us = t0.elapsedNow().inWholeNanoseconds / 1000.0 / n
        println("latency: ${(us * 10).roundToInt() / 10.0} µs per text, ${beacon.supportedLabels.size} labels")
        val l0 = TimeSource.Monotonic.markNow()
        Beacon().close()
        println("load: ${l0.elapsedNow().inWholeMilliseconds} ms")
        assertTrue(us < 20_000, "too slow: $us µs")
    }
}
