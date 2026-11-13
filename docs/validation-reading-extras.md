# CSV, bookmarks and keyboard validation

Issues #44–#46 are implemented in [PR #58](https://github.com/utrost/Textr/pull/58). Public release and production signing remain deferred.

## Local checks

Debug APK and test APK builds, all **47 JVM tests**, and Android lint pass with JDK 17 and compile SDK 36. The seven new parser tests cover quoting, embedded line endings, explicit delimiters, ragged/empty fields, every preview budget, hostile HTML/formulas, source preservation and cancellation. Existing format detection now recognizes CSV without changing the text-first default.

The eight new instrumented acceptance cases pass on **API 29 and API 36** emulators configured with `hw.keyboard=yes`:

- CSV options and mode restoration after clearing the retained ViewModel, escaped cells, disabled JavaScript, and byte-for-byte sharing including BOM/CRLF.
- Native long-press selection and clipboard copying from a CSV cell.
- Real vertical/horizontal gestures, sticky header paint and changing rows/columns. The test compares foreground strokes with small rasterization tolerance because WebView accessibility can report the original, offscreen bounds of sticky headers. Cell accessibility and selection are checked separately.
- Independent bookmark URI-grant ownership after clearing history and sessions, a 100-bookmark limit, metadata persistence, rename/removal and last-owner cleanup.
- Bookmark navigation, source fingerprints without content persistence, changed/shorter files, unavailable references, canceled/failed/successful picker-result handling and reconnection. Android instrumentation supplies picker results for these cases; the existing reader-control suite separately exercises the actual system picker.
- Real modifier-key injection for Find/Open/Go to line/document switching, text-field/dialog guards, unavailable preview actions, and shortcut-help registration.
- CSV and bookmark controls, accessible names and rename input at 200% system text size in landscape.
- Progress reaches 100% at the native scroll boundary and remains the same height when its text crosses a wrapping boundary.

The reader exposes its virtual accessibility tree after the completed page draw, together with removal of the loading indicator. This prevents accessibility clients from inspecting partially constructed table rows. The CSV checks refresh virtual nodes while waiting for visible cells and retain real gesture, clipboard and painted-header assertions.

## Full regression coverage

The PR's **Android checks** workflow runs build/JVM/lint checks plus the complete **72-case instrumented suite** on API 29 and API 36. The final CI run and downloaded report counts are recorded on PR #58; use those checks for the merged candidate rather than earlier superseded runs.

No physical-device, human TalkBack speech review or public-store submission is claimed. These follow-ups are tracked in #56 and #57. No network permission, renderer JavaScript, document writes, production keystore or public release artifact is introduced.
