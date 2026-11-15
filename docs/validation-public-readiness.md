# Public-readiness validation

Date: 15 November 2026. Covers #49, #54 and #55, repository documentation/screenshots, and preparatory portions of #53/#57/#59. Public binary release and production signing remain deferred.

## Local verification

- Debug app/test APK compilation, 47 JVM tests and Android lint pass with JDK 17 / SDK 36.
- The initial five `ReleasePolishTest` cases pass on API 36 (37.671 seconds) and API 29 (38.612 seconds). They cover stalled-read cancellation followed by a new file and stale callback, opening-help dismissal/recreation, wide-screen hint opt-in/dismissal, and German resources/preview labels/app language.
- A sixth regression checks full action/text visibility in narrow and wide dialogs at 200% system font, using German on API 36 and the system fallback on API 29. It passes on both (9.753 / 8.771 seconds). Visual review found a clipped uppercase German Cancel action; dialog actions now use normal case and the shorter German “Leseoptionen” title.
- A seventh regression cancels a 5 MiB escaped-text document during WebView layout and opens a new JSON file. It passes on API 36 / API 29 (4.099 / 3.253 seconds).
- The additional CSV probe records Android cell exposure and asserts accessible unchanged source across three preview/source/recreation cycles. The local API 36 probe exposed the target cell in all three iterations; it is not a claim that #59 is resolved.
- `bundleDebug` succeeds. `bundleRelease` fails clearly without signing inputs. A local release-bundle-path rehearsal using only the existing development key passes `jarsigner` verification. The output is retained locally as a development-only artifact, not a release candidate. No production key or store upload was created.
- Current [screenshots](screenshots.md) are actual emulator captures with verified synthetic document bytes. English/German home, Markdown, JavaScript, dark theme, German settings and optional wide sidebar were visually inspected.
- Existing-brand store artwork has editable SVG sources and 512×512 / 1024×500 PNG exports. Listing/Data safety documents are drafts for owner review.

## Full CI

[PR #60 checks](https://github.com/utrost/Textr/pull/60/checks) contain the full build/unit/lint and API 29/36 suite results and report artifacts for the candidate. The pull request records the accepted source commit and final totals; local checks above describe individual runs rather than substituting for the full CI suite.

## Regression scope

`ReleasePolishTest` supplements the existing rendering, provider, sharing, accessibility/layout, sessions, search and foldable tests. UI tests run with English by default and restore locale/window changes. The German resource check keeps source text intact while localizing only generated labels. The loading test waits for delayed provider text, cancels, opens another file and then allows the old provider to finish; cancelled source must not enter history or replace the new reader.

The screenshot tool passes synthetic text through a base64 test-provider operation and verifies returned source bytes before capture, avoiding Android shell-binding restrictions on punctuation/newlines.

## Full-suite findings

The first complete candidate run passed all 79 API 29 cases but exposed four API 36 failures: a stale native-menu snapshot and missing virtual nodes in link, selection and source-fallback checks. The reader now keeps WebView accessibility enabled through loading instead of disabling/re-enabling descendant exposure. The foldable test reacquires stale UiAutomator snapshots with a bounded retry; its behavioral assertions are unchanged. The focused Android 16 rerun passes all 21 foldable, offline-integrity, reader-UX and public-readiness cases together (147.996 seconds). A subsequent full local run still missed text nodes in two selection tests, and CI missed link/source nodes; the API 29 job also lost its emulator connection. Keeping the bridge enabled alone was insufficient. Loading controls now use a separate native panel, leaving the WebView visible while its virtual tree is built; restoration waits for a draw after the panel is removed and the viewport expands. That revision passes all 79 local API 36 tests and all 79 hosted API 29 tests, but hosted API 36 still missed a link node and a clipped About shortcut after the test scroll helper stopped. The completion event now explicitly identifies a changed accessibility subtree; the menu test targets native dialog scrolling and waits for updated nodes. Link lookup failures retain hierarchy diagnostics. Full-suite results for the final revision must be checked before merge. The intermittent WebView/TalkBack investigation remains open.

## Remaining limits

- #59: WebView 133 CSV virtual-cell accessibility remains unresolved. See [reproduction and source-fallback evidence](csv-accessibility.md). No speech/TalkBack success claim is made.
- #56: no physical device, provider beta, or actual Magic V6 fold/crease validation. [Checklist](beta-checklist.md).
- #53/#57: privacy text and About link are prepared; store developer identity/contact, final policy URL suitability, account declarations and signing choices need owner review. Public repository policy/contact uses the existing `utrost` handle and public project issues.
- #13: no public APK or production signing identity. Source-repository publication does not change that decision.
