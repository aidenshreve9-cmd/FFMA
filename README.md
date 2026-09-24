# Focus Friend

A privacy-first focus timer. One glowing orb: choose 15, 30, 45 or 60 minutes, tap Focus,
focus, finish, and you're back Home. No account, no login, no ads, no tracking, no analytics,
no cloud. Settings, contacts, sounds and pictures stay on your device.

| | Browser preview | Native Android |
|---|---|---|
| Where | `prototype/focus-friend.html` (+ `search.js`) | `android/` (Kotlin, API 29–35) |
| Silences notifications and calls | No — says so plainly | Yes — its own Do Not Disturb rule, restored afterwards |
| Trusted Contacts / Alarm Safety pass-through | Settings only | Yes (see `docs/ANDROID.md`) |
| Network | Fonts only | None (fonts bundled, no internet permission) |

## Try it
- Browser: open `prototype/focus-friend.html` (keep `search.js` next to it).
- Android: open `android/` in Android Studio and run the `app` configuration.

## Test it
```bash
npm test                 # 29 search tests (Node, no dependencies)
npm run test:ui          # 19 browser UI tests (Playwright)
cd android && ./gradlew -Pff.coreOnly=true :core:test      # 34 Kotlin core tests
cd android && ./gradlew :app:testDebugUnitTest             # Robolectric Do Not Disturb tests
```

## Documentation
- `docs/ARCHITECTURE.md` — structure, layers, data models, timing, errors, performance
- `docs/SEARCH.md` — local search: normalization, ranking, fuzzy, phone matching, relevance results
- `docs/ANDROID.md` — Do Not Disturb rule, Trusted Contacts, recovery, permissions
- `docs/PRIVACY_SECURITY.md` — what stays local, what's never sent or logged, protections
- `docs/TESTING.md` — suites, results, what wasn't verified, device checklist
- `docs/DECISIONS.md` — spec conflicts and how each was resolved; open choices
- `prototype/logbook.html` — change log

## V3.0 engineering outputs → where to find them

| # | Output | Location |
|---|--------|----------|
| 1–2 | Architecture, file structure | `docs/ARCHITECTURE.md` |
| 3 | Data models | `docs/ARCHITECTURE.md`, `android/core/.../Settings.kt`, `Focus.kt` |
| 4–11 | Search, index, normalization, tokenization, ranking, fuzzy, phone matching | `prototype/search.js`, `android/core/.../search/` |
| 12–15 | Contact / sound / scenic-view search, autocomplete, did-you-mean | Settings in `prototype/focus-friend.html`, `android/app/.../ui/SettingsScreen.kt` |
| 16 | Browser implementation | `prototype/` |
| 17 | Android implementation | `android/` |
| 18–19 | Accessibility, reduced motion | both apps; `docs/ANDROID.md`, `docs/SEARCH.md` |
| 20 | Privacy/security | `docs/PRIVACY_SECURITY.md` |
| 21 | Error handling | `docs/ARCHITECTURE.md` |
| 22 | Performance strategy | `docs/ARCHITECTURE.md`, `tests/search/performance.test.js` |
| 23–27 | Unit, integration, UI, relevance, edge-case tests | `tests/`, `android/core/src/test/`, `android/app/src/test/`, `docs/TESTING.md` |
