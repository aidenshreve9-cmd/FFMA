"use strict";
const test = require("node:test");
const assert = require("node:assert/strict");
const { S, controller, SOUNDS } = require("./fixtures");

const ids = rs => rs.map(r => r.item.id);
const titles = rs => rs.map(r => r.item.title);

test("normalization pipeline: NFKC, lowercase, trim, collapse, punctuation", () => {
  assert.equal(S.normalizeTitle("  Quantum-Nebula  "), "quantum nebula");
  assert.equal(S.normalizeTitle("ＱＵＡＮＴＵＭ　nebula"), "quantum nebula"); // full-width → NFKC
  assert.equal(S.normalizeTitle("Dark   Matter\tWeb"), "dark matter web");
  assert.equal(S.normalizeTitle("Aurora—Veil!"), "aurora veil");
  assert.equal(S.normalizeTitle(null), "");
  assert.equal(S.normalizeTitle(42), "");
});

test("names are normalized conservatively; accents kept, folded twin used only for matching", () => {
  assert.equal(S.normalizeName("O'Brien"), "obrien");
  assert.equal(S.normalizeName("Mary-Jane  Smith"), "mary jane smith");
  assert.equal(S.normalizeName("José"), "josé");
  assert.equal(S.fold("josé"), "jose");
});

test("filenames drop extension and separators", () => {
  assert.equal(S.normalizeFilename("Rain_on-the.roof.MP3"), "rain on the roof");
});

test("tokenization", () => {
  assert.deepEqual(S.tokenize(S.normalizeTitle("Quantum Nebula")), ["quantum", "nebula"]);
  assert.deepEqual(S.tokenize(""), []);
});

test("phone normalization and matching variants", () => {
  const variants = ["+1 780 555 1234", "1-780-555-1234", "17805551234", "7805551234"];
  variants.forEach(v => assert.ok(S.phonesMatch(S.normalizePhone(v), "17805551234"), v));
  assert.equal(S.normalizePhone("(403) 555-0102"), "4035550102");
  assert.ok(!S.phonesMatch("5551234", "17805551239"));
  assert.ok(!S.phonesMatch("123", "0123"), "too short to be a full number");
  assert.ok(S.isPhoneLike("780555"));
  assert.ok(!S.isPhoneLike("gran"));
  assert.ok(!S.isPhoneLike("12"), "under 3 digits is not a phone query");
});

test("bounded OSA distance", () => {
  assert.equal(S.boundedDistance("pnik", "pink", 2), 1, "transposition counts once");
  assert.equal(S.boundedDistance("quntum", "quantum", 1), 1);
  assert.equal(S.boundedDistance("abc", "xyz", 1), 2, "returns bound+1 once exceeded");
  assert.equal(S.boundedDistance("", "abc", 3), 3);
});

test("'neb' finds Quantum Nebula; 'quantum nebula' is an exact top match", () => {
  const c = controller();
  assert.deepEqual(titles(c.search({ collection: "scenes", query: "neb" })), ["Quantum Nebula"]);
  const r = c.search({ collection: "scenes", query: "quantum nebula" });
  assert.equal(r[0].item.title, "Quantum Nebula");
  assert.equal(r[0].matchType, "exact");
  assert.equal(r[0].score, S.SCORE.exact);
});

test("ranking order: exact > exact token > prefix > token prefix > substring > phone > fuzzy", () => {
  const c = new S.SearchController();
  c.define("t", { title: "title" });
  c.load("t", [
    { id: "exact", title: "star" },
    { id: "token", title: "dark star" },
    { id: "prefix", title: "starlight" },
    { id: "tprefix", title: "big starling" },
    { id: "sub", title: "megastars" },
  ]);
  assert.deepEqual(ids(c.search({ collection: "t", query: "star" })), ["exact", "token", "prefix", "tprefix", "sub"]);
  const s = S.SCORE;
  assert.ok(s.exact > s.exactToken && s.exactToken > s.prefix && s.prefix > s.tokenPrefix && s.tokenPrefix > s.substring
    && s.substring > s.phonePartial && s.phonePartial > s.fuzzyMax && s.fuzzyMax >= s.fuzzyMin && s.fuzzyMin > 0);
});

test("fuzzy: 'quntum' → Quantum Nebula; short typos don't fuzzy-match", () => {
  const c = controller();
  const r = c.search({ collection: "scenes", query: "quntum" });
  assert.equal(r[0].item.title, "Quantum Nebula");
  assert.equal(r[0].matchType, "fuzzy");
  assert.deepEqual(c.search({ collection: "sounds", query: "pnik" }), [], "1–4 characters: exact/prefix only");
});

test("did-you-mean: only on zero results with one strong candidate", () => {
  const c = controller();
  const s = c.suggest({ collection: "sounds", query: "pnik" });
  assert.equal(s.text, "Pink Noise");
  assert.equal(c.suggest({ collection: "sounds", query: "pink" }), null, "had results");
  assert.equal(c.suggest({ collection: "sounds", query: "xyzzyq" }), null, "no weak guesses");
  assert.equal(c.suggest({ collection: "trustedContacts", query: "alce" }).text, "Alice");
  assert.equal(c.suggest({ collection: "trustedContacts", query: "7" }), null);
});

test("weak fuzzy matches are not returned", () => {
  const c = controller();
  assert.deepEqual(c.search({ collection: "scenes", query: "qqqqqqq" }), []);
  assert.deepEqual(c.search({ collection: "sounds", query: "purple" }), []);
});

test("synonyms never outrank direct matches", () => {
  const c = controller();
  const r = c.search({ collection: "scenes", query: "galaxy" });
  assert.deepEqual(titles(r), ["Spiral Galaxy", "Stellar Nursery"]);
  assert.equal(r[1].matchType, "synonym");
  assert.ok(r[1].score < r[0].score);
  const n = c.search({ collection: "scenes", query: "nebula" });
  assert.equal(n[0].item.title, "Quantum Nebula");
  assert.equal(n[1].item.title, "Cosmic Dust");
});

test("autocomplete returns local records", () => {
  const c = controller();
  assert.deepEqual(titles(c.search({ collection: "sounds", query: "pink" })), ["Pink Noise"]);
  assert.deepEqual(titles(c.search({ collection: "trustedContacts", query: "gran" })), ["Grandma", "Grandpa"]);
});

test("contact search by phone variants, and partial digits", () => {
  const c = controller();
  ["+1 780 555 1234", "1-780-555-1234", "17805551234", "7805551234"].forEach(q =>
    assert.equal(c.search({ collection: "trustedContacts", query: q })[0].item.id, "c-grandma", q));
  assert.deepEqual(ids(c.search({ collection: "trustedContacts", query: "780555" })).sort(), ["c-grandma", "c-robert"]);
  // Display keeps the original text; the normalized number stays internal.
  const g = c.search({ collection: "trustedContacts", query: "grandma" })[0].item;
  assert.equal(g.subtitle, "+1 780 555 1234");
  assert.equal(g.normalizedFields.phone, "17805551234");
});

test("empty, whitespace and invalid queries return nothing and never throw", () => {
  const c = controller();
  [undefined, null, "", "   ", 7, {}, [], "!!!"].forEach(q =>
    assert.deepEqual(c.search({ collection: "sounds", query: q }), [], String(q)));
  assert.deepEqual(c.search(null), []);
  assert.deepEqual(c.search({ collection: "missing", query: "pink" }), []);
  assert.equal(c.suggest(undefined), null);
});

test("limit is honoured and clamped", () => {
  const c = controller();
  assert.equal(c.search({ collection: "sounds", query: "noise", limit: 3 }).length, 3);
  assert.equal(c.search({ collection: "sounds", query: "noise", limit: -1 }).length, 8, "falls back to default 10");
  assert.equal(c.search({ collection: "sounds", query: "noise", limit: "abc" }).length, 8);
});

test("results are deterministic, including ties", () => {
  const c = controller();
  const a = ids(c.search({ collection: "sounds", query: "noise" }));
  const b = ids(controller().search({ collection: "sounds", query: "noise" }));
  assert.deepEqual(a, b);
  // shorter titles first on ties, then alphabetical
  assert.deepEqual(a.slice(0, 3), ["sound-blue", "sound-grey", "sound-pink"]);
});

test("incremental index: add/remove update results without a rebuild", () => {
  const c = controller();
  const v0 = c.index.version("trustedContacts");
  c.add("trustedContacts", { id: "c-new", title: "Granddad", searchableFields: { name: "Granddad", phone: "555 0000 111" } });
  assert.equal(c.index.version("trustedContacts"), v0 + 1);
  assert.ok(ids(c.search({ collection: "trustedContacts", query: "grand" })).includes("c-new"));
  assert.ok(c.remove("trustedContacts", "c-new"));
  assert.ok(!ids(c.search({ collection: "trustedContacts", query: "grand" })).includes("c-new"));
  assert.equal(c.remove("trustedContacts", "c-new"), false, "removing twice is harmless");
});

test("corrupted metadata is skipped, not fatal", () => {
  const c = new S.SearchController();
  c.define("sounds", { title: "title" });
  c.load("sounds", [null, 5, { title: "no id" }, { id: "ok", title: "Rain", searchableFields: { title: "Rain" } }, { id: "bad", searchableFields: { title: 12 } }]);
  assert.equal(c.size("sounds"), 2);
  assert.deepEqual(ids(c.search({ collection: "sounds", query: "rain" })), ["ok"]);
});

test("generic record shape", () => {
  const r = S.createRecord({ id: "sound-pink-noise", type: "sound", title: "Pink Noise", searchableFields: { title: "Pink Noise" } });
  ["id", "type", "title", "subtitle", "searchableFields", "normalizedFields", "tokens", "createdAt"].forEach(k => assert.ok(k in r, k));
  assert.deepEqual(r.tokens, ["pink", "noise"]);
  assert.equal(r.normalizedFields.title, "pink noise");
});

test("dev metrics are local and never contain personal text", () => {
  const lines = [];
  const c = controller({ devMetrics: true, devLog: l => lines.push(l) });
  c.search({ collection: "trustedContacts", query: "Grandma" });
  c.search({ collection: "trustedContacts", query: "7805551234" });
  c.search({ collection: "sounds", query: "nothing-here" });
  assert.equal(c.metrics.searches, 3);
  assert.equal(c.metrics.zeroResults, 1);
  const all = lines.join("\n");
  assert.match(all, /search executed collection=trustedContacts resultCount=1/);
  ["Grandma", "grandma", "7805551234", "780", "nothing"].forEach(s => assert.ok(!all.includes(s), "leaked " + s));
});

test("metrics are off by default", () => {
  const lines = [];
  const c = controller({ devLog: l => lines.push(l) });
  c.search({ collection: "sounds", query: "pink" });
  assert.equal(lines.length, 0);
});

test("built-in sounds all searchable by name", () => {
  const c = controller();
  SOUNDS.forEach(s => assert.equal(c.search({ collection: "sounds", query: s.title })[0].item.id, s.id));
});
