package io.github.rajumark.hoverfly.beacon.sample

import io.github.rajumark.hoverfly.beacon.Beacon
import io.github.rajumark.hoverfly.beacon.DetectedLanguage
import kotlinx.browser.document
import org.w3c.dom.HTMLElement
import org.w3c.dom.HTMLInputElement
import kotlin.math.roundToInt
import kotlin.time.TimeSource

/** "Kotlin/JS" or "Kotlin/Wasm". */
expect val runtime: String

private val examples = listOf(
    "Kal milte hain bhai", "Enna panra da", "நான் வீட்டுக்கு போறேன்", "Nos vemos mañana",
    "मैं घर जा रहा हूँ", "Ami tomake bhalobashi", "Wir sehen uns morgen", "今日はいい天気ですね",
)

private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

fun main() {
    fun el(id: String) = document.getElementById(id) as HTMLElement
    val input = document.getElementById("text") as HTMLInputElement
    el("platform").textContent = "Kotlin Multiplatform · $runtime · io.github.rajumark:beacon:2.0.0"

    val t0 = TimeSource.Monotonic.markNow()
    val beacon = Beacon()
    el("load").textContent = "Model loaded in ${t0.elapsedNow().inWholeMilliseconds} ms"

    fun render() {
        val mark = TimeSource.Monotonic.markNow()
        val c = beacon.candidates(input.value, limit = 3)
        val micros = mark.elapsedNow().inWholeMicroseconds
        val best = c.firstOrNull() ?: DetectedLanguage.UNDETERMINED
        el("name").textContent = best.name
        el("code").textContent = "${best.label}  ·  ${best.language}  ·  ${best.script}"
        el("bars").innerHTML = c.joinToString("") {
            "<div class=\"bar\"><code>${esc(it.label)}</code><div class=\"track\"><div class=\"fill\" style=\"width:${(it.confidence * 100).roundToInt()}%\"></div></div>" +
                "<span class=\"pct\">${(it.confidence * 100).roundToInt()}%</span></div>"
        }
        el("timing").textContent = (if (best.isReliable) "Reliable" else "Uncertain") + "  ·  $micros µs"
    }

    el("examples").innerHTML = examples.joinToString("") { "<button class=\"chip\">${esc(it)}</button>" }
    el("examples").querySelectorAll("button").let { list ->
        for (i in 0 until list.length) {
            val b = list.item(i) as HTMLElement
            b.onclick = { input.value = b.textContent ?: ""; render(); null }
        }
    }
    input.oninput = { render(); null }
    input.value = examples[0]
    render()
}
