# Textr feature roadmap

Updated 20 November 2026. Textr is an offline Android reader that preserves originals, with explicit export and copy editing. The current testing release is **0.2-alpha**, available to repository maintainers as a draft GitHub prerelease. Public APK and store publication remain deferred.

[Live tracker #14](https://github.com/utrost/Textr/issues/14) links the work below. Individual issues hold acceptance criteria; feature implementation is complete on `main`; reliability, physical acceptance and distribution gates remain separate. The phases describe delivery order, not dates or promised version numbers.

## Available in the alpha

The `v0.2-alpha` tag points to source commit `e4d3ce7182978d235f6035f439e4f169adff5b60`. Its non-debuggable APK (code 2) uses the same development signing identity as 0.1-alpha; an in-place upgrade preserving settings was verified on API 36. See [release notes](release-notes-0.2-alpha.md), the [private draft release](https://github.com/utrost/Textr/releases/tag/untagged-91b226e699ea4047e362) and [signing guidance](releasing.md). The original `v0.1-alpha` artifact remains immutable.

In addition to the baseline below, **all five feature epics and 19 implementation issues are included in 0.2-alpha**: reading profiles/typography, JSON/XML navigation, everyday formats, HTML Markdown export, read-only comparison and TXT/Markdown copy editing. They are merged via PR #96 (`07a1f7c`); physical acceptance and remaining reliability/distribution work are tracked separately.

| Capability | Included behavior | Tracking |
| --- | --- | --- |
| Open and share | System picker, Open with/Share, original-file sharing, document information and format overrides | #24–#27, #33 |
| Reading continuity | Recent-file pinning/Undo, reload, saved position/mode, up to five open documents | #26, #28, #30, #32 |
| Find and navigate | Source search, matching options, line numbers, Go to line, Markdown outline, bookmarks/progress, keyboard shortcuts | #29, #31, #41, #45, #46 |
| Formats and decoding | Bounded source highlighting and Markdown/JSON/calendar/CSV previews; explicit encoding recovery | #36–#39, #42, #44 |
| Layout and help | Optional wide-screen sidebar, document metadata and offline opening/default-app help | #43, #47, #48, #51 |
| Reader polish | Loading stages/cancel, English/German interface and help, dismissible opening/sidebar hints | #49, #54, #55; implemented, acceptance pending |
| Accessible CSV fallback | Native table/source views, search and original-byte sharing independent of WebView accessibility nodes | #59; compatibility implementation, investigation remains open |

The implementation passed build/lint, **86 JVM tests and 123 device tests on each of API 29 and API 36** in [hosted CI](https://github.com/utrost/Textr/actions/runs/44590125674), source `352d178`. Release commit `e4d3ce7` changes version defaults and release documentation only; its clean release build/lint, signing/manifest/checksum, downloaded-artifact verification, fresh installation and upgrade from 0.1-alpha passed. See [implementation validation](validation-roadmap-implementation.md). Emulator evidence does not establish physical-device or human spoken-feedback acceptance.

## Now — reliability and private beta

- **Integration complete:** PR #96 merged the reader-polish, HTML and roadmap stack into `main` at `07a1f7c`. PR #60 is merged; #70/#95 were closed as included. All 19 feature implementation issues and five epics are complete, along with HTML conversion/reading (#68/#69) and recent-file activation correction (#61).
- **Current CI follow-up [#97](https://github.com/utrost/Textr/issues/97):** [main run 44716759569](https://github.com/utrost/Textr/actions/runs/44716759569) passed build/unit/lint and API 36. API 29 completed 123 cases, passing 122; `FoldableLayoutTest.windowTransitionsPreserveDocumentModePositionAndFind` expected scroll 600 but observed 0. Reproduce and distinguish synchronization from application restoration failure; retain the assertions and rerun the matrix after fixing the cause. This is an assertion failure, separate from earlier dependency-download failures.
- **Private beta [#56](https://github.com/utrost/Textr/issues/56):** real providers, human TalkBack speech quality and physical foldable transitions remain unverified.
- **WebView investigation [#59](https://github.com/utrost/Textr/issues/59):** the native CSV/source compatibility views are included, while the underlying accessibility omission remains unresolved.

## Next — private beta feedback and reading reliability

Use [#56 real-device beta](https://github.com/utrost/Textr/issues/56) to collect findings from the alpha. A physical device is not currently available to the implementation workflow; device-dependent checks remain open until evidence exists. Historical emulator-only acceptance for the original baseline does not imply that these new checks passed.

| Focus | User outcome | Acceptance |
| --- | --- | --- |
| Opening and recovery | Files open consistently from the picker, other apps and available document providers | Exercise denied/revoked grants, moved/unavailable files, reload and recovery with synthetic documents; originals remain unchanged |
| Accessible reading | Controls, native CSV cells/source, search and copy remain usable with TalkBack and enlarged text | Human spoken-feedback review, repeated navigation/recreation, visible and reachable actions at 200% text |
| Foldables and large windows | Reading context survives outer/inner display changes, rotation and resizing | Physical Magic V6 or equivalent device evidence, sidebar opt-in and compact fallback, no lost file/mode/position |
| Responsiveness | Slow-provider reads and bounded previews explain progress and cancel reliably | Cancel during reading/preparation/layout, open another document immediately, and confirm obsolete work cannot replace it |

Turn reproducible beta findings into focused issues with fixture, Android/WebView versions and acceptance criteria. Keep private document contents out of reports. Prioritize opening, data integrity and accessibility regressions before convenience features.

## Feature roadmap implementation

The requested feature roadmap now has **five epics and 19 implementation issues**, implemented in tested batches on [PR #96](https://github.com/utrost/Textr/pull/96), included in 0.2-alpha and merged into `main`. The [feature roadmap and backlog](feature-proposals.md) contains the detailed scope, tradeoffs and dependency order. Epics are native children of [tracker #14](https://github.com/utrost/Textr/issues/14), with their implementation issues nested underneath.

| Sequence | Epic | Implementation issues | State |
| --- | --- | --- | --- |
| 1 — reader improvements | [#71](https://github.com/utrost/Textr/issues/71) — Reading comfort and structured navigation | [#76](https://github.com/utrost/Textr/issues/76), [#77](https://github.com/utrost/Textr/issues/77), [#78](https://github.com/utrost/Textr/issues/78), [#79](https://github.com/utrost/Textr/issues/79) | Complete; merged in #96 and in 0.2-alpha |
| 2 — focused format batch | [#72](https://github.com/utrost/Textr/issues/72) — Everyday text and configuration format support | [#80](https://github.com/utrost/Textr/issues/80), [#81](https://github.com/utrost/Textr/issues/81), [#82](https://github.com/utrost/Textr/issues/82), [#83](https://github.com/utrost/Textr/issues/83), [#84](https://github.com/utrost/Textr/issues/84) | Complete; merged in #96 and in 0.2-alpha |
| 3a — explicit export | [#73](https://github.com/utrost/Textr/issues/73) — Export HTML reading content as Markdown | [#85](https://github.com/utrost/Textr/issues/85), [#86](https://github.com/utrost/Textr/issues/86) | Complete; merged in #96 and in 0.2-alpha |
| 3b — compare documents | [#74](https://github.com/utrost/Textr/issues/74) — Bounded read-only document comparison | [#87](https://github.com/utrost/Textr/issues/87), [#88](https://github.com/utrost/Textr/issues/88), [#89](https://github.com/utrost/Textr/issues/89) | Complete; merged in #96 and in 0.2-alpha |
| 4 — deliberate editing extension | [#75](https://github.com/utrost/Textr/issues/75) — Optional plain-text and Markdown editing of copies | [#90](https://github.com/utrost/Textr/issues/90), [#91](https://github.com/utrost/Textr/issues/91), [#92](https://github.com/utrost/Textr/issues/92), [#93](https://github.com/utrost/Textr/issues/93), [#94](https://github.com/utrost/Textr/issues/94) | Complete; merged in #96 and in 0.2-alpha |

Implementation followed reader improvements and focused formats → Markdown export and read-only comparison → TXT/Markdown editing of copies. All batches are merged and available for private testing; reliability follow-up and beta feedback are next. Save as output is shared by export and editing; edit-copy saving follows the explicit scope/recovery work. Existing beta and distribution issues continue separately.

Original files remain read-only. Export and copy editing use new output documents; [private recovery](editing-copies.md) explicitly extends storage/privacy behavior. In-place editing, more languages, PDF/DOCX/EPUB and large/live logs remain deferred decisions. No dates or future version numbers are committed. These feature epics are independent of the first public APK gate (#13); completing all of them is not a prerequisite for publishing the accepted baseline.

## Distribution work — separate from reader features

Private testing is available now. Preparation below does not authorize public publication or production-key creation.

| Track | Work and dependencies | Status / exit gate |
| --- | --- | --- |
| Public downloadable APK | [#13](https://github.com/utrost/Textr/issues/13): accepted candidate, retained production signing identity, matching source/notices/checksum, downloaded-artifact verification | Deferred; private development-signed alpha does not complete this issue |
| Privacy readiness | [#53](https://github.com/utrost/Textr/issues/53): policy/About integration and store privacy declarations | Policy/About implementation is merged; accountable developer/contact and final public policy URL still need owner review |
| F-Droid build and listing | [#62](https://github.com/utrost/Textr/issues/62) secret-free release path and [#63](https://github.com/utrost/Textr/issues/63) English/German metadata/screenshots | Separate draft [PR #67](https://github.com/utrost/Textr/pull/67) now targets main; not merged, needs rebase/version alignment and fresh validation |
| F-Droid recipe and signing policy | [#64](https://github.com/utrost/Textr/issues/64) clean recipe/build/scanner validation; [#65](https://github.com/utrost/Textr/issues/65) signing and cross-channel updates | #64 follows the unsigned build path and an integrated tested tag; recipe must match the selected #65 route |
| F-Droid submission | [#66](https://github.com/utrost/Textr/issues/66) | Wait for reliability/accessibility acceptance (#59/#97), #62–#65, tested source/tag, current eligibility review and explicit publication authorization; external acceptance is not guaranteed |
| Optional Google Play | [#57](https://github.com/utrost/Textr/issues/57): AAB path, listing assets, account/signing/testing requirements | Preparation is merged via #96; account-specific checks, #53/#56 follow-ups and publication decision remain open |

Rebase the separate F-Droid work onto accepted source before validating that channel. PR #67's older version/listing/recipe must be aligned with the selected release. Current private testing is `0.2-alpha`/code 2; preserve both alpha tags and artifacts. Future APK updates must use a version code above 2 and a compatible signing identity; switching to a production key requires uninstalling the development-signed alpha, removing private settings/history.

Distribution sequence: accept the shared source → integrate and validate each selected build path → create a new tested tag → complete that channel's signing, policy and owner decisions → publish only when authorized. Standard F-Droid signing need not depend on an owner-signed GitHub APK; #65 determines the route.

## Product boundaries and upkeep

Keep Android 10+, the 5 MiB source-file limit, unchanged originals, bounded rendering/sessions and no app network permission. Explicit export, read-only comparison and TXT/Markdown copy editing are roadmap extensions; copy recovery is the documented content-bearing private-storage exception. In-place editing, file management, huge-log support, direct cloud integrations, Markdown images/embedded HTML and unbounded tabs remain outside the agreed scope. See [product scope](scope.md).

Update this roadmap and #14 when priorities or acceptance change. Close issues with exact-source evidence, distinguish shipped/merged/candidate/proposed work, and verify external channel requirements at submission time. Dates and version names for future phases remain unset.

Completed foundation, reader UX, formats, everyday-reading and foldable milestones, their issue links and the prior release plan are preserved in the [historical roadmap](roadmap-history.md). They are history, not additional outstanding work.

## Branch housekeeping

After merging #96, ten fully merged remote branches and four local branches were removed. Their commits remain reachable from `main`; alpha tags/artifacts are unchanged. The unmerged F-Droid branch and Dependabot PR branches are retained. PR #67 now targets `main` rather than the removed reader-polish branch.
