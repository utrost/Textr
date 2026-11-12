# Roadmap to the first public Textr release

The roadmap leads to a signed, downloadable 0.1.0 APK with matching source, preserving Textr's minimal, offline, read-only scope. On 2026-11-12 the owner requested completion of implementation and deferred public release. Issue #13 remains open; no production signing key is created as part of implementation.

**Live tracker:** [Roadmap #14](https://github.com/utrost/Textr/issues/14). Individual GitHub issues are the source of truth for progress, acceptance criteria, and verification evidence.

## Starting point

The local development build already provides file opening, recent history, plain text, syntax highlighting, Markdown/JSON previews, and reading controls. It passed 13 JVM tests and 8 Android 15 emulator tests, plus selected manual checks. At roadmap creation, the source has not been committed or published to the GitHub default branch, and no release exists. The issues below cover remaining work rather than reimplementing completed features.

P0 identifies critical-path work and explicit release gates. P1 identifies other required work before public release. Both are required; priority determines scheduling, not optional scope. Milestones are delivery stages for the same release, with no assumed deadlines or assignees.

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

Android 10+, text files up to 5 MiB, read-only behavior, and no app network permission remain fixed. Editing, tabs, file management, huge-log support, direct cloud integrations, Markdown images/embedded HTML, and store publication remain outside v1. New features require a separate scope decision.

Close work issues with acceptance evidence and update the [tracker checklist](https://github.com/utrost/Textr/issues/14). Update this document when scope or dependencies change; use GitHub for live completion status. See the [product scope](scope.md) and [development notes](development.md) for implementation context.
