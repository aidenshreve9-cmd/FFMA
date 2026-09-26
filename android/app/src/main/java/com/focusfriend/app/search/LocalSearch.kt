package com.focusfriend.app.search

import java.text.Normalizer

/*
 * Private, deterministic, in-memory search for the app's small lists (sounds, atmospheres,
 * your own files, Trusted Contacts). No network, no history: queries are never stored.
 *
 * Ranking order: exact > exact token > prefix > token prefix > substring > phone (partial digits) > fuzzy.
 */

object Score {
    const val EXACT = 100
    const val EXACT_TOKEN = 80
    const val PHONE_FULL = 80
    const val PREFIX = 60
    const val TOKEN_PREFIX = 50
    const val SUBSTRING = 30
    const val PHONE_PARTIAL = 25
    const val FUZZY_MAX = 24
    const val FUZZY_MIN = 10
}

enum class MatchType(val rank: Int) {
    EXACT(7), EXACT_TOKEN(6), PHONE_FULL(6), PREFIX(5), TOKEN_PREFIX(4), SUBSTRING(3), PHONE_PARTIAL(2), FUZZY(1)
}

enum class FieldKind { TITLE, NAME, FILENAME, PHONE }

object TextNormalizer {
    private val PUNCT = Regex("""[‐-―−\-_./\\,;:!?()\[\]{}"“”«»`~|+*&^%$#@=<>]+""")
    private val APOS = Regex("""['’ʼ]""")
    private val WS = Regex("""\s+""")
    private val EXTENSION = Regex("""\.[a-z0-9]{1,5}$""", RegexOption.IGNORE_CASE)
    private val PHONE_LIKE = Regex("""^[\d\s+().\-‐-―]+$""")

    private fun base(s: String): String = Normalizer.normalize(s, Normalizer.Form.NFKC).lowercase()
    private fun collapse(s: String): String = s.replace(WS, " ").trim()

    /** Titles: punctuation becomes a space. */
    fun title(s: String): String = collapse(base(s).replace(APOS, "").replace(PUNCT, " "))

    /** Personal names: apostrophes join ("O'Brien" → "obrien"), hyphens split. */
    fun name(s: String): String = title(s)

    /** Filenames: drop a trailing extension, then treat _ - . as spaces. */
    fun filename(s: String): String = collapse(base(s).replace(EXTENSION, "").replace(APOS, "").replace(PUNCT, " "))

    fun tokenize(normalized: String): List<String> = if (normalized.isEmpty()) emptyList() else normalized.split(' ').filter { it.isNotEmpty() }

    fun phone(s: String): String = s.filter { it in '0'..'9' }

    fun isPhoneLike(raw: String): Boolean = PHONE_LIKE.matches(raw.trim()) && phone(raw).length >= 3

    /** Same number, allowing a 1–3 digit country code on either side. */
    fun phonesMatch(a: String, b: String): Boolean {
        if (a.isEmpty() || b.isEmpty()) return false
        if (a == b) return true
        val (lo, hi) = if (a.length < b.length) a to b else b to a
        return lo.length >= 7 && hi.length - lo.length <= 3 && hi.endsWith(lo)
    }
}

/** Optimal-string-alignment distance with an upper bound; returns bound + 1 once the bound can't be met. */
fun boundedDistance(a: String, b: String, bound: Int): Int {
    val n = a.length
    val m = b.length
    if (kotlin.math.abs(n - m) > bound) return bound + 1
    if (n == 0 || m == 0) return maxOf(n, m)
    var prev2 = IntArray(m + 1)
    var prev = IntArray(m + 1) { it }
    var cur = IntArray(m + 1)
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

private fun fuzzyBound(len: Int) = if (len <= 4) 0 else if (len <= 8) 1 else 2
private fun suggestBound(len: Int) = if (len <= 2) 0 else if (len <= 6) 1 else 2

class PreparedField(val kind: FieldKind, val norm: String, val tokens: List<String>, val digits: String)

class SearchRecord(val id: String, val title: String, val subtitle: String, val fields: List<PreparedField>)

class SearchResult(val record: SearchRecord, val score: Int, val matchType: MatchType)

class Query(val raw: String, val norm: String, val tokens: List<String>, val digits: String, val phoneLike: Boolean)

fun createRecord(id: String, title: String, fields: Map<String, String>, kinds: Map<String, FieldKind>, subtitle: String = ""): SearchRecord {
    val prepared = fields.mapNotNull { (key, raw) ->
        if (raw.isEmpty()) return@mapNotNull null
        val kind = kinds[key] ?: when (key) { "phone" -> FieldKind.PHONE; "name" -> FieldKind.NAME; else -> FieldKind.TITLE }
        if (kind == FieldKind.PHONE) {
            val digits = TextNormalizer.phone(raw)
            if (digits.isEmpty()) null else PreparedField(kind, "", emptyList(), digits)
        } else {
            val norm = when (kind) {
                FieldKind.NAME -> TextNormalizer.name(raw)
                FieldKind.FILENAME -> TextNormalizer.filename(raw)
                else -> TextNormalizer.title(raw)
            }
            PreparedField(kind, norm, TextNormalizer.tokenize(norm), "")
        }
    }
    return SearchRecord(id, title, subtitle, prepared)
}

fun prepareQuery(raw: String): Query? {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return null
    val norm = TextNormalizer.title(trimmed)
    val tokens = TextNormalizer.tokenize(norm)
    val phoneLike = TextNormalizer.isPhoneLike(trimmed)
    if (tokens.isEmpty() && !phoneLike) return null
    return Query(trimmed, norm, tokens, if (phoneLike) TextNormalizer.phone(trimmed) else "", phoneLike)
}

private fun scoreField(f: PreparedField, q: Query): Pair<Int, MatchType>? {
    if (f.kind == FieldKind.PHONE) {
        if (!q.phoneLike || q.digits.isEmpty()) return null
        if (TextNormalizer.phonesMatch(q.digits, f.digits)) return Score.PHONE_FULL to MatchType.PHONE_FULL
        if (q.digits.length >= 3 && f.digits.contains(q.digits)) return Score.PHONE_PARTIAL to MatchType.PHONE_PARTIAL
        return null
    }
    val text = f.norm
    val toks = f.tokens
    if (text.isEmpty()) return null
    if (text == q.norm) return Score.EXACT to MatchType.EXACT
    if (q.tokens.all { it in toks }) return Score.EXACT_TOKEN to MatchType.EXACT_TOKEN
    if (text.startsWith(q.norm)) return Score.PREFIX to MatchType.PREFIX
    if (q.tokens.all { t -> toks.any { it.startsWith(t) } }) return Score.TOKEN_PREFIX to MatchType.TOKEN_PREFIX
    if (q.norm.length >= 2 && text.contains(q.norm)) return Score.SUBSTRING to MatchType.SUBSTRING

    // Fuzzy: every query token must land on some field token (by prefix or a small edit).
    var total = 0
    var edited = false
    for (t in q.tokens) {
        if (toks.any { it.startsWith(t) }) continue
        val bound = fuzzyBound(t.length)
        if (bound == 0) return null
        var best = bound + 1
        for (cand in toks) {
            best = minOf(best, boundedDistance(t, cand, bound))
            if (cand.length > t.length) best = minOf(best, boundedDistance(t, cand.substring(0, t.length), bound))
            if (best == 0) break
        }
        if (best > bound) return null
        total += best
        edited = true
    }
    if (!edited) return null
    return maxOf(Score.FUZZY_MIN, Score.FUZZY_MAX - (total - 1) * 7) to MatchType.FUZZY
}

private fun scoreRecord(r: SearchRecord, q: Query): Pair<Int, MatchType>? {
    var best: Pair<Int, MatchType>? = null
    for (f in r.fields) {
        val s = scoreField(f, q) ?: continue
        if (best == null || s.first > best.first || (s.first == best.first && s.second.rank > best.second.rank)) best = s
    }
    return best
}

/** Deterministic order: score, match strength, shorter title, alphabetical, id. */
private val RESULT_ORDER = Comparator<SearchResult> { a, b ->
    when {
        a.score != b.score -> b.score - a.score
        a.matchType.rank != b.matchType.rank -> b.matchType.rank - a.matchType.rank
        a.record.title.length != b.record.title.length -> a.record.title.length - b.record.title.length
        a.record.title != b.record.title -> a.record.title.compareTo(b.record.title)
        else -> a.record.id.compareTo(b.record.id)
    }
}

class LocalSearch {
    private class Collection(val kinds: Map<String, FieldKind>) {
        val records = LinkedHashMap<String, SearchRecord>()
    }

    private val collections = HashMap<String, Collection>()

    fun define(name: String, kinds: Map<String, FieldKind>) {
        collections[name] = Collection(kinds)
    }

    /** Replaces a whole collection. Each item is (id, title, searchable fields). */
    fun load(name: String, items: List<Triple<String, String, Map<String, String>>>) {
        val c = collections.getOrPut(name) { Collection(emptyMap()) }
        c.records.clear()
        items.forEach { (id, title, fields) -> c.records[id] = createRecord(id, title, fields, c.kinds) }
    }

    fun size(name: String): Int = collections[name]?.records?.size ?: 0

    fun search(collection: String, query: String, limit: Int = 10): List<SearchResult> = try {
        val q = prepareQuery(query)
        val c = collections[collection]
        if (q == null || c == null) emptyList()
        else c.records.values.mapNotNull { r -> scoreRecord(r, q)?.let { SearchResult(r, it.first, it.second) } }
            .sortedWith(RESULT_ORDER)
            .take(limit.coerceIn(1, 200))
    } catch (e: Exception) {
        emptyList() // search must never break the app
    }

    /** Did-you-mean: only when a query found nothing and one clearly strong candidate exists. */
    fun suggest(collection: String, query: String): SearchRecord? = try {
        val q = prepareQuery(query)
        val c = collections[collection]
        if (q == null || c == null || q.phoneLike || search(collection, query, 1).isNotEmpty()) null
        else {
            var best: SearchRecord? = null
            var bestDist = Int.MAX_VALUE
            var tie = false
            val qtext = q.norm.replace(" ", "")
            val bound = suggestBound(qtext.length)
            if (bound > 0) {
                for (r in c.records.values) for (f in r.fields) {
                    if (f.kind == FieldKind.PHONE) continue
                    for (cand in f.tokens + f.norm) {
                        val d = minOf(boundedDistance(q.norm, cand, bound), boundedDistance(qtext, cand, bound))
                        if (d > bound) continue
                        if (d < bestDist) { bestDist = d; best = r; tie = false }
                        else if (d == bestDist && best != null && best.id != r.id) tie = true
                    }
                }
            }
            if (tie) null else best
        }
    } catch (e: Exception) {
        null
    }
}
