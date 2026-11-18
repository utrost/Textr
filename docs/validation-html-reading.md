# HTML reading-view validation

Date: 18 November 2026. Candidate branch: `feat/html-markdown`. Implements [#68](https://github.com/utrost/Textr/issues/68) and [#69](https://github.com/utrost/Textr/issues/69), based on the private alpha/reader-validation candidate. The published draft `v0.1-alpha` APK is unchanged.

## Behavior checked

- Local HTML stays source-first and offers an explicit English/German Markdown reading view. The original source, highlighting and read-only original-byte sharing remain available.
- Tolerant HTML parsing and Markdown conversion cover headings/outline, emphasis, lists, blockquotes, code and simple tables. Tests distinguish literal Markdown/HTML/entity text from generated structure and preserve Unicode, code blank lines and table pipes.
- Scripts, styles, embedded resources, forms and unsafe/relative links never enter the generated preview. The actual APK still has no network permission; WebView JavaScript, network/image loading, file/content access and DOM storage stay disabled.
- HTML VIEW/SEND attachments and text/html, application/xhtml+xml or explicit EXTRA_HTML_TEXT shares are recognized. Plain shared URLs remain text; shared content stays outside recent-file history. Reading-mode restoration, displayed search, hidden script text, converted heading navigation and recreation are exercised on devices.
- Input/DOM/depth/table/Markdown limits fall back to complete source. Tests cover malformed HTML, complex tables, exact/over-size inputs, output expansion, pre-cancelled work and cancellation during conversion.
- MIT attribution/license for jsoup is bundled and listed by the existing About license browser. Documentation and resource XML checks pass.

## Environment and commands

Host: OpenJDK 21.0.13 (Java target 17), Gradle 9.3.1, AGP 9.1.0, SDK 36 / Build Tools 36.0.0. Runtime parser: jsoup 1.24.1. Google APIs x86_64 emulators: Android 10/API 29 with WebView 74.0.3729.185; Android 16/API 36 with WebView 133.0.6943.137.

```sh
ANDROID_HOME=/tmp/textr-sdk ./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=de.utrost.textr.HtmlReadingDeviceTest,de.utrost.textr.AdditionalFormatsDeviceTest
ANDROID_HOME=/tmp/textr-sdk ./gradlew :app:assembleDebug :app:assembleDebugAndroidTest \
  :app:testDebugUnitTest :app:lintDebug :app:connectedDebugAndroidTest
```

Focused run: **6/6 passed on each of API 29 and API 36**, zero failures/skips. That run preceded the final whitespace/cancellation/help refinement; the complete run below rebuilds and covers the final source. Host log: `/tmp/textr-html-device-focused.log`.

Complete initial run: **85/85 passed on API 29; 84/85 passed on API 36**, no errors/skips. API 36 failed the existing `ReleasePolishTest.largeTextDialogActionsStayFullyVisibleOnCompactAndWideWindows`: its immediate `WindowInspector` lookup found no shown positive-button window. The test now waits for an attached, laid-out, nonzero-size settings action before executing the original visibility, exact clipping and text-fit assertions. No timeout was increased and no production/UI code changed for this correction. Initial reports are preserved at `/tmp/textr-html-full-results/`; log: `/tmp/textr-html-final.log`.

Final confirmation: **build, 59 JVM tests, lint, and 7/7 device cases on each API 29/36 passed**, zero failures/errors/skips, in 2m 47s. This reran the corrected large-text test (settings and native CSV at compact/wide sizes and 200% text), all three HTML tests and all three existing format tests on the final source. The remaining 84 full-suite cases had already passed on each platform against the byte-identical application APK. The whole 85-case matrix was not repeated after this test-only synchronization correction. Log: `/tmp/textr-html-confirmation.log`.

```sh
ANDROID_HOME=/tmp/textr-sdk ./gradlew :app:assembleDebug :app:assembleDebugAndroidTest \
  :app:testDebugUnitTest :app:lintDebug :app:connectedDebugAndroidTest \
  '-Pandroid.testInstrumentationRunnerArguments.class=de.utrost.textr.HtmlReadingDeviceTest,de.utrost.textr.AdditionalFormatsDeviceTest,de.utrost.textr.ReleasePolishTest#largeTextDialogActionsStayFullyVisibleOnCompactAndWideWindows'
```

Final Reports: `app/build/test-results/testDebugUnitTest/`, `app/build/reports/tests/testDebugUnitTest/`, `app/build/reports/lint-results-debug.html`, `app/build/outputs/androidTest-results/connected/debug/`, `app/build/reports/androidTests/connected/debug/`.

## Exact tested inputs

- `app/src` SHA-256: `79f1dd0d155e5fdb9c0007a962de37dd488f847a332c466b050f9ffdfc927411`.
- Debug application APK SHA-256: `e48c27ace2d8a373936a2ff98f6a21ab4423fa8decf9db9bf0e0bd96f5299bcf`.
- Debug test APK SHA-256: `e386d68776d3629de9a4fb66c6c7b51875b93ffce1ef9b7ddaaf0bc9cfb2776b`.

Initial full-run source SHA-256 before the test synchronization change: `ed5d7174f1238be91efe62f430c42a3c47d79f05327d13fd83799c60d690d695`; its test APK: `db037d0412396e9edcfc16c1a29397eaf584c9f59da329520080810ceb0b9a37`. The application APK hash above is identical for both runs.

Source hash algorithm: sort all file paths below `app/src`; hash each relative UTF-8 path, NUL, file bytes, NUL. This excludes generated build output and documentation. Dependency versions are pinned in `app/build.gradle.kts`; APK hashes identify the installed dependency/resource packages.

## Limits of this evidence

This is debug-variant emulator coverage, not a new signed release or store submission. No live website is fetched, no generated-Markdown export is added, and raw embedded HTML in ordinary Markdown remains excluded. Complex HTML tables and pages whose readable text depends on scripts may require the original source view. Existing #59 WebView accessibility limitations remain; this change does not claim to fix the vendor bridge. Physical-device and human spoken-feedback review remain #56 follow-ups. Hosted CI/merge acceptance is separate from these local results.
