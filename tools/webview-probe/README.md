# Android WebView bridge probe

A standalone developer fixture for [Textr #59](https://github.com/utrost/Textr/issues/59). It uses platform Activity/WebView and generated synthetic text/table documents. It imports no Textr reader code, opens no document providers, requests no network permission and disables JavaScript, remote loads and file/content access. The separate application ID is `de.utrost.textr.webviewprobe`.

The current fixture applies system-bar insets and relies on WebView's own accessibility notifications. A visual-state callback schedules page readiness; the probe then refreshes Android nodes for up to five seconds. Each iteration checks source, table, recreated table and returned source. Markers uniquely identify the current document. Any missing visible marker fails the complete run; the next independent transition is not a retry of the failed assertion. Up to five missing-node XML/PNG pairs remain in the probe app's cache.

## Build and run

Use the repository's pinned wrapper, JDK 17+ and Android SDK platform/Build Tools 36. Run from this directory:

```sh
../../gradlew --project-dir . :app:assembleDebug :app:assembleDebugAndroidTest
adb -s SERIAL install -r app/build/outputs/apk/debug/app-debug.apk
adb -s SERIAL install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb -s SERIAL shell am instrument -w -r -e iterations 30 \
  de.utrost.textr.webviewprobe.test/androidx.test.runner.AndroidJUnitRunner
adb -s SERIAL logcat -d -s WebViewProbe:I
```

`iterations` defaults to 30 and must be 1–100. Thirty iterations produce **120 observations in one instrumentation test**, not 120 separate test cases. Page readiness is bounded to 30 seconds and each node lookup to five seconds. The generated table has 26 body rows. No real file contents are involved. This project is independent of the normal Textr build and 164-case application matrix; compile it separately. Its instrumentation deliberately remains a diagnostic for the open framework/bridge issue.

Retrieve a captured failure using the sample/phase named in the failing assertion:

```sh
adb -s SERIAL exec-out run-as de.utrost.textr.webviewprobe \
  cat cache/probe-N-PHASE.xml > probe.xml
adb -s SERIAL exec-out run-as de.utrost.textr.webviewprobe \
  cat cache/probe-N-PHASE.png > probe.png
```

The probe uses UiAutomation with its ordinary service connection. It does not establish spoken TalkBack behavior. Test real speech/focus/navigation separately with Textr's native compatibility route and physical-device beta checklist. To remove this synthetic fixture, uninstall exactly `de.utrost.textr.webviewprobe.test` and `de.utrost.textr.webviewprobe`; the Textr application has a separate package.

## Recorded result — 22 November 2026

The current inset-adjusted fixture, without an extra app-generated content-change event:

| Emulator / provider | Observations | Missing | Instrumentation result |
| --- | ---: | ---: | --- |
| API 29 / WebView 74.0.3729.185 | 120 | 0 | Pass; 39.837 s |
| API 36 / WebView 133.0.6943.137 | 120 | 55 | Fail; 304.198 s |

A retained source screenshot visibly contains its unique marker while Android exposes an empty WebView body. Earlier variants also reproduce the omission: 65/120 without explicit insets and 12/40 with insets plus an extra content-change event. These samples show that Textr reader classes, missing system-bar padding and the extra event are not required for reproduction. They do not identify the responsible Android/WebView/UiAutomation component, demonstrate current-stable-provider behavior or establish human TalkBack acceptance. Keep #59 open.

The main Textr matrix retains its original browser text/selection/header assertions. This diagnostic does not replace them. No fix for #59 or passing current-provider survey is claimed.

Source code is covered by the repository's [GPL-3.0-only license](../../LICENSE). AndroidX test dependencies use the same versions as Textr's existing tests; this fixture adds no runtime service SDK.
