# Architecture — Focus Friend V3.0

Focus Friend is a privacy-first focus timer with two front ends that share one design:

- **Browser preview** — `prototype/` (single HTML page + `search.js`). States its limits honestly.
- **Native Android** — `android/` (Kotlin, Android framework only; no AndroidX, Compose, network or analytics libraries).

The intended experience never changes: open → quantum orb → choose 15/30/45/60 → tap Focus → focus → finish → Home.

## Project structure

```
prototype/
  focus-friend.html      Browser app (UI, session, audio, scenes, Settings, local search UI)
  search.js              Local search engine (browser + Node tests), no dependencies
  logbook.html           Change log
tests/
  search/*.test.js       node --test: unit, relevance, A/B, performance, edge cases
  ui/run-ui-tests.mjs    Playwright UI/integration tests against the real page
android/
  core/                  Pure Kotlin (JVM-tested): search, durations, session clock, contacts, settings, policy
  app/                   Android app (framework only)
    platform/            Do Not Disturb rule, Trusted Contact starring, recovery receiver
    session/             Session store, runtime (exactly-once finish), foreground service
    audio/               Noise engine (8 colours), user sounds, chime
    data/                Settings store, local media library
    ui/                  Theme, orb (AGSL + 2D fallback), dial, scenes, screens, Settings, sheets
    src/test/            Robolectric tests for the Do Not Disturb layer
  verify/                Type-checks app/ against Robolectric's Android 15 framework jar (no SDK needed)
docs/                    This file, SEARCH, ANDROID, PRIVACY_SECURITY, TESTING, DECISIONS
```

## Layers

```
 UI (screens, sheets, Settings, orb)          ← never touches system APIs directly
   │
 Session runtime / state machine             IDLE → PERMISSION_REQUIRED → (BARRIER) → ACTIVE → (CONFIRM_END) → COMPLETED → IDLE
   │            │                 │
 Platform     Audio            Storage          (independent of each other)
 DistractionControl  NoiseEngine     SettingsStore / MediaLibrary (Android)
 (Android: DND rule; browser: "can't")          localStorage / IndexedDB (browser)
   │
 Core domain (shared rules, pure Kotlin / JS)
   Durations · SlideGesture · FocusSession (fixed end time) · Catalog · Quotes
   TrustedContactBook · FocusSettings · InterruptionPolicy · Search
```

Search sits beside Settings only (`SearchController → SearchNormalizer → SearchIndex → SearchRanker`).
It is independent of the timer, DND, notifications, Alarm Safety, audio and scenes: a search
failure returns no results and can never stop a session from starting (tested in the browser).

## Data models

| Model | Fields | Persisted? |
|-------|--------|------------|
| Settings | sound (or "none"), scene (atmosphere), alarmSafety (default ON), trustedContactsOn (default OFF), contacts, permissionSheetSeen (Android) / previewAck (browser) | Yes, on the device |
| Timer duration | 15/30/45/60 | **Never** — every launch starts at 15 |
| TrustedContact | id, name, number (as typed), normalized (digits), lookupKey? (Android) | Yes, on the device |
| FocusSession | minutes, startedAt, endAt = startedAt + minutes | Only while running, for recovery |
| User media | id, kind, name, local file | Yes, app-private storage |
| Search record | id, type, title, subtitle, searchableFields, normalizedFields, tokens, createdAt | In memory only |

## Session timing

A session stores a **fixed end timestamp**. Remaining time is always `endAt − now`, shown as whole
minutes rounded up ("1 min" until the final second), with the arc on the same 60-minute face as Home.
Completion and cleanup run **exactly once** (`finishOnce()`), whichever path gets there first:
natural end, early end, service timer, recovery.

## Atmosphere and sound

- **Settings background:** the chosen atmosphere is drawn behind Settings (dimmed so text stays
  readable) and crossfades (0.7 s) as you pick another. Home keeps its nebula.
- **Preview:** tapping a sound in Settings plays a 5-second preview: 0.9 s fade in, hold, 0.9 s fade
  out. Only one sound ever plays. Switching: the current sound fades out (up to 0.45 s, shorter if
  it is still quiet, never under 0.12 s); at that midpoint, in silence, it stops and the new one starts
  fading in. Taps during a switch only change which sound comes next. Tapping the chosen sound again
  fades it out and deselects it ("none": Focus is silent). Leaving Settings stops the preview at once
  (30 ms fade, only to avoid a click). Random previews one randomly picked sound.
- **Focus:** the sound starts silent and, every frame of the page transition into Focus, its level
  is set to the incoming page's opacity — same delay, duration and easing — then it loops until
  the session ends. Without animation, a 0.25 s fade.
- Browser: Web Audio gain automation. Android: `SoundPreview` (core, unit-tested) drives
  `NoiseEngine` / `UserSoundPlayer`, whose level follows a ramp shaped for when each block is heard;
  `SessionAudio` links the activity's page transition to the foreground service's sound.

## Platform adapter

`DistractionControl` (Android) has `hasAccess()`, `begin(policy)`, `end()`, `recover()`.
Permission state is always read from the OS. The browser adapter's `beginDistractionReduction`
returns `false`, so the UI never claims silencing it can't provide.

See `docs/ANDROID.md` for the Android specifics and `docs/SEARCH.md` for search.

## Error handling (summary)

| Situation | Behaviour |
|-----------|-----------|
| Empty/invalid query | No results, no error |
| Corrupted settings / metadata | Fall back to defaults; bad records skipped |
| Missing or deleted media | Skipped; selection falls back (White Noise / Quantum Nebula) |
| Duplicate contact (any format) | Rejected with a clear message |
| Storage full | "Your phone is full. Free up space and try again." |
| Unsupported browser API (contact picker, wake lock, WebGL) | Feature hidden or falls back |
| DND access missing | Permission sheet → "Focus can't start without access." |
| DND access revoked mid-session (Android) | Status changes; calm message; never claims silence |
| Audio failure | Session continues silently; timer and DND unaffected |
| App killed / phone rebooted mid-session | Rule and starred contacts restored by service restart, alarm, boot receiver or next launch |

## Performance strategy

- Search: normalized fields and tokens are computed once at index time; a keystroke only prepares
  the query and scans small collections; bounded fuzzy distance with early exit and reused buffers;
  small result cache cleared on change. Measured p95 at 10,000 records: ~16 ms (JS), ~9 ms (Kotlin).
- Rendering: ambient animation at ~30 fps, paused when hidden; the orb lowers its resolution (web)
  or volume samples (Android) when frames run late; reduced motion stops animation entirely.
