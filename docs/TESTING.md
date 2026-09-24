# Testing — Focus Friend V3.0

## How to run

```bash
npm test                      # search: unit, relevance, A/B, performance, edge cases (Node ≥ 20, no deps)
npm run test:ui               # browser UI/integration tests (Playwright + Chromium)
cd android
./gradlew -Pff.coreOnly=true :core:test          # Kotlin core (no Android SDK needed)
./gradlew :app:testDebugUnitTest                 # Robolectric Do Not Disturb tests (needs the SDK)
```

## Results in this build

| Suite | Result |
|-------|--------|
| `tests/search` (JS) — 29 tests | **29 passed** |
| `tests/ui` (Playwright, Chromium) — 16 tests | **16 passed** |
| `android/core` (Kotlin, JUnit 5) — 29 tests | **29 passed** |
| `android/app` type-check against Android 15 framework (`:verify`) | **Compiles, 0 errors** (91 classes) |
| `android/app/src/test` Robolectric DND tests — 8 tests | **Compile; not run here** (need `androidx.test` from Google's repository, which is blocked in this sandbox) |
| Android APK build / on-device tests | **Not run here** (Android SDK host blocked); do before release |

## Coverage map

**Unit (search, both platforms):** normalization pipeline, names/filenames/phone normalizers,
tokenization, phone variants, bounded distance, ranking order, token prefix, exact match, fuzzy,
did-you-mean (only on zero results; no weak/ambiguous guesses), no synonyms (direct matches only),
autocomplete, empty/invalid queries, limits, deterministic ties, incremental add/remove,
corrupted records, record shape.

**Unit (Kotlin core):** durations loop both ways; slide axis; a flick is exactly one step; fixed
end time; rounding up; "1 min" to the last second; accurate after leaving and returning; finish runs
once; catalog (8 sounds, 8 views, defaults); Random draws from built-ins + user files; 10 quotes
with authors; settings defaults (Alarm ON, Trusted OFF, no duration field); contact book rules
(duplicates across formats, invalid numbers, max 50); validation fallbacks; policy follows switches.

**Relevance:** Precision@1, Precision@3, MRR, NDCG@3, zero-result rate on the spec corpus; local A/B
of ranking A vs B (see `docs/SEARCH.md`).

**Performance:** 50 contacts, 1,000 sounds, 1,000 images, 10,000 synthetic records; p95 < 100 ms
required. Measured p95 at 10,000: ~16 ms (JS), ~9 ms (Kotlin, cold, cache cleared).

**UI / integration (browser):** home opens at 15 with exact hint; slide left/right loops both ways;
vertical ignored; a slide never starts Focus; a flick is exactly one step; keyboard ← → Enter;
permission sheet wording and ON/OFF states; "Focus can't start without access." with "Review the
rules again" / "Go back"; exact session status; footer; early end → "Session ended"; natural finish
via the fixed end time; Done resets to 15; Settings order and defaults; exact Alarm Safety wording;
contact search (ranking, phone variants, did-you-mean, duplicates, normalized storage); search
keyboard (↓ ↑ Enter Escape); removal updates the index; sound search appears past 12 and handles
`pnik`; persistence (duration never saved); reduced motion; no network except fonts and no names
or numbers in the console; search failure never prevents Focus.

**Robolectric (Android DND layer, written):** no access → nothing silenced, nothing created; the
rule uses the spec policy (calls/messages none, repeat callers, alarms, media, visual effects hidden);
Alarm Safety OFF blocks alarms; Trusted Contacts without contacts access reported honestly; the
person's own DND policy and filter untouched; rule reused, never duplicated; recovery after an
unexpected end is correct and idempotent; session store rejects corrupted durations.

## Bugs these tests caught (and were fixed)
- `search.js` broke when a page didn't declare UTF-8 → the module is now ASCII-only.
- A fast flick could move two steps → a flick now adds at most one step.
- Messages could block taps on sheet buttons → messages no longer intercept taps.
- A performance test was timing the cache → it now measures cold searches.

## Before release — manual device checklist
1. Grant Do Not Disturb access; start Focus; confirm notifications are silent and the status line shows.
2. Call from another phone: first call silent; second call within 15 minutes rings.
3. Trusted Contacts ON with a saved contact: their call rings; after Focus, their star state is as before.
4. Set an alarm inside a session with Alarm Safety ON (rings) and OFF (silent).
5. Turn on your own DND schedule before Focus; confirm it's unchanged after Focus ends.
6. Force-stop the app mid-session; reopen: the phone is restored or the session resumes correctly.
7. Reboot mid-session: after boot, Focus's rule is off and stars are restored.
8. Revoke DND access mid-session: the status changes and a calm message appears.
9. TalkBack pass on every screen; "Remove animations" on; large font sizes.
10. Battery: a 60-minute session on an older phone (orb quality should step down if needed).
