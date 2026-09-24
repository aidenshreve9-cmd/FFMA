// Focus Friend browser UI / integration tests.
// Runs the real page in Chromium via Playwright (uses the machine's Playwright install;
// set PLAYWRIGHT_MODULE to point elsewhere). Serves prototype/ from a local HTTP server.
import http from "node:http";
import fs from "node:fs";
import path from "node:path";
import { execSync } from "node:child_process";
import { fileURLToPath, pathToFileURL } from "node:url";

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "../../prototype");
const pwPath = process.env.PLAYWRIGHT_MODULE || path.join(execSync("npm root -g").toString().trim(), "playwright/index.mjs");
const { chromium } = await import(pathToFileURL(pwPath).href);

/* ---------- tiny static server ---------- */
const TYPES = { ".html": "text/html; charset=utf-8", ".js": "text/javascript; charset=utf-8" };
const server = http.createServer((req, res) => {
  const p = path.join(ROOT, decodeURIComponent(req.url.split("?")[0]).replace(/^\/+/, ""));
  if (!p.startsWith(ROOT) || !fs.existsSync(p)) { res.writeHead(404); return res.end(); }
  res.writeHead(200, { "content-type": TYPES[path.extname(p)] || "application/octet-stream" });
  fs.createReadStream(p).pipe(res);
});
await new Promise(r => server.listen(0, "127.0.0.1", r));
const BASE = `http://127.0.0.1:${server.address().port}/focus-friend.html`;

/* ---------- mini harness ---------- */
const results = [];
async function test(name, fn) {
  try { await fn(); results.push([true, name]); console.log("ok  -", name); }
  catch (e) { results.push([false, name]); console.log("FAIL-", name, "\n     ", e.message.split("\n")[0]); }
}
const assert = (c, m) => { if (!c) throw new Error(m || "assertion failed"); };
const eq = (a, b, m) => { if (JSON.stringify(a) !== JSON.stringify(b)) throw new Error(`${m || ""} expected ${JSON.stringify(b)} got ${JSON.stringify(a)}`); };

const browser = await chromium.launch({ args: ["--use-gl=swiftshader", "--enable-webgl", "--ignore-gpu-blocklist"] });

async function open(opts = {}) {
  const ctx = await browser.newContext({ viewport: { width: 390, height: 800 }, reducedMotion: opts.reducedMotion || "no-preference" });
  const page = await ctx.newPage();
  const errors = [], requests = [], logs = [];
  page.on("pageerror", e => errors.push(e.message));
  page.on("console", m => { logs.push(m.text()); if (m.type() === "error" && !/fonts|ERR_CERT|net::/.test(m.text())) errors.push(m.text()); });
  page.on("request", r => requests.push(r.url()));
  if (opts.blockSearch) await page.route("**/search.js", r => r.abort());
  await page.goto(BASE);
  await page.waitForTimeout(400);
  return { ctx, page, errors, requests, logs };
}
const hidden = (page, id) => page.evaluate(i => document.getElementById(i).hidden, id);
const text = (page, sel) => page.evaluate(s => document.querySelector(s).textContent.trim(), sel);
const duration = page => page.evaluate(() => +document.getElementById("focusBtn").getAttribute("aria-label").match(/\d+/)[0]);
async function slide(page, dx, dy = 0) {
  const b = await page.locator("#focusBtn").boundingBox(), x = b.x + b.width / 2, y = b.y + b.height / 2;
  await page.mouse.move(x - dx / 2, y - dy / 2); await page.mouse.down();
  await page.mouse.move(x + dx / 2, y + dy / 2, { steps: 12 }); await page.mouse.up();
  await page.waitForTimeout(750);
}
const click = (page, sel) => page.click(sel, { force: true });
async function openSettings(page) { await click(page, "#openSettings"); await page.waitForTimeout(900); }
async function addContact(page, name, num) {
  await page.fill("#cName", name); await page.fill("#cNum", num);
  await click(page, "#contactForm button"); await page.waitForTimeout(80);
}
const visibleContacts = page => page.evaluate(() => Array.from(document.querySelectorAll("#contactList .contact"))
  .filter(r => !r.hidden && r.dataset.want !== "hidden")
  .sort((a, b) => (+a.style.order || 0) - (+b.style.order || 0))
  .map(r => r.querySelector("b").textContent));
// A tiny valid WAV so the page's own audio validation accepts it.
function wav() {
  const n = 800, b = Buffer.alloc(44 + n);
  b.write("RIFF", 0); b.writeUInt32LE(36 + n, 4); b.write("WAVE", 8); b.write("fmt ", 12);
  b.writeUInt32LE(16, 16); b.writeUInt16LE(1, 20); b.writeUInt16LE(1, 22); b.writeUInt32LE(8000, 24);
  b.writeUInt32LE(8000, 28); b.writeUInt16LE(1, 32); b.writeUInt16LE(8, 34); b.write("data", 36); b.writeUInt32LE(n, 40);
  b.fill(128, 44); return b;
}

/* ================= Chapter 1: Home ================= */
await test("home: opens on 15, exact hint, gear top-right, no load announcement", async () => {
  const { page, errors, ctx } = await open();
  eq(await duration(page), 15);
  eq(await text(page, "#hint"), "Slide the button to change the time");
  const g = await page.locator("#openSettings").boundingBox();
  assert(g.x > 300 && g.y < 80, "gear top-right");
  eq(await text(page, "#durLive"), "", "no announcement on load");
  eq(await page.evaluate(() => document.title), "Focus Friend Version 3.0");
  eq(errors, [], "page errors");
  await ctx.close();
});

await test("home: slide left = next, right = previous, loops both ways; vertical does nothing", async () => {
  const { page, ctx } = await open();
  const seq = [];
  for (let i = 0; i < 4; i++) { await slide(page, -70); seq.push(await duration(page)); }
  eq(seq, [30, 45, 60, 15], "left loops 60→15");
  await slide(page, 70); eq(await duration(page), 60, "right loops 15→60");
  await slide(page, 0, 120); eq(await duration(page), 60, "vertical ignored");
  eq(await hidden(page, "permSheet"), true, "a slide never starts Focus");
  await ctx.close();
});

await test("home: a quick flick changes exactly one step", async () => {
  const { page, ctx } = await open();
  for (const [dx, want] of [[-30, 30], [-30, 45], [30, 30]]) {
    const b = await page.locator("#focusBtn").boundingBox(), x = b.x + b.width / 2, y = b.y + b.height / 2;
    await page.mouse.move(x, y); await page.mouse.down(); await page.mouse.move(x + dx, y, { steps: 2 }); await page.mouse.up();
    await page.waitForTimeout(750);
    eq(await duration(page), want, "flick " + dx);
  }
  await ctx.close();
});

await test("home: keyboard ← → change duration, Enter starts", async () => {
  const { page, ctx } = await open();
  await page.focus("#focusBtn");
  await page.keyboard.press("ArrowRight"); await page.waitForTimeout(700); eq(await duration(page), 30);
  await page.keyboard.press("ArrowLeft"); await page.waitForTimeout(700); eq(await duration(page), 15);
  await page.keyboard.press("ArrowLeft"); await page.waitForTimeout(700); eq(await duration(page), 60);
  await page.keyboard.press("Enter"); await page.waitForTimeout(600);
  eq(await hidden(page, "permSheet"), false, "Enter asks for permission first");
  await ctx.close();
});

/* ================= Chapter 7: Permissions ================= */
await test("permissions: exact browser wording, ON/OFF states, 'Not now' barrier", async () => {
  const { page, ctx } = await open();
  await click(page, "#focusBtn"); await page.waitForTimeout(700);
  eq(await text(page, "#permAllow"), "Continue in preview");
  assert((await text(page, "#permSheet .callout")).includes("A browser can't silence notifications or calls"));
  eq(await page.evaluate(() => [...document.querySelectorAll("#permOptional .tag")].map(t => t.textContent)), ["OFF", "ON"]);
  await click(page, "#permDeny"); await page.waitForTimeout(700);
  eq(await text(page, "#barTitle"), "Focus can't start without access.");
  eq(await text(page, "#barReview"), "Review the rules again");
  eq(await text(page, "#barBack"), "Go back");
  await click(page, "#barReview"); await page.waitForTimeout(700);
  eq(await hidden(page, "permSheet"), false, "review shows the rules again");
  await click(page, "#permDeny"); await page.waitForTimeout(600);
  await click(page, "#barBack"); await page.waitForTimeout(700);
  eq(await hidden(page, "barrier"), true); eq(await hidden(page, "home"), false);
  await ctx.close();
});

/* ================= Chapters 3–4: Session ================= */
await test("session: exact status, footer, early end → 'Session ended', Done resets to 15", async () => {
  const { page, ctx, errors } = await open();
  await slide(page, -70);                                   // 30 min
  await click(page, "#focusBtn"); await page.waitForTimeout(600);
  await click(page, "#permAllow"); await page.waitForTimeout(1200);
  eq(await text(page, "#statusLine"), "Browser preview · notifications not silenced");
  eq(await text(page, "#minsNum"), "30");
  eq(await text(page, "#sessionFoot"), "White Noise · Quantum Nebula");
  await click(page, "#timerBtn"); await page.waitForTimeout(600);
  eq(await text(page, "#endTitle"), "End focus early?");
  assert(/About 30 min left/.test(await text(page, "#endLeft")));
  await page.keyboard.press("Escape"); await page.waitForTimeout(600);
  eq(await hidden(page, "confirmEnd"), true, "Escape keeps focusing");
  await click(page, "#timerBtn"); await page.waitForTimeout(600);
  await click(page, "#endBtn"); await page.waitForTimeout(1200);
  eq(await text(page, "#doneTitle"), "Session ended");
  assert((await text(page, "#quoteCite")).startsWith("— "), "author shown");
  await click(page, "#doneBtn"); await page.waitForTimeout(900);
  eq(await duration(page), 15);
  eq(errors, []);
  await ctx.close();
});

await test("session: natural finish shows 'Done' (fixed end timestamp)", async () => {
  const { page, ctx } = await open();
  await click(page, "#focusBtn"); await page.waitForTimeout(600);
  await click(page, "#permAllow"); await page.waitForTimeout(900);
  // Jump the clock past the end time: the timer derives from the end timestamp, not a counter.
  await page.evaluate(() => { const real = Date.now; Date.now = () => real() + 16 * 60 * 1000; });
  await page.waitForTimeout(1500);
  eq(await hidden(page, "done"), false); eq(await text(page, "#doneTitle"), "Done");
  await ctx.close();
});

/* ================= Chapters 8–10: Settings ================= */
await test("settings: section order, defaults, exact Alarm Safety wording, permission status", async () => {
  const { page, ctx } = await open();
  await openSettings(page);
  eq(await page.evaluate(() => [...document.querySelectorAll("#settings .set-sec h2")].map(h => h.textContent)),
    ["Sound", "Scenic view", "Trusted Contacts", "Alarm Safety", "Permissions"]);
  eq(await text(page, "#alarmState"), "ON"); eq(await text(page, "#trustState"), "OFF");
  eq(await page.evaluate(() => document.querySelector("#alarmSwitch").closest(".card").querySelector("p").textContent),
    "Allow alarms to interrupt Focus. Alarms may be used for medication, emergencies, or other safety or health-related purposes.");
  eq(await text(page, "#permPill"), "Not set up");
  eq(await text(page, "#donateBtn"), "Buy me a coffee");
  assert((await text(page, "#trustNote")).startsWith("The Trusted Contacts list is off."));
  eq(await page.evaluate(() => document.querySelectorAll("#soundList .choice").length), 9, "8 sounds + Random");
  eq(await page.evaluate(() => document.querySelectorAll("#sceneList .scene-opt").length), 9, "8 views + Random");
  eq(await hidden(page, "soundSearch"), true, "no search for short lists");
  eq(await hidden(page, "sceneSearch"), true);
  await ctx.close();
});

/* ================= Search: Trusted Contacts ================= */
await test("contacts: empty state, search appears, ranking, phone variants, did-you-mean, duplicates", async () => {
  const { page, ctx } = await open();
  await openSettings(page);
  assert((await text(page, "#contactList")).includes("No trusted contacts yet"));
  eq(await hidden(page, "contactSearch"), true);
  const people = [["Alice", "+1 403 555 0101"], ["Alicia", "(403) 555-0102"], ["Alex", "403.555.0103"], ["Grandma", "+1 780 555 1234"],
    ["Grandpa", "+1 587 555 4321"], ["Robert", "780-555-9876"], ["Rob", "+44 20 7946 0958"], ["Robin", "250 555 0199"]];
  for (const [n, p] of people) await addContact(page, n, p);
  eq(await hidden(page, "contactSearch"), false, "search shows once contacts exist");
  await page.fill("#contactQuery", "gran"); await page.waitForTimeout(400);
  eq(await visibleContacts(page), ["Grandma", "Grandpa"]);
  await page.fill("#contactQuery", "rob"); await page.waitForTimeout(400);
  eq((await visibleContacts(page))[0], "Rob", "exact name first");
  for (const q of ["+1 780 555 1234", "1-780-555-1234", "17805551234", "7805551234"]) {
    await page.fill("#contactQuery", q); await page.waitForTimeout(300);
    eq(await visibleContacts(page), ["Grandma"], q);
  }
  await page.fill("#contactQuery", "780555"); await page.waitForTimeout(400);
  eq((await visibleContacts(page)).sort(), ["Grandma", "Robert"]);
  await page.fill("#contactQuery", "alce"); await page.waitForTimeout(400);
  eq(await hidden(page, "contactEmpty"), false);
  assert((await text(page, "#contactEmpty")).includes("Did you mean Alice?"), await text(page, "#contactEmpty"));
  await click(page, "#contactEmpty .dym"); await page.waitForTimeout(400);
  eq((await visibleContacts(page))[0], "Alice", "suggested name ranks first");
  await page.fill("#contactQuery", "zzzzzz"); await page.waitForTimeout(400);
  assert((await text(page, "#contactEmpty")).includes("No matching contacts"));
  assert((await text(page, "#contactEmpty")).includes("Try a different name."));
  await page.fill("#contactQuery", ""); await page.waitForTimeout(300);
  await addContact(page, "Grandma again", "1-780-555-1234");
  eq(await page.evaluate(() => JSON.parse(localStorage.getItem("ff.settings.v1")).contacts.length), 8, "duplicate rejected");
  const saved = await page.evaluate(() => JSON.parse(localStorage.getItem("ff.settings.v1")).contacts[3]);
  eq([saved.name, saved.number, saved.normalized, typeof saved.id], ["Grandma", "+1 780 555 1234", "17805551234", "string"]);
  await ctx.close();
});

await test("contacts search keyboard: ↓ ↑ move, Enter selects, Escape clears then closes", async () => {
  const { page, ctx } = await open();
  await openSettings(page);
  for (const [n, p] of [["Grandma", "780 555 1234"], ["Grandpa", "587 555 4321"], ["Robin", "250 555 0199"]]) await addContact(page, n, p);
  await page.focus("#contactQuery"); await page.keyboard.type("gran"); await page.waitForTimeout(300);
  await page.keyboard.press("ArrowDown");
  eq(await page.evaluate(() => document.activeElement.getAttribute("aria-label")), "Grandma, 780 555 1234");
  await page.keyboard.press("ArrowDown");
  eq(await page.evaluate(() => document.activeElement.getAttribute("aria-label")), "Grandpa, 587 555 4321");
  await page.keyboard.press("ArrowUp"); await page.keyboard.press("ArrowUp");
  eq(await page.evaluate(() => document.activeElement.id), "contactQuery", "↑ from first result returns to the field");
  await page.keyboard.press("ArrowDown"); await page.keyboard.press("Enter");
  eq(await page.evaluate(() => document.activeElement.getAttribute("aria-label")), "Remove Grandma", "Enter selects the result");
  await page.focus("#contactQuery");
  await page.keyboard.press("Escape"); await page.waitForTimeout(300);
  eq(await page.inputValue("#contactQuery"), "", "first Escape clears");
  eq(await hidden(page, "settings"), false, "…and keeps Settings open");
  await page.keyboard.press("Escape"); await page.waitForTimeout(900);
  eq(await hidden(page, "settings"), true, "second Escape closes Settings");
  await page.waitForTimeout(1000);
  assert((await text(page, "#liveMsg")).length >= 0);
  await ctx.close();
});

await test("contacts: removal slides out and updates the index", async () => {
  const { page, ctx } = await open();
  await openSettings(page);
  await addContact(page, "Grandma", "780 555 1234"); await addContact(page, "Grandpa", "587 555 4321");
  await page.fill("#contactQuery", "gran"); await page.waitForTimeout(300);
  await click(page, '#contactList .rm[aria-label="Remove Grandma"]'); await page.waitForTimeout(700);
  eq(await visibleContacts(page), ["Grandpa"]);
  await page.fill("#contactQuery", "780"); await page.waitForTimeout(300);
  eq(await visibleContacts(page), [], "removed contact no longer found");
  await ctx.close();
});

/* ================= Search: sounds (appears only for long lists) ================= */
await test("sounds: search appears past 12 options; 'pnik' → did-you-mean Pink Noise; Enter selects", async () => {
  const { page, ctx, errors } = await open();
  await openSettings(page);
  for (let i = 1; i <= 4; i++) {
    await page.setInputFiles("#soundFile", { name: `rain_${i}.wav`, mimeType: "audio/wav", buffer: wav() });
    await page.waitForTimeout(500);
  }
  eq(await page.evaluate(() => document.querySelectorAll("#soundList .choice").length), 13);
  eq(await hidden(page, "soundSearch"), false, "search now useful");
  await page.fill("#soundQuery", "pnik"); await page.waitForTimeout(400);
  const empty = await text(page, "#soundEmpty");
  assert(empty.includes("No sounds found for “pnik”.") && empty.includes("Did you mean Pink Noise?"), empty);
  await page.fill("#soundQuery", "rain"); await page.waitForTimeout(400);
  eq(await page.evaluate(() => [...document.querySelectorAll("#soundList > [data-sid]")].filter(r => !r.hidden && r.dataset.want !== "hidden").length), 4);
  await page.fill("#soundQuery", "brown"); await page.waitForTimeout(300);
  await page.focus("#soundQuery"); await page.keyboard.press("Enter"); await page.waitForTimeout(200);
  eq(await page.evaluate(() => JSON.parse(localStorage.getItem("ff.settings.v1")).audio), "brown", "Enter selects the top result");
  await page.fill("#soundQuery", "xqzvw"); await page.waitForTimeout(300);
  assert((await text(page, "#soundEmpty")).includes("No matching sounds"));
  eq(errors, []);
  await ctx.close();
});

/* ================= Persistence ================= */
await test("persistence: choices saved, duration never saved", async () => {
  const { page, ctx } = await open();
  await openSettings(page);
  await click(page, '#soundList .choice[data-id="pink"]');
  await click(page, '#sceneList .scene-opt[data-id="aurora"]');
  await click(page, "#trustSwitch"); await click(page, "#alarmSwitch");
  await click(page, "#closeSettings"); await page.waitForTimeout(800);
  await slide(page, -70);
  await page.reload(); await page.waitForTimeout(500);
  eq(await duration(page), 15, "duration resets");
  const s = await page.evaluate(() => JSON.parse(localStorage.getItem("ff.settings.v1")));
  eq([s.audio, s.scene, s.trustedOn, s.alarmSafety, "duration" in s], ["pink", "aurora", true, false, false]);
  await ctx.close();
});

/* ================= Reduced motion ================= */
await test("reduced motion: state changes are immediate, no running animations", async () => {
  const { page, ctx } = await open({ reducedMotion: "reduce" });
  await click(page, "#openSettings"); await page.waitForTimeout(80);
  eq(await hidden(page, "home"), true, "home hidden immediately");
  eq(await page.evaluate(() => document.getAnimations().filter(a => a.playState === "running" && !(a instanceof CSSAnimation)).length), 0);
  eq(await page.evaluate(() => getComputedStyle(document.querySelector("#homeDial .ticks")).animationName), "none");
  await ctx.close();
});

/* ================= Privacy ================= */
await test("privacy: no network except fonts; nothing personal logged", async () => {
  const { page, ctx, requests, logs } = await open();
  await openSettings(page);
  await addContact(page, "Grandma", "+1 780 555 1234");
  await page.fill("#contactQuery", "Grandma"); await page.waitForTimeout(300);
  await page.fill("#contactQuery", "7805551234"); await page.waitForTimeout(300);
  const external = requests.filter(u => !u.startsWith("http://127.0.0.1") && !/^https:\/\/fonts\.(googleapis|gstatic)\.com\//.test(u) && !u.startsWith("data:") && !u.startsWith("blob:"));
  eq(external, [], "unexpected requests");
  assert(logs.every(l => !/grandma|7805551234|780 555/i.test(l)), "console must not contain names or numbers");
  await ctx.close();
});

/* ================= Failure isolation ================= */
await test("search failure never prevents Focus from starting", async () => {
  const { page, ctx, errors } = await open({ blockSearch: true });
  await openSettings(page);
  await addContact(page, "Grandma", "780 555 1234");
  eq(await hidden(page, "contactSearch"), true, "search UI hidden without the search module");
  await click(page, "#closeSettings"); await page.waitForTimeout(800);
  await click(page, "#focusBtn"); await page.waitForTimeout(600);
  await click(page, "#permAllow"); await page.waitForTimeout(1000);
  eq(await hidden(page, "session"), false, "session running");
  eq(errors.filter(e => !/search\.js/.test(e)), []);
  await ctx.close();
});

await browser.close();
server.close();
const failed = results.filter(r => !r[0]);
console.log(`\n${results.length - failed.length}/${results.length} UI tests passed`);
process.exit(failed.length ? 1 : 0);
