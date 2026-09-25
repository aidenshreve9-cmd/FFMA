"use strict";

/* ================= Storage (local only) ================= */
const SKEY = "ff.settings.v1";
const defaults = () => ({ v: 1, audio: "white", scene: DEFAULT_SCENE, alarmSafety: true, trustedOn: false, contacts: [], previewAck: false });
function loadSettings() {
  let s = defaults();
  try {
    const raw = JSON.parse(localStorage.getItem(SKEY) || "null");
    if (raw && raw.v === 1) {
      if (typeof raw.audio === "string") s.audio = raw.audio;
      if (typeof raw.scene === "string") s.scene = raw.scene;
      if (typeof raw.alarmSafety === "boolean") s.alarmSafety = raw.alarmSafety;
      if (typeof raw.trustedOn === "boolean") s.trustedOn = raw.trustedOn;
      if (Array.isArray(raw.contacts)) s.contacts = raw.contacts
        .filter(c => c && typeof c.name === "string" && typeof c.number === "string" && digitsOf(c.number).length >= 3)
        .slice(0, 50)
        .map(c => ({ id: typeof c.id === "string" && c.id ? c.id : newId(), name: c.name.slice(0, 40), number: c.number, normalized: digitsOf(c.number) }));
      s.previewAck = raw.previewAck === true;
    }
  } catch (e) { /* corrupt or blocked storage: fall back to defaults */ }
  return s;
}
function saveSettings() {
  try { localStorage.setItem(SKEY, JSON.stringify(settings)); }
  catch (e) { toast("Couldn't save settings on this device."); }
}
const settings = loadSettings();

/* ================= Local search wiring ================= */
const searcher = (() => {
  try {
    if (!window.FFSearch) return null;
    const c = new FFSearch.SearchController();
    c.define("sounds", { title: "title" });
    c.define("scenes", { title: "title" });
    c.define("trustedContacts", { name: "name", phone: "phone" });
    c.load("sounds", SOUNDS.map(x => ({ id: x.id, type: "sound", title: x.name, searchableFields: { title: x.name } }))
      .concat([{ id: "random", type: "sound", title: "Random", searchableFields: { title: "Random" } }]));
    c.load("scenes", SCENES.map(x => ({ id: x.id, type: "scene", title: x.name, searchableFields: { title: x.name } }))
      .concat([{ id: "random", type: "scene", title: "Random", searchableFields: { title: "Random" } }]));
    c.load("trustedContacts", settings.contacts.map(contactRecord));
    return c;
  } catch (e) { return null; } // search is optional: Focus works without it
})();
function contactRecord(c) {
  return { id: c.id, type: "trusted-contact", title: c.name, subtitle: c.number, searchableFields: { name: c.name, phone: c.number } };
}
function assetRecord(a) {
  return { id: a.id, type: a.type === "img" ? "picture" : "user-sound", title: a.name, searchableFields: { title: a.name } };
}
function indexAdd(collection, record) { try { searcher && searcher.add(collection, record); } catch (e) { /* never block */ } }
function indexRemove(collection, id) { try { searcher && searcher.remove(collection, id); } catch (e) { /* never block */ } }

// User pictures and sounds live in IndexedDB on this device only.
const assets = []; // {id, type:'img'|'snd', name, blob, url}
let db = null;
const justAdded = new Set();
function openDB() {
  return new Promise(res => {
    try {
      const r = indexedDB.open("focus-friend", 1);
      r.onupgradeneeded = () => r.result.createObjectStore("assets", { keyPath: "id" });
      r.onsuccess = () => res(r.result);
      r.onerror = () => res(null);
    } catch (e) { res(null); }
  });
}
function dbTx(mode, fn) {
  return new Promise((res, rej) => {
    if (!db) return rej(new Error("no-db"));
    try {
      const tx = db.transaction("assets", mode);
      const out = fn(tx.objectStore("assets"));
      tx.oncomplete = () => res(out && out.result);
      tx.onerror = () => rej(tx.error);
      tx.onabort = () => rej(tx.error);
    } catch (e) { rej(e); }
  });
}
async function loadAssets() {
  db = await openDB();
  if (!db) { validateSelections(); renderSettings(); return; }
  try {
    const all = await dbTx("readonly", st => st.getAll());
    (all || []).forEach(a => {
      // Skip missing or corrupted media instead of failing the whole list.
      if (!a || !a.blob || !a.id || (a.type !== "img" && a.type !== "snd")) return;
      const item = { ...a, name: typeof a.name === "string" && a.name ? a.name : "Untitled", url: URL.createObjectURL(a.blob) };
      assets.push(item);
      indexAdd(item.type === "img" ? "scenes" : "sounds", assetRecord(item));
    });
  } catch (e) { /* ignore unreadable store */ }
  validateSelections();
  renderSettings();
}
function validateSelections() {
  const has = id => assets.some(a => a.id === id);
  const soundIds = SOUNDS.map(s => s.id).concat("random", "none");
  if (settings.audio.startsWith("snd:") ? !has(settings.audio) : !soundIds.includes(settings.audio)) settings.audio = "white";
  const sceneIds = SCENES.map(s => s.id).concat("random");
  if (settings.scene.startsWith("img:") ? !has(settings.scene) : !sceneIds.includes(settings.scene)) settings.scene = DEFAULT_SCENE;
}
const shortName = n => { n = n.replace(/\.[a-z0-9]{2,5}$/i, "").trim() || "Untitled"; return n.length > 26 ? n.slice(0, 25) + "…" : n; };

async function addAsset(type, blob, name) {
  const id = (type === "img" ? "img:" : "snd:") + Date.now().toString(36) + Math.random().toString(36).slice(2, 6);
  const rec = { id, type, name: shortName(name), blob, v: 1 };
  try { await dbTx("readwrite", st => st.put(rec)); }
  catch (e) {
    toast(e && e.name === "QuotaExceededError" ? "Your device is full. Free up space and try again." : "Couldn't save that file on this device.");
    return null;
  }
  const a = { ...rec, url: URL.createObjectURL(blob) };
  assets.push(a); justAdded.add(id);
  indexAdd(type === "img" ? "scenes" : "sounds", assetRecord(a));
  return a;
}
async function removeAsset(id, node) {
  try { await dbTx("readwrite", st => st.delete(id)); } catch (e) { toast("Couldn't remove that file."); return; }
  const i = assets.findIndex(a => a.id === id);
  if (player.voice && player.voice.choice.id === id) releaseSound(0.03); // its preview can't outlive the file
  if (i >= 0) { URL.revokeObjectURL(assets[i].url); assets.splice(i, 1); }
  indexRemove(id.startsWith("img:") ? "scenes" : "sounds", id);
  validateSelections(); saveSettings();
  await slideOut(node);
  renderSettings();
}
// Removed items slide out (immediate with reduced motion).
function slideOut(node) {
  return new Promise(res => {
    if (!node || !motionOK()) return res();
    const a = node.animate([{ opacity: 1, transform: "none" }, { opacity: 0, transform: "translateX(18px)" }], { duration: 300, easing: EASE, fill: "forwards" });
    a.onfinish = res; a.oncancel = res;
  });
}
