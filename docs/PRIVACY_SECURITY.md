# Privacy and security — Focus Friend V3.0

## What never exists
No account, login, ads, tracking, analytics, behavioural profiling, search history, cloud search,
cloud database, remote processing of personal data, or remote relevance telemetry.

## What stays on the device
Settings, Trusted Contacts (name, number as typed, normalized number), your sounds and pictures,
and any search data (the in-memory index). Nothing is uploaded.

| Platform | Network | Storage |
|----------|---------|---------|
| Browser preview | Only the Google Fonts stylesheet and font files | `localStorage` (small settings), IndexedDB (your files) |
| Android | **None** — no `INTERNET` permission; fonts are bundled | App-private preferences and files; backups disabled |

## Never sent, never logged
Contact names, phone numbers, filenames, search queries, pictures and sounds.
Developer metrics are counts and timings only:

- Bad: `Search executed: Grandma 7805551234`
- Good: `search executed collection=trustedContacts resultCount=4 ms=0.21`

Verified by tests in both implementations (the log lines are checked for names and digits), and by
a browser UI test that records every network request and fails on anything other than fonts.

## Security measures
- Imported files are validated (type, size, playable audio / decodable image) and copied into
  app-private storage; nothing imported is executed.
- Pictures are shrunk and re-encoded on both platforms, which also drops embedded metadata such as location.
- User-provided text is rendered as text (`textContent` / `TextView`), never as HTML.
- Search never throws into the app; corrupted data is skipped.
- Android: the restore receiver is exported only for `BOOT_COMPLETED`; its only action is an
  idempotent "restore the phone if no session is running". Pending intents are immutable.
- Android: Trusted Contact starring records every change before making it and undoes exactly those.
- No secrets or API keys exist in either app.
