"use strict";

/* ================= Scenic views: eight cosmic scenes, drawn in code ================= */
const RG = rng(3);
const GALAXY = Array.from({ length: 1500 }, (_, i) => {
  const arm = i % 2, rr = Math.pow(RG(), .75), spread = (RG() - .5) * .55 * (1 - rr * .4);
  const col = rr < .16 ? "#FFE3CC" : rr < .35 ? (RG() < .5 ? "#E0C3FC" : "#F72585") : (RG() < .6 ? "#8A2BE2" : "#6f7dff");
  return { ang: arm * Math.PI + rr * 5.2 + spread, rr, off: (RG() - .5) * .05, s: .6 + RG() * 1.2, a: .35 + RG() * .6, col };
});
const DISK = Array.from({ length: 900 }, () => {
  const k = 1.55 + Math.pow(RG(), 1.4) * 2.6;
  const col = k < 1.9 ? "#FFE3CC" : k < 2.4 ? "#FF9A5A" : k < 3.1 ? "#FF5400" : RG() < .5 ? "#F72585" : "#8A2BE2";
  return { k, ang: RG() * Math.PI * 2, sp: .35 / Math.pow(k, 1.5), s: .6 + RG() * 1.4, a: .3 + RG() * .6, col, z: (RG() - .5) * .06 };
});
const MW = Array.from({ length: 900 }, () => {
  const g = (RG() + RG() + RG() - 1.5) / 1.5;
  return { u: RG(), v: g * .16, s: .5 + RG() * 1.1, a: .25 + RG() * .7, col: RG() < .15 ? "#FFD2B0" : RG() < .3 ? "#E0C3FC" : "#cfd6ff" };
});
const NODES = Array.from({ length: 28 }, () => ({ x: .05 + RG() * .9, y: .05 + RG() * .9, ph: RG() * 6.28, r: .6 + RG() * 1.2 }));
const EDGES = [];
NODES.forEach((a, i) => {
  NODES.map((b, j) => ({ j, d: Math.hypot(a.x - b.x, a.y - b.y) })).filter(e => e.j !== i).sort((p, q) => p.d - q.d).slice(0, 3)
    .forEach(e => { if (!EDGES.some(x => (x[0] === e.j && x[1] === i))) EDGES.push([i, e.j, RG()]); });
});
const YOUNG = Array.from({ length: 16 }, () => ({ x: RG(), y: RG() * .7, s: .6 + RG() * 1.4, ph: RG() * 6.28 }));
const nodeAt = (n, t, w, h) => [(n.x + .012 * Math.sin(t * .03 + n.ph)) * w, (n.y + .012 * Math.cos(t * .025 + n.ph)) * h];

const PAINT = {
  quantum(c, w, h, t) {
    vgrad(c, w, h, ["#000000", "#07021a", "#10042c"]);
    const m = Math.max(w, h);
    c.globalCompositeOperation = "lighter";
    [["#6A0DAD", .5, .30, .38, .55, -.5, 2.4], ["#D900FF", .22, .62, .50, .45, .7, 2.8], ["#F72585", .18, .40, .66, .42, -.2, 3.0],
     ["#1E3AA8", .35, .75, .30, .5, .4, 2.2], ["#8A2BE2", .35, .50, .80, .5, -.8, 2.6], ["#FF5400", .10, .70, .22, .3, .2, 2.4]]
      .forEach(([col, a, x, y, r, rot, st], i) => streak(c, w * (x + .05 * Math.sin(t * .02 + i)), h * (y + .04 * Math.cos(t * .017 + i)), m * r * .5, col, a, rot + .15 * Math.sin(t * .01 + i), st, 1.3 / st));
    c.globalCompositeOperation = "source-over";
    starfield(c, w, h, t, 150, .8);
  },
  galaxy(c, w, h, t) {
    vgrad(c, w, h, ["#000000", "#02010a", "#05031a"]);
    starfield(c, w, h, t, 120, .7);
    const cx = w * .5, cy = h * .44, sc = Math.min(w, h) * .47, rot = t * .012;
    c.globalCompositeOperation = "lighter";
    streak(c, cx, cy, sc * 1.1, "#2A085C", .55, -.35, 1, .55);
    glow(c, cx, cy, sc * .38, "#FFB080", .45);
    glow(c, cx, cy, sc * .14, "#FFFFFF", .8);
    const cr = Math.cos(-.35), sr = Math.sin(-.35);
    GALAXY.forEach(p => {
      const a = p.ang + rot * (1.4 - p.rr), x0 = Math.cos(a) * p.rr * sc + p.off * sc, y0 = Math.sin(a) * p.rr * sc * .52;
      c.fillStyle = `rgba(${rgb(p.col)},${p.a})`; c.fillRect(cx + x0 * cr - y0 * sr, cy + x0 * sr + y0 * cr, p.s, p.s);
    });
    c.globalCompositeOperation = "source-over";
  },
  horizon(c, w, h, t) {
    c.fillStyle = "#000"; c.fillRect(0, 0, w, h);
    starfield(c, w, h, t, 140, .6);
    const cx = w * .5, cy = h * .44, R = Math.min(w, h) * .13;
    c.globalCompositeOperation = "lighter";
    glow(c, cx, cy, R * 4, "#FF5400", .22);
    const part = back => DISK.forEach(p => {
      const a = p.ang + t * p.sp, s = Math.sin(a); if ((s < 0) !== back) return;
      const x = cx + Math.cos(a) * p.k * R, y = cy + (s * .2 + p.z) * p.k * R;
      const dop = .55 + .45 * Math.cos(a);                       // brighter on the approaching side
      c.fillStyle = `rgba(${rgb(p.col)},${(p.a * dop).toFixed(3)})`; c.fillRect(x, y, p.s * 1.4, p.s);
    });
    part(true);
    // lensed image of the far side of the disk, arching over the shadow
    c.lineCap = "round";
    [[R * 1.55, .5, 3], [R * 1.75, .25, 6], [R * 2.1, .08, 10]].forEach(([rr, a, lw]) => {
      c.strokeStyle = `rgba(255,154,90,${a})`; c.lineWidth = lw; c.beginPath(); c.ellipse(cx, cy, rr, rr * .92, 0, Math.PI * 1.05, Math.PI * 1.95); c.stroke();
    });
    c.globalCompositeOperation = "source-over";
    const sh = c.createRadialGradient(cx, cy, R * .8, cx, cy, R * 1.12); sh.addColorStop(0, "#000"); sh.addColorStop(1, "rgba(0,0,0,0)");
    c.fillStyle = sh; c.fillRect(cx - R * 1.2, cy - R * 1.2, R * 2.4, R * 2.4);
    c.fillStyle = "#000"; c.beginPath(); c.arc(cx, cy, R, 0, 7); c.fill();
    c.globalCompositeOperation = "lighter";
    c.strokeStyle = "rgba(255,227,204,.85)"; c.lineWidth = 1.2; c.beginPath(); c.arc(cx, cy, R * 1.03, 0, 7); c.stroke();
    part(false);
    c.globalCompositeOperation = "source-over";
  },
  aurora(c, w, h, t) {
    vgrad(c, w, h, ["#000000", "#02010c", "#0a0626"]);
    starfield(c, w, h, t, 160, .75);
    c.globalCompositeOperation = "lighter";
    [["#8A2BE2", 0], ["#D900FF", 1], ["#3a5bff", 2]].forEach(([col, b]) => {
      const top = x => h * (.24 + .08 * b + .06 * Math.sin(x / w * 5 + t * .07 + b * 2) + .025 * Math.sin(x / w * 13 - t * .05 + b));
      const H = h * (.34 - .06 * b), g = c.createLinearGradient(0, h * .12, 0, h * .9), k = rgb(col);
      g.addColorStop(0, `rgba(${k},0)`); g.addColorStop(.35, `rgba(${k},.30)`); g.addColorStop(.55, `rgba(${k},.12)`); g.addColorStop(1, `rgba(${k},0)`);
      c.fillStyle = g; c.beginPath(); c.moveTo(0, top(0));
      for (let x = 0; x <= w; x += 6) c.lineTo(x, top(x));
      for (let x = w; x >= 0; x -= 6) c.lineTo(x, top(x) + H * (.6 + .4 * Math.sin(x / w * 7 + t * .04 + b)));
      c.closePath(); c.fill();
      c.lineWidth = 1;
      for (let x = 0; x <= w; x += 5) {
        const a = .05 * (.5 + .5 * Math.sin(x * .31 + t * .2 + b)); if (a < .01) continue;
        c.strokeStyle = `rgba(${k},${a.toFixed(3)})`; c.beginPath(); c.moveTo(x, top(x)); c.lineTo(x, top(x) + H * .9); c.stroke();
      }
    });
    c.globalCompositeOperation = "source-over";
    c.fillStyle = "#010005"; c.beginPath(); c.moveTo(0, h);
    for (let x = 0; x <= w; x += 8) c.lineTo(x, h * .9 + Math.sin(x / w * 6) * h * .02 + Math.sin(x / w * 17) * h * .008);
    c.lineTo(w, h); c.closePath(); c.fill();
  },
  dust(c, w, h, t) {
    vgrad(c, w, h, ["#000000", "#030724", "#081138"]);
    starfield(c, w, h, t, 120, .6);
    const ang = -.62, cx = w * .5, cy = h * .5, len = Math.hypot(w, h), ca = Math.cos(ang), sa = Math.sin(ang);
    const at = (u, v) => [cx + (u - .5) * len * ca - v * len * sa, cy + (u - .5) * len * sa + v * len * ca];
    c.globalCompositeOperation = "lighter";
    for (let i = 0; i <= 8; i++) { const [x, y] = at(i / 8, 0); glow(c, x, y, len * .2, i % 3 === 1 ? "#8A2BE2" : "#2A3A9A", .28); }
    const drift = t * .002;
    MW.forEach(s => { const [x, y] = at(((s.u + drift) % 1 + 1) % 1, s.v); c.fillStyle = `rgba(${rgb(s.col)},${s.a})`; c.fillRect(x, y, s.s, s.s); });
    c.globalCompositeOperation = "source-over";
    [[.22, .01, .18], [.45, -.02, .22], [.7, .015, .2], [.9, -.01, .15]].forEach(([u, v, r], i) => {
      const [x, y] = at(u + .02 * Math.sin(t * .01 + i), v); streak(c, x, y, len * r * .5, "#000000", .75, ang, 3, .28);
    });
  },
  nursery(c, w, h, t) {
    vgrad(c, w, h, ["#000000", "#0d030d", "#1a0619"]);
    starfield(c, w, h, t, 90, .5);
    const m = Math.max(w, h);
    c.globalCompositeOperation = "lighter";
    streak(c, w * .5, h * .3, m * .4, "#F72585", .38, .3 + .05 * Math.sin(t * .02), 2.2, .6);
    streak(c, w * .35, h * .42, m * .32, "#FF5400", .26, -.4, 2, .6);
    streak(c, w * .7, h * .22, m * .3, "#D900FF", .22, .8, 2.2, .5);
    glow(c, w * .52, h * .36, m * .12, "#FFE3CC", .35);
    c.globalCompositeOperation = "source-over";
    [[.28, .16, .06, .44], [.55, .22, .08, .34], [.8, .14, .05, .5]].forEach(([x0, wb, wt, top], i) => {
      c.fillStyle = "rgba(4,1,6,.94)"; c.beginPath();
      c.moveTo(w * (x0 - wb / 2), h);
      for (let y = h; y >= h * top; y -= 6) { const k = (h - y) / (h * (1 - top)); c.lineTo(w * (x0 - (wb + (wt - wb) * k) / 2) + Math.sin(y * .05 + i) * 4, y); }
      c.quadraticCurveTo(w * x0, h * (top - .04), w * (x0 + wt / 2), h * top);
      for (let y = h * top; y <= h; y += 6) { const k = (h - y) / (h * (1 - top)); c.lineTo(w * (x0 + (wb + (wt - wb) * k) / 2) + Math.sin(y * .045 + i * 2) * 4, y); }
      c.closePath(); c.fill();
      c.globalCompositeOperation = "lighter"; glow(c, w * x0, h * top, w * .08, "#FF9A5A", .35); c.globalCompositeOperation = "source-over";
    });
    c.globalCompositeOperation = "lighter";
    YOUNG.forEach(s => {
      const x = s.x * w, y = s.y * h, a = .55 + .45 * Math.sin(t * .5 + s.ph), L = 5 + s.s * 6;
      glow(c, x, y, L, "#E0C3FC", .45 * a);
      c.strokeStyle = `rgba(255,255,255,${(.5 * a).toFixed(3)})`; c.lineWidth = .7;
      c.beginPath(); c.moveTo(x - L, y); c.lineTo(x + L, y); c.moveTo(x, y - L); c.lineTo(x, y + L); c.stroke();
    });
    c.globalCompositeOperation = "source-over";
  },
  web(c, w, h, t) {
    c.fillStyle = "#000"; c.fillRect(0, 0, w, h);
    c.globalCompositeOperation = "lighter";
    glow(c, w * .5, h * .5, Math.max(w, h) * .7, "#1E005B", .5);
    c.lineWidth = .8;
    EDGES.forEach(([i, j, ph]) => {
      const [x1, y1] = nodeAt(NODES[i], t, w, h), [x2, y2] = nodeAt(NODES[j], t, w, h);
      c.strokeStyle = "rgba(138,43,226,.28)"; c.beginPath(); c.moveTo(x1, y1); c.lineTo(x2, y2); c.stroke();
      const k = ((t * .04 + ph) % 1 + 1) % 1; glow(c, x1 + (x2 - x1) * k, y1 + (y2 - y1) * k, 5, "#E0C3FC", .5);
    });
    NODES.forEach(n => {
      const [x, y] = nodeAt(n, t, w, h);
      glow(c, x, y, 12 + n.r * 12, "#D900FF", .22 + .08 * Math.sin(t * .3 + n.ph));
      c.fillStyle = "rgba(255,240,255,.85)"; c.beginPath(); c.arc(x, y, n.r, 0, 7); c.fill();
    });
    c.globalCompositeOperation = "source-over";
    starfield(c, w, h, t, 60, .4);
  },
  void(c, w, h, t) {
    vgrad(c, w, h, ["#000000", "#020106", "#05020e"]);
    const m = Math.max(w, h);
    c.globalCompositeOperation = "lighter";
    glow(c, w * (.3 + .05 * Math.sin(t * .012)), h * (.34 + .03 * Math.cos(t * .01)), m * .35, "#8A2BE2", .22);
    glow(c, w * (.72 + .04 * Math.cos(t * .009)), h * (.6 + .04 * Math.sin(t * .011)), m * .3, "#E0C3FC", .12);
    glow(c, w * (.5 + .06 * Math.sin(t * .007)), h * (.82 + .02 * Math.cos(t * .013)), m * .4, "#1E005B", .5);
    [.3, .55, .78].forEach((y, i) => streak(c, w * (.5 + .2 * Math.sin(t * .008 + i * 2)), h * y, m * .3, "#B7A6CC", .05, 0, 3.4, .22));
    c.globalCompositeOperation = "source-over";
    starfield(c, w, h, t, 45, .45);
  },
};
function paintImage(c, w, h, img) {
  const s = Math.max(w / img.naturalWidth, h / img.naturalHeight), iw = img.naturalWidth * s, ih = img.naturalHeight * s;
  c.fillStyle = "#000"; c.fillRect(0, 0, w, h);
  c.drawImage(img, (w - iw) / 2, (h - ih) / 2, iw, ih);
}
function resolveScene() { return sceneChoiceFor(settings.scene) || { id: DEFAULT_SCENE, name: SCENES[0].name }; }
function sceneChoiceFor(id) {
  if (id === "random") {
    const pool = [...SCENES.map(s => s.id), ...assets.filter(a => a.type === "img").map(a => a.id)];
    id = pool[Math.floor(Math.random() * pool.length)];
  }
  const b = SCENES.find(s => s.id === id); if (b) return { id, name: b.name };
  const a = assets.find(x => x.id === id);
  return a ? { id, name: a.name, url: a.url } : null;
}
let sceneImg = null, sceneChoice = null;
function drawScene(t) {
  const { c, w, h } = fit($("sceneCanvas"), 1);
  if (sceneChoice.url) { if (sceneImg && sceneImg.complete && sceneImg.naturalWidth) paintImage(c, w, h, sceneImg); else { c.fillStyle = "#000"; c.fillRect(0, 0, w, h); } return; }
  PAINT[sceneChoice.id](c, w, h, t);
}
function startScene(choice) {
  sceneChoice = choice; sceneImg = null;
  if (choice.url) {
    sceneImg = new Image(); sceneImg.onload = markDirty;
    sceneImg.onerror = () => { sceneChoice = { id: DEFAULT_SCENE, name: SCENES[0].name }; markDirty(); };
    sceneImg.src = choice.url;
  }
  markDirty();
}
function stopScene() { if (visibleScreen !== "session") { sceneChoice = null; sceneImg = null; } }
