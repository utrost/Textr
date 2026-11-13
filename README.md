# Textr

Textr is a minimal, read-only text viewer for Android. Open files from other apps or the system file picker, revisit recent files, and switch between plain text, syntax highlighting, and supported formatted previews.

**Status: early development build (0.1.0).** The Android app is implemented and can be built locally. Public release and production signing are deferred; no public APK is available.

## Current features

- Android 10 and newer.
- Everyday text files up to 5 MiB (5,242,880 bytes).
- TXT, LOG, CONF/CFG, INI, JSON, Markdown, YAML, TOML, XML, CSV, Java, JavaScript, CSS, HTML, Kotlin, Python, shell, SQL, diff/patch, and iCalendar, with other text files available through the system file picker.
- Automatic display selection: rendered Markdown, formatted JSON, supported iCalendar event summaries, syntax-highlighted source for recognized syntax, and plain text otherwise.
- Manual switching between applicable display modes, with plain-text source always available for supported text files.
- Search the displayed view, or search source with case/whole-word options, snippets and line-number results.
- Text selection, copying and sharing, adjustable font size, line wrapping, and light/dark themes.
- Share original files through Android; reopen recent files at their last position and display mode.
- Document information and a remembered **Treat as…** format override for misidentified or generic files.
- Manual reload, source line numbers and Go to line, and a Markdown heading outline.
- A five-document switcher with active-document labels and a tab strip on wide screens, pinned recents with Undo, and read-only **Open with…** handoff.
- An optional [file sidebar for foldable and tablet screens](docs/foldable-layout.md), with automatic compact-window fallback.
- Per-file encoding overrides, encoding/line information, and offline help for opening files and Android defaults.
- Optional CSV tables, approximate reading progress, up to 100 named bookmarks, and hardware keyboard shortcuts. See [reading extras](docs/reading-extras.md).
- A minimal home screen with an **Open file** action and recent files. Remove individual entries or clear the history.

Opening a format does not guarantee syntax highlighting. Basic highlighting covers JSON, Markdown source, INI, YAML, TOML, XML, and the [additional source formats](docs/formats.md). Generic configuration files use plain text unless the provider identifies a supported syntax. CSV opens as text, with an optional bounded table preview and explicit delimiter/header choices.

Markdown previews support headings, lists, links, quotes, code blocks, and tables. Images are replaced with an omission marker; embedded HTML is removed. Web and email links open in an external app only when tapped. JSON previews add indentation without modifying the file; invalid JSON remains readable as source.

iCalendar previews summarize events with explicit date/time-zone labels and unexpanded recurrence metadata. Unsupported calendars remain readable as source. HTML stays an escaped source view. See [format behavior and limits](docs/formats.md).

## Opening files and choosing a default

See the [installation and default-app guide](docs/installing.md) for screenshots and step-by-step instructions.

Use **Open file** to select a document through Android's system picker, or choose Textr in another app's **Open with** or **Share** menu. Shared text can also be read; text without a file reference is not added to recent history. Long-press document text to select, copy, or share it. The **⋮** menu offers file sharing, document information, reading settings, and About. Choose **Treat as…** in the reader menu or Document info to override syntax detection; **Automatic** restores normal detection. **Find** searches the currently displayed view. Its **⋯** button opens **Search source…**, with case/whole-word options and results that jump to plain text. **Document info → Reopen with encoding…** handles older encodings. [Search, encoding and navigation details](docs/everyday-reading.md).

Android's default-app behavior depends on the sending app and the reported file type, including its MIME type. Where Android offers the choice, you can select Textr as the default handler. Textr cannot guarantee a default association for every filename extension in every app. The file picker provides another way to select text files.

The last 30 recent files store references and metadata locally, including the last display mode, scroll position, and optional format override, without retaining document contents. Positions are approximate when the file or text layout changes. Removing an entry offers ten seconds of Undo and then removes its history preferences. Open-document state is separate; clearing history leaves open documents and bookmarks available. Reopening depends on continued access: moved, deleted, or temporarily shared files may become unavailable. Textr prompts you to reopen unavailable files through the picker. Removing a recent entry or clearing history does not delete the underlying files.

Textr also handles generic `application/octet-stream` requests because some apps report configuration files that way. This can make it appear for binary files too; unsupported binary, oversized, and unreadable files produce an explanatory message.

See Android's documentation on [intents and file-type matching](https://developer.android.com/guide/components/intents-filters) and [document access permissions](https://developer.android.com/training/data-storage/shared/documents-files) for platform details.

## Privacy and boundaries

Textr is read-only, with no editing, accounts, ads, analytics, or network permission. External document providers used through the system picker may independently use network access. Document rendering disables JavaScript, network loads, images, and WebView file/content access. History, open-document references, bookmarks, and settings are excluded from app backups. Explicit **Share file…** or **Open with…** creates a temporary copy of the original bytes with read-only access for the receiving app. Copies are separate from history, limited to the latest 20, and cleaned on the next app start or share after 24 hours. Short incoming shared text is sent as text; larger shared text is offered as a UTF-8 file. Merely viewing a document creates no sharing copy. Textr opts out of WebView usage metrics. Android/WebView crash diagnostics remain governed by the device and provider settings; see [Android’s WebView privacy documentation](https://developer.android.com/develop/ui/views/layout/webapps/webview-privacy).

File management, unbounded tab sessions, huge-log support, direct cloud integrations, and store publication are outside the first version. See the [v1 scope](docs/scope.md) for the agreed requirements and acceptance criteria, and the [roadmap](docs/roadmap.md) for milestones and individual GitHub issues leading to the first public APK.

See [open documents and navigation](docs/open-documents.md) for session limits, grant cleanup, pinning, Undo, line-number limits, and external handoff behavior.

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

On Windows, use `gradlew.bat`. Debug APKs use a local development key; public release is deferred; [release signing support](docs/releasing.md) is available. No signing keys are committed.

The app uses Kotlin, AndroidX Activity, native Android controls, and an offline WebView. CommonMark handles Markdown and tables; Jackson Core formats JSON while preserving number spelling and duplicate keys. See [development and testing](docs/development.md) for architecture and checks, and the [validation record](docs/validation.md) for tested configurations and limitations.

## Current limits

Files near the size limit, especially long unbroken lines, can render slowly on older WebViews. **Back to files** and **Open file** remain available during loading. See the [measured emulator results](docs/validation.md).

- Automatic decoding supports UTF-8, UTF-8 with a BOM, and UTF-16 LE/BE with a BOM. Explicit per-file choices add Windows-1252, ISO-8859-1 and UTF-16 LE/BE without a BOM. Malformed text and conflicting BOMs are rejected.
- Highlighting is lightweight rather than a complete language parser. It is available for sources up to 512 Ki characters and decorates up to 10,000 tokens; remaining source stays readable without colors.
- JSON preview accepts files up to 1 MiB and has a nesting limit of 128 and a formatted-output limit of 5 Mi characters. Documents exceeding preview limits remain available as source.
- iCalendar event previews accept up to 512 Ki characters and 200 events, with bounded lines, nesting, and output. No recurrence expansion, time-zone conversion, calendar import, or alarms.
- Markdown preview accepts up to 512 Ki characters, 20,000 nodes, and nesting depth 64; larger or more complex documents remain readable as source.
- Markdown images, embedded HTML, and relative file links are not supported. Web links and email links require an external handler.
- Find searches visible text, so preview and source results can differ. Source search shows up to 200 literal matches for queries up to 1,000 UTF-16 code units; no regex or cross-file search. Shared text without a URI survives rotation but is not restored after process termination.

## License

Textr is licensed under the GNU General Public License, version 3 only (`GPL-3.0-only`). See [LICENSE](LICENSE) for the full text.

Dependency notices are bundled under [licenses](app/src/main/assets/licenses/Attributions.txt) and available in the app through **About Textr → Open-source licenses**.
