# Public-readiness validation

Date: 15 November 2026. Covers #49, #54 and #55, repository documentation/screenshots, and preparatory portions of #53/#57/#59. Public binary release and production signing remain deferred.

## Local verification

- Debug app/test APK compilation, 47 JVM tests and Android lint pass with JDK 17 / SDK 36.
- All five `ReleasePolishTest` cases pass on API 36 (37.671 seconds). They cover stalled-read cancellation followed by a new file and stale callback, opening-help dismissal/recreation, wide-screen hint opt-in/dismissal, and German resources/preview labels/app language.
- The additional CSV probe records Android cell exposure and asserts accessible unchanged source across three preview/source/recreation cycles. The local API 36 probe exposed the target cell in all three iterations; it is not a claim that #59 is resolved.
- `bundleDebug` succeeds. `bundleRelease` fails clearly without signing inputs. A local release-bundle-path rehearsal using only the existing development key passes `jarsigner` verification. The output is retained locally as a development-only artifact, not a release candidate. No production key or store upload was created.
- Current [screenshots](screenshots.md) are actual emulator captures with verified synthetic document bytes. English/German home, Markdown, JavaScript, dark theme, German settings and optional wide sidebar were visually inspected.
- Existing-brand store artwork has editable SVG sources and 512×512 / 1024×500 PNG exports. Listing/Data safety documents are drafts for owner review.

## Regression scope

`ReleasePolishTest` supplements the existing rendering, provider, sharing, accessibility/layout, sessions, search and foldable tests. UI tests run with English by default and restore locale/window changes. The German resource check keeps source text intact while localizing only generated labels. The loading test waits for delayed provider text, cancels, opens another file and then allows the old provider to finish; cancelled source must not enter history or replace the new reader.

Build/test failures during development are not acceptance evidence. An initial test used ActivityScenario cleanup after replacing its launch intent, causing a lifecycle timeout; it now uses the same explicit activity lifecycle pattern as the existing stale-load test. A screenshot shell-binding failure was fixed by base64 transport in the test-only fixture provider and verification of the returned source bytes before capture.

## Remaining limits

- #59: WebView 133 CSV virtual-cell accessibility remains unresolved. See [reproduction and source-fallback evidence](csv-accessibility.md). No speech/TalkBack success claim is made.
- #56: no physical device, provider beta, or actual Magic V6 fold/crease validation. [Checklist](beta-checklist.md).
- #53/#57: privacy text and About link are prepared; store developer identity/contact, final policy URL suitability, account declarations and signing choices need owner review. Public repository policy/contact uses the existing `utrost` handle and public project issues.
- #13: no public APK or production signing identity. Source-repository publication does not change that decision.
