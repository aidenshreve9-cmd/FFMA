"use strict";
// Offline relevance evaluation on a fixed corpus. No behavioral data, no remote A/B tests:
// ranking variants are compared with local feature flags against these judgments.
const test = require("node:test");
const assert = require("node:assert/strict");
const { controller } = require("./fixtures");

// Graded judgments: 3 = the answer, 2 = clearly relevant, 1 = acceptable.
const JUDGMENTS = [
  { q: "ali", rel: { "c-alice": 3, "c-alicia": 3 } },
  { q: "grand", rel: { "c-grandma": 3, "c-grandpa": 3 } },
  { q: "rob", rel: { "c-rob": 3, "c-robert": 2, "c-robin": 2 } },
  { q: "robert", rel: { "c-robert": 3 } },
  { q: "alce", rel: { "c-alice": 3 } },                 // typo: answered by did-you-mean
  { q: "780555", rel: { "c-grandma": 3, "c-robert": 3 } },
  { q: "+1 780 555 1234", rel: { "c-grandma": 3 } },
  { q: "1-780-555-1234", rel: { "c-grandma": 3 } },
  { q: "17805551234", rel: { "c-grandma": 3 } },
  { q: "7805551234", rel: { "c-grandma": 3 } },
];

// What a person sees: results, or the single did-you-mean candidate when there are none.
function pipeline(c, q) {
  const rs = c.search({ collection: "trustedContacts", query: q, limit: 10 }).map(r => r.item.id);
  if (rs.length) return { ids: rs, viaSuggest: false };
  const s = c.suggest({ collection: "trustedContacts", query: q });
  return { ids: s ? [s.item.id] : [], viaSuggest: !!s };
}

function metrics(c) {
  let p1 = 0, p3 = 0, mrr = 0, ndcg = 0, zero = 0, zeroAfterSuggest = 0;
  const K = 3;
  JUDGMENTS.forEach(({ q, rel }) => {
    const { ids, viaSuggest } = pipeline(c, q);
    if (viaSuggest || !ids.length) zero++;
    if (!ids.length) zeroAfterSuggest++;
    const top = ids.slice(0, K);
    p1 += rel[ids[0]] ? 1 : 0;
    p3 += top.filter(id => rel[id]).length / Math.min(K, Object.keys(rel).length);
    const firstRel = ids.findIndex(id => rel[id]);
    mrr += firstRel === -1 ? 0 : 1 / (firstRel + 1);
    const dcg = top.reduce((s, id, i) => s + ((2 ** (rel[id] || 0)) - 1) / Math.log2(i + 2), 0);
    const ideal = Object.values(rel).sort((a, b) => b - a).slice(0, K)
      .reduce((s, g, i) => s + ((2 ** g) - 1) / Math.log2(i + 2), 0);
    ndcg += ideal ? dcg / ideal : 0;
  });
  const n = JUDGMENTS.length;
  return { p1: p1 / n, p3: p3 / n, mrr: mrr / n, ndcg: ndcg / n, zeroResultRate: zero / n, zeroAfterSuggest: zeroAfterSuggest / n };
}

test("relevance on the spec corpus (ranking B, default)", () => {
  const m = metrics(controller({ ranking: "B" }));
  console.log("ranking B", JSON.stringify(m));
  assert.equal(m.p1, 1, "Precision@1");
  assert.ok(m.p3 >= 0.99, "Precision@3");
  assert.equal(m.mrr, 1, "MRR");
  assert.ok(m.ndcg >= 0.95, "NDCG@3 " + m.ndcg);
  assert.equal(m.zeroAfterSuggest, 0, "every query answered by results or did-you-mean");
  assert.ok(m.zeroResultRate <= 0.1, "only the typo 'alce' has zero direct results");
});

test("local A/B: ranking B (with token prefix) is at least as good as A", () => {
  const a = metrics(controller({ ranking: "A" }));
  const b = metrics(controller({ ranking: "B" }));
  console.log("ranking A", JSON.stringify(a));
  assert.ok(b.ndcg >= a.ndcg && b.mrr >= a.mrr && b.p1 >= a.p1);
});

test("'rob' puts the exact name first", () => {
  const c = controller();
  assert.equal(c.search({ collection: "trustedContacts", query: "rob" })[0].item.id, "c-rob");
  assert.equal(c.search({ collection: "trustedContacts", query: "robert" })[0].item.id, "c-robert");
});

// The contact corpus can't tell A from B (they tie). This one can: whole-word hits
// ("Red Star Nursery") should beat letters buried inside a word ("Superstar").
test("local A/B on a word-boundary corpus: B ranks whole-word matches higher", () => {
  const { S } = require("./fixtures");
  const titles = ["Stardust", "Red Star Nursery", "Superstar", "Starling Drift"];
  const rel = { "Stardust": 3, "Starling Drift": 3, "Red Star Nursery": 3, "Superstar": 1 };
  const run = ranking => {
    const c = new S.SearchController({ ranking });
    c.define("t", { title: "title" });
    c.load("t", titles.map((t, i) => ({ id: "t" + i, title: t })));
    const top = c.search({ collection: "t", query: "star" }).map(r => r.item.title).slice(0, 3);
    const dcg = top.reduce((s, t, i) => s + (2 ** rel[t] - 1) / Math.log2(i + 2), 0);
    const ideal = [3, 3, 3].reduce((s, g, i) => s + (2 ** g - 1) / Math.log2(i + 2), 0);
    return { top, ndcg: dcg / ideal };
  };
  const a = run("A"), b = run("B");
  console.log("word-boundary A", JSON.stringify(a), "B", JSON.stringify(b));
  assert.ok(b.ndcg > a.ndcg);
  assert.ok(!b.top.includes("Superstar"));
});
