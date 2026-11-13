# Reader UX validation

This iteration implements roadmap issues #24–#27. The earlier [baseline validation](validation.md) remains the record for original interoperability, performance, and signing rehearsals. Public release remains deferred.

## Coverage

The suite now contains 17 JVM tests and 43 instrumentation cases. The original decoding, rendering, file routing, privacy, cancellation, grant cleanup, and renderer-recovery checks remain. New coverage exercises:

- A queued mode-selection callback delivered after its activity is destroyed is ignored; open dialogs and popup menus are dismissed during teardown.
- Home and reader menus, installed version and About/license access, and hidden mode selection for plain-only files.
- Reopening through Home and a new activity with the last position and display mode; removing history resets that state.
- Migration from old URI/name/timestamp-only entries, preservation on deduplication, eviction, clearing, and preventing removed entries from being resurrected by a late state save.
- Generic `.conf` interpretation as JSON, persistence, Automatic reset, invalid-JSON fallback, and unchanged source bytes.
- Sharing the original UTF-8 BOM and CRLF bytes while a formatted preview is displayed. A separate test APK/UID receives the file and verifies Android grants reading but denies writing.
- Native selected-text sharing and outgoing text without a source URI.
- Unavailable, oversized, and canceled shares; partial-copy cleanup; expiry and the 20-copy cap; long Unicode provider display names and correct CSV MIME type.

The test-only provider and receiver are not included in the application APK. Sharing tests intercept the outgoing chooser to inspect its contents and additionally deliver the same intent to the separate receiver to exercise actual permission enforcement.

## Emulator acceptance

Local checks on 2026-11-13 used the existing isolated Android 10 (API 29 / WebView 74) and Android 15 (API 35 / WebView 124) configurations from the baseline. Focused UX, history, reader-control, and accessibility checks passed. The final implementation PR runs the complete suite independently on API 29 and 36, plus build, unit tests, and lint; its check results are the authoritative final-commit record.

A live TalkBack acceptance run on Android 15 passed at 360 dp width and 200% font size. It preserves the running accessibility service, verifies touch exploration is active, and activates native popup-menu rows through their accessible labels. These checks validate exposed focus/actions, not a human evaluation of synthesized speech.

Manual Android 15 checks inspected the Home, JSON preview, dark Markdown preview, menu, and About screens. Updated screenshots are in the [user guide](installing.md). A real **Share file… → Android Sharesheet → test receiver** interaction delivered the original JSON SHA-256 `384742851e060d655fa8735fa44816377e3cfb928e086ef0a2345b738fbe5651`; the receiver's attempt to obtain write access was denied.

## Limits and privacy

Reading position is stored as a scroll offset and can be approximate after a file or its layout changes. Overrides and display modes never change source bytes. History contains only references and reading metadata; search text is not persisted with history.

Explicit file sharing creates a copy in the app's private sharing-cache directory. The provider exposes only that directory and requires a per-URI grant. Copies older than 24 hours are removed at the next app start/share; at most 20 are retained. This is not a timer guarantee, and receiving applications may retain their own copies. Incoming text below 100,000 UTF-8 bytes is shared inline; larger text is shared as a UTF-8 file to avoid oversized Android intent payloads.

No network or broad storage permission was added. Source/issue links in About open the external browser only when tapped. Tabs and backlog issues #28–#33 remain outside this iteration.
