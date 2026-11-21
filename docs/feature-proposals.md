# Feature roadmap and implementation backlog

Updated 21 November 2026. The five 0.2-alpha epics and 19 implementation issues are complete. Their rationale and original sequence are retained in the [completed design record](feature-roadmap-0.2.md). This document records the current batch and its acceptance; the reliability implementation is merged through PR #117 and included in 0.3-alpha; none of this batch is included in 0.2-alpha.

## Next roadmap — reliable daily use

The next roadmap adds **six epics and 13 implementation issues**. These are follow-ups, separate from the five completed 0.2-alpha epics; the reliability implementation is merged through PR #117 and included in 0.3-alpha and #101/#109 are merged in [quality-of-life PR #126](https://github.com/utrost/Textr/pull/126). The ten remaining feature/automation implementations are delivered in [PR #127](https://github.com/utrost/Textr/pull/127); see its checks and the current validation record. No date or version is promised. Existing reliability, private-beta and distribution issues remain open and are linked from the new epics rather than duplicated.

| Order | Epic | New implementation issues | State |
| --- | --- | --- | --- |
| 1 | [#98](https://github.com/utrost/Textr/issues/98) — Reliable daily use: recovery and acceptance | [#99](https://github.com/utrost/Textr/issues/99) | Software merged in [PR #117](https://github.com/utrost/Textr/pull/117), included in 0.3-alpha; #56/#59 remain open |
| 2 | [#100](https://github.com/utrost/Textr/issues/100) — Copy editor polish: preview, navigation and saved copies | [#101](https://github.com/utrost/Textr/issues/101), [#102](https://github.com/utrost/Textr/issues/102), [#103](https://github.com/utrost/Textr/issues/103) | Implemented in PR #127 |
| 3 | [#104](https://github.com/utrost/Textr/issues/104) — Comparison improvements: draft review and portable diffs | [#105](https://github.com/utrost/Textr/issues/105), [#106](https://github.com/utrost/Textr/issues/106), [#107](https://github.com/utrost/Textr/issues/107) | Implemented in PR #127 |
| 4 | [#108](https://github.com/utrost/Textr/issues/108) — Reading organization: recents search and reference collections | [#109](https://github.com/utrost/Textr/issues/109), [#110](https://github.com/utrost/Textr/issues/110) | Implemented in PR #127 |
| 5 | [#111](https://github.com/utrost/Textr/issues/111) — Focused formats: JSON Lines and lightweight markup | [#112](https://github.com/utrost/Textr/issues/112), [#113](https://github.com/utrost/Textr/issues/113), [#114](https://github.com/utrost/Textr/issues/114) | Implemented in PR #127 |
| 6 | [#115](https://github.com/utrost/Textr/issues/115) — Repeatable release preparation and channel readiness | [#116](https://github.com/utrost/Textr/issues/116) | Implemented in PR #127 |

**Delivery status:** recovery and position preservation are merged; draft preview and recent-file filtering are merged with the eight QoL changes. The current branch implements draft comparison, editor navigation/reopening, comparison filters/export, collections, focused formats and release automation. See the [roadmap](roadmap.md) and [validation](validation-roadmap-completion.md) for current evidence. #56/#59 physical/accessibility acceptance and external distribution gates remain separate.

Draft comparison uses the original editing snapshot, not an unannounced reread of the provider file. Preview and comparison never save the draft. Collections contain bounded metadata/references only. AsciiDoc/reStructuredText support is source highlighting; Markdown task lists are read-only. Every issue defines acceptance and validation, preserving offline operation, unchanged originals and current resource limits.

Dependencies: collections (#110) follow recovery/reselection behavior (#99); exact unified-diff export (#107) follows filter semantics (#106); task-list rendering (#114) follows the shared draft-preview path (#101). Existing distribution issues #13/#53/#57/#62–#66 retain their owner decisions and acceptance requirements.

**Deferred decisions:** in-place editing, multiple recovery drafts and EPUB require separate scope/privacy/resource decisions backed by use cases. PDF/DOCX, direct URL fetching and large/live logs remain outside this batch. Future language support follows demonstrated demand.

See below for the issue breakdown and [0.2-alpha design record](feature-roadmap-0.2.md) for the completed batch.

## Issue breakdown and acceptance

Roadmap [#14](https://github.com/utrost/Textr/issues/14) owns the six epics as native GitHub sub-issues, each with its new implementation children. Existing reliability/distribution issues are cross-linked and retain their current scope. Each child has explicit acceptance criteria, dependencies and verification expectations.

### Reliable daily use: recovery and acceptance — [#98](https://github.com/utrost/Textr/issues/98)

- [#99](https://github.com/utrost/Textr/issues/99) **Explain provider and draft recovery failures with actionable choices** — Users can recover from unavailable documents and failed output without losing work.

Existing acceptance work: [#97](https://github.com/utrost/Textr/issues/97), [#59](https://github.com/utrost/Textr/issues/59), [#56](https://github.com/utrost/Textr/issues/56).

### Copy editor polish: preview, navigation and saved copies — [#100](https://github.com/utrost/Textr/issues/100)

- [#101](https://github.com/utrost/Textr/issues/101) **Preview unsaved Markdown copies safely** — Inspect the current Markdown draft before saving.
- [#102](https://github.com/utrost/Textr/issues/102) **Add Go to line and indentation actions to the copy editor** — Make small text changes easier with touch or a keyboard.
- [#103](https://github.com/utrost/Textr/issues/103) **Offer to open a successfully saved copy** — Continue reading the new output after Save as.

### Comparison improvements: draft review and portable diffs — [#104](https://github.com/utrost/Textr/issues/104)

- [#105](https://github.com/utrost/Textr/issues/105) **Compare an unsaved draft with its original snapshot** — Review copy edits before saving.
- [#106](https://github.com/utrost/Textr/issues/106) **Add explicit whitespace and line-ending comparison filters** — Inspect meaningful differences while retaining an exact comparison.
- [#107](https://github.com/utrost/Textr/issues/107) **Export an exact unified diff through Save as** — Share a portable textual record of changes.

### Reading organization: recents search and reference collections — [#108](https://github.com/utrost/Textr/issues/108)

- [#109](https://github.com/utrost/Textr/issues/109) **Filter recent documents by name and format** — Find a previously opened file without scrolling the whole list.
- [#110](https://github.com/utrost/Textr/issues/110) **Group document references into named reading collections** — Organize reference documents without copying their contents.

### Focused formats: JSON Lines and lightweight markup — [#111](https://github.com/utrost/Textr/issues/111)

- [#112](https://github.com/utrost/Textr/issues/112) **Recognize JSON Lines and provide bounded record navigation** — Inspect newline-delimited JSON records without loading an unbounded tree.
- [#113](https://github.com/utrost/Textr/issues/113) **Add AsciiDoc and reStructuredText source highlighting** — Read lightweight markup source more comfortably.
- [#114](https://github.com/utrost/Textr/issues/114) **Render Markdown task lists as read-only document content** — Read checklist notes with clear checked and unchecked states.

### Repeatable release preparation and channel readiness — [#115](https://github.com/utrost/Textr/issues/115)

- [#116](https://github.com/utrost/Textr/issues/116) **Automate verified draft-release packaging and changelog preparation** — Prepare repeatable test releases with matching source and verification evidence.

Existing acceptance work: [#13](https://github.com/utrost/Textr/issues/13), [#53](https://github.com/utrost/Textr/issues/53), [#62](https://github.com/utrost/Textr/issues/62), [#63](https://github.com/utrost/Textr/issues/63), [#64](https://github.com/utrost/Textr/issues/64), [#65](https://github.com/utrost/Textr/issues/65), [#66](https://github.com/utrost/Textr/issues/66), [#57](https://github.com/utrost/Textr/issues/57).

## Completion and boundaries

Close implementation issues only with the tested commit, appropriate checks and updated user documentation. Close epics after their child and linked acceptance work is accepted. Keep historical release evidence separate from new main-branch results. Physical devices, real providers and human TalkBack acceptance require actual evidence under #56; emulator results cannot close those checks.

Retain Android 10+, no INTERNET permission, the 5 MiB input cap and tighter existing editor/diff/render budgets. Originals remain unchanged; output uses explicit Save as. Keep a single private recovery draft until a separately approved policy changes that behavior. Collection metadata needs bounded retention and documented clear-history semantics. Public release and production signing remain separate owner decisions; packaging automation creates drafts.

## Reliability implementation follow-up

The first implementation batch addresses #97 and #99: pending-position preservation during recreation, actionable opening/output errors, cancellation returning to the previous reader snapshot, explicit bookmark reconnection and private-recovery retry. See [behavior](recovery.md) and [validation](validation-reliability.md). Validation passes on `35ecbc4`: 86 JVM tests, lint and hosted 129/129 device tests per API 29/36; unchanged application source also passes full local suites and repeated position-restoration checks. [PR #117](https://github.com/utrost/Textr/pull/117) is merged and included in 0.3-alpha; #98 remains open for #56 physical acceptance and #59 WebView investigation.

## Quality-of-life implementation

[PR #126](https://github.com/utrost/Textr/pull/126) implements #101, #109 and #120–#125. See [behavior and limits](quality-of-life.md) and [validation](validation-quality-of-life.md). It is merged and included in the private 0.4-alpha candidate; the immutable 0.3-alpha remains unchanged.
