# Textr

Textr is a minimal, read-only text viewer for Android. Open files from other apps or the system file picker, revisit recent files, and switch between plain text, syntax highlighting, and supported formatted previews.

**Status: early development build (0.1.0).** The Android app is implemented and can be built locally. There is no published or release-signed APK yet.

## Current features

- Android 10 and newer.
- Everyday text files up to 5 MiB (5,242,880 bytes).
- TXT, LOG, CONF/CFG, INI, JSON, Markdown, YAML, TOML, XML, and CSV, with other text files available through the system file picker.
- Automatic display selection: rendered Markdown, formatted JSON, syntax-highlighted source for recognized syntax, and plain text otherwise.
- Manual switching between applicable display modes, with plain-text source always available for supported text files.
- Search, text selection and copying, adjustable font size, line wrapping, and light/dark themes.
- A minimal home screen with an **Open file** action and recent files. Remove individual entries or clear the history.

Opening a format does not guarantee syntax highlighting. Basic highlighting covers JSON, Markdown source, INI, YAML, TOML, and XML. Generic configuration files use plain text unless the provider identifies a supported syntax. CSV stays a text view.

Markdown previews support headings, lists, links, quotes, code blocks, and tables. Images are replaced with an omission marker; embedded HTML is removed. Web and email links open in an external app only when tapped. JSON previews add indentation without modifying the file; invalid JSON remains readable as source.

## Opening files and choosing a default

Use **Open file** to select a document through Android's system picker, or choose Textr in another app's **Open with** or **Share** menu. Shared text can also be read; text without a file reference is not added to recent history. Long-press document text to select and copy it. **Find** searches the currently displayed view.

Android's default-app behavior depends on the sending app and the reported file type, including its MIME type. Where Android offers the choice, you can select Textr as the default handler. Textr cannot guarantee a default association for every filename extension in every app. The file picker provides another way to select text files.

The last 30 recent files store references and metadata locally, without retaining document contents. Reopening depends on continued access: moved, deleted, or temporarily shared files may become unavailable. Textr prompts you to reopen unavailable files through the picker. Removing a recent entry or clearing history does not delete the underlying files.

Textr also handles generic `application/octet-stream` requests because some apps report configuration files that way. This can make it appear for binary files too; unsupported binary, oversized, and unreadable files produce an explanatory message.

See Android's documentation on [intents and file-type matching](https://developer.android.com/guide/components/intents-filters) and [document access permissions](https://developer.android.com/training/data-storage/shared/documents-files) for platform details.

## Privacy and boundaries

Textr is read-only, with no editing, accounts, ads, analytics, or network permission. External document providers used through the system picker may independently use network access. Document rendering disables JavaScript, network loads, images, and WebView file/content access. History and settings are excluded from app backups.

File management, tabs, huge-log support, direct cloud integrations, and store publication are outside the first version. See the [v1 scope](docs/scope.md) for the agreed requirements and acceptance criteria, and the [roadmap](docs/roadmap.md) for milestones and individual GitHub issues leading to the first public APK.

## Installation and development

Requirements: JDK 17, Android SDK Platform 36, and SDK Build Tools 36.0.0. Set `ANDROID_HOME` to your SDK directory, or set `sdk.dir` in an untracked `local.properties`. Android Studio can import the project and configure the SDK path. The included Gradle 9.3.1 wrapper downloads dependencies on the first build.

```sh
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest :app:lintDebug
```

The debug APK is generated at `app/build/outputs/apk/debug/app-debug.apk`. With a device or emulator connected through ADB:

```sh
adb install -r app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:connectedDebugAndroidTest
```

On Windows, use `gradlew.bat`. Debug APKs use a local development key; downloadable release APKs and release signing remain future work. No signing keys are committed.

The app uses Kotlin, AndroidX Activity, native Android controls, and an offline WebView. CommonMark handles Markdown and tables; Jackson Core formats JSON while preserving number spelling and duplicate keys. See [development and testing](docs/development.md) for architecture, checks, and manual test scenarios.

## Current limits

- Text decoding supports UTF-8, UTF-8 with a BOM, and UTF-16 LE/BE with a BOM. Other encodings produce an explanatory message.
- Highlighting is lightweight rather than a complete language parser. It decorates up to 50,000 tokens; remaining source stays readable without colors.
- JSON preview has a nesting limit of 128 and a formatted-output limit of 20 MiB. Documents exceeding preview limits remain available as source.
- Markdown images, embedded HTML, and relative file links are not supported. Web links and email links require an external handler.
- Search operates on visible text, so preview and source searches can differ. Shared text without a URI survives rotation but is not restored after process termination.

## License

Textr is licensed under the GNU General Public License, version 3 only (`GPL-3.0-only`). See [LICENSE](LICENSE) for the full text.

Dependency notices are bundled under [licenses](app/src/main/assets/licenses/Attributions.txt) and available in the app through **About Textr → Open-source licenses**.
