package io.github.rajumark.hoverfly.beacon.internal

internal actual fun nfkc(s: String): String = s.asDynamic().normalize("NFKC") as String
