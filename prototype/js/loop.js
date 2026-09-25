"use strict";

/* ================= One animation loop ================= */
let dirty = true, lastFrame = 0;
// Battery/frame budget: if animated frames keep arriving late, render the orb at a lower
// resolution (1.35× → 1× → 0.75×). The look stays the same; the GPU does less work.
let orbQuality = 1.35, slowFrames = 0, frameSamples = 0;
function trackFrameCost(gap) {
  if (gap > 200) return;               // tab switch or pause, not a slow frame
  frameSamples++;
  if (gap > 48) slowFrames++;          // well under the 30 fps target
  if (frameSamples >= 90) {
    if (slowFrames > 30 && orbQuality > 0.75) { orbQuality = orbQuality > 1 ? 1 : 0.75; }
    frameSamples = 0; slowFrames = 0;
  }
}
const T0 = performance.now();
function markDirty() { dirty = true; }
function frame(now) {
  requestAnimationFrame(frame);
  if (document.hidden) return;
  const animate = !reduceMotion.matches;
  // ease the touch state and nebula level so every change is smooth
  let settling = false;
  let actChanged = false;
  ["home", "session"].forEach(k => { const s = act[k]; const d = s.target - s.v; if (Math.abs(d) > .002) { s.v += d * .08; settling = true; actChanged = true; } else if (s.v !== s.target) { s.v = s.target; actChanged = true; } });
  const nt = NEBULA_LEVEL[visibleScreen]; if (Math.abs(nt - nebLevel) > .002) { nebLevel += (nt - nebLevel) * .06; settling = true; } else nebLevel = nt;
  if (actChanged) {
    $("focusBtn").style.setProperty("--act", act.home.v.toFixed(3));
    $("timerBtn").style.setProperty("--act", act.session.v.toFixed(3));
  }
  if (!animate && !dirty && !settling) return;
  if (!dirty && now - lastFrame < 33) return;
  if (animate && lastFrame) trackFrameCost(now - lastFrame);
  lastFrame = now; const wasDirty = dirty; dirty = false;
  const t = animate ? (now - T0) / 1000 : 20;
  const onSession = visibleScreen === "session" || !$("session").hidden;
  if (onSession && sceneChoice && (!sceneChoice.url || wasDirty)) drawScene(t);
  if ((visibleScreen !== "session" && visibleScreen !== "settings" && visibleScreen !== "start") || !$("home").hidden || !$("done").hidden) drawNebula(t);
  if (!$("settings").hidden && setBg && (!setBg.url || wasDirty)) drawAtmosphere(t);
  if (!$("start").hidden) tunnel.draw(t);
  if (!$("home").hidden) orb("home").draw(t, act.home.v);
  if (!$("session").hidden) orb("session").draw(t, act.session.v);
  if (!$("done").hidden) orb("done").draw(t, 0);
}
