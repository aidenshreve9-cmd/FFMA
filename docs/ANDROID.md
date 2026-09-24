# Native Android — Focus Friend V3.0

Kotlin, minimum Android 10 (API 29), target Android 15 (API 35). The app uses only the Android
framework plus the shared `:core` module — no AndroidX, Compose, analytics, ads or network code.

## Build

```bash
cd android
./gradlew :app:assembleDebug          # needs the Android SDK (Android Studio)
./gradlew :app:testDebugUnitTest      # Robolectric Do Not Disturb tests
./gradlew -Pff.coreOnly=true :core:test   # core logic, no SDK needed
# Type-check the app without an SDK (downloads Robolectric's Android 15 jar from Maven Central):
./gradlew -Pff.coreOnly=true -Pff.verifyAndroid=true :verify:compileKotlin :verify:compileTestKotlin
```

## How Focus silences the phone (Chapter 15)

Focus owns one **AutomaticZenRule** (API 29+) with a **ZenPolicy**, and switches it on with
`setAutomaticZenRuleState(…, STATE_TRUE)` at the start and `STATE_FALSE` at the end.

- It **never** calls `setInterruptionFilter` or `setNotificationPolicy`, so your own Do Not Disturb
  settings are never overwritten. Turning Focus's rule off returns the phone to exactly its previous
  state — including any Do Not Disturb you had on yourself. (This is also Android 15's supported path.)
- The rule reuses one id; it never piles up duplicates.

| During Focus | ZenPolicy |
|--------------|-----------|
| Notification sounds, vibration | blocked (priority-only filter) |
| Pop-ups, peeking, badges, lights | `hideAllVisualEffects()` |
| Calls and messages from everyone else | `PEOPLE_TYPE_NONE` |
| Repeat callers (2nd call within 15 min) | `allowRepeatCallers(true)` |
| Trusted Contacts (switch ON) | `PEOPLE_TYPE_STARRED` + temporary starring (below) |
| Alarms (Alarm Safety ON) | `allowAlarms(true/false)` |
| Focus Friend's own sound | `allowMedia(true)` |
| Emergency alerts | always delivered by the system |

### Trusted Contacts
Android can't let through an app-specific list, only starred (or all) contacts. With contacts
permission, Focus looks up each Trusted Contact's number (`PhoneLookup`), **stars** the ones not
already starred, and **un-stars exactly those** when the session ends. Every contact it changes is
recorded *before* the change, so recovery can always undo it. Disclosed in Settings: people you've
already starred also get through; numbers not saved in your Contacts can't be recognised.

### Preserving and restoring your settings
- Changes are recorded first (`rule_active`, `starred_by_focus`) using synchronous commits.
- A foreground service (media playback type, sticky) keeps sound playing with the screen locked and
  ends the session on time; if the system kills and restarts it, it resumes from the stored end time
  or cleans up.
- An end-time alarm, a boot/app-update receiver and every app launch call `recover()`, which
  restores the phone whenever no session is running. `recover()` is idempotent.

## Permissions

| Permission | Why | When asked |
|------------|-----|------------|
| Do Not Disturb access (`ACCESS_NOTIFICATION_POLICY`) | Turn Focus's own rule on/off | Permission sheet → system screen |
| `READ_CONTACTS`, `WRITE_CONTACTS` | Temporarily star Trusted Contacts | Only when Trusted Contacts is switched ON |
| `POST_NOTIFICATIONS` | The ongoing "Focusing" notification (optional) | First session, once |
| `FOREGROUND_SERVICE(_MEDIA_PLAYBACK)` | Sound + timer while locked | Install-time |
| `RECEIVE_BOOT_COMPLETED` | Restore the phone after a reboot mid-session | Install-time |
| **No `INTERNET`** | — | The Android app never goes online |

The system contact picker returns only the chosen contact, so adding a contact that way needs no
contacts permission; starring during Focus does.

## Other Android details
- **Orb:** API 33+ uses an AGSL `RuntimeShader` (a port of the browser's WebGL shader); older
  versions, or any shader failure, use the 2D fallback. Late frames lower the sample count (20 → 14 → 10).
- **Scenes:** the eight views are drawn on Canvas (same seeds as the browser).
- **Sounds:** eight noise colours generated live on an `AudioTrack` (media volume only — no slider);
  your own sounds via `MediaPlayer`; soft three-note chime on natural finish only.
- **Storage:** app-private preferences and files; pictures shrunk to 1100 px (JPEG 82); sounds ≤ 20 MB
  and validated as audio; backups disabled (`allowBackup="false"`).
- **Accessibility:** TalkBack roles and states (switch, radio, button, headings, pane titles), the orb
  exposes scroll actions to change the time, polite live regions for minutes left, keyboard ← → Enter,
  Escape/Back close sheets, 48 dp touch targets, "Remove animations" respected everywhere.

## Known limitations
- The APK couldn't be built in the development sandbox (Google's SDK host is blocked there); the code
  is type-checked against Android 15 framework classes, and the core is unit-tested. Build and test on
  a device before release (see `docs/TESTING.md`).
- The AGSL orb shader compiles only on a device (API 33+); on failure the 2D orb is used automatically.
- The purple Alarm Safety effect is a future feature and is not implemented.
