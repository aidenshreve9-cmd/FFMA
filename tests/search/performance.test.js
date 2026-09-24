"use strict";
// Performance budget: a keystroke search must answer in well under ~100 ms.
const test = require("node:test");
const assert = require("node:assert/strict");
const S = require("../../prototype/search.js");

function rng(seed) { return () => { seed = (seed * 1664525 + 1013904223) >>> 0; return seed / 4294967296; }; }
const SYL = ["ka", "lo", "mi", "ra", "sen", "tor", "vel", "qua", "neb", "ul", "dar", "wyn", "zel", "po", "ri", "an"];

function synth(n, kind, seed) {
  const r = rng(seed), out = [];
  for (let i = 0; i < n; i++) {
    const words = 1 + Math.floor(r() * 3);
    const title = Array.from({ length: words }, () => {
      const syl = 2 + Math.floor(r() * 2);
      const w = Array.from({ length: syl }, () => SYL[Math.floor(r() * SYL.length)]).join("");
      return w[0].toUpperCase() + w.slice(1);
    }).join(" ");
    const phone = String(2000000000 + Math.floor(r() * 7999999999));
    out.push(kind === "contact"
      ? { id: "c" + i, title, searchableFields: { name: title, phone } }
      : { id: kind + i, title, searchableFields: { title } });
  }
  return out;
}

function timeQueries(c, collection, queries, rounds) {
  const times = [];
  for (let k = 0; k < rounds; k++) queries.forEach(q => {
    c._cache.clear(); // measure cold per-keystroke cost, not the cache
    const t = process.hrtime.bigint();
    c.search({ collection, query: q });
    times.push(Number(process.hrtime.bigint() - t) / 1e6);
  });
  times.sort((a, b) => a - b);
  return { median: times[Math.floor(times.length / 2)], p95: times[Math.floor(times.length * 0.95)], max: times[times.length - 1] };
}

const TYPING = ["k", "ka", "kal", "kalo", "kalom", "kalomi", "quantm", "nebul", "dar wyn", "vel", "zeltor", "555", "2345678"];

[
  ["50 contacts", 50, "contact"],
  ["1,000 sounds", 1000, "sound"],
  ["1,000 images", 1000, "image"],
  ["10,000 synthetic records", 10000, "contact"],
].forEach(([label, n, kind], i) => {
  test(`search latency: ${label}`, () => {
    const c = new S.SearchController();
    c.define("x", kind === "contact" ? { name: "name", phone: "phone" } : { title: "title" });
    const t0 = process.hrtime.bigint();
    c.load("x", synth(n, kind, 7 + i));
    const indexMs = Number(process.hrtime.bigint() - t0) / 1e6;
    const t = timeQueries(c, "x", TYPING, 5);
    console.log(`${label}: index ${indexMs.toFixed(1)} ms, query median ${t.median.toFixed(2)} ms, p95 ${t.p95.toFixed(2)} ms, max ${t.max.toFixed(2)} ms`);
    assert.ok(t.p95 < 100, `p95 ${t.p95} ms`);
  });
});
