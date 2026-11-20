# Textr 0.1-alpha — private testing

Release variant, version code 1, Android 10/API 29 or later. This is a draft prerelease for the repository owner, signed with the existing development identity, not a production signing key. Keep the draft unpublished for private testing. A future differently signed build requires uninstalling this alpha, removing its private settings/history.

Includes offline document reading, source search, encoding recovery, CSV preview, reading progress/bookmarks, keyboard shortcuts, optional wide-screen sidebar, loading/cancellation feedback, German UI/help, discovery hints, and the native accessible CSV table/source compatibility view.

The underlying WebView accessibility defect remains: use **More options → Accessible table… → Show source** when CSV cells or source are missing from accessibility navigation. Physical-device and human spoken-feedback review remain follow-ups.

The candidate passed 48 JVM tests, build/lint, 82 instrumentation tests on each of API 29 and API 36, and three additional API 36 checks with real TalkBack. See [reader-polish validation](validation-reader-polish.md). These tests used the debug variant; release packaging/signature/install evidence accompanies the draft assets.

Install `Textr-0.1-alpha.apk` after allowing installation from your download application. Verify its checksum against `SHA256SUMS`. The matching source archive and bundled dependency notices are included for GPLv3 compliance. Private keys and signing properties are never uploaded.

## Release verification — 18 November 2026

- `:app:assembleRelease :app:lintRelease` passed with `ANDROID_HOME=/tmp/textr-sdk` and `TEXTR_SIGNING_PROPERTIES` pointing to a private file outside the checkout (JDK 21.0.13, compile SDK 36, Java target 17).
- Build Tools 36.0.0 `apksigner verify --verbose --print-certs` passed (APK Signature Scheme v2). Certificate SHA-256: `35cb98bd5bb72616db4af92d9032865582c00b0b0b61143867f6c5394bbd7cb9`.
- APK SHA-256: `90e50f673e825289a710ea56570e7dadab7ff14eb293c492cb0d4459c13e7488`.
- Manifest/package checks: `de.utrost.textr`, `0.1-alpha`, code 1, minimum API 29, target API 36, not debuggable, no network or broad-storage permission. GPL and dependency notices are packaged.
- Android 16/API 36 emulator: clean release install and cold launch passed; `adb install -r` of the same signed APK passed and retained the dismissed opening hint. This checks replacement of this alpha, not a production-version upgrade.
- Source is tagged `v0.1-alpha`; exact commit and checksum-verified source archive are recorded in the draft release.
