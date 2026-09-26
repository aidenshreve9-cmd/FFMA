"use strict";

/* ================= Misc ================= */
let toastT = 0, toastOut = null;
function toast(msg) {
  const t = $("toast"); t.textContent = msg;
  if (toastOut) { toastOut.onfinish = null; toastOut.cancel(); toastOut = null; }
  const wasHidden = t.hidden; t.hidden = false;
  if (wasHidden && motionOK()) t.animate([{ opacity: 0, transform: "translate(-50%,12px)" }, { opacity: 1, transform: "translate(-50%,0)" }], { duration: 380, easing: EASE });
  clearTimeout(toastT);
  toastT = setTimeout(() => {
    if (!motionOK()) { t.hidden = true; return; }
    toastOut = t.animate([{ opacity: 1 }, { opacity: 0, transform: "translate(-50%,8px)" }], { duration: 320, easing: "ease-in", fill: "forwards" });
    toastOut.onfinish = () => { t.hidden = true; toastOut.cancel(); toastOut = null; };
  }, 2800);
}
document.addEventListener("keydown", e => {
  if (e.key !== "Escape") return;
  if (isOpen("confirmEnd")) $("keepBtn").click();
  else if (isOpen("barrier")) $("barBack").click();
  else if (isOpen("permSheet")) $("permDeny").click();
  else if (isOpen("donateSheet")) $("donClose").click();
  else if (visibleScreen === "settings") $("closeSettings").click();
});
// Android back button: returns true when the page handled it (closed a sheet or went back a screen).
window.ffBack = () => {
  if (isOpen("confirmEnd")) { $("keepBtn").click(); return true; }
  if (isOpen("barrier")) { $("barBack").click(); return true; }
  if (isOpen("permSheet")) { $("permDeny").click(); return true; }
  if (isOpen("donateSheet")) { $("donClose").click(); return true; }
  if (visibleScreen === "settings") { $("closeSettings").click(); return true; }
  if (visibleScreen === "done") { $("doneBtn").click(); return true; }
  return false; // Home, or a running session: the app goes to the background and Focus keeps going
};
window.addEventListener("pagehide", () => { if (session && !session.finished) { distraction.restorePreviousSystemState(); } });

/* ---------- Welcome screen ---------- */
// Two calm lines fade in and out, then the title and Start rise in. A tap skips ahead.
const introAnims = [];
let introDone = !motionOK();
function skipIntro() { introDone = true; introAnims.forEach(x => x.finish()); }
if (!introDone) {
  const play = (node, k, delay, duration) => { const x = node.animate(k, { delay, duration, easing: "ease-in-out", fill: "both" }); introAnims.push(x); return x; };
  const lineK = [
    { opacity: 0, transform: "translateY(12px)", filter: "blur(6px)" },
    { opacity: 1, transform: "none", filter: "blur(0px)", offset: .3 },
    { opacity: 1, transform: "none", filter: "blur(0px)", offset: .7 },
    { opacity: 0, transform: "translateY(-10px)", filter: "blur(6px)" },
  ];
  const riseK = [{ opacity: 0, transform: "translateY(14px)", filter: "blur(6px)" }, { opacity: 1, transform: "none", filter: "blur(0px)" }];
  play($("intro1"), lineK, 500, 3000);
  play($("intro2"), lineK, 3400, 3200);
  Array.from($("startTitle").children).forEach((n, i) => play(n, riseK, 6500 + i * 260, 1400));
  play($("startFoot"), riseK, 7400, 1300).onfinish = () => { introDone = true; };
}
$("start").addEventListener("click", () => { if (!introDone) skipIntro(); });
$("startBtn").onclick = () => {
  if (visibleScreen !== "start" || !introDone) return; // during the intro, the tap only skips ahead
  setScreen("home");
  $("focusBtn").focus({ preventScroll: true });
  setTimeout(() => { if (visibleScreen !== "start") tunnel.release(); }, 1000);
};
$("startBtn").focus({ preventScroll: true });

resetDuration();
renderSettings();
loadAssets();

// Start drawing only once every script has loaded.
requestAnimationFrame(frame);
window.addEventListener("resize", markDirty);
if (reduceMotion.addEventListener) reduceMotion.addEventListener("change", markDirty);
