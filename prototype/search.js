/*
 * Focus Friend - local search.
 *
 * Private, deterministic, in-memory search for small on-device collections
 * (built-in sounds and scenic views, user sounds and pictures, Trusted Contacts).
 * No network, no history, no telemetry, no logging: queries never leave this module
 * and are never stored.
 *
 *   UI -> SearchController -> SearchNormalizer -> SearchIndex -> SearchRanker -> results
 *
 * Works as a browser <script> (window.FFSearch) and as a Node module (tests).
 */
(function (root, factory) {
  if (typeof module === "object" && module.exports) module.exports = factory();
  else root.FFSearch = factory();
})(typeof self !== "undefined" ? self : this, function () {
  "use strict";

  /* ======================= Scores ======================= */
  // The spec's ranking order is authoritative:
  //   exact > exact token > prefix > token prefix > substring > phone (partial digits) > fuzzy.
  // Two spec example values were adjusted so the numbers can't contradict that order
  // (see docs/DECISIONS.md, C1/C2): a full phone-number match counts as an exact-token
  // match of the phone field (80), and fuzzy is capped below partial phone matches.
  const SCORE = Object.freeze({
    exact: 100,
    exactToken: 80,
    phoneFull: 80,
    prefix: 60,
    tokenPrefix: 50,
    substring: 30,
    phonePartial: 25,
    fuzzyMax: 24,
    fuzzyMin: 10,
  });

  const DEFAULTS = Object.freeze({ limit: 10, ranking: "B", fuzzy: true });

  /* ======================= SearchNormalizer ======================= */
  // Raw -> Unicode NFKC -> lowercase -> trim -> collapse whitespace -> safe punctuation -> normalized.
  const PUNCT = /[\u2010-\u2015\u2212\-_./\\,;:!?()[\]{}"\u201c\u201d\u00ab\u00bb`~|+*&^%$#@=<>]+/g;
  const APOS = /['\u2019\u02bc]/g;
  const WS = /\s+/g;

  function base(s) {
    if (typeof s !== "string") return "";
    try { s = s.normalize("NFKC"); } catch (e) { /* very old engines: keep as is */ }
    return s.toLowerCase();
  }
  function collapse(s) { return s.replace(WS, " ").trim(); }

  // Titles (built-in names, scenic views): punctuation becomes a space.
  function normalizeTitle(s) { return collapse(base(s).replace(APOS, "").replace(PUNCT, " ")); }
  // Personal names: conservative. Apostrophes join ("O'Brien" -> "obrien"), hyphens split,
  // letters and accents are kept as written.
  function normalizeName(s) { return collapse(base(s).replace(APOS, "").replace(PUNCT, " ")); }
  // Filenames: drop a trailing extension, then treat _ - . as spaces.
  function normalizeFilename(s) {
    const b = base(s).replace(/\.[a-z0-9]{1,5}$/i, "");
    return collapse(b.replace(APOS, "").replace(PUNCT, " "));
  }
  function tokenize(normalized) { return normalized ? normalized.split(" ").filter(Boolean) : []; }

  /* ---------- Phone numbers ---------- */
  // Internal form is digits only. Display always keeps the person's original text.
  function normalizePhone(s) { return typeof s === "string" ? s.replace(/\D+/g, "") : ""; }
  const PHONE_LIKE = /^[\d\s+().\-\u2010-\u2015]+$/;
  function isPhoneLike(raw) {
    return typeof raw === "string" && PHONE_LIKE.test(raw.trim()) && normalizePhone(raw).length >= 3;
  }
  // Same number, allowing a country code (1-3 digits) on either side:
  // "+1 780 555 1234", "1-780-555-1234", "17805551234" and "7805551234" all match.
  function phonesMatch(a, b) {
    if (!a || !b) return false;
    if (a === b) return true;
    const [lo, hi] = a.length < b.length ? [a, b] : [b, a];
    return lo.length >= 7 && hi.length - lo.length <= 3 && hi.endsWith(lo);
  }

  const NORMALIZERS = { title: normalizeTitle, name: normalizeName, filename: normalizeFilename };

  /* ======================= Fuzzy (bounded OSA distance) ======================= */
  // Optimal-string-alignment distance (Levenshtein + adjacent transposition) with an
  // upper bound; returns bound+1 as soon as the bound can't be met. Reuses buffers,
  // so a keystroke allocates nothing here.
  let rowA = new Int32Array(64), rowB = new Int32Array(64), rowC = new Int32Array(64);
  function boundedDistance(a, b, bound) {
    const n = a.length, m = b.length;
    if (Math.abs(n - m) > bound) return bound + 1;
    if (n === 0 || m === 0) return Math.max(n, m);
    if (m + 1 > rowA.length) { const k = m + 1; rowA = new Int32Array(k); rowB = new Int32Array(k); rowC = new Int32Array(k); }
    let prev2 = rowA, prev = rowB, cur = rowC;
    for (let j = 0; j <= m; j++) prev[j] = j;
    for (let i = 1; i <= n; i++) {
      cur[0] = i;
      let rowMin = cur[0];
      const ai = a.charCodeAt(i - 1);
      for (let j = 1; j <= m; j++) {
        const bj = b.charCodeAt(j - 1);
        let v = Math.min(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + (ai === bj ? 0 : 1));
        if (i > 1 && j > 1 && ai === b.charCodeAt(j - 2) && a.charCodeAt(i - 2) === bj) v = Math.min(v, prev2[j - 2] + 1);
        cur[j] = v;
        if (v < rowMin) rowMin = v;
      }
      if (rowMin > bound) return bound + 1;
      const t = prev2; prev2 = prev; prev = cur; cur = t;
    }
    return prev[m];
  }
  // Allowed edits by token length. 1-4 characters: none in results (exact/prefix only;
  // did-you-mean may still offer a strong guess). Longer tokens: small, bounded edits.
  function fuzzyBound(len) { return len <= 4 ? 0 : len <= 8 ? 1 : 2; }
  function suggestBound(len) { return len <= 2 ? 0 : len <= 6 ? 1 : 2; }

  /* ======================= Records ======================= */
  // Generic searchable record:
  // { id, type, title, subtitle, searchableFields, normalizedFields, tokens, createdAt }
  // `fieldKinds` maps each searchable field to how it is normalized: title | name | filename | phone.
  function createRecord(input, fieldKinds) {
    if (!input || input.id == null) throw new TypeError("record needs an id");
    const searchableFields = Object.assign({}, input.searchableFields || { title: input.title });
    const kinds = fieldKinds || {};
    const normalizedFields = {};
    const tokens = [];
    const prepared = [];
    Object.keys(searchableFields).forEach(key => {
      const raw = searchableFields[key];
      if (typeof raw !== "string" || !raw) return;
      const kind = kinds[key] || (key === "phone" ? "phone" : key === "name" ? "name" : "title");
      if (kind === "phone") {
        const digits = normalizePhone(raw);
        normalizedFields[key] = digits;
        if (digits) prepared.push({ key, kind, norm: "", toks: [], digits });
        return;
      }
      const norm = (NORMALIZERS[kind] || normalizeTitle)(raw);
      const toks = tokenize(norm);
      normalizedFields[key] = norm;
      toks.forEach(t => tokens.push(t));
      prepared.push({ key, kind, norm, toks, digits: "" });
    });
    const record = {
      id: String(input.id),
      type: input.type || "item",
      title: String(input.title == null ? "" : input.title),
      subtitle: input.subtitle == null ? "" : String(input.subtitle),
      searchableFields,
      normalizedFields,
      tokens,
      createdAt: typeof input.createdAt === "number" ? input.createdAt : 0,
      payload: input.payload,
    };
    Object.defineProperty(record, "_fields", { value: prepared, enumerable: false });
    return record;
  }

  /* ======================= Query preparation ======================= */
  function prepareQuery(raw) {
    if (typeof raw !== "string") return null;
    const trimmed = raw.trim();
    if (!trimmed) return null;
    const norm = normalizeTitle(trimmed);
    const toks = tokenize(norm);
    const phoneLike = isPhoneLike(trimmed);
    if (!toks.length && !phoneLike) return null;
    return { raw: trimmed, norm, toks, digits: phoneLike ? normalizePhone(trimmed) : "", phoneLike };
  }

  /* ======================= SearchRanker ======================= */
  const MATCH_RANK = { exact: 7, exactToken: 6, phoneFull: 6, prefix: 5, tokenPrefix: 4, substring: 3, phonePartial: 2, fuzzy: 1 };

  function hasToken(list, t) { for (let i = 0; i < list.length; i++) if (list[i] === t) return true; return false; }
  function hasTokenPrefix(list, t) { for (let i = 0; i < list.length; i++) if (list[i].startsWith(t)) return true; return false; }

  function scoreField(f, q, opts) {
    if (f.kind === "phone") {
      if (!q.phoneLike || !q.digits) return null;
      if (phonesMatch(q.digits, f.digits)) return { score: SCORE.phoneFull, matchType: "phoneFull" };
      if (q.digits.length >= 3 && f.digits.indexOf(q.digits) !== -1) return { score: SCORE.phonePartial, matchType: "phonePartial" };
      return null;
    }
    const text = f.norm, toks = f.toks;
    if (!text) return null;
    if (text === q.norm) return { score: SCORE.exact, matchType: "exact" };
    const B = opts.ranking !== "A";
    if (B && q.toks.every(t => hasToken(toks, t))) return { score: SCORE.exactToken, matchType: "exactToken" };
    if (text.startsWith(q.norm)) return { score: SCORE.prefix, matchType: "prefix" };
    if (B && q.toks.every(t => hasTokenPrefix(toks, t))) return { score: SCORE.tokenPrefix, matchType: "tokenPrefix" };
    if (q.norm.length >= 2 && text.indexOf(q.norm) !== -1) return { score: SCORE.substring, matchType: "substring" };

    // Fuzzy: every query token must land on some field token (by prefix or a small edit).
    if (opts.fuzzy) {
      let total = 0, edited = false, ok = true;
      for (let i = 0; i < q.toks.length && ok; i++) {
        const t = q.toks[i];
        if (hasTokenPrefix(toks, t)) continue;
        const bound = fuzzyBound(t.length);
        if (!bound) { ok = false; break; }
        let best = bound + 1;
        for (let j = 0; j < toks.length && best > 0; j++) {
          // compare against the token and, for longer tokens, its same-length prefix
          const cand = toks[j];
          best = Math.min(best, boundedDistance(t, cand, bound));
          if (cand.length > t.length) best = Math.min(best, boundedDistance(t, cand.slice(0, t.length), bound));
        }
        if (best > bound) ok = false; else { total += best; edited = true; }
      }
      if (ok && edited) {
        const score = Math.max(SCORE.fuzzyMin, SCORE.fuzzyMax - (total - 1) * 7);
        return { score, matchType: "fuzzy" };
      }
    }
    return null;
  }

  function scoreRecord(record, q, opts) {
    let best = null;
    const fields = record._fields;
    for (let i = 0; i < fields.length; i++) {
      const r = scoreField(fields[i], q, opts);
      if (r && (!best || r.score > best.score || (r.score === best.score && MATCH_RANK[r.matchType] > MATCH_RANK[best.matchType]))) best = r;
    }
    return best;
  }

  // Deterministic order: score, match strength, shorter title, alphabetical, id.
  function compareResults(a, b) {
    return (b.score - a.score)
      || (MATCH_RANK[b.matchType] - MATCH_RANK[a.matchType])
      || (a.item.title.length - b.item.title.length)
      || (a.item.title < b.item.title ? -1 : a.item.title > b.item.title ? 1 : 0)
      || (a.item.id < b.item.id ? -1 : a.item.id > b.item.id ? 1 : 0);
  }

  /* ======================= SearchIndex ======================= */
  class SearchIndex {
    constructor() { this.collections = new Map(); }
    _col(name) {
      let c = this.collections.get(name);
      if (!c) { c = { records: new Map(), kinds: {}, version: 0 }; this.collections.set(name, c); }
      return c;
    }
    define(name, fieldKinds) { this._col(name).kinds = Object.assign({}, fieldKinds); return this; }
    // Adds or replaces one record; returns the normalized record.
    add(name, input) {
      const c = this._col(name);
      const r = createRecord(input, c.kinds);
      c.records.set(r.id, r); c.version++;
      return r;
    }
    remove(name, id) {
      const c = this.collections.get(name);
      if (!c) return false;
      const ok = c.records.delete(String(id));
      if (ok) c.version++;
      return ok;
    }
    // Replaces a whole collection (used only at startup / after an import).
    load(name, inputs) {
      const c = this._col(name);
      c.records.clear();
      (inputs || []).forEach(input => {
        try { const r = createRecord(input, c.kinds); c.records.set(r.id, r); }
        catch (e) { /* corrupted metadata: skip that record, keep the rest */ }
      });
      c.version++;
    }
    get(name, id) { const c = this.collections.get(name); return c ? c.records.get(String(id)) || null : null; }
    size(name) { const c = this.collections.get(name); return c ? c.records.size : 0; }
    all(name) { const c = this.collections.get(name); return c ? Array.from(c.records.values()) : []; }
    version(name) { const c = this.collections.get(name); return c ? c.version : 0; }
  }

  /* ======================= SearchController ======================= */
  class SearchController {
    constructor(options) {
      this.index = new SearchIndex();
      this.options = Object.assign({}, DEFAULTS, options || {});
      this._cache = new Map(); // key: collection|version|query|limit -> results (small, cleared on change)
    }
    define(name, kinds) { this.index.define(name, kinds); return this; }
    load(name, items) { this.index.load(name, items); this._cache.clear(); }
    add(name, item) { const r = this.index.add(name, item); this._cache.clear(); return r; }
    remove(name, id) { const ok = this.index.remove(name, id); if (ok) this._cache.clear(); return ok; }
    size(name) { return this.index.size(name); }

    // search({ collection, query, limit }) -> [{ item, score, matchType }]
    // score and matchType are internal: never show them to people.
    search(req) {
      let results = [];
      try {
        const collection = req && req.collection;
        const limit = clampLimit(req && req.limit, this.options.limit);
        const q = prepareQuery(req && req.query);
        if (!collection || !q) return results;
        const key = collection + "|" + this.index.version(collection) + "|" + q.raw + "|" + limit + "|" + this.options.ranking;
        const hit = this._cache.get(key);
        if (hit) return hit;
        const c = this.index.collections.get(collection);
        if (!c) return results;
        const found = [];
        c.records.forEach(record => {
          const s = scoreRecord(record, q, this.options);
          if (s) found.push({ item: record, score: s.score, matchType: s.matchType });
        });
        found.sort(compareResults);
        results = found.slice(0, limit);
        if (this._cache.size > 24) this._cache.clear();
        this._cache.set(key, results);
        return results;
      } catch (e) {
        // Search must never break the app: report nothing, keep going.
        return [];
      }
    }

    // Did-you-mean: only when a query found nothing and one clearly strong candidate exists.
    suggest(req) {
      try {
        const collection = req && req.collection;
        const q = prepareQuery(req && req.query);
        if (!collection || !q || q.phoneLike) return null;
        if (this.search({ collection, query: req.query, limit: 1 }).length) return null;
        const c = this.index.collections.get(collection);
        if (!c) return null;
        let best = null, bestDist = Infinity, tie = false;
        const qtext = q.norm.replace(/ /g, "");
        c.records.forEach(record => {
          record._fields.forEach(f => {
            if (f.kind === "phone") return;
            const cands = f.toks.concat([f.norm]);
            cands.forEach(cand => {
              const bound = suggestBound(qtext.length);
              if (!bound) return;
              const d = Math.min(boundedDistance(q.norm, cand, bound), boundedDistance(qtext, cand, bound));
              if (d > bound) return;
              if (d < bestDist) { bestDist = d; best = record; tie = false; }
              else if (d === bestDist && best && best.id !== record.id) tie = true;
            });
          });
        });
        // Never offer a weak or ambiguous guess.
        if (!best || tie) return null;
        return { item: best, text: best.title };
      } catch (e) { return null; }
    }
  }

  function clampLimit(v, d) { const n = Number(v); return Number.isFinite(n) && n > 0 ? Math.min(200, Math.floor(n)) : d; }

  return {
    SCORE,
    normalizeTitle, normalizeName, normalizeFilename, normalizePhone, isPhoneLike, phonesMatch,
    tokenize, boundedDistance, createRecord, prepareQuery,
    SearchIndex, SearchController,
  };
});
