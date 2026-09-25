"use strict";

/* ================= Settings UI ================= */
function el(tag, props = {}, kids = []) {
  const n = document.createElement(tag);
  Object.entries(props).forEach(([k, v]) => { if (k === "text") n.textContent = v; else if (k.startsWith("on")) n[k] = v; else n.setAttribute(k, v); });
  kids.forEach(k => n.appendChild(k)); return n;
}
function renderSound() {
  const list = $("soundList"); list.textContent = "";
  const opts = [...SOUNDS.map(s => [s.id, s.name, s.kind]), ["random", "Random", "EACH SESSION"],
    ...assets.filter(a => a.type === "snd").map(a => [a.id, a.name, "YOURS"])];
  opts.forEach(([id, name, kind]) => {
    const choice = el("button", { class: "choice", role: "radio", "aria-checked": String(settings.audio === id),
      onclick: () => tapSound(id) },
      [el("span", { class: "radio", "aria-hidden": "true" }), el("span", { class: "nm", text: name }), el("span", { class: "kind", text: kind })]);
    choice.dataset.id = id;
    let row = choice;
    if (id.startsWith("snd:")) row = el("div", { class: "row-wrap" }, [choice, el("button", { class: "rm", "aria-label": "Remove " + name, text: "×", onclick: () => removeAsset(id, row) })]);
    row.dataset.sid = id;
    list.appendChild(row);
    if (justAdded.delete(id)) enterItem(row);
  });
  soundFilter.sync(opts.length);
}
// Update selection in place so the radio ember animates instead of the list re-rendering.
// Tapping a sound selects it and plays a 5-second preview; tapping the chosen sound again
// fades it out and deselects it (Focus is then silent). Search's Enter always selects.
function tapSound(id, fromSearch) {
  const deselect = settings.audio === id && !fromSearch;
  settings.audio = deselect ? "none" : id; saveSettings();
  $("soundList").querySelectorAll(".choice").forEach(b => b.setAttribute("aria-checked", String(b.dataset.id === settings.audio)));
  if (deselect) { fadeOutPreview(); return; }
  const c = soundChoiceFor(id); if (c) previewSound(c);
}
function selectScene(id) {
  settings.scene = id; saveSettings();
  $("sceneList").querySelectorAll(".scene-opt").forEach(b => b.setAttribute("aria-checked", String(b.dataset.id === id)));
  showAtmosphere(id, true);
}
// Settings' background is the chosen atmosphere, and changes (crossfading) as you choose.
let setBg = null, setBgImg = null, setBgFor = null;
function showAtmosphere(id, animate) {
  const cur = $("setScene"), old = $("setSceneOld");
  if (animate && setBg && motionOK() && cur.width > 1) {
    const { c, w, h } = fit(old, 1);
    c.drawImage(cur, 0, 0, w, h);
    old.getAnimations().forEach(x => x.cancel());
    old.animate([{ opacity: 1 }, { opacity: 0 }], { duration: 700, easing: EASE });
  }
  setBgFor = id;
  setBg = sceneChoiceFor(id) || { id: DEFAULT_SCENE, name: SCENES[0].name };
  setBgImg = null;
  if (setBg.url) { setBgImg = new Image(); setBgImg.onload = markDirty; setBgImg.src = setBg.url; }
  markDirty();
}
function drawAtmosphere(t) {
  const { c, w, h } = fit($("setScene"), 1);
  if (setBg.url) { if (setBgImg && setBgImg.complete && setBgImg.naturalWidth) paintImage(c, w, h, setBgImg); else { c.fillStyle = "#000"; c.fillRect(0, 0, w, h); } return; }
  PAINT[setBg.id](c, w, h, t);
}
function renderScenes() {
  if (setBgFor !== settings.scene) showAtmosphere(settings.scene, !$("settings").hidden);
  const list = $("sceneList"); list.textContent = "";
  const tile = (id, name, media) => {
    const b = el("button", { class: "scene-opt", role: "radio", "aria-checked": String(settings.scene === id), "aria-label": name,
      onclick: () => selectScene(id) }, [media, el("span", { class: "cap", text: name, "aria-hidden": "true" })]);
    b.dataset.id = id; b.dataset.sid = id; return b;
  };
  SCENES.forEach(s => {
    const cv = el("canvas", { "aria-hidden": "true" });
    list.appendChild(tile(s.id, s.name, cv));
    requestAnimationFrame(() => { const { c, w, h } = fit(cv, 1); PAINT[s.id](c, w, h, 6); });
  });
  const rnd = el("span", { class: "random-tile", "aria-hidden": "true" });
  rnd.innerHTML = '<svg viewBox="0 0 24 24"><path d="M16 3h5v5M4 20L21 3M21 16v5h-5M15 15l6 6M4 4l5 5"/></svg>';
  list.appendChild(tile("random", "Random", rnd));
  const pics = assets.filter(a => a.type === "img");
  pics.forEach(a => {
    const wrap = tile(a.id, a.name, el("img", { src: a.url, alt: "" }));
    delete wrap.dataset.sid;
    let holder;
    const x = el("button", { class: "x", "aria-label": "Remove " + a.name, text: "×", onclick: e => { e.stopPropagation(); removeAsset(a.id, holder); } });
    holder = el("div", { style: "position:relative" }, [wrap, x]);
    holder.dataset.sid = a.id;
    list.appendChild(holder);
    if (justAdded.delete(a.id)) enterItem(holder);
  });
  sceneFilter.sync(SCENES.length + 1 + pics.length);
}
function renderContacts() {
  const list = $("contactList"); list.textContent = "";
  if (!settings.contacts.length) {
    list.appendChild(el("div", { class: "search-empty", role: "listitem" }, [
      el("b", { text: "No trusted contacts yet" }),
      el("p", { text: "Add someone you want to be able to reach you during Focus." }),
    ]));
    contactFilter.sync(0);
    return;
  }
  settings.contacts.forEach(c => {
    let row;
    row = el("div", { class: "contact", role: "listitem", tabindex: "-1", "aria-label": c.name + ", " + c.number }, [
      el("div", { class: "who", "aria-hidden": "true" }, [el("b", { text: c.name }), el("span", { text: c.number })]),
      el("button", { class: "rm", "aria-label": "Remove " + c.name, text: "×", onclick: () => removeContact(c.id, row) }),
    ]);
    row.dataset.sid = c.id;
    list.appendChild(row);
    if (justAdded.delete(c.id)) enterItem(row);
  });
  contactFilter.sync(settings.contacts.length);
}
function removeContact(id, row) {
  const done = () => {
    const gone = settings.contacts.find(x => x.id === id);
    settings.contacts = settings.contacts.filter(x => x.id !== id);
    indexRemove("trustedContacts", id);
    saveSettings(); renderContacts(); renderTrust();
    if (gone) toast("Removed " + gone.name);
    const next = $("contactQuery").hidden || $("contactSearch").hidden ? $("cName") : $("contactQuery");
    next.focus({ preventScroll: true });
  };
  slideOut(row).then(done);
}
// Returns an error message, or null when the contact was added.
function addContact(name, number) {
  number = String(number || "").trim(); name = String(name || "").trim() || number;
  if (!/^[+()\d\s.\-]{3,24}$/.test(number) || digitsOf(number).length < 3) return "Add a name and a phone number.";
  const normalized = digitsOf(number);
  if (settings.contacts.some(c => samePhone(c.normalized || digitsOf(c.number), normalized))) return "That number is already a Trusted Contact.";
  if (settings.contacts.length >= 50) return "You can add up to 50 Trusted Contacts.";
  const contact = { id: newId(), name: name.slice(0, 40), number, normalized };
  settings.contacts.push(contact);
  indexAdd("trustedContacts", contactRecord(contact));
  justAdded.add(contact.id);
  return null;
}
function renderToggle(sw, label, on) {
  sw.setAttribute("aria-checked", String(on));
  label.textContent = on ? "ON" : "OFF"; label.classList.toggle("off", !on);
}
function renderTrust() {
  renderToggle($("trustSwitch"), $("trustState"), settings.trustedOn);
  $("contactCard").classList.toggle("dim", !settings.trustedOn);
  const n = settings.contacts.length;
  $("trustNote").textContent = native ? "Stored only on this device."
    : settings.trustedOn
    ? (n ? `On: ${n} ${n === 1 ? "person" : "people"} can reach you during Focus in the Android app. Stored only on this device.`
         : "On, but no one is added yet. Stored only on this device.")
    : "The Trusted Contacts list is off. During Focus, only emergency alerts and repeat callers get through. Stored only on this device.";
}
function renderAlarm() { renderToggle($("alarmSwitch"), $("alarmState"), settings.alarmSafety); }
function renderPerm() {
  const p = $("permPill");
  if (native) { const ok = distraction.checkPermissionState() === "granted"; p.textContent = ok ? "Allowed" : "Not set up"; p.classList.toggle("no", !ok); $("permAllow").textContent = ok ? "Continue" : "Allow in Settings"; return; }
  p.textContent = settings.previewAck ? "Preview only" : "Not set up";
}
function renderSettings() { renderSound(); renderScenes(); renderContacts(); renderTrust(); renderAlarm(); renderPerm(); }

/* ---------- Search-as-you-type filter for one Settings list ---------- */
// Filters the existing list in place (ranked with CSS `order`), so selection, removal and
// radio semantics keep working. Never shown on Home. Only appears when a list is long enough.
function createFilter(cfg) {
  const box = $(cfg.box), input = $(cfg.input), list = $(cfg.list), empty = $(cfg.empty);
  let announceT = 0;
  const rows = () => Array.from(list.children).filter(n => n.dataset && n.dataset.sid);
  const visible = () => rows().filter(r => !r.hidden && r.dataset.want !== "hidden")
    .sort((a, b) => (+a.style.order || 0) - (+b.style.order || 0));
  const active = () => !box.hidden && !!input.value.trim();

  function show(r) {
    const wasLeaving = r.dataset.want === "hidden";
    r.dataset.want = "shown";
    if (wasLeaving && r.getAnimations) r.getAnimations().forEach(x => x.cancel());
    if (!r.hidden) return;
    r.hidden = false;
    enterItem(r);
  }
  function hide(r) {
    if (r.hidden || r.dataset.want === "hidden") return;
    r.dataset.want = "hidden";
    if (!motionOK()) { r.hidden = true; return; }
    const a = r.animate([{ opacity: 1, transform: "none" }, { opacity: 0, transform: "translateX(14px)" }], { duration: 180, easing: EASE });
    a.onfinish = () => { if (r.dataset.want === "hidden") r.hidden = true; };
  }
  function renderEmpty(q) {
    empty.textContent = "";
    let s = null;
    try { s = searcher.suggest({ collection: cfg.collection, query: q }); } catch (e) { s = null; }
    if (s) {
      const btn = el("button", { class: "dym", type: "button", text: s.text, onclick: () => { input.value = s.text; apply(); input.focus(); } });
      empty.append(el("p", { text: `No ${cfg.plural} found for “${q.trim()}”.` }), el("p", {}, [document.createTextNode("Did you mean "), btn, document.createTextNode("?")]));
    } else {
      empty.append(el("b", { text: `No matching ${cfg.plural}` }), el("p", { text: "Try a different name." }));
    }
  }
  function announce(msg) {
    // Wait for a pause in typing so the screen reader isn't interrupted on every keystroke.
    clearTimeout(announceT);
    announceT = setTimeout(() => say(msg), 900);
  }
  function apply() {
    const all = rows();
    if (!searcher || !active()) {
      all.forEach(r => { r.style.order = ""; show(r); });
      empty.hidden = true; clearTimeout(announceT);
      return;
    }
    let res = [];
    try { res = searcher.search({ collection: cfg.collection, query: input.value, limit: 200 }); } catch (e) { res = []; }
    const rank = new Map(res.map((r, i) => [r.item.id, i]));
    let shown = 0;
    all.forEach(r => {
      const i = rank.get(r.dataset.sid);
      if (i == null) hide(r); else { r.style.order = String(i); show(r); shown++; }
    });
    empty.hidden = shown > 0;
    if (!shown) renderEmpty(input.value);
    announce(shown ? `${shown} matching ${shown === 1 ? cfg.singular : cfg.plural}.` : `No matching ${cfg.plural}.`);
  }
  function focusRow(r) { (r.matches(cfg.focusSel) ? r : r.querySelector(cfg.focusSel) || r).focus({ preventScroll: false }); }

  input.addEventListener("input", apply);
  input.addEventListener("keydown", e => {
    if (e.key === "ArrowDown") { const f = visible()[0]; if (f) { e.preventDefault(); focusRow(f); } }
    else if (e.key === "Enter") { e.preventDefault(); const f = visible()[0]; if (f && active()) cfg.select(f); }
    else if (e.key === "Escape" && input.value) { e.preventDefault(); e.stopPropagation(); input.value = ""; apply(); say(`Search cleared.`); }
  });
  list.addEventListener("keydown", e => {
    if (box.hidden) return;
    const vis = visible(), cur = vis.findIndex(r => r.contains(document.activeElement));
    if (cur === -1) return;
    if (e.key === "ArrowDown") { e.preventDefault(); focusRow(vis[Math.min(vis.length - 1, cur + 1)]); }
    else if (e.key === "ArrowUp") { e.preventDefault(); if (cur === 0) input.focus(); else focusRow(vis[cur - 1]); }
    else if (e.key === "Enter" && cfg.enterOnRow && document.activeElement === vis[cur]) { e.preventDefault(); cfg.select(vis[cur]); }
    else if (e.key === "Escape" && active()) { e.preventDefault(); e.stopPropagation(); input.focus(); }
  });
  return {
    // Show the search box only when it helps; hiding it clears the filter.
    sync(count) {
      const need = !!searcher && count >= cfg.threshold;
      box.hidden = !need;
      if (!need && input.value) input.value = "";
      apply();
    },
    apply,
  };
}
// Browsing is easier for short lists: sounds and atmospheres get search only past 12 options.
// Trusted Contacts get search as soon as one exists (as specified).
const soundFilter = createFilter({ collection: "sounds", box: "soundSearch", input: "soundQuery", list: "soundList", empty: "soundEmpty",
  singular: "sound", plural: "sounds", threshold: 13, focusSel: ".choice", select: r => { const b = r.matches(".choice") ? r : r.querySelector(".choice"); tapSound(b.dataset.id, true); b.focus(); } });
const sceneFilter = createFilter({ collection: "scenes", box: "sceneSearch", input: "sceneQuery", list: "sceneList", empty: "sceneEmpty",
  singular: "atmosphere", plural: "atmospheres", threshold: 13, focusSel: ".scene-opt", select: r => { const b = r.matches(".scene-opt") ? r : r.querySelector(".scene-opt"); b.click(); b.focus(); } });
// Selecting a contact result moves focus to that contact's actions (its Remove button).
const contactFilter = createFilter({ collection: "trustedContacts", box: "contactSearch", input: "contactQuery", list: "contactList", empty: "contactEmpty",
  singular: "contact", plural: "contacts", threshold: 1, focusSel: ".contact", enterOnRow: true, select: r => { const b = r.querySelector(".rm"); (b || r).focus(); } });

// Polite screen-reader announcements for important state changes.
let sayT = 0;
function say(msg) {
  const n = $("liveMsg"); clearTimeout(sayT);
  n.textContent = "";
  sayT = setTimeout(() => { n.textContent = msg; }, 40);
}

$("openSettings").onclick = () => { if (state !== "IDLE") return; renderSettings(); showAtmosphere(settings.scene, false); setScreen("settings"); $("closeSettings").focus({ preventScroll: true }); };
// Leaving Settings stops any preview at once (a 30 ms fade only to avoid a click).
$("closeSettings").onclick = () => { releaseSound(0.03); setScreen("home"); $("openSettings").focus({ preventScroll: true }); };
$("alarmSwitch").onclick = () => { settings.alarmSafety = !settings.alarmSafety; saveSettings(); renderAlarm(); say("Alarm Safety " + (settings.alarmSafety ? "on." : "off.")); };
$("trustSwitch").onclick = () => { settings.trustedOn = !settings.trustedOn; saveSettings(); renderTrust(); say("Trusted Contacts " + (settings.trustedOn ? "on." : "off.")); };
$("reviewPerm").onclick = showPermSheet;
$("donateBtn").onclick = () => {
  if (DONATION_LINK) return; // real link opens in the phone's browser once provided
  openModal("donateSheet");
};
$("donClose").onclick = () => { closeModals(); $("donateBtn").focus(); };

$("contactForm").addEventListener("submit", e => {
  e.preventDefault();
  const name = $("cName").value.trim();
  if (!name) { toast("Add a name and a phone number."); return; }
  const err = addContact(name, $("cNum").value);
  if (err) { toast(err); return; }
  saveSettings(); renderContacts(); renderTrust();
  toast("Added " + name.slice(0, 40));
  $("cName").value = ""; $("cNum").value = ""; $("cName").focus();
});
// Contact Picker API: returns only the contacts the person chooses (Android Chrome, top-level pages).
if ("contacts" in navigator && navigator.contacts && typeof navigator.contacts.select === "function") {
  $("pickContact").hidden = false;
  $("pickContact").onclick = async () => {
    try {
      const picked = await navigator.contacts.select(["name", "tel"], { multiple: true });
      let added = 0, skipped = 0;
      (picked || []).forEach(p => { const tel = (p.tel || [])[0]; if (tel && !addContact((p.name || [])[0], tel)) added++; else skipped++; });
      saveSettings(); renderContacts(); renderTrust();
      toast(added ? `Added ${added} Trusted ${added === 1 ? "Contact" : "Contacts"}.` : skipped ? "Those contacts were already added or had no number." : "No contacts chosen.");
    } catch (e) { toast("The contact picker isn't available here. Type the number instead."); }
  };
}

$("addSound").onclick = () => $("soundFile").click();
$("addPic").onclick = () => $("picFile").click();
$("soundFile").addEventListener("change", async e => {
  const f = e.target.files[0]; e.target.value = ""; if (!f) return;
  if (!db) { toast("This browser can't store your own files."); return; }
  if (!/^audio\//.test(f.type)) { toast("That file isn't a sound."); return; }
  if (f.size > 20 * 1024 * 1024) { toast("That sound is too large. Pick one under 20 MB."); return; }
  const ok = await new Promise(res => { const a = new Audio(); const u = URL.createObjectURL(f); a.preload = "metadata";
    a.onloadedmetadata = () => { URL.revokeObjectURL(u); res(true); }; a.onerror = () => { URL.revokeObjectURL(u); res(false); }; a.src = u; });
  if (!ok) { toast("That sound can't be played on this device."); return; }
  const a = await addAsset("snd", f, f.name);
  if (a) { settings.audio = a.id; saveSettings(); renderSound(); toast("Added " + a.name); }
});
$("picFile").addEventListener("change", async e => {
  const f = e.target.files[0]; e.target.value = ""; if (!f) return;
  if (!db) { toast("This browser can't store your own files."); return; }
  if (!/^image\//.test(f.type)) { toast("That file isn't a picture."); return; }
  if (f.size > 30 * 1024 * 1024) { toast("That picture is too large."); return; }
  const blob = await new Promise(res => {
    const u = URL.createObjectURL(f), img = new Image();
    img.onload = () => {
      const s = Math.min(1, 1100 / Math.max(img.naturalWidth, img.naturalHeight));
      const cv = document.createElement("canvas"); cv.width = Math.round(img.naturalWidth * s); cv.height = Math.round(img.naturalHeight * s);
      cv.getContext("2d").drawImage(img, 0, 0, cv.width, cv.height); URL.revokeObjectURL(u);
      cv.toBlob(b => res(b), "image/jpeg", 0.82);
    };
    img.onerror = () => { URL.revokeObjectURL(u); res(null); };
    img.src = u;
  });
  if (!blob) { toast("That picture can't be opened."); return; }
  const a = await addAsset("img", blob, f.name);
  if (a) { settings.scene = a.id; saveSettings(); renderScenes(); toast("Added " + a.name); }
});
