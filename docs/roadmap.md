# Textr roadmap

The roadmap leads to a signed, downloadable 0.1.0 APK with matching source, preserving Textr's minimal, offline, read-only scope. On 2026-11-12 the owner requested completion of implementation and deferred public release. Issue #13 remains open; no production signing key is created as part of implementation.

**Live tracker:** [Roadmap #14](https://github.com/utrost/Textr/issues/14). Individual GitHub issues are the source of truth for progress, acceptance criteria, and verification evidence.

## Current state

The initial implementation and validation issues #1–#12 are complete, merged through #23. The baseline passed 17 JVM tests and 33 instrumentation cases on Android 10 and 16 CI, plus Android 15 and live TalkBack validation. Public release remains deferred.

## Reader UX iteration (approved 2026-11-13)

Implemented in [PR #34](https://github.com/utrost/Textr/pull/34): navigation, sharing, reading continuity, and format interpretation. These changes preserve read-only operation, Android 10+, the 5 MiB limit, and no network permission. See the [iteration milestone](https://github.com/utrost/Textr/milestone/4).

| Issue | Work |
| --- | --- |
| [#24](https://github.com/utrost/Textr/issues/24) | Simplify reader navigation and add contextual menus and About |
| [#25](https://github.com/utrost/Textr/issues/25) | Share original files and selected text through Android |
| [#26](https://github.com/utrost/Textr/issues/26) | Remember reading position and display mode for recent files |
| [#27](https://github.com/utrost/Textr/issues/27) | Add document information and a remembered Treat as format override |

The owner authorized the following backlog on 2026-11-13. Implementation includes a five-document switcher and bounded source navigation; [design and limits](open-documents.md) define session persistence, memory, grant lifetime, Back behavior, pins, and Undo.

Implementation: [PR #35](https://github.com/utrost/Textr/pull/35), with [validation evidence](validation-backlog.md). See the [implementation milestone](https://github.com/utrost/Textr/milestone/5).

| Issue | Approved backlog implementation |
| --- | --- |
| [#28](https://github.com/utrost/Textr/issues/28) | Reload an externally changed document while preserving reading context |
| [#29](https://github.com/utrost/Textr/issues/29) | Add optional source line numbers and Go to line |
| [#30](https://github.com/utrost/Textr/issues/30) | Improve recent-file metadata and add pinning and undo |
| [#31](https://github.com/utrost/Textr/issues/31) | Navigate Markdown documents using a heading outline |
| [#32](https://github.com/utrost/Textr/issues/32) | Add a bounded open-document switcher |
| [#33](https://github.com/utrost/Textr/issues/33) | Hand off a document using Open with another app |

## Additional text formats (approved 2026-11-13)

Implementation extends source highlighting and adds a bounded iCalendar event summary. Public release remains deferred. See [format behavior and limits](formats.md) and [milestone 6](https://github.com/utrost/Textr/milestone/6).

| Issue | Work |
| --- | --- |
| [#36](https://github.com/utrost/Textr/issues/36) | Java, JavaScript, CSS, and HTML source highlighting |
| [#37](https://github.com/utrost/Textr/issues/37) | Kotlin, Python, shell, SQL, and diff highlighting |
| [#38](https://github.com/utrost/Textr/issues/38) | iCalendar recognition and source highlighting |
| [#39](https://github.com/utrost/Textr/issues/39) | Bounded read-only iCalendar event previews |

## Everyday reading (approved 2026-11-13)

[Milestone 7](https://github.com/utrost/Textr/milestone/7) tracks the next improvements. The first implementation batch covers source search, encoding recovery, navigation, document metadata and offline help. CSV preview, bookmarks/progress and keyboard shortcuts are implemented by the next batch (#44–#46); loading feedback (#49) remains open. Public release is still deferred.

| Issue | Work | Sequence |
| --- | --- | --- |
| [#41](https://github.com/utrost/Textr/issues/41) | Improve search with source results and matching options | First implementation batch |
| [#42](https://github.com/utrost/Textr/issues/42) | Reopen documents with an explicit text encoding | First implementation batch |
| [#43](https://github.com/utrost/Textr/issues/43) | Make document switching and format selection easier to discover | First implementation batch |
| [#44](https://github.com/utrost/Textr/issues/44) | Add an optional bounded CSV table preview | Implemented; validation in progress |
| [#45](https://github.com/utrost/Textr/issues/45) | Add reading progress and named document bookmarks | Implemented; validation in progress |
| [#46](https://github.com/utrost/Textr/issues/46) | Support hardware keyboard reader shortcuts | Implemented; validation in progress |
| [#47](https://github.com/utrost/Textr/issues/47) | Show text encoding and line information in Document info | First implementation batch |
| [#48](https://github.com/utrost/Textr/issues/48) | Add offline help for opening files and Android defaults | First implementation batch |
| [#49](https://github.com/utrost/Textr/issues/49) | Explain slow loading and provide a clear cancel action | Later iteration |

## Foldable layout (requested 2026-11-13)

[#51](https://github.com/utrost/Textr/issues/51) adds an opt-in file sidebar beside the reader on sufficiently wide and tall windows, with automatic compact/large-text fallback. See [activation, behavior, and limits](foldable-layout.md). This supports the requested use of a foldable inner display without device-model detection. Physical Magic V6 and hinge/posture-specific validation remain follow-ups. Public release remains deferred.

## Original release roadmap

The completed foundation and validation stages are retained below for traceability. GitHub issues are the source of truth for completion evidence. Original P0/P1 priorities describe release sequencing.

## 1. Foundation

[GitHub milestone](https://github.com/utrost/Textr/milestone/1)

Publish the existing source, establish automated checks, and fix the optional-provider-metadata opening failure.

| Issue | Work | Priority | Depends on |
| --- | --- | --- | --- |
| [#1](https://github.com/utrost/Textr/issues/1) | Publish the initial Android source and documentation baseline | P0 | None |
| [#2](https://github.com/utrost/Textr/issues/2) | Add CI for builds, unit tests, lint, and Android instrumentation | P0 | #1 |
| [#3](https://github.com/utrost/Textr/issues/3) | Open readable documents when optional provider metadata is unavailable | P1 | #1 |

## 2. Validation

[GitHub milestone](https://github.com/utrost/Textr/milestone/2)

Validate Android integration and the app's reading, reliability, accessibility, and privacy promises. Independent work can proceed in parallel once its listed prerequisites are met.

| Issue | Work | Priority | Depends on |
| --- | --- | --- | --- |
| [#4](https://github.com/utrost/Textr/issues/4) | Validate Open with, Share, and default-handler behavior across file types | P0 | #1, #3 |
| [#5](https://github.com/utrost/Textr/issues/5) | Complete Android 10–16 emulator compatibility validation | P0 | #1 |
| [#6](https://github.com/utrost/Textr/issues/6) | Automate reader controls and restoration across lifecycle changes | P1 | #1 |
| [#7](https://github.com/utrost/Textr/issues/7) | Test recent-file persistence, grant cleanup, and unavailable-file recovery | P1 | #1 |
| [#8](https://github.com/utrost/Textr/issues/8) | Validate accessible reading controls and layouts | P1 | #1 |
| [#9](https://github.com/utrost/Textr/issues/9) | Bound rendering work and recover from document-renderer failures | P1 | #1 |
| [#10](https://github.com/utrost/Textr/issues/10) | Add regression coverage for offline rendering and source-file integrity | P1 | #1 |

## 3. Release

[GitHub milestone](https://github.com/utrost/Textr/milestone/3)

Signing setup can proceed once CI exists. Publication waits for all foundation and validation work, verified signing, and accurate user documentation.

| Issue | Work | Priority | Depends on |
| --- | --- | --- | --- |
| [#11](https://github.com/utrost/Textr/issues/11) | Set up a repeatable signed release build and upgrade verification | P0 | #2 |
| [#12](https://github.com/utrost/Textr/issues/12) | Prepare installation, default-app guidance, screenshots, and release notes | P1 | #4, #5, #11 |
| [#13](https://github.com/utrost/Textr/issues/13) | Publish the first signed APK release with matching source and checksums | P0 | #2–#12; #1 is a prerequisite of those issues |

The release must come from the tested source commit, pass signature/checksum verification, include matching source and license notices, and pass a downloaded-artifact installation check. A signing identity must be supplied and retained by the repository owner; keys and passwords stay outside source control.

## Scope and upkeep

The owner confirmed emulator-only validation on 2026-11-12 because no physical device is available. API 29, 35, and 36 emulator evidence satisfies the compatibility release gate; physical-device checks remain follow-up coverage.

Android 10+, text files up to 5 MiB, read-only behavior, and no app network permission remain fixed. Editing, unbounded tab sessions, file management, huge-log support, direct cloud integrations, Markdown images/embedded HTML, and store publication remain outside v1. New features require a separate scope decision.

Close work issues with acceptance evidence and update the [tracker checklist](https://github.com/utrost/Textr/issues/14). Update this document when scope or dependencies change; use GitHub for live completion status. See the [product scope](scope.md) and [development notes](development.md) for implementation context.

## Release polish follow-ups

Public release remains deferred. These issues capture the release-readiness review without adding an implementation commitment to this batch.

- [#49](https://github.com/utrost/Textr/issues/49): loading explanation and explicit cancellation (existing issue).
- [#53](https://github.com/utrost/Textr/issues/53): public privacy policy and About integration.
- [#54](https://github.com/utrost/Textr/issues/54): German interface and offline help.
- [#55](https://github.com/utrost/Textr/issues/55): contextual opening-help and sidebar discovery.
- [#56](https://github.com/utrost/Textr/issues/56): real-device beta, providers, accessibility and Magic V6 checks.
- [#57](https://github.com/utrost/Textr/issues/57): optional Play App Bundle and store-listing preparation; publication remains deferred.
