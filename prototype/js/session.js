"use strict";

/* ================= Session state machine ================= */
// IDLE | PERMISSION_REQUIRED | PERMISSION_BARRIER | ACTIVE_SESSION | CONFIRM_END | COMPLETED
let state = "IDLE";
let session = null; // { minutes, startedAt, endAt, finished }
let tickTimer = null;

// The interruption rules a session asks the phone to enforce. The Android adapter maps these onto
// Do Not Disturb: PRIORITY_CATEGORY_REPEAT_CALLERS, PRIORITY_CATEGORY_ALARMS (Alarm Safety), and the
// Trusted Contacts list; emergency alerts are never suppressed by Do Not Disturb.
function interruptionPolicy() {
  return {
    notifications: "blocked",
    calls: "blocked",
    emergencyAlerts: true,
    repeatCallers: { allowed: true, windowMinutes: REPEAT_CALL_WINDOW_MIN }, // 2nd call from same number rings
    alarms: settings.alarmSafety,
    trustedContacts: settings.trustedOn ? settings.contacts.map(c => c.number) : [],
  };
}
// Platform adapter. Inside the Android app, window.FFAndroid (MainActivity's bridge) turns on
// Do Not Disturb. A web page has no access to Do Not Disturb or incoming calls, so the browser
// adapter reports honestly that nothing is being blocked.
const native = window.FFAndroid || null;
const distraction = native ? {
  platform: "android",
  checkPermissionState: () => { try { return native.dndState() === "granted" ? "granted" : "not-asked"; } catch (e) { return "not-asked"; } },
  beginDistractionReduction: policy => { try { return native.begin(JSON.stringify(policy), session ? session.endAt : 0); } catch (e) { return false; } },
  endDistractionReduction: () => { try { native.end(); } catch (e) {} },
  restorePreviousSystemState: () => { try { native.end(); } catch (e) {} },
} : {
  platform: "browser-preview",
  checkPermissionState: () => (settings.previewAck ? "preview" : "not-asked"),
  beginDistractionReduction: policy => false,
  endDistractionReduction: () => {},
  restorePreviousSystemState: () => {},
};
if (native) $("app").classList.add("android");

function renderPermOptional() {
  const ul = $("permOptional"); ul.textContent = "";
  const n = settings.contacts.length;
  const row = (label, on, detail) => {
    const li = document.createElement("li");
    const b = document.createElement("b"); b.textContent = label + ": ";
    const tag = document.createElement("span"); tag.className = "tag" + (on ? "" : " off"); tag.textContent = on ? "ON" : "OFF";
    li.append(b, tag, document.createTextNode(" — " + detail));
    ul.appendChild(li);
  };
  if (native) row("Trusted Contacts", false, "not available in the Android app yet.");
  else row("Trusted Contacts", settings.trustedOn, n ? `${n} ${n === 1 ? "person" : "people"} can call and message you.` : "no one added yet.");
  row("Alarm Safety", settings.alarmSafety, "alarms ring during Focus.");
}
function showPermSheet() { renderPermOptional(); openModal("permSheet"); }

function requestStart() {
  if (state !== "IDLE") return;
  if (distraction.checkPermissionState() === "not-asked") { state = "PERMISSION_REQUIRED"; showPermSheet(); return; }
  startSession();
}
$("permAllow").onclick = () => {
  if (native) {
    // Android shows its own access page; nativeResume() picks up the answer when the app comes back.
    if (distraction.checkPermissionState() === "granted") { renderPerm(); closeModals(); if (state === "PERMISSION_REQUIRED") startSession(); else state = "IDLE"; return; }
    awaitingAccess = true;
    try { native.openDndAccess(); } catch (e) { awaitingAccess = false; toast("Couldn't open Android's settings."); }
    return;
  }
  settings.previewAck = true; saveSettings(); renderPerm(); closeModals();
  if (state === "PERMISSION_REQUIRED") startSession(); else state = "IDLE";
};
$("permDeny").onclick = () => {
  if (state === "PERMISSION_REQUIRED") { state = "PERMISSION_BARRIER"; openModal("barrier"); }
  else closeModals();
};
// Called by the Android app whenever it comes back to the foreground.
let awaitingAccess = false;
window.ffNativeResume = () => {
  renderPerm();
  if (!awaitingAccess) return;
  awaitingAccess = false;
  const ok = distraction.checkPermissionState() === "granted";
  if (state === "PERMISSION_REQUIRED") {
    if (ok) startSession(); else { state = "PERMISSION_BARRIER"; openModal("barrier"); }
  } else if (ok) closeModals();
};
$("barReview").onclick = () => { state = "PERMISSION_REQUIRED"; showPermSheet(); };
$("barBack").onclick = () => { state = "IDLE"; closeModals(); $("focusBtn").focus(); };

function startSession() {
  closeModals();
  const startedAt = Date.now();
  session = { minutes, startedAt, endAt: startedAt + minutes * 60000, finished: false,
    planned: minutes, pausedAt: 0, pausedTotal: 0, intention: currentIntention() };
  state = "ACTIVE_SESSION";

  const silenced = distraction.beginDistractionReduction(interruptionPolicy());
  const sl = $("statusLine");
  if (silenced) { sl.textContent = "Notifications silenced · Emergency calls unaffected"; sl.classList.remove("preview"); }
  else { sl.textContent = native ? "Do Not Disturb couldn't turn on · notifications not silenced" : "Browser preview · notifications not silenced"; sl.classList.add("preview"); }

  const sceneChoice = resolveScene();
  const soundChoice = resolveSound();
  $("sessionFoot").textContent = soundChoice ? soundChoice.name + " · " + sceneChoice.name : sceneChoice.name;

  startScene(sceneChoice);
  const pageIn = setScreen("session");
  say(`Focus started. ${minutes} minutes.`);
  startSessionSound(soundChoice, pageIn); // fades in with the page transition
  acquireWakeLock();
  lastAnnounced = null;
  onSessionStart();
  tick();
  clearInterval(tickTimer);
  tickTimer = setInterval(tick, 1000);
  $("timerBtn").focus({ preventScroll: true });
}

// While paused the clock stands still at the moment of pausing.
function remainingMs() { return session ? Math.max(0, session.endAt - (session.pausedAt || Date.now())) : 0; }
const minsLeft = ms => Math.max(1, Math.ceil(ms / 60000)); // rounds up; shows 1 until it ends
let lastAnnounced = null;
function tick() {
  if (!session || session.finished) return;
  const ms = remainingMs();
  if (ms <= 0) { finish(true); return; }
  const m = minsLeft(ms);
  $("minsNum").textContent = m;
  sessionDial.set(ms / 3600000, session.minutes); // same 60-minute face as Home
  $("timerBtn").setAttribute("aria-label", `${m} ${m === 1 ? "minute" : "minutes"} left. Tap to end early.`);
  if (m !== lastAnnounced) { $("timerLive").textContent = `${m} ${m === 1 ? "minute" : "minutes"} left`; lastAnnounced = m; }
  if (state === "CONFIRM_END") $("endLeft").textContent = `About ${m} min left.`;
  onSessionTick(ms);
}

$("timerBtn").onclick = () => {
  if (state !== "ACTIVE_SESSION") return;
  state = "CONFIRM_END";
  $("endLeft").textContent = `About ${minsLeft(remainingMs())} min left.`;
  openModal("confirmEnd");
};
$("keepBtn").onclick = () => { if (state === "CONFIRM_END") { state = "ACTIVE_SESSION"; closeModals(); $("timerBtn").focus(); } };
$("endBtn").onclick = () => { if (state === "CONFIRM_END") finish(false); };

function finish(natural) {
  if (!session || session.finished) return; // completion + cleanup run exactly once
  session.finished = true;
  clearInterval(tickTimer); tickTimer = null;
  distraction.endDistractionReduction();
  distraction.restorePreviousSystemState();
  releaseSound(0.9);
  releaseWakeLock();
  setActive("session", false);
  closeModals();
  state = "COMPLETED";
  onSessionFinish(natural);
  if (natural) playChime();
  showDone(natural);
}
function showDone(natural) {
  const [q, who] = QUOTES[Math.floor(Math.random() * QUOTES.length)];
  $("doneTitle").textContent = natural ? "Done" : "Session ended";
  $("quoteText").textContent = "“" + q + "”";
  $("quoteCite").textContent = "— " + who;
  setScreen("done");
  say(natural ? "Session complete." : "Session ended.");
  setTimeout(stopScene, 700); // keep the view on screen while it fades out
  $("doneBtn").focus({ preventScroll: true });
}
$("doneBtn").onclick = () => {
  stopBreak(false);
  session = null; state = "IDLE";
  resetDuration();
  setScreen("home"); $("focusBtn").focus({ preventScroll: true });
};

document.addEventListener("visibilitychange", () => {
  if (document.visibilityState === "visible") {
    tick(); // absolute end time: catches up after the app was in the background
    if (state === "ACTIVE_SESSION" || state === "CONFIRM_END") acquireWakeLock();
    markDirty();
  }
});

/* ================= Wake lock ================= */
let wakeLock = null;
async function acquireWakeLock() {
  if (native) { try { native.keepScreenOn(true); } catch (e) {} return; }
  try { if ("wakeLock" in navigator && !wakeLock) { wakeLock = await navigator.wakeLock.request("screen"); wakeLock.addEventListener("release", () => { wakeLock = null; }); } }
  catch (e) { wakeLock = null; }
}
function releaseWakeLock() {
  if (native) { try { native.keepScreenOn(false); } catch (e) {} return; }
  try { wakeLock && wakeLock.release(); } catch (e) {} wakeLock = null;
}
