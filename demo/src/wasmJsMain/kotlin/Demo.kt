@file:OptIn(ExperimentalWasmJsInterop::class)

import kotlin.js.ExperimentalWasmJsInterop
import io.github.rajumark.hoverfly.beacon.Beacon

// Website live demo: docs/demo/worker.js calls load() once, then run() per input; run() returns JSON.

private fun q(s: String) = buildString {
    append('"')
    for (c in s) when (c) {
        '"' -> append("\\\""); '\\' -> append("\\\\")
        else -> if (c < ' ') append("\\u").append(c.code.toString(16).padStart(4, '0')) else append(c)
    }
    append('"')
}

private var instance: Beacon? = null

private fun model(): Beacon = instance ?: Beacon().also { instance = it }

/** Loads the bundled model and warms it up. */
@JsExport
fun load() {
    model()
}

/** Language candidates, best first: [{label, name, script, confidence}]. */
@JsExport
fun run(input: String, option: String): String =
    model().candidates(input, 5).joinToString(",", "[", "]") {
        "{\"label\":${q(it.label)},\"name\":${q(it.name)},\"script\":${q(it.script)},\"confidence\":${it.confidence}}"
    }

fun main() {}
