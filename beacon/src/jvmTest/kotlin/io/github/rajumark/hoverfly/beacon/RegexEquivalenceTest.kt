package io.github.rajumark.hoverfly.beacon

import io.github.rajumark.hoverfly.beacon.internal.Featurizer
import java.text.Normalizer
import java.util.Locale
import java.util.regex.Pattern
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The common normalizer matches URLs, e-mails, mentions and non-letters by hand. This checks it
 * against the exact java.util.regex patterns of the 1.x Android version on random text.
 */
class RegexEquivalenceTest {
    private val ws = " \\t\\n\\x0B\\f\\r\\x1C-\\x1F\\u0085\\u00A0\\u1680\\u2000-\\u200A\\u2028\\u2029\\u202F\\u205F\\u3000"
    private val ns = "[^$ws]"
    private val w = "[\\p{L}\\p{N}_]"
    private val url = Pattern.compile("(https?://|www\\.)$ns+|$ns+@$ns+\\.$ns+|@$w+")
    private val nonLetter = Pattern.compile("[^\\p{L}\\p{M}]+")

    /** The 1.x implementation, verbatim. */
    private fun reference(text: String): String {
        var t = Normalizer.normalize(text, Normalizer.Form.NFKC)
        t = url.matcher(t).replaceAll(" ").lowercase(Locale.ROOT)
        t = nonLetter.matcher(t).replaceAll(" ").trim(' ')
        val n = t.codePointCount(0, t.length)
        if (n > Featurizer.MAX_CHARS) t = t.substring(0, t.offsetByCodePoints(0, Featurizer.MAX_CHARS)).trimEnd(' ')
        return t
    }

    private val pieces = listOf(
        "http://", "https://", "www.", "http:/", "https//", "HTTP://", "@", "@@", ".", "..", "a", "Zü", "é", "é",
        "_", "9", "٣", " ", "  ", "\t", "\n", " ", "　", " ", "😀", "👍🏽", "\uD800", "\uDC00", "中文", "नमस्",
        "ß", "İ", "Σ", "ΣΑΣ", "ﬁ", "①", "x.y", "a@b", "a@b.c", "@x.", "mail@host.com", "-", "/", "?", "=", "&",
    )

    @Test
    fun handWrittenMatchesJavaRegex() {
        val rnd = Random(42)
        repeat(200_000) {
            val s = buildString { repeat(rnd.nextInt(1, 12)) { append(pieces[rnd.nextInt(pieces.size)]) } }
            assertEquals(reference(s), Featurizer.normalize(s), "input: ${s.map { it.code.toString(16) }}")
        }
        val long = buildString { repeat(400) { append("word ") } }
        assertEquals(reference(long), Featurizer.normalize(long))
    }
}
