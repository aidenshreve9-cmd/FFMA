"use strict";

/* ================= Extras ================= */
// Quick-pick times, "What are you focusing on?", pause and +5 min during Focus, a countdown in the
// tab title, today's total with a streak and daily goal, session history, and a break timer.

const HKEY = "ff.history.v1";
const BASE_TITLE = document.title;
const clockOf = ms => { const s = Math.ceil(ms / 1000); return Math.floor(s / 60) + ":" + String(s % 60).padStart(2, "0"); };

/* ---------- History (local only) ---------- */
function loadHistory() {
  try {
    const raw = JSON.parse(localStorage.getItem(HKEY) || "[]");
    if (!Array.isArray(raw)) return [];
    return raw.filter(h => h && typeof h.start === "number" && typeof h.focused === "number").slice(0, 200)
      .map(h => ({ start: h.start, focused: Math.max(0, Math.round(h.focused)), planned: Number(h.planned) || 0, done: h.done === true,
        intention: typeof h.intention === "string" ? h.intention.slice(0, 60) : "" }));
  } catch (e) { return []; }
}
const pastSessions = loadHistory(); // newest first
function saveHistory() {
  try { localStorage.setItem(HKEY, JSON.stringify(pastSessions.slice(0, 200))); }
  catch (e) { toast("Couldn't save your history on this device."); }
}

const dayKey = t => { const d = new Date(t); return d.getFullYear() + "-" + (d.getMonth() + 1) + "-" + d.getDate(); };
function minutesOn(key) { return pastSessions.reduce((n, h) => n + (dayKey(h.start) === key ? h.focused : 0), 0); }
// Days in a row with at least a minute of Focus. Today counts once you've focused; until then
// yesterday's streak still stands.
function streakDays() {
  const days = new Set(pastSessions.filter(h => h.focused >= 1).map(h => dayKey(h.start)));
  const d = new Date(); d.setHours(12, 0, 0, 0);
  if (!days.has(dayKey(d))) d.setDate(d.getDate() - 1);
  let n = 0;
  while (days.has(dayKey(d))) { n++; d.setDate(d.getDate() - 1); }
  return n;
}
const goalLabel = g => (g >= 60 && g % 60 === 0 ? g / 60 + " h" : g + " min");

/* ---------- Home: quick picks, intention, today ---------- */
const quickBtns = QUICK_PICKS.map(m => {
  const b = el("button", { type: "button", class: "chip", "aria-label": `${m} minutes`, text: String(m) });
  b.onclick = () => { setMinutes(m); hideHint(); syncQuickPicks(); };
  $("quickPicks").appendChild(b);
  return b;
});
function syncQuickPicks() { quickBtns.forEach((b, i) => b.setAttribute("aria-pressed", String(QUICK_PICKS[i] === minutes))); }

function currentIntention() { return $("intention").value.trim().slice(0, 60); }
$("intention").addEventListener("keydown", e => { if (e.key === "Enter") { e.preventDefault(); $("intention").blur(); requestStart(); } });

function renderToday() {
  const today = minutesOn(dayKey(Date.now())), goal = settings.goal, streak = streakDays();
  $("todayText").textContent = goal ? `Today ${today} of ${goalLabel(goal)}` : `Today ${today} min`;
  $("streakText").textContent = streak ? `${streak}-day streak` : "";
  $("todayBarWrap").hidden = !goal;
  $("todayBar").style.width = goal ? Math.min(100, (today / goal) * 100) + "%" : "0";
  $("todayStats").classList.toggle("met", !!goal && today >= goal);
}

/* ---------- During Focus: pause, +5 min, ends-at, tab title ---------- */
function onSessionStart() {
  const it = session.intention;
  $("intentShow").hidden = !it;
  $("intentShow").textContent = it;
  setPausedLook(false);
}
function onSessionTick(ms) {
  const paused = !!session.pausedAt;
  document.title = (paused ? "Paused · " : "") + clockOf(ms) + " · Focus Friend";
  $("endsAt").textContent = paused ? "Paused" : "Ends at " + new Date(session.endAt).toLocaleTimeString([], { hour: "numeric", minute: "2-digit" });
  $("extendBtn").disabled = ms + EXTEND_MIN * 60000 > MAX_MINUTES * 60000;
}
function setPausedLook(paused) {
  $("pauseBtn").textContent = paused ? "Resume" : "Pause";
  $("pauseBtn").setAttribute("aria-pressed", String(paused));
  $("session").classList.toggle("paused", paused);
}
$("pauseBtn").onclick = () => {
  if (!session || session.finished || state !== "ACTIVE_SESSION") return;
  if (session.pausedAt) {
    const gap = Date.now() - session.pausedAt;
    session.endAt += gap; session.pausedTotal += gap; session.pausedAt = 0;
    say("Resumed.");
  } else {
    session.pausedAt = Date.now();
    say("Paused.");
  }
  setPausedLook(!!session.pausedAt);
  tick();
};
$("extendBtn").textContent = `+${EXTEND_MIN} min`;
$("extendBtn").onclick = () => {
  if (!session || session.finished || state !== "ACTIVE_SESSION") return;
  if (remainingMs() + EXTEND_MIN * 60000 > MAX_MINUTES * 60000) { toast(`Focus can run up to ${MAX_MINUTES} minutes at a time.`); return; }
  session.endAt += EXTEND_MIN * 60000;
  session.planned += EXTEND_MIN;
  session.minutes = Math.min(MAX_MINUTES, session.minutes + EXTEND_MIN);
  toast(`Added ${EXTEND_MIN} minutes.`);
  lastAnnounced = null;
  tick();
};

function onSessionFinish(natural) {
  const end = session.pausedAt || Date.now();
  const focused = Math.round(Math.max(0, end - session.startedAt - session.pausedTotal) / 60000);
  pastSessions.unshift({ start: session.startedAt, focused, planned: session.planned, done: natural, intention: session.intention || "" });
  saveHistory();
  document.title = BASE_TITLE;
  $("doneIntent").hidden = !session.intention;
  $("doneIntent").textContent = session.intention;
  $("intention").value = "";
  $("breakBtn").hidden = false;
  renderToday();
  renderHistory();
  if (natural && "vibrate" in navigator) { try { navigator.vibrate([120, 80, 120]); } catch (e) {} }
}

/* ---------- Done: break timer ---------- */
let breakT = 0, breakEnd = 0;
$("breakBtn").textContent = `Take a ${BREAK_MIN}-min break`;
$("breakBtn").onclick = () => {
  breakEnd = Date.now() + BREAK_MIN * 60000;
  $("quoteCard").hidden = true;
  $("breakCard").hidden = false;
  $("breakBtn").hidden = true;
  say(`${BREAK_MIN}-minute break started.`);
  breakTick();
  clearInterval(breakT);
  breakT = setInterval(breakTick, 1000);
};
function breakTick() {
  if (!breakEnd) return;
  const ms = Math.max(0, breakEnd - Date.now());
  $("breakTime").textContent = clockOf(ms);
  document.title = "Break · " + clockOf(ms) + " · Focus Friend";
  if (ms <= 0) stopBreak(true);
}
function stopBreak(finished) {
  if (!breakEnd) return;
  clearInterval(breakT); breakT = 0; breakEnd = 0;
  document.title = BASE_TITLE;
  $("breakCard").hidden = true;
  $("quoteCard").hidden = false;
  $("breakBtn").hidden = false;
  if (finished) { playChime(); toast("Break's over. Ready for another round?"); say("Break's over."); }
}
$("skipBreak").onclick = () => stopBreak(false);
document.addEventListener("visibilitychange", () => { if (document.visibilityState === "visible") breakTick(); });

/* ---------- Settings: daily goal and history ---------- */
function renderGoal() {
  const row = $("goalRow"); row.textContent = "";
  GOALS.forEach(g => {
    const b = el("button", { type: "button", class: "chip", role: "radio", "aria-checked": String(settings.goal === g), text: g ? goalLabel(g) : "Off" });
    b.onclick = () => { settings.goal = g; saveSettings(); renderGoal(); renderToday(); };
    row.appendChild(b);
  });
  $("goalNote").textContent = settings.goal
    ? `Home shows how close you are to ${goalLabel(settings.goal)} of Focus today.`
    : "No daily goal. Home still shows today's total.";
}

function whenLabel(t) {
  const d = new Date(t), time = d.toLocaleTimeString([], { hour: "numeric", minute: "2-digit" });
  const y = new Date(); y.setDate(y.getDate() - 1);
  if (dayKey(t) === dayKey(Date.now())) return "Today, " + time;
  if (dayKey(t) === dayKey(y)) return "Yesterday, " + time;
  return d.toLocaleDateString([], { month: "short", day: "numeric" }) + ", " + time;
}
function renderHistory() {
  const list = $("historyList"); list.textContent = "";
  if (!pastSessions.length) list.appendChild(el("p", { class: "hist-empty", text: "Your finished sessions will show up here." }));
  pastSessions.slice(0, 20).forEach(h => {
    list.appendChild(el("div", { class: "hist-row", role: "listitem" }, [
      el("div", { class: "hist-main" }, [
        el("b", { text: h.intention || "Focus" }),
        el("span", { class: "hist-when", text: whenLabel(h.start) }),
      ]),
      el("div", { class: "hist-side" }, [
        el("span", { class: "hist-min", text: h.done ? `${h.focused} min` : `${h.focused} of ${h.planned} min` }),
        el("span", { class: "pill" + (h.done ? "" : " no"), text: h.done ? "Completed" : "Ended early" }),
      ]),
    ]));
  });
  $("clearHistory").hidden = !pastSessions.length;
}
let clearArmed = 0;
$("clearHistory").onclick = () => {
  const b = $("clearHistory");
  if (!clearArmed) {
    b.textContent = "Tap again to clear";
    clearArmed = setTimeout(() => { clearArmed = 0; b.textContent = "Clear history"; }, 4000);
    return;
  }
  clearTimeout(clearArmed); clearArmed = 0;
  pastSessions.length = 0;
  saveHistory(); renderHistory(); renderToday();
  b.textContent = "Clear history";
  toast("History cleared.");
};

syncQuickPicks();
renderToday();
renderGoal();
renderHistory();
