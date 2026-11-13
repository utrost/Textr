# Everyday reading validation

Scope: issues #41, #42, #43, #47 and #48. Public release and production signing remain deferred. See [behavior and limits](everyday-reading.md).

## Automated coverage

The JVM suite now contains 40 cases. Ten new cases cover strict legacy/endian decoding, BOM conflicts, malformed sequences, binary and byte limits, line-ending metadata, literal search, Unicode case and word boundaries, source line mapping, non-overlapping results, cancellation, repetitive worst-case input, query/result caps, and escaped exact-match markup across stream chunks. All 40 pass locally, alongside debug builds and lint.

Six new instrumentation cases cover:

- Source search from Markdown preview, case/word controls, result counts, an exact occurrence jump to plain text, recreation and in-memory-only query state.
- Windows-1252 error recovery, persisted read access, metadata, reload, switching, ViewModel loss/restoration, failed-choice handling and unchanged original-byte sharing.
- BOM-less UTF-16 recovery from an initial binary message.
- Current-document labels, direct Treat as and offline help from reader/Home.
- Wide-screen tab switching, stable ordering, recreation and closing documents. Screen size, density and font overrides are restored after the test.
- Source-search controls and result navigation at 200% fonts in landscape. Controls and results share a scrolling list so neither can be clipped out of reach.

The complete instrumentation suite contains 61 cases. PR CI runs all cases on Android 10/API 29 and Android 16/API 36, plus the JVM/build/lint job. PR checks and issue completion comments identify the final tested revision and outcome. Existing reader-control, session, source-integrity and large-font coverage remains in place.

A local Android 16 combined run passed 25 feature, reader-control, session and large-font cases before the additional landscape regression was added. After the layout fix, all six feature cases passed together on Android 15 at 200% fonts and 360 dp width (99.955 seconds). The About help link remains accessible by scrolling, and the final source-search screen was visually inspected. The existing session test additionally checks that reusing the same shared-text document preserves source-query options, while a fresh incoming share resets them and no query is written to preferences.

All six feature cases also pass together on Android 10 locally (83.696 seconds). The UI helper reacquires stale native accessibility nodes and waits for each nested help/About dialog to disappear before continuing; feature assertions remain unchanged. Search-result scrolling targets its own list explicitly. The accessibility navigation check uses the native forward-scroll action when Reading settings is below the visible menu area.

The corrected six-feature sequence also passes on Android 16 (91.010 seconds). Android 10 large-font layout followed by accessibility-tree navigation/actions passes as a pair (28.001 seconds), including the scrollable reader menu.

The older format-override tests select Document info’s neutral button by ID, avoiding the identically named row in the dismissing reader menu. The invalid-JSON, Automatic-restoration and iCalendar override cases pass together on Android 10 (41.453 seconds). Native control reacquisition remains bounded to three attempts.

## Limits

This batch does not add regex/cross-file search, general charset guessing, editing or new permissions. Source search compares Unicode code points without normalization or multi-character case expansions. Result lists are capped and disclose truncation. New source queries/options are held in memory only. Tab strips give way to the compact switcher above 130% system font scale.

Emulator validation does not claim physical-device coverage or human evaluation of screen-reader speech. No production key, release tag or public APK release is created.
