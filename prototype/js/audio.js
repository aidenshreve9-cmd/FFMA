"use strict";

/* ================= Audio: eight noise colours ================= */
let ac = null;
const bufCache = {};
function actx() {
  if (!ac) { const C = window.AudioContext || window.webkitAudioContext; if (!C) return null; ac = new C(); }
  if (ac.state === "suspended") ac.resume().catch(() => {});
  return ac;
}
// 4-second loop with a crossfaded seam, normalised to the same RMS before filtering.
function noiseBuffer(ctx, kind) {
  if (bufCache[kind]) return bufCache[kind];
  const sr = ctx.sampleRate, len = sr * 4, fade = Math.floor(sr * .3), d = new Float32Array(len + fade);
  let b0 = 0, b1 = 0, b2 = 0, b3 = 0, b4 = 0, b5 = 0, b6 = 0, brown = 0, prevP = 0, prevW = 0;
  for (let i = 0; i < d.length; i++) {
    const w = Math.random() * 2 - 1;
    // Paul Kellet's pink filter
    b0 = .99886 * b0 + w * .0555179; b1 = .99332 * b1 + w * .0750759; b2 = .96900 * b2 + w * .1538520;
    b3 = .86650 * b3 + w * .3104856; b4 = .55000 * b4 + w * .5329522; b5 = -.7616 * b5 - w * .0168980;
    const p = b0 + b1 + b2 + b3 + b4 + b5 + b6 + w * .5362; b6 = w * .115926;
    brown = (brown + .02 * w) / 1.02;
    let v;
    switch (kind) {
      case "pink": v = p; break;
      case "brown": case "black": v = brown; break;
      case "blue": v = p - prevP; break;          // +3 dB/octave
      case "violet": v = w - prevW; break;        // +6 dB/octave
      default: v = w;                             // white, green, grey (shaped by filters)
    }
    prevP = p; prevW = w; d[i] = v;
  }
  for (let i = 0; i < fade; i++) { const k = i / fade; d[i] = d[i] * Math.sqrt(k) + d[len + i] * Math.sqrt(1 - k); }
  let sum = 0; for (let i = 0; i < len; i++) sum += d[i] * d[i];
  const scale = .14 / Math.max(1e-6, Math.sqrt(sum / len));
  const buf = ctx.createBuffer(1, len, sr), out = buf.getChannelData(0);
  for (let i = 0; i < len; i++) out[i] = Math.max(-1, Math.min(1, d[i] * scale));
  return (bufCache[kind] = buf);
}
function colourChain(ctx, kind) {
  const f = (type, freq, q, gain) => { const n = ctx.createBiquadFilter(); n.type = type; n.frequency.value = freq; if (q != null) n.Q.value = q; if (gain != null) n.gain.value = gain; return n; };
  switch (kind) {
    case "brown":  return [f("lowpass", 1400, .7)];
    case "black":  return [f("lowpass", 90, .7), f("lowpass", 90, .7)];
    case "green":  return [f("bandpass", 500, .55)];
    case "grey":   return [f("lowshelf", 220, null, 7), f("peaking", 3200, 1, -10), f("highshelf", 9000, null, 3)];
    case "blue":   return [f("lowpass", 14000, .7)];
    case "violet": return [f("lowpass", 16000, .7)];
    default:       return [];
  }
}
function soundChoiceFor(id) {
  if (id === "random") {
    const pool = [...SOUNDS.map(s => s.id), ...assets.filter(a => a.type === "snd").map(a => a.id)];
    id = pool[Math.floor(Math.random() * pool.length)];
  }
  const b = SOUNDS.find(s => s.id === id); if (b) return { id, name: b.name, gain: b.gain };
  const a = assets.find(x => x.id === id);
  return a ? { id, name: a.name, url: a.url } : null;
}
// null = no sound chosen (Focus is silent).
function resolveSound() {
  if (settings.audio === "none") return null;
  return soundChoiceFor(settings.audio) || { id: "white", name: "White Noise", gain: SOUNDS[0].gain };
}

// One sound at a time: source → colour filters → loudness → fade → speakers.
// Fades are scheduled on the audio clock; the current level is tracked so any fade can
// start smoothly from wherever the previous one had got to.
const PREVIEW_MS = 5000, PREVIEW_FADE = 0.9, SWITCH_FADE = 0.45;
const easeInOut = k => 0.5 - 0.5 * Math.cos(Math.PI * k);
function makeVoice(choice) {
  const ctx = actx(); if (!ctx) { toast("Sound isn't available in this browser."); return null; }
  try {
    const fade = ctx.createGain(); fade.gain.value = 0; fade.connect(ctx.destination);
    const v = { choice, fade, lvl: { from: 0, to: 0, t0: 0, t1: 0, ease: easeInOut } };
    if (choice.url) {
      const el = new Audio(choice.url); el.loop = true;
      const src = ctx.createMediaElementSource(el); src.connect(fade);
      el.play().catch(() => toast("Couldn't play that sound. Try another in Settings."));
      v.stop = () => { el.pause(); el.removeAttribute("src"); src.disconnect(); fade.disconnect(); };
    } else {
      const src = ctx.createBufferSource(); src.buffer = noiseBuffer(ctx, choice.id); src.loop = true;
      const loud = ctx.createGain(); loud.gain.value = choice.gain;
      let node = src;
      colourChain(ctx, choice.id).forEach(fl => { node.connect(fl); node = fl; });
      node.connect(loud).connect(fade);
      src.start();
      v.stop = () => { try { src.stop(); } catch (e) {} src.disconnect(); fade.disconnect(); };
    }
    return v;
  } catch (e) { toast("Couldn't start the sound."); return null; }
}
function levelOf(v) {
  const L = v.lvl, t = ac.currentTime;
  if (t >= L.t1) return L.to; if (t <= L.t0) return L.from;
  return L.from + (L.to - L.from) * L.ease((t - L.t0) / (L.t1 - L.t0));
}
function fadeVoice(v, to, secs, delay = 0, ease = easeInOut) {
  const p = v.fade.gain, now = ac.currentTime, from = levelOf(v), t0 = now + delay;
  v.lvl = { from, to, t0, t1: t0 + secs, ease };
  p.cancelScheduledValues(now); p.setValueAtTime(from, now);
  if (delay) p.setValueAtTime(from, t0);
  const n = 16; // a smooth curve made of short linear steps
  for (let i = 1; i <= n; i++) p.linearRampToValueAtTime(from + (to - from) * ease(i / n), t0 + secs * i / n);
}
const player = { voice: null, pending: null, timers: [] };
function later(ms, fn) { player.timers.push(setTimeout(fn, ms)); }
function clearLater() { player.timers.forEach(clearTimeout); player.timers = []; }
// Fades the current sound out and lets it go; nothing is left playing afterwards.
function releaseSound(secs) {
  clearLater(); player.pending = null;
  const v = player.voice; if (!v) return;
  player.voice = null;
  fadeVoice(v, 0, secs);
  setTimeout(v.stop, secs * 1000 + 60);
}
// Settings preview: fade in, hold, fade out, silent at exactly 5 s.
function beginPreview(choice) {
  const v = makeVoice(choice); if (!v) return;
  player.voice = v;
  fadeVoice(v, 1, PREVIEW_FADE);
  later(PREVIEW_MS - PREVIEW_FADE * 1000, () => {
    fadeVoice(v, 0, PREVIEW_FADE);
    later(PREVIEW_FADE * 1000, () => { if (player.voice === v) { player.voice = null; v.stop(); } });
  });
}
// Switching sounds: the current one fades out; at the midpoint (silence) it stops and the
// next one starts fading in. Quick taps only change which sound comes next, so two sounds
// never overlap and nothing clicks.
function previewSound(choice) {
  clearLater();
  const v = player.voice;
  if (!v) { beginPreview(choice); return; }
  player.pending = choice;
  // Proportional to the current level, but never shorter than 120 ms, so a burst of taps
  // lands inside one fade and only the last choice plays.
  const secs = Math.max(0.12, SWITCH_FADE * levelOf(v));
  fadeVoice(v, 0, secs);
  later(secs * 1000, () => {
    const next = player.pending; player.pending = null;
    if (player.voice === v) { player.voice = null; v.stop(); }
    if (next) beginPreview(next);
  });
}
// Re-tapping the chosen sound: fade out from wherever it is.
function fadeOutPreview() { if (player.voice) releaseSound(Math.max(0.03, PREVIEW_FADE * levelOf(player.voice))); else { clearLater(); player.pending = null; } }
// Focus: the sound fades in in step with the page transition. Every frame, its level is
// set to the page animation's own progress (same delay, duration and easing), so the
// sound can't run ahead of or behind what's on screen. Then it plays on a loop until the
// session ends. Without animation: a short click-free fade.
const linear = k => k;
function startSessionSound(choice, pageIn) {
  releaseSound(0.03);
  if (!choice) return;
  const v = makeVoice(choice); if (!v) return;
  player.voice = v;
  if (!pageIn || !pageIn.effect) { fadeVoice(v, 1, 0.25); return; }
  const step = () => {
    if (player.voice !== v || v.lvl.to >= 1) return;
    const live = pageIn.playState === "running" || pageIn.playState === "pending";
    const k = live ? Math.min(1, Math.max(0, pageIn.effect.getComputedTiming().progress || 0)) : 1;
    fadeVoice(v, k, 0.03, 0, linear);
    if (k < 1) requestAnimationFrame(step);
  };
  requestAnimationFrame(step);
  setTimeout(() => { if (player.voice === v && v.lvl.to < 1) fadeVoice(v, 1, 0.25); }, 1500); // e.g. page hidden mid-transition
}
function playChime() {
  const ctx = actx(); if (!ctx) return;
  const t0 = ctx.currentTime + 0.05;
  [523.25, 659.25, 783.99].forEach((f, i) => {
    const o = ctx.createOscillator(), g = ctx.createGain(), t = t0 + i * 0.16;
    o.type = "sine"; o.frequency.value = f;
    g.gain.setValueAtTime(0, t); g.gain.linearRampToValueAtTime(0.18, t + 0.02); g.gain.exponentialRampToValueAtTime(0.0001, t + 2);
    o.connect(g).connect(ctx.destination); o.start(t); o.stop(t + 2.1);
  });
}
