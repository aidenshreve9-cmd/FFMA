# Decisions and spec conflicts — Focus Friend V3.0

Where two requirements pulled in different directions, the conflict is listed here with
the smallest change that keeps both as intact as possible. Nothing was changed silently.
Items marked **Needs your OK** are choices only you can make.

## Spec conflicts and how they were resolved

| # | Conflict | Resolution (smallest change) |
|---|----------|------------------------------|
| C1 | The ranking order lists *normalized phone match* 6th (after substring), but the example score for it is +80 (above prefix, 60). | The ordered list wins. A **full** phone-number match ("7805551234" vs "+1 780 555 1234") is treated as an exact-token match of the phone field (80). A **partial** digit match ("780555") is the 6th tier (25). Names and numbers rarely compete, so both rules hold in practice. |
| C2 | Fuzzy is last in the ranking order, but its example range (+10 to +40) could beat a substring match (30). | Fuzzy is capped at 24 (range 10–24), so it can never outrank substring or phone matches. |
| C3 | "1–4 character queries: primarily exact/prefix" vs. the fuzzy example `pnik → Pink Noise` vs. the did-you-mean example *"No sounds found for 'pnik'. Did you mean 'Pink Noise'?"* | Queries of 1–4 characters get no fuzzy **results**; a strong candidate is offered through **did-you-mean**. `pnik` therefore shows exactly the spec's did-you-mean message, and `quntum` (6 chars) returns Quantum Nebula as a fuzzy result. All three examples now hold. |
| C4 | "Trusted Contacts: use a search field when contacts exist" vs. "for tiny lists, prefer browsing". | The explicit rule wins: the contacts search appears as soon as one contact exists. **Needs your OK** — if you'd prefer it to appear only from, say, 6 contacts, it's one number (`threshold`). |
| C5 | Settings › Permissions status "Not set up / Preview only" is browser wording. | Browser: "Not set up" / "Preview only". Android: "Not set up" / "Allowed" (Android has no preview). |
| C6 | Chapter 7's button "Continue in preview" is browser-only. | Browser keeps "Continue in preview". Android shows "Allow Do Not Disturb access" (opens the system screen), then "Start Focus". |
| C7 | Search "Enter: select result" — a Trusted Contact has no single obvious action. | Enter on a contact result moves focus to that contact's **Remove** action. Enter on a sound or view result selects it. |
| C8 | Search "Escape: clear or close search" vs. "Escape closes sheets/dialogs". | In a search field with text, the first Escape clears it; the next Escape closes Settings. Sheets always close first. |
| C9 | "Only fonts may load from the internet." | Browser: Google Fonts only. Android: fonts are **bundled** (OFL), so the Android app declares no internet permission at all — stricter than required. |
| C10 | Android Do Not Disturb can let through "starred" or "all" contacts, not an app-specific list. | Focus temporarily **stars** each Trusted Contact that isn't starred yet and **un-stars exactly those** afterwards (recorded before changing, restored after a crash or reboot). Consequences, disclosed in Settings: people you've *already* starred also get through; numbers typed in that aren't in the phone's Contacts can't be recognised; contacts read/write permission is needed. Kept (see *Your choices*). The alternative — temporarily un-starring your other starred contacts — would edit far more of your data, so it wasn't done. |
| C11 | "Emergency alerts always allowed." | Wireless emergency alerts bypass Do Not Disturb at the system level; the app can't block or unblock them and never claims otherwise. |
| C12 | Repeat-caller window "15 minutes". | That is Android's own system definition; the app turns the category on but can't change the window. |
| C13 | The Android status line. Chapter 3 only fixes the browser's text. | Android shows "Notifications silenced · Emergency calls unaffected" **only** when its rule is really active; otherwise "Do Not Disturb is off · notifications not silenced". |

## Your choices (24 Sep 2026)

Changes the spec didn't require were put to you. What you chose:

| Change | Your choice | Result |
|--------|-------------|--------|
| Search synonyms (noise↔sound, galaxy↔stellar, …) | **Remove** | Removed from both apps. Only what you type is matched. |
| Accent matching ("jose" finds "José") | **Remove** | Removed. Names match as written. |
| Developer timing logs | **Remove** | Removed. Search does no logging in any build. |
| "Phone" placeholder in the contact form | **Undo** | Back to "Phone number". |
| Android extras: bundled fonts / no internet, backups off, notification permission ask | No preference | Kept. |
| Trusted Contacts on Android via temporary starring (C10) | No preference | Kept. |

## Atmosphere & audio patch (24 Sep 2026)

Built as specified: "Scenic view" is now **Atmosphere**; Settings' background shows the chosen
atmosphere and changes as you pick; 5-second sound previews with fades; crossfaded switching that
changes over at the silent midpoint; re-tapping the chosen sound deselects it; leaving Settings stops
the preview; the Focus sound plays on a loop and fades in with the page transition. Where the patch
left something open:

| # | Question | What was done (smallest change) |
|---|----------|---------------------------------|
| A1 | "Update the app's background… as options are selected in Settings" — Settings only, or Home too? | Settings' own background shows the atmosphere (that is where you choose). Home keeps its near-black nebula so the orb stays the focus. **Needs your OK** — Home can show it too. |
| A2 | Re-tapping deselects the sound. Then what does Focus play? | Nothing: "no sound" is saved and Focus is silent (the footer then shows only the atmosphere). Tapping any sound selects again. |
| A3 | "Crossfade" vs. "stop and start at the midpoint so sounds never overlap". | The midpoint rule wins: the old sound fades out to silence, stops, and the new one fades in. So there is no moment with both playing. |
| A4 | Preview for Random and for your own sounds. | Random previews one randomly picked sound (Focus picks again each session). Your own sounds preview like the built-in ones. |
| A5 | Search's Enter on the already-chosen sound. | Enter always selects (and previews); only a tap deselects. |

## Choices made so the app works (carried over, still open)

- **Slide direction:** sliding left = next duration (content moves right-to-left), right = previous.
- **Defaults:** Alarm Safety ON (safety), Trusted Contacts OFF.
- **Black Noise:** a quiet sub-bass rumble (strictly, "black noise" means silence).
- **Default atmosphere:** Quantum Nebula.
- **Done screen:** shows the quote's author; early end says "Session ended".
- **Buy Me a Coffee:** no link yet; the button explains that and does nothing.
- **Quote wording:** still to be checked against public-domain translations before release.
- **Sound/scenic-view search threshold:** shown only past 12 options (browsing is easier below that).

## Not built (by design or blocked)

- **Purple Alarm Safety effect:** future feature. Detecting a ringing alarm reliably needs more research; nothing pretends it exists.
- **Browser silencing/call bypass:** impossible for a web page; the preview says so everywhere it matters.
- **Android APK build in this sandbox:** Google's Maven/SDK host (`dl.google.com`) is blocked by the environment's network policy. See `docs/TESTING.md` for what was verified instead.
