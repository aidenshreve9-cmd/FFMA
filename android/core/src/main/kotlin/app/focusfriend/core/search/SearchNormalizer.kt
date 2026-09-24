package app.focusfriend.core.search

import java.text.Normalizer
import java.util.Locale

/**
 * Text normalization for local search.
 *
 * Raw → Unicode NFKC → lowercase → trim → collapse whitespace → safe punctuation → normalized.
 * Names, titles, filenames and phone numbers each keep their own normalized form.
 * Mirrors prototype/search.js so both platforms rank identically.
 */
object SearchNormalizer {
    private val PUNCT = Regex("""[‐-―−\-_./\\,;:!?()\[\]{}"“”«»`~|+*&^%$#@=<>]+""")
    private val APOS = Regex("""['’ʼ]""")
    private val WS = Regex("""\s+""")
    private val EXTENSION = Regex("""\.[a-z0-9]{1,5}$""")
    private val NON_DIGITS = Regex("""\D+""")
    private val PHONE_LIKE = Regex("""^[\d\s+().\-‐-―]+$""")

    private fun base(s: String?): String =
        if (s == null) "" else Normalizer.normalize(s, Normalizer.Form.NFKC).lowercase(Locale.ROOT)

    private fun collapse(s: String) = s.replace(WS, " ").trim()

    /** Built-in names and scenic views: punctuation becomes a space. */
    fun normalizeTitle(s: String?): String = collapse(base(s).replace(APOS, "").replace(PUNCT, " "))

    /** Personal names: conservative. Apostrophes join, hyphens split, accents stay as written. */
    fun normalizeName(s: String?): String = collapse(base(s).replace(APOS, "").replace(PUNCT, " "))

    /** Filenames: drop a trailing extension, then treat _ - . as spaces. */
    fun normalizeFilename(s: String?): String =
        collapse(base(s).replace(EXTENSION, "").replace(APOS, "").replace(PUNCT, " "))

    fun tokenize(normalized: String): List<String> =
        if (normalized.isEmpty()) emptyList() else normalized.split(' ').filter { it.isNotEmpty() }

    /** Internal phone form: digits only. The person's original text is what gets displayed. */
    fun normalizePhone(s: String?): String = s?.replace(NON_DIGITS, "") ?: ""

    fun isPhoneLike(raw: String?): Boolean =
        raw != null && PHONE_LIKE.matches(raw.trim()) && normalizePhone(raw).length >= 3

    /**
     * Same number, allowing a 1–3 digit country code on either side:
     * "+1 780 555 1234", "1-780-555-1234", "17805551234" and "7805551234" all match.
     */
    fun phonesMatch(a: String, b: String): Boolean {
        if (a.isEmpty() || b.isEmpty()) return false
        if (a == b) return true
        val (lo, hi) = if (a.length < b.length) a to b else b to a
        return lo.length >= 7 && hi.length - lo.length <= 3 && hi.endsWith(lo)
    }

    fun normalize(kind: FieldKind, raw: String): String = when (kind) {
        FieldKind.TITLE -> normalizeTitle(raw)
        FieldKind.NAME -> normalizeName(raw)
        FieldKind.FILENAME -> normalizeFilename(raw)
        FieldKind.PHONE -> normalizePhone(raw)
    }
}

/**
 * Bounded optimal-string-alignment distance (Levenshtein + adjacent transposition).
 * Returns `bound + 1` as soon as the bound can't be met. Reuses its buffers, so a
 * keystroke allocates nothing here. Not thread-safe: one instance per search thread.
 */
class BoundedDistance {
    private var a0 = IntArray(64)
    private var a1 = IntArray(64)
    private var a2 = IntArray(64)

    fun distance(a: String, b: String, bound: Int): Int {
        val n = a.length
        val m = b.length
        if (kotlin.math.abs(n - m) > bound) return bound + 1
        if (n == 0 || m == 0) return maxOf(n, m)
        if (m + 1 > a0.size) { a0 = IntArray(m + 1); a1 = IntArray(m + 1); a2 = IntArray(m + 1) }
        var prev2 = a0
        var prev = a1
        var cur = a2
        for (j in 0..m) prev[j] = j
        for (i in 1..n) {
            cur[0] = i
            var rowMin = cur[0]
            val ai = a[i - 1]
            for (j in 1..m) {
                val bj = b[j - 1]
                var v = minOf(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + if (ai == bj) 0 else 1)
                if (i > 1 && j > 1 && ai == b[j - 2] && a[i - 2] == bj) v = minOf(v, prev2[j - 2] + 1)
                cur[j] = v
                if (v < rowMin) rowMin = v
            }
            if (rowMin > bound) return bound + 1
            val t = prev2; prev2 = prev; prev = cur; cur = t
        }
        return prev[m]
    }

    companion object {
        /** 1–4 characters: exact/prefix only in results. Longer tokens: small bounded edits. */
        fun fuzzyBound(len: Int) = if (len <= 4) 0 else if (len <= 8) 1 else 2
        /** Did-you-mean may reach a little further, but only for one clearly strong candidate. */
        fun suggestBound(len: Int) = if (len <= 2) 0 else if (len <= 6) 1 else 2
    }
}
