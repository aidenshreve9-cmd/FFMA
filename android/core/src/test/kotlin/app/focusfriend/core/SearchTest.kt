package app.focusfriend.core

import app.focusfriend.core.search.*
import kotlin.math.log2
import kotlin.math.pow
import kotlin.test.*

internal object Fixtures {
    val SOUNDS = Catalog.SOUNDS.map { SearchInput("sound-" + it.id, "sound", it.name) }
    val SCENES = Catalog.SCENES.map { SearchInput("scene-" + it.id, "scene", it.name) }
    val CONTACTS = listOf(
        Triple("c-alice", "Alice", "+1 403 555 0101"), Triple("c-alicia", "Alicia", "(403) 555-0102"),
        Triple("c-alex", "Alex", "403.555.0103"), Triple("c-grandma", "Grandma", "+1 780 555 1234"),
        Triple("c-grandpa", "Grandpa", "+1 587 555 4321"), Triple("c-robert", "Robert", "780-555-9876"),
        Triple("c-rob", "Rob", "+44 20 7946 0958"), Triple("c-robin", "Robin", "250 555 0199"),
    ).mapIndexed { i, (id, name, phone) ->
        SearchInput(id, "trusted-contact", name, phone, mapOf("name" to name, "phone" to phone), i.toLong())
    }

    fun controller(options: SearchOptions = SearchOptions(), log: ((String) -> Unit)? = null) =
        SearchController(options, log).apply {
            define("sounds", mapOf("title" to FieldKind.TITLE))
            define("scenes", mapOf("title" to FieldKind.TITLE))
            define("trustedContacts", mapOf("name" to FieldKind.NAME, "phone" to FieldKind.PHONE))
            load("sounds", SOUNDS); load("scenes", SCENES); load("trustedContacts", CONTACTS)
        }
}

class SearchTest {
    private val c = Fixtures.controller()
    private fun titles(col: String, q: String?) = c.search(col, q).map { it.item.title }
    private fun ids(col: String, q: String?) = c.search(col, q).map { it.item.id }

    @Test fun normalizationPipeline() {
        assertEquals("quantum nebula", SearchNormalizer.normalizeTitle("  Quantum-Nebula  "))
        assertEquals("quantum nebula", SearchNormalizer.normalizeTitle("ＱＵＡＮＴＵＭ　nebula"))
        assertEquals("dark matter web", SearchNormalizer.normalizeTitle("Dark   Matter\tWeb"))
        assertEquals("", SearchNormalizer.normalizeTitle(null))
        assertEquals("obrien", SearchNormalizer.normalizeName("O'Brien"))
        assertEquals("josé", SearchNormalizer.normalizeName("José"))
        assertEquals("jose", SearchNormalizer.fold("josé"))
        assertEquals("rain on the roof", SearchNormalizer.normalizeFilename("Rain_on-the.roof.MP3"))
        assertEquals(listOf("quantum", "nebula"), SearchNormalizer.tokenize(SearchNormalizer.normalizeTitle("Quantum Nebula")))
    }

    @Test fun phoneVariants() {
        listOf("+1 780 555 1234", "1-780-555-1234", "17805551234", "7805551234").forEach {
            assertTrue(SearchNormalizer.phonesMatch(SearchNormalizer.normalizePhone(it), "17805551234"), it)
            assertEquals("c-grandma", ids("trustedContacts", it).first(), it)
        }
        assertEquals(setOf("c-grandma", "c-robert"), ids("trustedContacts", "780555").toSet())
        assertFalse(SearchNormalizer.phonesMatch("123", "0123"))
        val g = c.search("trustedContacts", "grandma").first().item
        assertEquals("+1 780 555 1234", g.subtitle, "display keeps the original number")
        assertEquals("17805551234", g.normalizedFields["phone"])
    }

    @Test fun boundedDistance() {
        val d = BoundedDistance()
        assertEquals(1, d.distance("pnik", "pink", 2))
        assertEquals(1, d.distance("quntum", "quantum", 1))
        assertEquals(2, d.distance("abc", "xyz", 1))
    }

    @Test fun tokenPrefixAndExact() {
        assertEquals(listOf("Quantum Nebula"), titles("scenes", "neb"))
        val r = c.search("scenes", "quantum nebula").first()
        assertEquals("Quantum Nebula", r.item.title); assertEquals(MatchType.EXACT, r.matchType)
    }

    @Test fun rankingOrder() {
        val s = SearchController().apply {
            define("t", mapOf("title" to FieldKind.TITLE))
            load("t", listOf("star" to "exact", "dark star" to "token", "starlight" to "prefix", "big starling" to "tprefix", "megastars" to "sub")
                .map { (t, id) -> SearchInput(id, "t", t) })
        }
        assertEquals(listOf("exact", "token", "prefix", "tprefix", "sub"), s.search("t", "star").map { it.item.id })
        assertTrue(Scores.EXACT > Scores.EXACT_TOKEN && Scores.EXACT_TOKEN > Scores.PREFIX && Scores.PREFIX > Scores.TOKEN_PREFIX &&
            Scores.TOKEN_PREFIX > Scores.SUBSTRING && Scores.SUBSTRING > Scores.PHONE_PARTIAL && Scores.PHONE_PARTIAL > Scores.FUZZY_MAX)
    }

    @Test fun fuzzyAndDidYouMean() {
        val r = c.search("scenes", "quntum").first()
        assertEquals("Quantum Nebula", r.item.title); assertEquals(MatchType.FUZZY, r.matchType)
        assertTrue(c.search("sounds", "pnik").isEmpty(), "1–4 chars: exact/prefix only")
        assertEquals("Pink Noise", c.suggest("sounds", "pnik")?.text)
        assertNull(c.suggest("sounds", "pink"), "had results")
        assertNull(c.suggest("sounds", "xyzzyq"), "no weak guesses")
        assertEquals("Alice", c.suggest("trustedContacts", "alce")?.text)
        assertTrue(c.search("scenes", "qqqqqqq").isEmpty())
    }

    @Test fun synonymsNeverOutrankDirect() {
        val r = c.search("scenes", "galaxy")
        assertEquals(listOf("Spiral Galaxy", "Stellar Nursery"), r.map { it.item.title })
        assertEquals(MatchType.SYNONYM, r[1].matchType)
    }

    @Test fun autocomplete() {
        assertEquals(listOf("Pink Noise"), titles("sounds", "pink"))
        assertEquals(listOf("Grandma", "Grandpa"), titles("trustedContacts", "gran"))
    }

    @Test fun emptyInvalidAndMissing() {
        listOf(null, "", "   ", "!!!").forEach { assertTrue(c.search("sounds", it).isEmpty(), "$it") }
        assertTrue(c.search("missing", "pink").isEmpty())
        assertNull(c.suggest("missing", "pnik"))
        assertEquals(3, c.search("sounds", "noise", 3).size)
        assertEquals(8, c.search("sounds", "noise", -1).size)
    }

    @Test fun deterministicTies() {
        val a = ids("sounds", "noise")
        assertEquals(a, Fixtures.controller().search("sounds", "noise").map { it.item.id })
        assertEquals(listOf("sound-blue", "sound-grey", "sound-pink"), a.take(3))
    }

    @Test fun incrementalIndex() {
        val v0 = c.index.version("trustedContacts")
        c.add("trustedContacts", SearchInput("c-new", "trusted-contact", "Granddad", searchableFields = mapOf("name" to "Granddad", "phone" to "555 0000 111")))
        assertEquals(v0 + 1, c.index.version("trustedContacts"))
        assertTrue("c-new" in ids("trustedContacts", "grand"))
        assertTrue(c.remove("trustedContacts", "c-new"))
        assertFalse("c-new" in ids("trustedContacts", "grand"))
        assertFalse(c.remove("trustedContacts", "c-new"))
    }

    @Test fun corruptedInputsSkipped() {
        val s = SearchController().apply {
            define("sounds", mapOf("title" to FieldKind.TITLE))
            load("sounds", listOf(null, SearchInput("", "x", "no id"), SearchInput("ok", "sound", "Rain")))
        }
        assertEquals(1, s.size("sounds"))
    }

    @Test fun devLogsNeverContainPersonalText() {
        val lines = mutableListOf<String>()
        val d = Fixtures.controller(SearchOptions(devMetrics = true)) { lines += it }
        d.search("trustedContacts", "Grandma"); d.search("trustedContacts", "7805551234"); d.search("sounds", "nothing-here")
        assertEquals(3, d.metrics.searches); assertEquals(1, d.metrics.zeroResults)
        val all = lines.joinToString("\n")
        assertTrue(all.contains("search executed collection=trustedContacts resultCount=1"))
        listOf("Grandma", "grandma", "7805551234", "780", "nothing").forEach { assertFalse(all.contains(it), "leaked $it") }
        val quiet = mutableListOf<String>()
        Fixtures.controller { quiet += it }.search("sounds", "pink")
        assertTrue(quiet.isEmpty(), "metrics off by default")
    }
}

class RelevanceTest {
    private val judgments = listOf(
        "ali" to mapOf("c-alice" to 3, "c-alicia" to 3),
        "grand" to mapOf("c-grandma" to 3, "c-grandpa" to 3),
        "rob" to mapOf("c-rob" to 3, "c-robert" to 2, "c-robin" to 2),
        "robert" to mapOf("c-robert" to 3),
        "alce" to mapOf("c-alice" to 3),
        "780555" to mapOf("c-grandma" to 3, "c-robert" to 3),
        "+1 780 555 1234" to mapOf("c-grandma" to 3), "1-780-555-1234" to mapOf("c-grandma" to 3),
        "17805551234" to mapOf("c-grandma" to 3), "7805551234" to mapOf("c-grandma" to 3),
    )

    data class M(val p1: Double, val p3: Double, val mrr: Double, val ndcg: Double, val zeroRate: Double, val zeroAfterSuggest: Double)

    private fun metrics(c: SearchController): M {
        var p1 = 0.0; var p3 = 0.0; var mrr = 0.0; var ndcg = 0.0; var zero = 0; var zeroAfter = 0
        for ((q, rel) in judgments) {
            var ids = c.search("trustedContacts", q).map { it.item.id }
            if (ids.isEmpty()) { zero++; ids = listOfNotNull(c.suggest("trustedContacts", q)?.item?.id) }
            if (ids.isEmpty()) zeroAfter++
            val top = ids.take(3)
            if (rel.containsKey(ids.firstOrNull())) p1++
            p3 += top.count { it in rel } / minOf(3, rel.size).toDouble()
            val first = ids.indexOfFirst { it in rel }
            if (first >= 0) mrr += 1.0 / (first + 1)
            val dcg = top.mapIndexed { i, id -> (2.0.pow(rel[id] ?: 0) - 1) / log2(i + 2.0) }.sum()
            val ideal = rel.values.sortedDescending().take(3).mapIndexed { i, g -> (2.0.pow(g) - 1) / log2(i + 2.0) }.sum()
            ndcg += dcg / ideal
        }
        val n = judgments.size.toDouble()
        return M(p1 / n, p3 / n, mrr / n, ndcg / n, zero / n, zeroAfter / n)
    }

    @Test fun specCorpus() {
        val b = metrics(Fixtures.controller())
        println("ranking B $b")
        assertEquals(1.0, b.p1); assertEquals(1.0, b.mrr); assertTrue(b.p3 >= 0.99); assertTrue(b.ndcg >= 0.95)
        assertEquals(0.0, b.zeroAfterSuggest); assertTrue(b.zeroRate <= 0.1)
        val a = metrics(Fixtures.controller(SearchOptions(ranking = Ranking.A)))
        println("ranking A $a")
        assertTrue(b.ndcg >= a.ndcg && b.mrr >= a.mrr)
    }

    @Test fun wordBoundaryAB() {
        val titles = listOf("Stardust", "Red Star Nursery", "Superstar", "Starling Drift")
        fun top(r: Ranking) = SearchController(SearchOptions(ranking = r)).apply {
            define("t", mapOf("title" to FieldKind.TITLE))
            load("t", titles.mapIndexed { i, t -> SearchInput("t$i", "t", t) })
        }.search("t", "star").map { it.item.title }.take(3)
        assertTrue("Superstar" in top(Ranking.A))
        assertEquals(listOf("Red Star Nursery", "Stardust", "Starling Drift"), top(Ranking.B))
    }
}

class SearchPerformanceTest {
    private val syl = listOf("ka", "lo", "mi", "ra", "sen", "tor", "vel", "qua", "neb", "ul", "dar", "wyn", "zel", "po", "ri", "an")

    private fun synth(n: Int, seed: Int): List<SearchInput> {
        val r = kotlin.random.Random(seed)
        return (0 until n).map { i ->
            val title = (0..r.nextInt(3)).joinToString(" ") { (0..(1 + r.nextInt(2))).joinToString("") { syl[r.nextInt(syl.size)] }.replaceFirstChar(Char::uppercase) }
            SearchInput("c$i", "contact", title, searchableFields = mapOf("name" to title, "phone" to (2_000_000_000L + r.nextLong(7_999_999_999L)).toString()))
        }
    }

    @Test fun tenThousandRecordsUnder100ms() {
        for ((label, n) in listOf("50 contacts" to 50, "1,000 sounds" to 1000, "1,000 images" to 1000, "10,000 synthetic" to 10_000)) {
            val c = SearchController().apply { define("x", mapOf("name" to FieldKind.NAME, "phone" to FieldKind.PHONE)); load("x", synth(n, n)) }
            val queries = listOf("k", "ka", "kal", "kalo", "kalom", "quantm", "nebul", "dar wyn", "zeltor", "555", "2345678")
            repeat(3) { queries.forEach { q -> c.search("x", q) } }           // warm up the JIT
            val times = (0 until 5).flatMap { queries.map { q -> c.clearCache(); val t = System.nanoTime(); c.search("x", q); (System.nanoTime() - t) / 1e6 } }.sorted()
            val p95 = times[(times.size * 0.95).toInt()]
            println("$label: p95 ${"%.2f".format(p95)} ms")
            assertTrue(p95 < 100, "$label p95 $p95 ms")
        }
    }
}
