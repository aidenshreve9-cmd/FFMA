# Local search — Focus Friend V3.0

Search is local, deterministic, fast, private, accessible and small. There is no search server,
no Elasticsearch/Algolia/OpenSearch/Typesense, no cloud API, no history and no telemetry.
The browser (`prototype/search.js`) and Android (`android/core/.../search/`) implementations
use the same scores and rules, and are tested against the same corpus.

## Where search appears

| List | When the search box shows | Why |
|------|---------------------------|-----|
| Trusted Contacts | As soon as one contact exists | Explicit spec rule (see DECISIONS C4) |
| Sounds | More than 12 options (8 built-in + Random + 4 of your own) | Browsing is easier for short lists |
| Scenic views | More than 12 options | Same |
| Home | **Never** | Core principle |

## Pipeline

```
UI → SearchController → SearchNormalizer → SearchIndex → SearchRanker → accessible results
```

**Normalization:** raw → Unicode NFKC → lowercase → trim → collapse whitespace → safe punctuation →
normalized. `"  Quantum-Nebula  " → "quantum nebula"`. Separate normalizers for titles, names
(conservative: apostrophes join, accents kept as written), filenames (extension dropped) and phone
numbers (digits only). An accent-folded twin is used for matching only ("jose" finds "José").
No stemming or lemmatization.

**Tokenization:** `"Quantum Nebula" → ["quantum", "nebula"]`. `neb` finds Quantum Nebula;
`quantum nebula` is an exact (top) match.

**Phone numbers:** `+1 780 555 1234`, `1-780-555-1234`, `17805551234` and `7805551234` all match
(a 1–3 digit country code is allowed on either side). The normalized number stays internal; the
person's own formatting is what's shown.

## Ranking

| Tier | Internal score |
|------|----------------|
| Exact full match | 100 |
| Exact token match | 80 |
| Full phone-number match | 80 |
| Prefix | 60 |
| Token prefix | 50 |
| Substring | 30 |
| Partial phone digits | 25 |
| Fuzzy | 10–24 |
| Synonym | 12 |

Ties break by match strength, shorter title, alphabetical, id — so results are deterministic.
Scores and match types are internal and never shown.

**Fuzzy:** bounded optimal-string-alignment distance (Levenshtein + adjacent swaps). 1–4 character
tokens: no fuzzy results. 5–8: one edit. 9+: two edits. Weak matches are never returned.

**Synonyms:** tiny bidirectional dictionary (noise↔sound, galaxy↔stellar, nebula↔cosmic,
contact↔person), always below direct matches.

**Autocomplete:** the list itself narrows as you type (`pink` → Pink Noise, `gran` → Grandma, Grandpa).

**Did you mean:** only when there are zero results and one clearly strong candidate exists —
*"No sounds found for “pnik”. Did you mean Pink Noise?"*. Never a weak or ambiguous guess.

**Empty states:** "No matching sounds / Try a different name." · "No trusted contacts yet / Add
someone you want to be able to reach you during Focus."

## API

```js
search({ collection: "trustedContacts", query: "grand", limit: 10 })
// → [{ item, score, matchType }]
suggest({ collection: "sounds", query: "pnik" })  // → { item, text: "Pink Noise" } | null
```
Kotlin: `controller.search("trustedContacts", "grand", 10)`, `controller.suggest(...)`.

## Indexing

Startup: load local metadata → normalize → build in-memory index. Adding an item normalizes and
adds just that record; removing deletes just that record (a version counter invalidates the small
result cache). Corrupted records are skipped, never fatal.

## Accessibility and keyboard

Labelled search fields ("Search trusted contacts"); ↓ moves into results, ↑/↓ between them, ↑ from
the first result returns to the field; Enter selects; Escape clears (then closes Settings).
Result counts ("3 matching contacts.") are announced politely after a pause in typing, not on
every keystroke. Results fade in, removed results slide out; immediate with reduced motion.

## Privacy

No search history, no query storage, no CTR/abandonment metrics, no profiles. Developer builds may
measure latency, result count, zero-result rate and fuzzy usage **locally**; log lines contain
counts only (`search executed collection=trustedContacts resultCount=4`), never names, numbers,
filenames or queries. Browser: enable with `localStorage["ff.dev"] = "1"`. Android: debug builds.

## Relevance evaluation (offline, deterministic)

Corpus: Alice, Alicia, Alex, Grandma, Grandpa, Robert, Rob, Robin (synthetic numbers).
Queries: ali, grand, rob, robert, alce, 780555, and four phone formats.

| Metric | Ranking B (default) |
|--------|---------------------|
| Precision@1 | 1.00 |
| Precision@3 | 1.00 |
| MRR | 1.00 |
| NDCG@3 | 1.00 |
| Zero-result rate (direct) | 0.10 (`alce`, answered by did-you-mean) |
| Zero-result rate after did-you-mean | 0.00 |

**Local A/B (feature flag, no remote tests):** A = exact > prefix > substring > fuzzy;
B = exact > prefix > token prefix > substring > fuzzy. They tie on the contact corpus; on a
word-boundary corpus (`star` vs Stardust / Red Star Nursery / Superstar / Starling Drift) B scores
NDCG 1.00 vs A 0.80, so B is the default.
