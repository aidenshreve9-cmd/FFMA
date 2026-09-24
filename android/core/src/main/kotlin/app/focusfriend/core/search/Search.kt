package app.focusfriend.core.search

/**
 * Focus Friend local search: private, deterministic, in-memory.
 *
 *   UI → SearchController → SearchNormalizer → SearchIndex → SearchRanker → results
 *
 * No network, no history, no telemetry. Queries are never stored. Optional developer
 * metrics are numbers only (latency, counts) and never leave the device.
 */

enum class FieldKind { TITLE, NAME, FILENAME, PHONE }

/** Match strengths in the spec's ranking order (higher rank wins ties). */
enum class MatchType(val rank: Int) {
    EXACT(7), EXACT_TOKEN(6), PHONE_FULL(6), PREFIX(5), TOKEN_PREFIX(4), SUBSTRING(3), PHONE_PARTIAL(2), FUZZY(1), SYNONYM(0)
}

/**
 * Internal scores. The spec's ranking order is authoritative:
 * exact > exact token > prefix > token prefix > substring > phone (partial) > fuzzy.
 * A full phone-number match counts like an exact token of the phone field; fuzzy is
 * capped below partial phone matches (docs/DECISIONS.md, C1/C2). Never shown to people.
 */
object Scores {
    const val EXACT = 100
    const val EXACT_TOKEN = 80
    const val PHONE_FULL = 80
    const val PREFIX = 60
    const val TOKEN_PREFIX = 50
    const val SUBSTRING = 30
    const val PHONE_PARTIAL = 25
    const val FUZZY_MAX = 24
    const val FUZZY_MIN = 10
    const val SYNONYM = 12
}

/** Small bidirectional synonym dictionary. Synonyms always score below direct matches. */
val SYNONYMS: Map<String, List<String>> = mapOf(
    "noise" to listOf("sound"), "sound" to listOf("noise"),
    "galaxy" to listOf("stellar"), "stellar" to listOf("galaxy"),
    "nebula" to listOf("cosmic"), "cosmic" to listOf("nebula"),
    "contact" to listOf("person"), "person" to listOf("contact"),
)

/** What callers hand to the index. */
data class SearchInput(
    val id: String,
    val type: String,
    val title: String,
    val subtitle: String = "",
    val searchableFields: Map<String, String> = mapOf("title" to title),
    val createdAt: Long = 0L,
)

/** Generic searchable record: { id, type, title, subtitle, searchableFields, normalizedFields, tokens, createdAt }. */
class SearchRecord internal constructor(
    val id: String,
    val type: String,
    val title: String,
    val subtitle: String,
    val searchableFields: Map<String, String>,
    val normalizedFields: Map<String, String>,
    val tokens: List<String>,
    val createdAt: Long,
    internal val fields: List<PreparedField>,
)

internal class PreparedField(
    val kind: FieldKind,
    val folded: String,
    val foldedTokens: List<String>,
    val digits: String,
)

data class SearchResult(val item: SearchRecord, val score: Int, val matchType: MatchType)

enum class Ranking { A, B }

data class SearchOptions(
    val limit: Int = 10,
    /** A: exact > prefix > substring > fuzzy.  B (default): adds exact-token and token-prefix tiers. */
    val ranking: Ranking = Ranking.B,
    val fuzzy: Boolean = true,
    val synonyms: Boolean = true,
    val devMetrics: Boolean = false,
)

internal class PreparedQuery(val raw: String, val norm: String, val tokens: List<String>, val digits: String, val phoneLike: Boolean)

internal fun prepareQuery(raw: String?): PreparedQuery? {
    val trimmed = raw?.trim().orEmpty()
    if (trimmed.isEmpty()) return null
    val norm = SearchNormalizer.fold(SearchNormalizer.normalizeTitle(trimmed))
    val tokens = SearchNormalizer.tokenize(norm)
    val phoneLike = SearchNormalizer.isPhoneLike(trimmed)
    if (tokens.isEmpty() && !phoneLike) return null
    return PreparedQuery(trimmed, norm, tokens, if (phoneLike) SearchNormalizer.normalizePhone(trimmed) else "", phoneLike)
}

fun createRecord(input: SearchInput, kinds: Map<String, FieldKind>): SearchRecord {
    require(input.id.isNotEmpty()) { "record needs an id" }
    val normalized = LinkedHashMap<String, String>()
    val tokens = ArrayList<String>()
    val prepared = ArrayList<PreparedField>()
    for ((key, raw) in input.searchableFields) {
        if (raw.isEmpty()) continue
        val kind = kinds[key] ?: when (key) { "phone" -> FieldKind.PHONE; "name" -> FieldKind.NAME; else -> FieldKind.TITLE }
        if (kind == FieldKind.PHONE) {
            val digits = SearchNormalizer.normalizePhone(raw)
            normalized[key] = digits
            if (digits.isNotEmpty()) prepared += PreparedField(kind, "", emptyList(), digits)
            continue
        }
        val norm = SearchNormalizer.normalize(kind, raw)
        val folded = SearchNormalizer.fold(norm)
        normalized[key] = norm
        tokens += SearchNormalizer.tokenize(norm)
        prepared += PreparedField(kind, folded, SearchNormalizer.tokenize(folded), "")
    }
    return SearchRecord(input.id, input.type, input.title, input.subtitle, input.searchableFields.toMap(), normalized, tokens, input.createdAt, prepared)
}

/** Scores one record against one query. Stateless apart from the reusable distance buffers. */
class SearchRanker(private val options: SearchOptions) {
    private val dist = BoundedDistance()

    private data class Hit(val score: Int, val type: MatchType)

    internal fun score(record: SearchRecord, q: PreparedQuery): SearchResult? {
        var best: Hit? = null
        for (f in record.fields) {
            val h = scoreField(f, q) ?: continue
            if (best == null || h.score > best.score || (h.score == best.score && h.type.rank > best.type.rank)) best = h
        }
        return best?.let { SearchResult(record, it.score, it.type) }
    }

    private fun scoreField(f: PreparedField, q: PreparedQuery): Hit? {
        if (f.kind == FieldKind.PHONE) {
            if (!q.phoneLike || q.digits.isEmpty()) return null
            if (SearchNormalizer.phonesMatch(q.digits, f.digits)) return Hit(Scores.PHONE_FULL, MatchType.PHONE_FULL)
            if (q.digits.length >= 3 && f.digits.contains(q.digits)) return Hit(Scores.PHONE_PARTIAL, MatchType.PHONE_PARTIAL)
            return null
        }
        val text = f.folded
        val toks = f.foldedTokens
        if (text.isEmpty()) return null
        if (text == q.norm) return Hit(Scores.EXACT, MatchType.EXACT)
        val b = options.ranking == Ranking.B
        if (b && q.tokens.all { it in toks }) return Hit(Scores.EXACT_TOKEN, MatchType.EXACT_TOKEN)
        if (text.startsWith(q.norm)) return Hit(Scores.PREFIX, MatchType.PREFIX)
        if (b && q.tokens.all { t -> toks.any { it.startsWith(t) } }) return Hit(Scores.TOKEN_PREFIX, MatchType.TOKEN_PREFIX)
        if (q.norm.length >= 2 && text.contains(q.norm)) return Hit(Scores.SUBSTRING, MatchType.SUBSTRING)

        if (options.fuzzy) {
            var total = 0
            var edited = false
            var ok = true
            for (t in q.tokens) {
                if (toks.any { it.startsWith(t) }) continue
                val bound = BoundedDistance.fuzzyBound(t.length)
                if (bound == 0) { ok = false; break }
                var best = bound + 1
                for (cand in toks) {
                    if (best == 0) break
                    best = minOf(best, dist.distance(t, cand, bound))
                    if (cand.length > t.length) best = minOf(best, dist.distance(t, cand.substring(0, t.length), bound))
                }
                if (best > bound) { ok = false; break }
                total += best; edited = true
            }
            if (ok && edited) return Hit(maxOf(Scores.FUZZY_MIN, Scores.FUZZY_MAX - (total - 1) * 7), MatchType.FUZZY)
        }
        if (options.synonyms) {
            var viaSyn = false
            var ok = true
            for (t in q.tokens) {
                if (toks.any { it.startsWith(t) }) continue
                val syns = SYNONYMS[t]
                if (syns != null && syns.any { it in toks }) viaSyn = true else { ok = false; break }
            }
            if (ok && viaSyn) return Hit(Scores.SYNONYM, MatchType.SYNONYM)
        }
        return null
    }

    internal fun distance(a: String, b: String, bound: Int) = dist.distance(a, b, bound)

    companion object {
        /** Deterministic order: score, match strength, shorter title, alphabetical, id. */
        val ORDER: Comparator<SearchResult> = compareByDescending<SearchResult> { it.score }
            .thenByDescending { it.matchType.rank }
            .thenBy { it.item.title.length }
            .thenBy { it.item.title }
            .thenBy { it.item.id }
    }
}

/** In-memory index, updated incrementally (no full rebuild on add/remove). */
class SearchIndex {
    private class Col(var kinds: Map<String, FieldKind> = emptyMap()) {
        val records = LinkedHashMap<String, SearchRecord>()
        var version = 0
    }
    private val collections = HashMap<String, Col>()
    private fun col(name: String) = collections.getOrPut(name) { Col() }

    fun define(name: String, kinds: Map<String, FieldKind>) { col(name).kinds = kinds }
    fun add(name: String, input: SearchInput): SearchRecord {
        val c = col(name)
        val r = createRecord(input, c.kinds)
        c.records[r.id] = r; c.version++
        return r
    }
    fun remove(name: String, id: String): Boolean {
        val c = collections[name] ?: return false
        val ok = c.records.remove(id) != null
        if (ok) c.version++
        return ok
    }
    /** Replaces a collection (startup / import). Corrupted inputs are skipped, never fatal. */
    fun load(name: String, inputs: List<SearchInput?>) {
        val c = col(name)
        c.records.clear()
        for (input in inputs) {
            if (input == null) continue
            try { val r = createRecord(input, c.kinds); c.records[r.id] = r } catch (_: IllegalArgumentException) { }
        }
        c.version++
    }
    fun size(name: String) = collections[name]?.records?.size ?: 0
    fun version(name: String) = collections[name]?.version ?: 0
    fun records(name: String): Collection<SearchRecord> = collections[name]?.records?.values ?: emptyList()
    fun get(name: String, id: String) = collections[name]?.records?.get(id)
}

/** Developer-only local metrics: numbers only, never text. Never transmitted. */
class SearchMetrics {
    var searches = 0; private set
    var zeroResults = 0; private set
    var fuzzyHits = 0; private set
    var totalMs = 0.0; private set
    var maxMs = 0.0; private set
    internal fun record(ms: Double, results: List<SearchResult>) {
        searches++; totalMs += ms; if (ms > maxMs) maxMs = ms
        if (results.isEmpty()) zeroResults++
        if (results.any { it.matchType == MatchType.FUZZY }) fuzzyHits++
    }
}

data class Suggestion(val item: SearchRecord, val text: String)

/**
 * Entry point for the UI. Never throws: a search problem returns nothing and can
 * never stop a Focus session from starting.
 */
class SearchController(
    val options: SearchOptions = SearchOptions(),
    private val devLog: ((String) -> Unit)? = null,
) {
    val index = SearchIndex()
    val metrics = SearchMetrics()
    private val ranker = SearchRanker(options)
    private val cache = LinkedHashMap<String, List<SearchResult>>()

    fun define(name: String, kinds: Map<String, FieldKind>) = apply { index.define(name, kinds) }
    fun load(name: String, inputs: List<SearchInput?>) { index.load(name, inputs); cache.clear() }
    fun add(name: String, input: SearchInput): SearchRecord { val r = index.add(name, input); cache.clear(); return r }
    fun remove(name: String, id: String): Boolean { val ok = index.remove(name, id); if (ok) cache.clear(); return ok }
    fun size(name: String) = index.size(name)
    /** Tests use this to time cold searches instead of cache hits. */
    internal fun clearCache() = cache.clear()

    /** search(collection, query, limit) → [(item, score, matchType)]. Score/matchType are internal. */
    fun search(collection: String, query: String?, limit: Int = options.limit): List<SearchResult> {
        val start = System.nanoTime()
        return try {
            val q = prepareQuery(query) ?: return emptyList()
            val lim = if (limit > 0) minOf(limit, 200) else options.limit
            val key = "$collection|${index.version(collection)}|${q.raw}|$lim|${options.ranking}"
            cache[key]?.let { return it }
            val found = ArrayList<SearchResult>()
            for (r in index.records(collection)) ranker.score(r, q)?.let(found::add)
            found.sortWith(SearchRanker.ORDER)
            val results = if (found.size > lim) found.subList(0, lim).toList() else found
            if (cache.size > 24) cache.clear()
            cache[key] = results
            measure(collection, start, results)
            results
        } catch (e: RuntimeException) {
            log("search failed collection=$collection error=${e.javaClass.simpleName}")
            emptyList()
        }
    }

    /** Did-you-mean: only for zero results with a single strong candidate. */
    fun suggest(collection: String, query: String?): Suggestion? = try {
        val q = prepareQuery(query)
        if (q == null || q.phoneLike || search(collection, query, 1).isNotEmpty()) null
        else {
            val compact = q.norm.replace(" ", "")
            val bound = BoundedDistance.suggestBound(compact.length)
            var best: SearchRecord? = null
            var bestDist = Int.MAX_VALUE
            var tie = false
            if (bound > 0) for (record in index.records(collection)) for (f in record.fields) {
                if (f.kind == FieldKind.PHONE) continue
                for (cand in f.foldedTokens + f.folded) {
                    val d = minOf(ranker.distance(q.norm, cand, bound), ranker.distance(compact, cand, bound))
                    if (d > bound) continue
                    if (d < bestDist) { bestDist = d; best = record; tie = false }
                    else if (d == bestDist && best != null && best.id != record.id) tie = true
                }
            }
            if (best == null || tie) null else Suggestion(best, best.title)
        }
    } catch (e: RuntimeException) { null }

    private fun measure(collection: String, start: Long, results: List<SearchResult>) {
        if (!options.devMetrics) return
        val ms = (System.nanoTime() - start) / 1e6
        metrics.record(ms, results)
        // Counts only. Never the query, names, numbers or filenames.
        log("search executed collection=$collection resultCount=${results.size} ms=${"%.2f".format(ms)}")
    }
    private fun log(line: String) { if (options.devMetrics) try { devLog?.invoke(line) } catch (_: RuntimeException) { } }
}
