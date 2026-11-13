# Backlog implementation validation

Issues #28–#33 implement the owner-authorized reader navigation and document-session extension. [Behavior and limits](open-documents.md) record the session cap, memory policy, Back behavior, restoration, permission ownership, pinning, Undo, and navigation budgets. Public release remains deferred.

## Automated coverage

The suite contains 20 JVM tests and 52 instrumentation cases. Existing decoding, format, intent-routing, rendering-budget, offline/privacy, cancellation, sharing, lifecycle, and accessibility coverage remains.

New renderer tests cover unique Markdown heading anchors, duplicate/empty labels, inline code/text labels, Unicode chunk boundaries, LF/CRLF/CR preservation, multiline highlighted tokens, and bounded line decorations with a jump into a 100,000-line source.

`BacklogTest` exercises:

- Provider-backed reload after changes, shortening, disappearance, and access revocation, preserving mode and clamping position to the new content height.
- Actual native Select all/Copy with line numbers enabled, confirming the clipboard contains only source text; Go to line through wrapped logical lines.
- Outline navigation to an empty heading after a duplicate title while JavaScript and network loading remain disabled.
- Switching with search query, mode, and position restoration; recreation, URI deduplication, and closing a document without removing history.
- Five-entry session eviction, restart metadata, temporary shared-text exclusion from persistence, and persisted-grant ownership independent of history.
- Thirty-entry history and ten-pin limits; Undo, expiry, grant cleanup, and Undo after both capacity limits have filled.
- Actual Home pin/removal/Undo controls without changing document contents.
- External VIEW handoff to a separate APK/UID, matching the original SHA-256 and denying write access; no compatible handler leaves the reader available.

The native overflow menu is scrollable at 200% fonts in landscape. The accessibility layout check exercises that scroll to reach reading settings. Source line decorations remain outside accessibility text and selection; numbering is capped at 10,000 lines, while a single jump anchor can target the complete supported source.

## Verification record

Local Android 15 validation passed the 51-case suite before the final missing-handler fixture was added, followed by the final backlog feature checks. Android 10 passed the eight feature flows and the corrected missing-handler case. Android 15 retains Markor and Fossify from interoperability validation, so its missing-handler case is skipped when those compatible viewers are installed; the empty-handler branch is verified on Android 10. The final Android 16 sequence passed both accessibility classes and all nine backlog cases together (11 tests). Its clipboard check waits for the source accessibility node before a native long-press; scroll checks wait for the requested offset rather than assuming that view readiness implies completed scrolling. A live Android 15 TalkBack run passed at 360 dp width and 200% fonts, validating exposed focus/actions rather than human speech quality. Manual inspection refreshed Home, JSON, dark Markdown, menu, About, source-line, and document-switcher screenshots. The implementation PR runs the full 52-case suite independently on Android 10 (API 29) and Android 16 (API 36), alongside build, unit tests, and lint. The PR's final successful checks identify the verified source revision. Test fixtures and the external receiver ship only in the test APK.

## Limits

CI exposed a restoration race when the native WebView content height had not caught up with its visual-state callback. Restoration now waits for the next document draw before clamping the saved offset; controls become ready after restoration. This also preserves clamping when a reloaded document becomes shorter. The page-finished callback ignores unrelated URLs.

Position restoration remains approximate after source/layout changes. Inactive URI documents reload on switch; search queries stay in memory and Find restarts at the first match. Shared text and queries are not persisted in session preferences. URI access may expire after process death; unavailable documents offer the picker.

Explicit Share/Open with copies use the existing private cache: at most twenty, with copies over 24 hours removed at the next start/copy operation. Undo expires after ten seconds; deferred cleanup after process termination runs at the next app start/history interaction. No network or broad storage permission is added, and no production signing identity or public release is created.
