# CSV accessibility investigation

[#59](https://github.com/utrost/Textr/issues/59) remains unresolved. Do not treat a passing source-fallback test or visible table as proof that TalkBack can navigate every CSV cell.

## Reproduction

The API 36 Google APIs emulator ships Android System WebView **133.0.6943.137** in the local validation environment. Previous #58 runs intermittently exposed empty rows or an empty WebView subtree through Android accessibility after document/mode/activity changes. The rendered table remained visible; Chromium’s browser-level accessibility tree contained cell names. The cause has not been established.

A standalone, script-free [HTML fixture](fixtures/csv-accessibility.html) preserves the table/header/scroll structure for browser comparison. It is not an Android lifecycle reproducer by itself. Use the instrumentation probe to exercise that lifecycle:

```sh
adb shell am instrument -w \
  -e class de.utrost.textr.ReleasePolishTest#csvSourceFallbackRemainsAccessibleAcrossPreviewChanges \
  de.utrost.textr.test/androidx.test.runner.AndroidJUnitRunner
adb logcat -d -s TextrCsvAccessibility
adb exec-out run-as de.utrost.textr cat cache/csv-accessibility-0.xml
```

The test uses synthetic CSV, toggles preview/source and recreates the activity three times. It records whether the preview cell appears and saves each Android hierarchy. It **asserts source accessibility, preserved contents and disabled JavaScript**, not successful preview cell navigation. The source fallback is the behavior this test guarantees.

## Investigation boundaries

Removing the nested scroll container, adding explicit table roles, adding cell focusability, refreshing accessibility nodes, clearing UiAutomation’s cache, and longer waits did not consistently resolve the earlier failure. Some wrapper labels collapsed descendants and were removed. The application publishes WebView accessibility only after the completed draw. No JavaScript bridge, network permission or unverified semantic workaround has been added.

Retest the same fixture and lifecycle on newer WebView versions and with an actual TalkBack service before concluding that this is a WebView-version defect. The available emulator version is not evidence about current stable WebView. Real spoken feedback and Magic V6 behavior belong to [#56’s beta](beta-checklist.md). Keep #59 open until cell-navigation evidence supports a fix or an accessible compatibility fallback.

## Current probe result

On 15 November 2026, the local API 36 / WebView 133.0.6943.137 probe exposed the target cell in all three iterations and passed source-fallback assertions. This small successful run does not disprove the previously intermittent failure or establish its cause. #59 remains open.
