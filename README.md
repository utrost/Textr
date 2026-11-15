# Textr

Textr is a minimal, read-only text viewer for Android. Open files from other apps or the system file picker, revisit recent files, and switch between plain text, syntax highlighting, and supported formatted previews.

**Android 10+ · Offline · English and German · GPL-3.0-only**

**Status:** working development build, version 0.1.0. The source is public. There is no public APK or Play Store release yet; production signing and app publication remain deferred. You can [build and install it locally](#build-and-install).

| Markdown preview | JavaScript source | Optional wide-screen sidebar |
| --- | --- | --- |
| ![Markdown preview in Textr](docs/images/reading-markdown.png) | ![JavaScript source with syntax highlighting](docs/images/reading-javascript.png) | ![Textr with its optional file sidebar on a wide emulator window](docs/images/reading-wide.png) |

[More screenshots and capture details](docs/screenshots.md)

## What it does

- Opens text files up to **5 MiB** through Android’s file picker, **Open with**, or **Share**.
- Reads TXT, LOG, CONF/CFG, INI, JSON, Markdown, YAML, TOML, XML, CSV, Java, JavaScript, CSS, HTML, Kotlin, Python, shell, SQL, diff/patch, and iCalendar. Other text files can be opened through the picker.
- Offers plain text, lightweight syntax highlighting, and supported Markdown, JSON, calendar and optional CSV previews. Plain text remains the baseline.
- Searches the displayed view or the complete source, with case/whole-word matching, line numbers and snippets. Includes Go to line and a Markdown heading outline.
- Supports selection/copy, original-file sharing, read-only **Open with…**, adjustable text size, wrapping, and system/light/dark themes.
- Remembers recent files and approximate reading positions. Pin recents, keep up to five documents open, and save up to 100 named bookmarks.
- Offers an optional file sidebar on sufficiently large windows, plus keyboard shortcuts. The sidebar stays off until you enable it.
- Provides English and German controls, errors, preview labels and offline help, following the device/app language. Document contents are never translated.
- Shows reading, preparation and layout stages, with cancellation and explanations when loading takes longer.

Opening a format does not guarantee highlighting. Generic configuration files use plain text unless their syntax is recognized. HTML is displayed as source. CSV starts as text; table preview requires explicit delimiter/header choices. See [formats and limits](docs/formats.md).

## Open a file

Use **Open file** on the home screen, or choose Textr from another app’s **Open with** or **Share** action. Tap the display-mode button to change views; **⋮** contains file actions, reading settings, bookmarks and About.

**Android controls default-app associations.** Where Android offers **Always**, you can choose Textr. The available choices depend on the sending app and reported MIME type, not just the filename extension. Textr cannot force an association for every extension. The system file picker remains a fallback.

The empty home screen links to opening help. On eligible windows, a dismissible hint points to **Reading settings → File sidebar on wide screens**. Both hints stay dismissed; help and settings remain available from the menu.

[Installation and opening guide](docs/installing.md) · [Search and encodings](docs/everyday-reading.md) · [Documents and history](docs/open-documents.md) · [CSV, bookmarks and shortcuts](docs/reading-extras.md) · [Foldables](docs/foldable-layout.md)

## Privacy

No editing, accounts, ads, analytics, or app network permission. Documents are processed locally. External document providers may independently use the network, and links open externally only when tapped.

History, sessions and bookmarks store local references and reading metadata, not document contents. Explicit sharing creates temporary copies, bounded to 20 and cleaned after 24 hours on the next app start/share. These copies are separate from history. App backup and device-transfer rules exclude private state. JavaScript, remote resources and WebView file/content access are disabled.

Read the [privacy policy](docs/privacy.md), also linked from **About Textr**. Moving a file or losing its provider permission can make a stored reference unavailable; choose the file again. Clearing history or bookmarks never deletes the original files.

## Build and install

Requirements: **JDK 17**, Android SDK **Platform 36** and **Build Tools 36.0.0**. Set `ANDROID_HOME`, or put `sdk.dir` in an untracked `local.properties`. Android Studio can import the project. The included Gradle wrapper downloads dependencies on the first build.

```sh
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest :app:lintDebug
```

The APK is `app/build/outputs/apk/debug/app-debug.apk`. With an emulator or device connected:

```sh
adb install -r app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:connectedDebugAndroidTest
```

Use `gradlew.bat` on Windows. Debug builds use a development signing key and are not public release artifacts. CI builds debug APKs and runs tests on Android 10/API 29 and Android 16/API 36. See [development](docs/development.md), [validation](docs/validation.md), and [release signing](docs/releasing.md).

## Limits and release readiness

- Automatic decoding supports UTF-8 and BOM-marked UTF-16. Explicit encoding choices also support Windows-1252, ISO-8859-1 and UTF-16 without a BOM.
- Previews and highlighting have [bounded complexity and size](docs/formats.md); larger or invalid previews fall back to source. Large text files and long lines may display slowly.
- Markdown images, embedded HTML and relative file links are unsupported. Calendar recurrence is not expanded; no events or alarms are imported.
- Search is literal, within one file; source results are capped at 200. No editing, file management, unbounded tabs, or huge-log support.
- CSV cell accessibility is intermittently affected on WebView 133; [#59](https://github.com/utrost/Textr/issues/59) tracks investigation. Plain text remains available. Emulator checks do not replace human TalkBack or physical foldable testing.
- Public APK release, real-device beta and optional Play distribution have separate remaining gates in the [roadmap](docs/roadmap.md).

## Contribute and get help

[Report a reproducible problem or suggest a feature](https://github.com/utrost/Textr/issues). Include Android/WebView versions and a small synthetic sample when relevant; avoid private documents and credentials. Read [CONTRIBUTING.md](CONTRIBUTING.md) and [SECURITY.md](SECURITY.md).

Textr uses Kotlin, native Android controls, AndroidX Activity and an offline WebView. CommonMark handles Markdown; Jackson Core formats JSON while preserving numeric spelling and duplicate keys. The [scope](docs/scope.md) records product boundaries.

## License

GNU General Public License, version 3 only (`GPL-3.0-only`). See [LICENSE](LICENSE). Dependency notices are bundled in [Attributions.txt](app/src/main/assets/licenses/Attributions.txt) and **About Textr → Open-source licenses**.
