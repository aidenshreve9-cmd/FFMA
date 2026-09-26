"use strict";

/* ================= Orbital dial ================= */
const SVGNS = "http://www.w3.org/2000/svg";
const ARC_R = 84, ARC_C = 2 * Math.PI * ARC_R;
function svg(tag, attrs, parent) {
  const n = document.createElementNS(SVGNS, tag);
  Object.entries(attrs).forEach(([k, v]) => n.setAttribute(k, v));
  if (parent) parent.appendChild(n);
  return n;
}
function buildDial(root, gid) {
  const defs = svg("defs", {}, root);
  const lg = svg("linearGradient", { id: gid, x1: "0", y1: "0", x2: "1", y2: "1" }, defs);
  svg("stop", { offset: "0", "stop-color": "#F72585" }, lg);
  svg("stop", { offset: ".55", "stop-color": "#D900FF" }, lg);
  svg("stop", { offset: "1", "stop-color": "#8A2BE2" }, lg);
  const f = svg("filter", { id: gid + "G", x: "-30%", y: "-30%", width: "160%", height: "160%" }, defs);
  svg("feGaussianBlur", { stdDeviation: "2.4", result: "b" }, f);
  const m = svg("feMerge", {}, f); svg("feMergeNode", { in: "b" }, m); svg("feMergeNode", { in: "SourceGraphic" }, m);

  svg("circle", { class: "outer", cx: 100, cy: 100, r: 97, fill: "none", "stroke-width": ".6" }, root);
  const ticks = svg("g", { class: "ticks" }, root);
  // A fixed compass-style scale: one tick per minute, a longer tick every 5.
  for (let i = 0; i < 60; i++) {
    const a = i / 60 * Math.PI * 2, major = i % 5 === 0, r1 = major ? 87.5 : 90.5, r2 = 93;
    svg("line", { class: major ? "major" : "", x1: 100 + Math.sin(a) * r1, y1: 100 - Math.cos(a) * r1, x2: 100 + Math.sin(a) * r2, y2: 100 - Math.cos(a) * r2, "stroke-width": major ? ".7" : ".35" }, ticks);
  }
  svg("circle", { class: "track", cx: 100, cy: 100, r: ARC_R, fill: "none", "stroke-width": "2.2" }, root);
  const arc = svg("circle", { class: "arc", cx: 100, cy: 100, r: ARC_R, fill: "none", "stroke-width": "2.6", "stroke-linecap": "round",
    stroke: `url(#${gid})`, filter: `url(#${gid}G)`, transform: "rotate(-90 100 100)", "stroke-dasharray": ARC_C, "stroke-dashoffset": ARC_C }, root);
  const node = svg("circle", { class: "node", r: 2.2, cx: 100, cy: 100 - ARC_R, filter: `url(#${gid}G)` }, root);
  const handleRing = svg("circle", { class: "handle-ring", r: 7.5, cx: 100, cy: 100 - ARC_R }, root);
  const handle = svg("circle", { class: "handle", r: 4.4, cx: 100, cy: 100 - ARC_R, filter: `url(#${gid}G)` }, root);
  svg("circle", { class: "inner", cx: 100, cy: 100, r: 77, fill: "none", "stroke-width": ".5" }, root);
  svg("circle", { class: "breath", cx: 100, cy: 100, r: 66, fill: "none", "stroke-width": "1.1", filter: `url(#${gid}G)` }, root);
  const nums = [5, 10, 15, 20, 25, 30, 35, 40, 45, 50, 55, 60].map(mn => {
    const a = mn / 60 * Math.PI * 2, r = 72.5;
    const t = svg("text", { class: "num", x: 100 + Math.sin(a) * r, y: 100 - Math.cos(a) * r + 2.4, "text-anchor": "middle" }, root);
    t.textContent = mn; return t;
  });
  return {
    set(frac, activeMinutes) {
      frac = Math.max(0, Math.min(1, frac));
      arc.setAttribute("stroke-dashoffset", ARC_C * (1 - frac));
      const a = frac * Math.PI * 2;
      node.setAttribute("cx", 100 + Math.sin(a) * ARC_R); node.setAttribute("cy", 100 - Math.cos(a) * ARC_R);
      node.style.opacity = frac > 0.002 ? 1 : 0;
      [handle, handleRing].forEach(h => { h.setAttribute("cx", 100 + Math.sin(a) * ARC_R); h.setAttribute("cy", 100 - Math.cos(a) * ARC_R); });
      nums.forEach(t => t.classList.toggle("on", +t.textContent === activeMinutes));
    },
  };
}
const homeDial = buildDial($("homeDial"), "gH");
const sessionDial = buildDial($("sessionDial"), "gS");

/* ================= Duration dial (V3.1: 1–60 minutes, set on the ring like a compass) ================= */
let minutes = DEFAULT_MINUTES;
let durAnnounceT = 0;
const clampMinutes = m => Math.max(MIN_MINUTES, Math.min(MAX_MINUTES, Math.round(m)));
// Returns true when the time changed.
function setMinutes(m) {
  m = clampMinutes(m);
  if (m === minutes) return false;
  minutes = m; renderDuration(true);
  return true;
}
function renderDuration(announce) {
  const m = minutes, word = m === 1 ? "minute" : "minutes";
  $("homeMins").textContent = m;
  $("focusBtn").setAttribute("aria-label", `Focus, ${m} ${word}. Drag around the ring, or use the arrow keys, to change the time. Press to start.`);
  homeDial.set(m / 60, m);
  if (typeof syncQuickPicks === "function") syncQuickPicks();
  clearTimeout(durAnnounceT);
  // Announced once the time settles, not on every minute a drag passes through.
  if (announce) durAnnounceT = setTimeout(() => { $("durLive").textContent = `${m} ${word}`; }, 450);
}
function resetDuration() { minutes = DEFAULT_MINUTES; renderDuration(false); }
function hideHint() { $("hint").classList.add("gone"); }

(function bindGesture() {
  const btn = $("focusBtn");
  let down = false, onRing = false, moved = false, swallow = false, sx = 0, sy = 0;
  // Where a pointer is on the dial: distance from the centre (1 = the dial's edge), and its compass
  // bearing in degrees (0 at the top, clockwise).
  function polar(e) {
    const r = btn.getBoundingClientRect(), dx = e.clientX - (r.left + r.width / 2), dy = e.clientY - (r.top + r.height / 2);
    return { d: Math.hypot(dx, dy) / (r.width / 2), deg: (Math.atan2(dx, -dy) * 180 / Math.PI + 360) % 360 };
  }
  // 6° per minute. The top of the ring is 60.
  const minutesAt = deg => { const m = Math.round(deg / 6); return m === 0 ? 60 : m; };
  function follow(deg) {
    let m = minutesAt(deg);
    // A drag never jumps across the top: pushing past 60 holds at 60, pulling back past 1 holds at 1.
    if (minutes >= 50 && m <= 10) m = MAX_MINUTES;
    else if (minutes <= 10 && m >= 50) m = MIN_MINUTES;
    setMinutes(m);
  }
  btn.addEventListener("pointerdown", e => {
    down = true; moved = false; swallow = false; sx = e.clientX; sy = e.clientY;
    const p = polar(e);
    onRing = p.d >= RING_INNER;
    try { btn.setPointerCapture(e.pointerId); } catch (_) {}
    setActive("home", true);
    if (onRing) { btn.classList.add("dragging"); setMinutes(minutesAt(p.deg)); hideHint(); } // touching the ring jumps there
  });
  btn.addEventListener("pointermove", e => {
    if (!down) return;
    if (onRing) follow(polar(e).deg);
    else if (!moved && Math.hypot(e.clientX - sx, e.clientY - sy) > TAP_SLOP) moved = true;
  });
  const release = () => {
    if (!down) return;
    down = false; setActive("home", false); btn.classList.remove("dragging");
    if (onRing || moved) swallow = true;     // setting the time, or a stray slide, never starts a session
  };
  btn.addEventListener("pointerup", release);
  btn.addEventListener("pointercancel", () => { release(); swallow = true; });
  btn.addEventListener("pointerenter", e => { if (e.pointerType === "mouse") setActive("home", true); });
  btn.addEventListener("pointerleave", e => { if (e.pointerType === "mouse" && !down) setActive("home", false); });
  btn.addEventListener("click", () => {
    if (swallow) { swallow = false; return; }
    hideHint(); requestStart();
  });
  btn.addEventListener("keydown", e => {
    const step = { ArrowRight: 1, ArrowUp: 1, ArrowLeft: -1, ArrowDown: -1, PageUp: 5, PageDown: -5 }[e.key];
    if (step) { e.preventDefault(); setMinutes(minutes + step); hideHint(); }
    else if (e.key === "Home" || e.key === "End") { e.preventDefault(); setMinutes(e.key === "Home" ? MIN_MINUTES : MAX_MINUTES); hideHint(); }
  });
  const tb = $("timerBtn");
  tb.addEventListener("pointerdown", () => setActive("session", true));
  ["pointerup", "pointercancel"].forEach(ev => tb.addEventListener(ev, () => setActive("session", false)));
  tb.addEventListener("pointerenter", e => { if (e.pointerType === "mouse") setActive("session", true); });
  tb.addEventListener("pointerleave", e => { if (e.pointerType === "mouse") setActive("session", false); });
})();

/* ================= Touch/hover active state + ripples ================= */
const act = { home: { v: 0, target: 0, timer: 0 }, session: { v: 0, target: 0, timer: 0 } };
function ripple(key) {
  if (!motionOK()) return;
  const host = $(key + "Ripples"), r = document.createElement("span");
  r.className = "ripple"; host.appendChild(r);
  const a = r.animate([{ transform: "scale(1)", opacity: .75 }, { transform: "scale(3.1)", opacity: 0 }], { duration: 2800, easing: "cubic-bezier(.2,.6,.3,1)" });
  a.onfinish = () => r.remove();
}
function setActive(key, on) {
  const s = act[key];
  if (on === (s.target === 1)) return;
  s.target = on ? 1 : 0; markDirty();
  clearInterval(s.timer);
  if (on) { ripple(key); s.timer = setInterval(() => ripple(key), 1500); }
}
