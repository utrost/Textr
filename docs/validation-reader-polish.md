# Reader polish and validation fixes

Historical validation record for the batch below. For the current 0.2-alpha test matrix and release evidence, see [roadmap implementation validation](validation-roadmap-implementation.md). Earlier failures and test counts are retained as recorded.

Date: 18 November 2026. This local follow-up builds on draft PR #60 for #49, #54 and #55 and implements the #59 compatibility fallback and #61 test-activation fix. Public release remains deferred.

## Resulting behavior

- Loading distinguishes reading, preparation and WebView layout, with delayed explanations and Cancel/Open file actions. Generation guards reject cancelled provider results and obsolete callbacks.
- English/German UI, offline help, accessible labels and dismissible opening/sidebar hints are retained from #60. Generated CSV headings are translated too; supplied headers and document contents remain unchanged.
- **CSV → More options → Accessible table…** opens a native compatibility view. Its selectable cells expose table dimensions, row/column indices and heading semantics independently of WebView. **Show source** exposes the complete decoded CSV through a selectable native text widget. Delimiter/header choices and all existing CSV budgets apply.
- Compatibility search reports matching cells in table view and up to 200 literal source matches in source view, with queries bounded to 1,000 UTF-16 units. Search, view choice and approximate scroll offsets survive activity recreation. **Share file** uses the original bytes, including BOM and CRLF, with read-only grants.
- Native rows are constructed in batches of at most 100 cells. Cancel remains available at the 10,000-cell limit, and queued construction stops when the dialog closes. Full-width actions and scrollable controls support large fonts and short windows.
- Native source/cell widgets disable Android text-state saving and autofill participation. Saved state contains only URI, search and viewport metadata; no document text. Dismissed or replaced-document share callbacks are ignored.
- Mode/settings/source-navigation renders reuse the attached WebView. Unique intercepted URLs and generation checks protect visual completion and position restoration. Old pages are removed from navigation history. Home, a different document and renderer recovery dispose of the old reader.

## Android 10 readiness diagnosis (#61)

The original [CI report](https://github.com/utrost/Textr/actions/runs/43719037135) logs a click at 13:38:19.869 on a non-clickable TextView whose current text is **Textr**, followed by the readiness timeout. `ReaderUxTest.recentReopenAndNewActivityRestorePositionAndMode` had matched the old `long.ini` document title while returning home. The recent-file button was never activated.

The test now targets the clickable Button named `long.ini`. Its original readiness deadline, remembered-mode checks and exact saved-position assertions remain. No timeout was extended to conceal a loading failure.

## WebView diagnosis and compatibility scope (#59)

Both the original hosted run and local full runs reproduce missing Android accessibility descendants with WebView 133. The installed TalkBack 16.0.0.738667889 service also reproduced a table containing headers and second-row cells but no first-row cell descendants.

Reusing the reader, restoring focus, changing header borders and HTML autofocus did not reliably fix the full sequence. Focus/border experiments were removed. An intermediate full run passed all 80 API 29 cases and 78/80 API 36 cases; another passed 80/80 and 79/80. These failures motivated a native compatibility view rather than a claim that the WebView defect is fixed.

Chromium's [Android accessibility architecture](https://chromium.googlesource.com/chromium/src/+/main/docs/accessibility/browser/android.md) documents its own node cache and structural/focus invalidations. Stale Chromium node publication remains a working explanation, not an established universal cause. The [standalone semantic fixture](fixtures/csv-accessibility.html), browser-cell exposure logs and hierarchy captures remain available for upstream/version comparison.

`csvSourceFallbackRemainsAccessibleAcrossPreviewChanges` still records browser-cell exposure and asserts unchanged, accessible source and disabled JavaScript across three preview/source/recreation cycles. Its source assertion now follows the explicit **Accessible table → Show source** compatibility route. It does not claim the WebView source subtree is reliable.

`accessibleCsvCellsKeepTableSemanticsAndFocusAcrossModeChanges` exercises native cell nodes, table metadata, source access and dialog restoration across four cycles. Standard runs verify native focus actions; the TalkBack run additionally requires real focus on each cell, a bound TalkBack service and active touch exploration. Other cases cover offscreen search, exact cell copying, header/delimiter choices, native source/query restoration, absent document text in saved hierarchy state, original BOM/CRLF sharing, and cancellation at the cell budget. Existing browser selection, scrolling, sticky-header, source-integrity and permission tests remain in the full suite.

## Verification

The matrix uses isolated Pixel 2 emulators with hardware keyboard enabled and animations disabled:

| Platform | WebView | Final full suite |
| --- | --- | --- |
| Android 10 / API 29 | 74.0.3729.185 | 82 passing; zero failures/errors/skips |
| Android 16 / API 36 | 133.0.6943.137 | 82 passing; zero failures/errors/skips |

Debug/instrumentation APK builds, all 48 JVM tests and lint pass with OpenJDK 21.0.13, compile SDK 36 and Build Tools 36.0.0, targeting Java 17 bytecode. The complete combined build/unit/lint/device run finished successfully in 11 minutes 51 seconds. JUnit XML confirms 82/82 on each platform and 48/48 JVM cases, with zero failures, errors or skips.

The same application APK also passes three API 36 acceptance cases with installed TalkBack 16.0.0.738667889 bound and touch exploration enabled (90.514 seconds): four native-cell/source/recreation cycles, the three-cycle source-fallback probe, and the existing reader/history/recovery accessibility navigation test. The final cancellation regression passes on API 29 and API 36 (4.498 / 5.178 seconds). Compact/wide dialog actions and native cells at 200% text, German on API 36 and the system fallback on API 29, pass in the full matrix. Read-only screenshots of the compatibility source view were visually inspected for content and action placement above navigation bars.

The candidate is based on PR #60 head `915e323877e066e3ab70c0b524a94b4f74c4f43e` plus this local follow-up. Exact artifact identities:

- Sorted `app/src` path-and-content SHA-256: `300e3c103702b9cc45d29dbc8e95ff9cb1450d54b705e7afbc63fa903ba233fa` (each path, NUL, file contents, NUL).
- Debug app APK: `5a11add3ca8484a61751ad738091418559d072ff8b050ba200da739869309420`.
- Instrumentation APK: `c2ac7e6f739a7c95fa9e69bdec25483682b5ee983a92dcaf3ff17f2dda1b617d`.

Reproduce the complete matrix with both emulators connected:

```sh
./gradlew :app:assembleDebug :app:assembleDebugAndroidTest \
  :app:testDebugUnitTest :app:lintDebug :app:connectedDebugAndroidTest
```

Reports: `app/build/reports/tests/`, `app/build/reports/androidTests/connected/`, `app/build/outputs/androidTest-results/connected/`, and `app/build/reports/lint-results-debug.html`.

For TalkBack acceptance, enable the installed service on a test device, then run:

```sh
adb shell am instrument -w -e talkback true \
  -e class de.utrost.textr.ReleasePolishTest#accessibleCsvCellsKeepTableSemanticsAndFocusAcrossModeChanges \
  de.utrost.textr.test/androidx.test.runner.AndroidJUnitRunner
```

This opt-in mode keeps accessibility services running through UiAutomation and fails if TalkBack is unavailable or touch exploration never becomes active. Human speech review, gesture-navigation quality, physical hardware and current stable WebView remain separate coverage. Hosted CI and merge acceptance remain pending until this local candidate is uploaded and checked.
