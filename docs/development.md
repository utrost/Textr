# Development

## Build

Use JDK 17, Android SDK Platform 36, and Build Tools 36.0.0. Configure `ANDROID_HOME` or an untracked `local.properties` containing `sdk.dir=/path/to/Android/Sdk`.

```sh
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

The Gradle wrapper pins version 9.3.1 and verifies its distribution checksum. Android Gradle Plugin 9.1.0 provides built-in Kotlin support. Production dependencies are pinned in `app/build.gradle.kts`. Initial dependency downloads require internet access on the build machine; the app has no network permission.

Install `app/build/outputs/apk/debug/app-debug.apk` with `adb install -r`. This is a development build using a local debug key. Owner-provided release signing is supported; see [release signing](releasing.md). No production signing credentials are committed.

## Structure

- `MainActivity` provides the home screen, reader controls, settings, file-picker integration, incoming intents, and background loading. AndroidX Activity handles results and back navigation. An in-memory ViewModel retains the current document through configuration changes.
- `DocumentRepository` reads Android content/file URIs using read-only descriptors and a cancellation signal. It records at most 30 recent URI/name/timestamp entries in private preferences, retaining read grants where offered and releasing them when entries are removed. It stores no document contents.
- `TextDocuments` enforces the 5 MiB byte limit while streaming, validates UTF-8 or BOM-marked UTF-16, rejects binary control characters, and classifies supported syntax from filenames or MIME types.
- `DocumentRenderer` and `SyntaxHighlighter` are plain Kotlin/JVM components. Previews never alter source. JSON formatting preserves duplicate keys and numeric spelling; malformed or overly nested JSON falls back to source. Highlighting accepts up to 512 Ki characters and stops adding spans after 10,000 tokens to bound the WebView node count.
- The WebView receives generated HTML through an intercepted in-memory HTTPS response, avoiding oversized data URLs. Controls become available after a visual-state callback confirms layout, and renderer termination offers a plain-text retry. The WebView receives generated HTML only. JavaScript, network loading, images, file access, content access, and DOM storage are disabled. A restrictive content security policy and request interception provide additional isolation. External web/email links require a user tap.

Reading preferences and recent metadata stay on the device and are excluded from backup/transfer rules. Source text is not written to saved-instance bundles or preferences. URI-based documents can be reloaded after process recreation when access remains valid; unreferenced shared text is session-only.

## Automated checks

JVM tests cover decoding, exact/over-limit sizes, unknown-length streams, file classification, invalid JSON, numeric fidelity, preview fallback, Markdown features, image/HTML exclusion, escaping, and bounded highlighting.

With an Android device or emulator connected:

```sh
./gradlew :app:connectedDebugAndroidTest
```

Device tests use a separate test-only content provider. They cover external file opening, Markdown/JSON rendering, search, recreation, unreferenced shared text, expired access, read-only descriptors, file size limits, history removal, MIME handlers, and absence of network/broad storage permissions. The test provider is not part of the application APK.

Reports are generated under `app/build/reports/tests/`, `app/build/reports/androidTests/`, and `app/build/reports/lint-results-debug.html`.

Validation uses clean Android 10, 15, and 16 emulators (API 29, 35, and 36). The owner explicitly selected emulator validation because no physical device is available. Physical-device coverage is follow-up work, not a release gate. CI runs build, unit tests, lint, and API 29/36 instrumentation for pull requests and the default branch.

## Manual acceptance checklist

- Open TXT, Markdown, JSON, and a generic `.conf` file through the system picker. Cancel the picker and confirm the existing screen remains usable.
- Open a file from a file manager and share an attachment from another app. Check Textr's default-handler option where the sending app offers it.
- Switch applicable display modes; verify invalid JSON remains readable. Check headings, tables, code blocks, and omission of images/HTML in Markdown.
- Search forwards/backwards and close search. Long-press to select/copy text. Change font size, wrapping, and system/light/dark appearance.
- Rotate the reader, then return home. Force-stop and relaunch the app; reopen a picker-selected recent file. Delete/move a file externally and verify the recovery message.
- Remove one recent entry and clear all history; confirm the actual documents remain intact.
- Check a 5 MiB file and a file one byte larger, binary data, malformed encoding, and provider access failure.
- Verify link taps open the external handler, with no automatic remote image or page loading.
- Check portrait/landscape, large system fonts, gesture navigation, and screen-reader labels on Android 10 and a current Android version before publishing a release.

## Release work

Use the [documented signing inputs and upgrade procedure](releasing.md), complete the compatibility checklist, and publish the corresponding source alongside any distributed GPLv3 APK. Keep keystores and local SDK paths out of Git. Store publication remains outside v1 scope.

The [release roadmap](roadmap.md) tracks this work through three milestones and individual GitHub issues with acceptance criteria and dependencies.
