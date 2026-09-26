"use strict";

/* ================= Config / decision registry ================= */
const MIN_MINUTES = 1, MAX_MINUTES = 60;      // V3.1: any whole minute from 1 to 60, set on the ring
const DEFAULT_MINUTES = 15;                    // APPROVED: always opens on 15
const RING_INNER = 0.68;                       // touches this far out (share of the dial's radius) set the time
const TAP_SLOP = 7;                            // px a touch in the middle can move and still count as a tap
const REPEAT_CALL_WINDOW_MIN = 15;             // Android's repeat-caller window
const DONATION_LINK = null;                    // NOT APPROVED — no link yet
const SCENES = [
  { id: "quantum", name: "Quantum Nebula" },
  { id: "galaxy",  name: "Spiral Galaxy" },
  { id: "horizon", name: "Event Horizon" },
  { id: "aurora",  name: "Aurora Veil" },
  { id: "dust",    name: "Cosmic Dust" },
  { id: "nursery", name: "Stellar Nursery" },
  { id: "web",     name: "Dark Matter Web" },
  { id: "void",    name: "Ethereal Void" },
];
const DEFAULT_SCENE = "quantum";
const QUICK_PICKS = [5, 15, 25, 45, 60];       // one-tap times on Home
const GOALS = [0, 30, 60, 90, 120, 180];       // daily goal in minutes; 0 = off
const EXTEND_MIN = 5;                          // "+5 min" during Focus
const BREAK_MIN = 5;                           // break timer after a session
const SOUNDS = [
  { id: "white",  name: "White Noise",  kind: "FLAT",       gain: .50 },
  { id: "pink",   name: "Pink Noise",   kind: "SOFT 1/F",   gain: .62 },
  { id: "brown",  name: "Brown Noise",  kind: "DEEP 1/F²",  gain: .70 },
  { id: "green",  name: "Green Noise",  kind: "~500 HZ",    gain: 1.3 },
  { id: "grey",   name: "Grey Noise",   kind: "EVEN EAR",   gain: .55 },
  { id: "blue",   name: "Blue Noise",   kind: "BRIGHT",     gain: .30 },
  { id: "violet", name: "Violet Noise", kind: "AIRY",       gain: .20 },
  { id: "black",  name: "Black Noise",  kind: "SUB-BASS",   gain: 2.2 },
];
const QUOTES = [                               // APPROVED list
  ["Each man lives only this present, this momentary thing.", "Marcus Aurelius"],
  ["Do not imagine this, that you will recover it when you choose.", "Epictetus"],
  ["To learn, and in due time to practise what you have learned — is that not a pleasure?", "Confucius"],
  ["Learning without thought is dark; thought without learning is perilous.", "Confucius"],
  ["Stillness should be guarded with unwearying vigour.", "Laozi"],
  ["Hold fast to these few things only.", "Marcus Aurelius"],
  ["To learn without tiring of it, to teach others without wearying.", "Confucius"],
  ["Be quick in what must be done and careful in what you say.", "Confucius"],
  ["Is there any part of life excepted, to which attention does not extend?", "Epictetus"],
  ["The task at hand.", "Marcus Aurelius"],
];
const reduceMotion = matchMedia("(prefers-reduced-motion: reduce)");
function digitsOf(v) { return String(v || "").replace(/\D+/g, ""); }
function newId() { return Date.now().toString(36) + Math.random().toString(36).slice(2, 7); }
// Same number, allowing a 1–3 digit country code on either side.
function samePhone(a, b) {
  if (window.FFSearch) return FFSearch.phonesMatch(a, b);
  return !!a && a === b;
}
const motionOK = () => !reduceMotion.matches && typeof Element.prototype.animate === "function";
const EASE = "cubic-bezier(.22,.8,.2,1)";
const $ = id => document.getElementById(id);
const mod = (n, m) => ((n % m) + m) % m;
