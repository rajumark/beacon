package io.github.rajumark.hoverfly.beacon.internal

import io.github.rajumark.hoverfly.beacon.Beacon
import java.text.Normalizer

internal actual fun nfkc(s: String): String = Normalizer.normalize(s, Normalizer.Form.NFKC)

// The model ships as Java resources in the jar/AAR (src/modelData), so no Context or copy is needed.
internal actual fun readModelFile(name: String): ByteArray =
    Beacon::class.java.getResourceAsStream("/io/github/rajumark/hoverfly/beacon/model/$name")?.use { it.readBytes() }
        ?: error("Beacon model file $name is missing from the library jar")
