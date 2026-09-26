"use strict";

/* ================= Transitions ================= */
let visibleScreen = "start"; // every launch opens on the welcome screen
function setScreen(id) {
  const from = visibleScreen;
  if (from === id) return;
  const a = $(from), b = $(id);
  visibleScreen = id; markDirty();
  if (from === "home") setActive("home", false);
  if (from === "session") setActive("session", false);
  if (b.getAnimations) b.getAnimations().forEach(x => x.cancel()); // a quick back-and-forth never leaves it half-faded
  b.style.pointerEvents = "";
  b.hidden = false;
  if (!motionOK()) { a.hidden = true; return; }
  let outK, inK;
  if (id === "settings") {
    outK = [{ opacity: 1, transform: "none" }, { opacity: 0, transform: "translateX(-10%) scale(.97)" }];
    inK = [{ opacity: 0, transform: "translateX(14%)" }, { opacity: 1, transform: "none" }];
  } else if (from === "settings") {
    outK = [{ opacity: 1, transform: "none" }, { opacity: 0, transform: "translateX(14%)" }];
    inK = [{ opacity: 0, transform: "translateX(-10%) scale(.97)" }, { opacity: 1, transform: "none" }];
  } else {
    outK = [{ opacity: 1, transform: "none", filter: "blur(0px)" }, { opacity: 0, transform: "scale(1.06)", filter: "blur(10px)" }];
    inK = [{ opacity: 0, transform: "scale(.94)", filter: "blur(12px)" }, { opacity: 1, transform: "none", filter: "blur(0px)" }];
  }
  a.style.pointerEvents = "none";
  const out = a.animate(outK, { duration: 480, easing: EASE, fill: "forwards" });
  const into = b.animate(inK, { duration: 620, delay: 80, easing: EASE, fill: "backwards" });
  out.onfinish = () => {
    if (visibleScreen !== from) a.hidden = true;
    out.cancel(); a.style.pointerEvents = "";
  };
  return into; // the incoming page's animation (lets Focus's sound move in step with it)
}
const MODALS = ["permSheet", "barrier", "confirmEnd", "donateSheet"];
function openModal(id) {
  MODALS.forEach(m => { if (m !== id) hideModal(m); });
  const s = $(id), sheet = s.firstElementChild;
  if (!s.hidden && !s.dataset.closing) return;
  if (s.getAnimations) s.getAnimations({ subtree: true }).forEach(x => x.cancel());
  delete s.dataset.closing; s.hidden = false;
  if (motionOK()) {
    s.animate([{ opacity: 0 }, { opacity: 1 }], { duration: 320, easing: "ease-out" });
    sheet.animate([{ opacity: 0, transform: "translateY(40px) scale(.97)" }, { opacity: 1, transform: "none" }], { duration: 560, easing: EASE });
  }
  const first = s.querySelector("button:not([disabled])");
  if (first) setTimeout(() => first.focus({ preventScroll: true }), 40);
}
function hideModal(id) {
  const s = $(id);
  if (s.hidden || s.dataset.closing) return;
  if (!motionOK()) { s.hidden = true; return; }
  s.dataset.closing = "1";
  const sheet = s.firstElementChild;
  sheet.animate([{ opacity: 1, transform: "none" }, { opacity: 0, transform: "translateY(30px) scale(.97)" }], { duration: 320, easing: "cubic-bezier(.4,0,.7,.4)", fill: "forwards" });
  const f = s.animate([{ opacity: 1 }, { opacity: 0 }], { duration: 340, easing: "ease-in", fill: "forwards" });
  f.onfinish = () => {
    if (!s.dataset.closing) return;
    delete s.dataset.closing; s.hidden = true;
    s.getAnimations({ subtree: true }).forEach(x => x.cancel());
  };
}
function closeModals() { MODALS.forEach(hideModal); }
function isOpen(id) { return !$(id).hidden && !$(id).dataset.closing; }
function enterItem(node) {
  if (motionOK()) node.animate([{ opacity: 0, transform: "translateY(8px) scale(.98)" }, { opacity: 1, transform: "none" }], { duration: 520, easing: EASE });
}
