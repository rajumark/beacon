@file:JvmName("BeaconAndroid")

package io.github.rajumark.hoverfly.beacon

import android.content.Context

/** Kept so 1.x code (`Beacon(context)`) still compiles; the model no longer needs a [Context]. */
@Deprecated("The model is bundled without assets now; use Beacon().", ReplaceWith("Beacon()"))
@Suppress("UNUSED_PARAMETER", "FunctionName")
public fun Beacon(context: Context): Beacon = Beacon()
