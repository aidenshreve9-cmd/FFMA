# Optimization pass — Focus Friend V3.0

## Versions
- **Version A (original):** Git tag `focus-friend-v3.0-original` (commit `68c1ab0`).
  Get it back with `git checkout focus-friend-v3.0-original`, or view it side by side with
  `git worktree add ../ff-original focus-friend-v3.0-original`.
- **Version B (optimized):** this branch. Same screens, wording, settings, storage keys, IndexedDB
  schema, search results and Android behaviour.

## What changed
**Browser (`prototype/focus-friend.html`, `prototype/search.js`)**
- One `choiceFor()` replaces the two copies of sound/atmosphere lookup (built-in, Random, your own file).
- One `paintChoice()` paints both the session and Settings backgrounds (was two copies); one
  `loadPicture()` and `defaultScene()` replace repeated code.
- One `acceptFile()` holds the checks both file pickers share; `contactsChanged()` replaces three
  repeated save/render calls; a `plural()` helper replaces six inline ternaries.
- Galaxy and Cosmic Dust particles get their fill colour once instead of building 1,500 / 900
  strings every frame. Measured: the galaxy particle loop went from 1.60 to 1.21 ms per frame
  (about 25% less; headless Chromium, median of 5 × 300 frames).
- The frame loop looks its elements up once instead of about 10 `getElementById` calls per frame.
- Startup no longer builds Settings twice (it's built once saved files are known, and whenever it opens).
- CSS: removed 4 unused colour tokens and 1 unused rule; search inputs share the text-field rule
  (3 duplicate declarations removed).
- `search.js`: the three normalizers share one `clean()`; did-you-mean works out its edit bound once
  per query (as the Kotlin version already did) and stops copying token arrays.
- Fixed a misplaced comment on `TAP_SLOP` / `FLICK_VELOCITY`.

**Android (`ui/Scenes.kt`, `ui/Orb.kt`, `ui/Theme.kt`)**: removed per-frame allocations only.
- The scenes' fixed layouts (streaks, lens arcs, dust lanes, pillars, orbits) are built once.
  Cosmic Dust reuses one point instead of about 900 new arrays per frame. Dark Matter Web works
  out node positions once per frame instead of about 150 arrays. The lens arcs reuse one `RectF`.
- The dial's tick geometry (480 trig calls), number labels and arc gradient are computed once.
- The session edge dim, glass-panel prism edge, primary button fill and switch track build their
  gradients only when their size changes.

## Files
- Changed: `prototype/focus-friend.html`, `prototype/search.js`, `android/.../ui/Scenes.kt`, `Orb.kt`,
  `Theme.kt`, `tests/ui/run-ui-tests.mjs` (2 new tests), docs (README, TESTING, ARCHITECTURE, logbook).
- Added: this file. Removed: none.
- Dependencies: none added or removed (there were none on the web side; Android is framework-only).

## Size
| File | Before | After | gzip before → after |
|------|--------|-------|---------------------|
| focus-friend.html | 115,263 B / 2,015 lines | 114,910 B / 2,010 lines | 35,194 → 35,597 |
| search.js | 16,350 B | 16,350 B | 5,421 → 5,438 |
| Scenes.kt + Orb.kt + Theme.kt | 59,522 B | 61,550 B | — |

The source size is about the same: the gains are less duplicated logic and less work per frame.
The Kotlin files grew a little because constant tables are now named fields. The browser file's
gzipped size grew by 403 B, mostly from the new explanatory comments.

## Verification
- `npm test`: 29/29. `npm run test:ui`: 21/21 (19 existing + Random choices + your own picture:
  wrong type, choose, reload, use in Focus, remove → fallback). Both new tests also pass on Version A.
- Kotlin core: 34/34. `:verify` (app + Robolectric tests compiled against Android 15): 0 errors.
- Pixel comparison (reduced motion, fixed clock, seeded random): Home, Settings (top and bottom),
  the permission sheet, confirm-end, Done, and all 8 atmospheres as the Settings background are
  **pixel-identical** to Version A. In the 8 session screens, the only differences are inside the
  WebGL orb, at most 2 of 255 levels. That's the same noise seen between two runs of Version A itself.

## Not done / not verified
- **Not run on a phone:** the Android changes type-check and keep the same arithmetic, but no APK
  build, Robolectric run or on-device check was possible here (Google's Maven and the SDK are blocked).
- **Minification:** not adopted, because it would add a build step and the project's first
  dependency. Measured with esbuild: app JS 27.9 → 20.1 KB gzipped, search.js 5.4 → 2.9 KB,
  CSS 5.1 → 4.8 KB (about 10 KB less in total).
- **Font subsetting:** the Android fonts (943 KB) are the largest part of the APK. Not done:
  Playfair Display's license reserves its name for unmodified fonts, and subsetting would drop
  non-Latin glyphs.
- **`isShrinkResources`:** not enabled, because there was no way to build a release APK here.
- **Kept on purpose:** the donation placeholder (`DONATION_LINK = null`) and the browser's
  do-nothing Do Not Disturb adapter. Both are documented placeholders, not dead code.
