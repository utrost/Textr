# Textr feature roadmap

Updated 19 November 2026. Textr is an offline, read-only Android reader. The current candidate is **0.1-alpha**, available to repository maintainers as a draft GitHub prerelease. Public APK and store publication remain deferred.

[Live tracker #14](https://github.com/utrost/Textr/issues/14) links the work below. Individual issues hold acceptance criteria; an implementation in a candidate branch is not the same as accepted and merged work. The phases describe delivery order, not dates or promised version numbers.

## Available in the alpha

The `v0.1-alpha` tag points to source commit `6c06422cf7a6bfa9140c9c3c3123bc252edf82fb`. Its APK is a non-debuggable release build signed with the existing development key for private testing. See [alpha notes and release checks](release-notes-0.1-alpha.md) and [signing guidance](releasing.md).

| Capability | Included behavior | Tracking |
| --- | --- | --- |
| Open and share | System picker, Open with/Share, original-file sharing, document information and format overrides | #24–#27, #33 |
| Reading continuity | Recent-file pinning/Undo, reload, saved position/mode, up to five open documents | #26, #28, #30, #32 |
| Find and navigate | Source search, matching options, line numbers, Go to line, Markdown outline, bookmarks/progress, keyboard shortcuts | #29, #31, #41, #45, #46 |
| Formats and decoding | Bounded source highlighting and Markdown/JSON/calendar/CSV previews; explicit encoding recovery | #36–#39, #42, #44 |
| Layout and help | Optional wide-screen sidebar, document metadata and offline opening/default-app help | #43, #47, #48, #51 |
| Reader polish | Loading stages/cancel, English/German interface and help, dismissible opening/sidebar hints | #49, #54, #55; implemented, acceptance pending |
| Accessible CSV fallback | Native table/source views, search and original-byte sharing independent of WebView accessibility nodes | #59; compatibility implementation, investigation remains open |

The candidate passed build/lint, 48 JVM tests, 82 instrumentation cases on each of API 29 and API 36, and three additional API 36 checks with real TalkBack. Release build/lint, signature/checksum, downloaded-artifact install/launch and same-key replacement checks also passed. These are emulator results; they do not establish physical-device or human spoken-feedback acceptance. See [reader-polish validation](validation-reader-polish.md).

## Now — accept the reader-polish candidate

Make the already implemented reader features reliable enough for the next testing build before expanding the reader.

| Work | Current state | Completion evidence |
| --- | --- | --- |
| [#49 loading/cancel](https://github.com/utrost/Textr/issues/49), [#54 German UI/help](https://github.com/utrost/Textr/issues/54), [#55 discovery hints](https://github.com/utrost/Textr/issues/55) | Implemented in draft [PR #60](https://github.com/utrost/Textr/pull/60) and retained in the alpha | Merge the fixes from `fix/reader-validation` into the candidate; pass hosted build/unit/lint and complete API 29/36 suites before closing the issues |
| [#61 recent-file reopening](https://github.com/utrost/Textr/issues/61) | Stale test title activation corrected; original readiness/mode/position assertions retained; local suites pass | Hosted acceptance on the integrated source; retain reports tied to the exact commit |
| [#59 accessible CSV](https://github.com/utrost/Textr/issues/59) | Native compatibility table/source works with real TalkBack; underlying WebView node omission remains unresolved | Check current WebView and repeated mode/document/recreation flows; record native fallback coverage separately from vendor-defect resolution |

The last recorded PR #60 candidate CI evidence has failures (see [validation](validation-public-readiness.md)), and no hosted run is recorded for `fix/reader-validation` at this review. Local green reports do not replace that merge gate. Do not close #59 merely because a native fallback exists.

## Approved feature extension — HTML Markdown reading view

Requested after the alpha roadmap review: [#68 conversion](https://github.com/utrost/Textr/issues/68) and [#69 reader integration](https://github.com/utrost/Textr/issues/69). The candidate on `feat/html-markdown` adds bounded local/shared HTML conversion, an explicit English/German reading mode, safe links, headings/outline and source fallback. See [behavior and limits](html-reading.md). This is newer than `v0.1-alpha`; the existing APK is unchanged. Local acceptance passes build/lint, 59 JVM tests and the HTML/format/large-text confirmation cases on API 29/36; [validation](validation-html-reading.md) retains the initial full-suite result and test-synchronization correction. Hosted build/unit/lint and API 36 checks pass; API 29 CI is failing at the 19 November review and remains a merge blocker. Live URL fetching remains outside scope.

## Next — private beta feedback and reading reliability

Use [#56 real-device beta](https://github.com/utrost/Textr/issues/56) to collect findings from the alpha. A physical device is not currently available to the implementation workflow; device-dependent checks remain open until evidence exists. Historical emulator-only acceptance for the original baseline does not imply that these new checks passed.

| Focus | User outcome | Acceptance |
| --- | --- | --- |
| Opening and recovery | Files open consistently from the picker, other apps and available document providers | Exercise denied/revoked grants, moved/unavailable files, reload and recovery with synthetic documents; originals remain unchanged |
| Accessible reading | Controls, native CSV cells/source, search and copy remain usable with TalkBack and enlarged text | Human spoken-feedback review, repeated navigation/recreation, visible and reachable actions at 200% text |
| Foldables and large windows | Reading context survives outer/inner display changes, rotation and resizing | Physical Magic V6 or equivalent device evidence, sidebar opt-in and compact fallback, no lost file/mode/position |
| Responsiveness | Slow-provider reads and bounded previews explain progress and cancel reliably | Cancel during reading/preparation/layout, open another document immediately, and confirm obsolete work cannot replace it |

Turn reproducible beta findings into focused issues with fixture, Android/WebView versions and acceptance criteria. Keep private document contents out of reports. Prioritize opening, data integrity and accessibility regressions before convenience features.

## Planned feature development

The requested feature roadmap now has **five epics and 19 implementation issues**, all in planned state. The [feature roadmap and backlog](feature-proposals.md) contains the detailed scope, tradeoffs and dependency order. Epics are native children of [tracker #14](https://github.com/utrost/Textr/issues/14), with their implementation issues nested underneath.

| Sequence | Epic | Implementation issues | State |
| --- | --- | --- | --- |
| 1 — reader improvements | [#71](https://github.com/utrost/Textr/issues/71) — Reading comfort and structured navigation | [#76](https://github.com/utrost/Textr/issues/76), [#77](https://github.com/utrost/Textr/issues/77), [#78](https://github.com/utrost/Textr/issues/78), [#79](https://github.com/utrost/Textr/issues/79) | Planned |
| 2 — focused format batch | [#72](https://github.com/utrost/Textr/issues/72) — Everyday text and configuration format support | [#80](https://github.com/utrost/Textr/issues/80), [#81](https://github.com/utrost/Textr/issues/81), [#82](https://github.com/utrost/Textr/issues/82), [#83](https://github.com/utrost/Textr/issues/83), [#84](https://github.com/utrost/Textr/issues/84) | Planned |
| 3a — explicit export | [#73](https://github.com/utrost/Textr/issues/73) — Export HTML reading content as Markdown | [#85](https://github.com/utrost/Textr/issues/85), [#86](https://github.com/utrost/Textr/issues/86) | Planned |
| 3b — compare documents | [#74](https://github.com/utrost/Textr/issues/74) — Bounded read-only document comparison | [#87](https://github.com/utrost/Textr/issues/87), [#88](https://github.com/utrost/Textr/issues/88), [#89](https://github.com/utrost/Textr/issues/89) | Planned |
| 4 — deliberate editing extension | [#75](https://github.com/utrost/Textr/issues/75) — Optional plain-text and Markdown editing of copies | [#90](https://github.com/utrost/Textr/issues/90), [#91](https://github.com/utrost/Textr/issues/91), [#92](https://github.com/utrost/Textr/issues/92), [#93](https://github.com/utrost/Textr/issues/93), [#94](https://github.com/utrost/Textr/issues/94) | Planned |

Delivery sequence: accept the current reader/HTML candidate → reader improvements and focused formats → Markdown export and read-only comparison → optional TXT/Markdown editing of copies. Save as output is shared by export and editing; edit-copy saving follows the explicit scope/recovery work. Existing beta and distribution issues continue separately.

Current behavior remains read-only. Future export and editing issues define their output/privacy/encoding changes before implementation delivery. In-place editing, more languages, PDF/DOCX/EPUB and large/live logs remain deferred decisions. No dates or future version numbers are committed. These future feature epics are independent of the first public APK gate (#13); completing all of them is not a prerequisite for publishing the accepted baseline.

## Distribution work — separate from reader features

Private testing is available now. Preparation below does not authorize public publication or production-key creation.

| Track | Work and dependencies | Status / exit gate |
| --- | --- | --- |
| Public downloadable APK | [#13](https://github.com/utrost/Textr/issues/13): accepted candidate, retained production signing identity, matching source/notices/checksum, downloaded-artifact verification | Deferred; private development-signed alpha does not complete this issue |
| Privacy readiness | [#53](https://github.com/utrost/Textr/issues/53): policy/About integration and store privacy declarations | Candidate implementation exists; accountable developer/contact and final public policy URL still need owner review |
| F-Droid build and listing | [#62](https://github.com/utrost/Textr/issues/62) secret-free release path and [#63](https://github.com/utrost/Textr/issues/63) English/German metadata/screenshots | Implemented in draft [PR #67](https://github.com/utrost/Textr/pull/67), stacked on #60; not merged, API 36 hosted check currently fails |
| F-Droid recipe and signing policy | [#64](https://github.com/utrost/Textr/issues/64) clean recipe/build/scanner validation; [#65](https://github.com/utrost/Textr/issues/65) signing and cross-channel updates | #64 follows the unsigned build path and an integrated tested tag; recipe must match the selected #65 route |
| F-Droid submission | [#66](https://github.com/utrost/Textr/issues/66) | Wait for #59/#61 acceptance, #62–#65, tested source/tag, current eligibility review and explicit publication authorization; external acceptance is not guaranteed |
| Optional Google Play | [#57](https://github.com/utrost/Textr/issues/57): AAB path, listing assets, account/signing/testing requirements | Preparation exists in #60; account-specific checks, #53/#56 follow-ups and publication decision remain open |

Integrate the reader fixes before rebasing the stacked F-Droid work. PR #67 currently describes `0.1.0`/code 1, while the private alpha is `0.1-alpha`/code 1: align the committed version, changelog, listing and recipe on the next candidate. Preserve `v0.1-alpha` as the immutable testing snapshot. The next APK update must increment version code above 1 and document signing compatibility; a different key requires uninstalling and removes private settings/history.

Distribution sequence: accept the shared source → integrate and validate each selected build path → create a new tested tag → complete that channel's signing, policy and owner decisions → publish only when authorized. Standard F-Droid signing need not depend on an owner-signed GitHub APK; #65 determines the route.

## Product boundaries and upkeep

Keep Android 10+, the 5 MiB source-file limit, read-only operation, bounded rendering/sessions, local metadata and no app network permission. Editing, file management, huge-log support, direct cloud integrations, Markdown images/embedded HTML and unbounded tabs remain outside the agreed scope. See [product scope](scope.md).

Update this roadmap and #14 when priorities or acceptance change. Close issues with exact-source evidence, distinguish shipped/merged/candidate/proposed work, and verify external channel requirements at submission time. Dates and version names for future phases remain unset.

Completed foundation, reader UX, formats, everyday-reading and foldable milestones, their issue links and the prior release plan are preserved in the [historical roadmap](roadmap-history.md). They are history, not additional outstanding work.
