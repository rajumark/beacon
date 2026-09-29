package io.github.rajumark.hoverfly.beacon.internal

import platform.Foundation.NSString
import platform.Foundation.precomposedStringWithCompatibilityMapping

@Suppress("CAST_NEVER_SUCCEEDS")
internal actual fun nfkc(s: String): String = (s as NSString).precomposedStringWithCompatibilityMapping
