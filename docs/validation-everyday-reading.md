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

A local Android 16 combined run passed 25 feature, reader-control, session and large-font cases before the additional landscape regression was added. Initial narrow-screen testing also confirmed that the About help link remains accessible by scrolling at 200% fonts.

## Limits

This batch does not add regex/cross-file search, general charset guessing, editing or new permissions. Source search compares Unicode code points without normalization or multi-character case expansions. Result lists are capped and disclose truncation. New source queries/options are held in memory only. Tab strips give way to the compact switcher above 130% system font scale.

Emulator validation does not claim physical-device coverage or human evaluation of screen-reader speech. No production key, release tag or public APK release is created.
