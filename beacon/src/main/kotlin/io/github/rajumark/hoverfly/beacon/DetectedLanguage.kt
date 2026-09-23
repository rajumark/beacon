package io.github.rajumark.hoverfly.beacon

/**
 * A detected language.
 *
 * @property label language and script, e.g. "hin_Latn" (Hindi written in Latin letters); "und" when the
 *   text has no letters to judge.
 * @property language the ISO 639-3 language code, e.g. "hin".
 * @property script the ISO 15924 script code, e.g. "Latn".
 * @property name an English name, e.g. "Hindi (romanised)".
 * @property confidence the model probability, 0..1.
 */
public data class DetectedLanguage(
    val label: String,
    val language: String,
    val script: String,
    val name: String,
    val confidence: Float,
) {
    /** A rough "trust this" flag (confidence of at least 0.5). Tune the threshold for your app. */
    val isReliable: Boolean get() = confidence >= 0.5f

    public companion object {
        /** The result for text with no letters, such as "😀 123" or "". */
        @JvmField
        public val UNDETERMINED: DetectedLanguage = DetectedLanguage("und", "und", "Zyyy", "Undetermined", 0f)
    }
}
