# Textr v1 scope

**Status: v1 scope implemented in the initial 0.1.0 development build.** This document records the agreed product boundaries. See the [README](../README.md) for current functionality and implementation limits, and [development notes](development.md) for build and validation instructions. Release signing support is implemented; selection of the production signing identity and publication are still pending.

## Approved reader UX extension (2026-11-13)

The first UX iteration added contextual menus and About, outgoing sharing, remembered reading state, and format overrides. The owner subsequently authorized #28–#33: manual reload, source line navigation, pinned recents with Undo, a Markdown outline, a five-document switcher, and Open with. See [behavior and limits](open-documents.md). The original source and rendering limits remain in force.

## Purpose

Provide a minimal, read-only Android app for opening and reading everyday text files. Users can select Textr from other apps, choose it as a default handler where Android allows, open files through the system picker, and revisit recent files.

## Platform and files

- Support Android 10 and newer.
- Support text files up to 5 MiB (5,242,880 bytes).
- Cover TXT, LOG, CONF/CFG, INI, JSON, Markdown, YAML, TOML, XML, CSV, and the [approved additional formats](formats.md). Allow other text files to be selected through the system picker.
- Treat plain-text access as the baseline. Opening a file does not imply a dedicated parser, syntax highlighting, or a formatted preview.
- Show understandable messages for oversized, unreadable, and unsupported binary files.

## Display and reading

Choose the initial display automatically and allow manual switching between applicable modes:

| File or syntax | Automatic display | Additional views |
| --- | --- | --- |
| Markdown | Rendered preview | Highlighted source and plain text |
| Valid JSON | Indented JSON preview | Highlighted source and plain text |
| Invalid JSON | Source, highlighted where possible | Plain text |
| Recognized INI, YAML, TOML, XML | Highlighted source | Plain text |
| Java, JavaScript, CSS, HTML, Kotlin, Python, shell, SQL, diff | Highlighted source | Plain text |
| Supported iCalendar events | Event summary preview | Highlighted source and plain text |
| Other supported text | Plain text | No dedicated preview required |

- Initial syntax highlighting covers JSON, Markdown source, INI, YAML, TOML, and XML. Generic configuration files use plain text unless their syntax is recognized.
- Markdown preview supports headings, lists, links, quotes, code blocks, and tables. Images and embedded HTML are outside v1. Links open externally only when tapped.
- JSON preview adds indentation without modifying the underlying file. Invalid JSON remains readable as source.
- Preview budgets bound expensive rendering: JSON accepts up to 1 MiB of input and 5 Mi characters of formatted output; Markdown accepts up to 512 Ki characters, 20,000 nodes, and depth 64. Exceeding a preview budget leaves the complete source readable and explains the fallback. The source limit remains 5 MiB.
- CSV stays a text view, with no spreadsheet interface.
- Provide search, text selection and copying, adjustable font size, line wrapping, and light/dark themes.
- Let users inspect document information and choose Automatic, plain text, or any supported syntax interpretation without changing file bytes. Remember the choice for recent files.
- Keep the reader focused on the document with a small toolbar. Viewing and switching modes must never modify the source file.

## Opening files and recent history

- Provide a minimal home screen with an **Open file** action using Android's system picker and a recent-files list.
- Accept supported files opened from other apps. Default-handler availability depends on the sending app and reported file type; do not promise universal extension associations.
- Store recent-file references and metadata locally, including reading position, mode, and optional format override, without retaining document contents as history. Remove this metadata with its history entry.
- Share original files through the Android Sharesheet with temporary read-only access. Explicit sharing may create a private temporary copy: retain at most 20 and remove copies older than 24 hours on the next start/share. Selected text uses Android’s native selection sharing. Short incoming text can be shared as text; larger incoming text is shared as a UTF-8 file.
- Retain access where Android and the document provider permit it. If a recent file is unavailable, explain the issue and prompt the user to reopen it through the picker.
- Allow removal of individual recent entries and clearing all history. Neither action deletes the underlying files.

## Privacy and exclusions

Textr has no editing, accounts, ads, analytics, or network permission. Document providers accessed through the system picker may independently use network access.

The following are outside v1:

- File management and unbounded tab sessions. A bounded document switcher is included in the approved extension.
- Huge-log support or files larger than 5 MiB.
- Direct cloud integrations.
- Markdown images and embedded HTML, and spreadsheet-style CSV previews.
- Store publication.

## Delivery and deferred decisions

- Keep the app name **Textr**.
- Publish the project as open source under `GPL-3.0-only`, with the standard GPLv3 text in [LICENSE](../LICENSE).
- Plan downloadable APK releases. Store distribution can follow later.
- Implement the app in Kotlin with native Android controls and an offline WebView; use CommonMark for Markdown and Jackson Core for JSON.
- Document verified local build and debug installation commands. Add release download instructions when a release is published.

## Acceptance criteria

The initial documentation milestone established:

- A [README](../README.md) and scope that agree and distinguish available functionality from future work.
- Both explain Android file-opening limitations and recent-file access constraints.
- The license is the standard, unmodified GPLv3 text, and the project explicitly selects version 3 only.
- Local documentation links resolve; no unsupported build commands, release links, or installation claims appear.
- Documentation and licensing before application implementation.

Implementation acceptance covers opening via the picker and another app; applicable display modes and invalid-JSON fallback; reading controls; recent-file reopening and history removal; unavailable-file handling; the 5 MiB boundary; and clear errors for unreadable and unsupported binary files. Confirm that viewing never modifies files and that Textr requests no network permission. Track verification and remaining device coverage in [development notes](development.md).

## Additional formats (approved 2026-11-13)

Java, JavaScript, CSS, HTML, Kotlin, Python, shell, SQL, diff/patch, and iCalendar receive bounded source highlighting and manual format choices. Supported iCalendar events also receive a read-only summary preview. HTML remains source-only. Calendar import, alarms, recurrence expansion, and time-zone conversion are excluded. Existing size, privacy, source-integrity, and rendering guarantees apply. See [format details and exact preview limits](formats.md).
