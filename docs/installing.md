# Installing and opening files

Textr supports Android 10 (API 29) and newer. Public release is deferred; no public APK is currently available. Use the [development build instructions](development.md) to build and install the candidate. CI debug artifacts are development builds. Future authorized releases will appear under [GitHub Releases](https://github.com/utrost/Textr/releases).

## Install a future release APK

1. Download the APK and checksum from the same release. Use the release's source tag and signing-certificate fingerprint to identify that version.
2. On a computer, compare `sha256sum Textr-VERSION.apk` with the published checksum. On Windows, use `Get-FileHash Textr-VERSION.apk -Algorithm SHA256` in PowerShell.
3. Open the APK on Android. If prompted, allow **Install unknown apps** for the browser or file manager you used, then install Textr. You can turn that permission off afterward. Android's exact wording varies by device.
4. Open Textr and tap **Open file**. Pick a text document using the system file picker.

A later APK with the same application ID and signing key installs as an update and preserves Textr's settings and recent references. A local debug build usually has a different key; Android will reject that update. Removing a debug installation first removes its private settings/history, while the original documents remain in their provider.

## Choose Textr as a default

In a file manager, tap a text file or choose **Open with**. If Android offers a chooser, select **Textr**, then **Always**. **Just once** leaves the current default unchanged. Some apps supply their own chooser or preview and never offer **Always**.

![Android's Open with chooser showing Textr, Just once, and Always](images/open-with.png)

Default choices depend on the incoming intent and MIME type, not simply the extension. A default for `application/json` does not necessarily set the default for TXT, Markdown, or a generic configuration file. To change an existing default, open Android **Settings → Apps → the current app → Open by default** and clear its defaults where available; labels vary by Android version and vendor.

If Textr is absent, open Textr directly and use **Open file**, or try the sender's **Share** action. Textr accepts text, supported application MIME types, and generic binary MIME requests, then validates the actual bytes. Binary files are rejected with a readable message. A sender that omits or mislabels its file type can still prevent an Android chooser association; no app can promise universal extension defaults.

## Read and revisit

| Home in the light theme | Markdown in the dark theme |
| --- | --- |
| ![Textr home with a recent file](images/home-light.png) | ![Markdown preview with a table and omitted-image marker](images/markdown-dark.png) |

- Tap the current display-mode button to switch between plain text, highlighted source, and available preview modes. The selector is hidden when only plain text applies. To interpret a generic configuration file, use **⋮ → Document info → Treat as…**; select **Automatic** to reset.
- **Find** searches the displayed view. Use the arrows to move through matches and **×** to close search. Source and preview searches may differ.
- Long-press text to select it, then choose Android's **Copy** or **Share** action (sometimes under the selection toolbar’s overflow).
- Open **⋮ → Reading settings** for text size, line wrapping, optional source line numbers, and system/light/dark appearance. **Go to line…** uses logical source lines even when wrapping. Numbering is bounded to 10,000 lines; jumps work throughout the supported source.
- The **Back to files** arrow returns to the last 30 successfully opened file references. Reopening restores the last position, display mode, and format override. The star pins an entry (up to ten). **×** removes one entry with ten seconds of **Undo removal**; **⋮ → Clear history** removes all history entries and commits pending removal. These actions never delete files or close open documents.

**Reload** rereads the current URI-backed file while retaining reading context. **Heading outline** jumps between headings in supported Markdown previews. The document-count button or **Open documents** menu switches between up to five documents; close an entry with its **×**. Inactive URI files reload on selection. Search queries remain in memory, and URI references/reading preferences survive restart separately from recent history. See [session behavior and limits](open-documents.md).

**⋮ → Open with…** offers compatible apps other than Textr and hands off a read-only temporary copy. If none is available, the reader stays open with a message.

**⋮ → Share file…** opens Android’s Sharesheet with the original file bytes, not a rendered preview. The receiving app gets temporary read access to a copy. Textr keeps at most 20 copies and removes copies older than 24 hours on the next start or share; recipients may keep their own copies. **About Textr** is available from the menu on both screens and shows the installed version, privacy information, source/issue links, and license notices.

![Formatted JSON in Textr](images/json-preview.png)

Recent files reopen only while their providers still grant access. Picker-selected files usually offer persistent read access; shared attachments may offer temporary access only. If a document moved, was deleted, or lost access, use **Open file again** to select it or a replacement. Shared text without a file URI is available for that session and rotation, but cannot be reopened after process termination.

| Reader menu | About Textr |
| --- | --- |
| ![Reader menu with sharing and document information](images/reader-menu.png) | ![About with installed version, privacy, source and licenses](images/about.png) |

| Source line numbers | Open-document switcher |
| --- | --- |
| ![Highlighted configuration source with logical line numbers](images/source-lines.png) | ![Three open documents with individual close controls](images/open-documents.png) |

## Limits and troubleshooting

The source limit is 5 MiB. UTF-8 and BOM-marked UTF-16 are supported. Oversized, binary, unsupported-encoding, and unavailable files show explanations and an action to choose another file. Complex or invalid previews fall back to readable source. If Android terminates a WebView renderer, Textr offers **Retry as plain text** and **Open file**.

Textr has no network permission. A selected external document provider can fetch its own content, and tapping a web/email link launches another app. Images, embedded HTML, scripts, and automatic remote resources are not rendered.
