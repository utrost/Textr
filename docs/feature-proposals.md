# Feature roadmap and implementation backlog

Updated 20 November 2026. All five epics and 19 implementation issues below are implemented in [PR #96](https://github.com/utrost/Textr/pull/96) and available in the **0.2-alpha private testing release**. Integration and acceptance remain pending. Build/lint, 86 JVM tests and 123 device tests per API 29/36 passed; see [validation](validation-roadmap-implementation.md). The [delivery roadmap](roadmap.md) records current testing, integration and distribution work. The remaining sections retain the design rationale and implementation dependency order, rather than an unimplemented feature queue. The reader remains offline with a 5 MiB source limit and unchanged originals; export creates separate outputs and copy editing retains one private recovery draft.

## Epics and implementation backlog

Five epics and 19 implementation issues were created on 19 November 2026. They are native GitHub sub-issues: [roadmap #14](https://github.com/utrost/Textr/issues/14) → epic → implementation issue. Epics also include completion checklists. Existing alpha/reader-polish, HTML (#68/#69), beta (#56) and distribution issues remain in use.

| Sequence | Epic | Implementation issues | State |
| --- | --- | --- | --- |
| 1 — reader improvements | [#71](https://github.com/utrost/Textr/issues/71) — Reading comfort and structured navigation | [#76](https://github.com/utrost/Textr/issues/76), [#77](https://github.com/utrost/Textr/issues/77), [#78](https://github.com/utrost/Textr/issues/78), [#79](https://github.com/utrost/Textr/issues/79) | In 0.2-alpha; integration/acceptance pending |
| 2 — focused format batch | [#72](https://github.com/utrost/Textr/issues/72) — Everyday text and configuration format support | [#80](https://github.com/utrost/Textr/issues/80), [#81](https://github.com/utrost/Textr/issues/81), [#82](https://github.com/utrost/Textr/issues/82), [#83](https://github.com/utrost/Textr/issues/83), [#84](https://github.com/utrost/Textr/issues/84) | In 0.2-alpha; integration/acceptance pending |
| 3a — explicit export | [#73](https://github.com/utrost/Textr/issues/73) — Export HTML reading content as Markdown | [#85](https://github.com/utrost/Textr/issues/85), [#86](https://github.com/utrost/Textr/issues/86) | In 0.2-alpha; integration/acceptance pending |
| 3b — compare documents | [#74](https://github.com/utrost/Textr/issues/74) — Bounded read-only document comparison | [#87](https://github.com/utrost/Textr/issues/87), [#88](https://github.com/utrost/Textr/issues/88), [#89](https://github.com/utrost/Textr/issues/89) | In 0.2-alpha; integration/acceptance pending |
| 4 — deliberate editing extension | [#75](https://github.com/utrost/Textr/issues/75) — Optional plain-text and Markdown editing of copies | [#90](https://github.com/utrost/Textr/issues/90), [#91](https://github.com/utrost/Textr/issues/91), [#92](https://github.com/utrost/Textr/issues/92), [#93](https://github.com/utrost/Textr/issues/93), [#94](https://github.com/utrost/Textr/issues/94) | In 0.2-alpha; integration/acceptance pending |

The implementation sequence below is complete in the testing release. Next: review/integrate the stacked candidate and collect private beta feedback. Physical-device/human accessibility acceptance and distribution gates remain separate. Later product extensions require their own scope decisions; no future dates or versions are committed.

## Dependencies and completion

- Reading typography follows per-document preference precedence: [#76](https://github.com/utrost/Textr/issues/76) → [#77](https://github.com/utrost/Textr/issues/77). JSON and XML navigation can be developed independently with their own parser/security/budget limits.
- HTML export follows the existing converter/reading view (#68/#69) and the new output flow: [#85](https://github.com/utrost/Textr/issues/85) → [#86](https://github.com/utrost/Textr/issues/86). The same output flow is reused by edit-copy saving.
- Comparison: [#87](https://github.com/utrost/Textr/issues/87) → [#88](https://github.com/utrost/Textr/issues/88) → [#89](https://github.com/utrost/Textr/issues/89). Wide comparison retains unified/large-text fallback.
- Editing: [#90](https://github.com/utrost/Textr/issues/90) → [#91](https://github.com/utrost/Textr/issues/91) → [#92](https://github.com/utrost/Textr/issues/92) → [#93](https://github.com/utrost/Textr/issues/93); saving also depends on [#85](https://github.com/utrost/Textr/issues/85). Draft find/replace ([#94](https://github.com/utrost/Textr/issues/94)) follows the edit session.

Each implementation issue specifies outcome, acceptance criteria, dependencies and verification. Close children with exact-source evidence and user-facing documentation; close an epic only when its children are accepted. Preserve offline operation, Android 10+, existing source limits and source integrity. Scope/privacy changes for output files and recovery drafts are resolved in their design issues before delivery.

## Recommended direction

Make Textr a convenient reader and inspector for notes, saved web documents, source and configuration files. Improve navigation and reading comfort before widening the product into an editor. Add formats in small, useful batches; distinguish opening text, syntax highlighting and a semantic preview rather than promising all three for every extension.

## Delivery order

| Phase | Recommended work | User benefit | Relative effort |
| --- | --- | --- | --- |
| Integrate the current candidate | Review the tested reader/HTML/roadmap stack and collect 0.2-alpha feedback | An accepted base for the next distribution decision | Implementation/testing release complete; review pending |
| Reader improvements | Per-document font/wrapping preferences; line-spacing/font choices; navigation within JSON/XML | More comfortable prose/code reading and faster inspection of structured files | Medium |
| Focused format batch | Explicit `.env`, `.properties`, Dockerfile and TypeScript recognition/highlighting; TSV recognition using existing tab-delimited CSV support | Better everyday configuration/source handling without new document engines | Small–medium; deliver issues individually |
| Export and compare | Save converted HTML as a new Markdown file; bounded comparison of two text documents | Reuse saved articles and inspect configuration/text changes | Export: small–medium; compare: medium–large |
| Optional light editing | Explicit edit mode for plain text and Markdown, starting with Save as copy | Make small corrections without leaving Textr | Large; begin with scope/recovery design issue [#90](https://github.com/utrost/Textr/issues/90) |
| Later decisions | In-place editing, additional languages, rich document formats or large/live logs | Broader workflows if actual use warrants them | Evaluate separately |

The reader, formats, export/comparison and editing phases are all included in 0.2-alpha. Relative effort records the original planning assessment. Later decisions remain uncommitted.

## 1. Improve the reader

- **Per-document reading profiles:** remember text size and wrapping independently for prose and code. Provide clear global defaults and Reset for this document. Mode, position and format are already remembered; do not duplicate those features.
- **Typography:** offer a small font selection and adjustable line spacing. Verify enlarged text, contrast, selection/copy, search and approximate position restoration.
- **Structured navigation:** bounded JSON/XML outlines or expandable trees, with copy-value/path actions and a way back to complete source. Never rewrite the original file or silently drop duplicate JSON keys, whitespace-sensitive XML or unsupported structure.

Acceptance should include lifecycle restoration, keyboard and screen-reader access, existing source/preview budgets, and graceful fallback on malformed data.

## 2. Add formats deliberately

| Priority | Candidate | Proposed support |
| --- | --- | --- |
| First | `.env`, `.properties`, Dockerfile | Explicit recognition and bounded source highlighting; preserve comments, spelling and all bytes; do not load variables or execute instructions |
| First | `.ts`, `.tsx` | Source highlighting and manual format override; no JavaScript/TypeScript execution. JSX/TSX coloring needs its own accuracy tests |
| First | `.tsv` / tab-separated MIME inputs | Recognition and convenient access to existing CSV tables with Tab selected. Tab parsing already exists; this is detection/default integration, not a new table engine |
| On request | Go, Rust and other programming languages | Small source-highlighting batches based on real files and demand |
| Separate evaluation | AsciiDoc or reStructuredText | Decide whether source highlighting or a bounded semantic preview is useful before adding a parser |
| Defer | PDF, DOCX, EPUB | Separate rendering, extraction or archive workflows with their own accessibility, resource and size policies; not a routine extension of the current text reader |

Some candidate files can already open as plain text. A format issue should say exactly what recognition, colors or navigation it adds. Keep extension/name detection, MIME fallback, manual overrides, source search, byte-preserving sharing and fallback behavior consistent.

## 3. Export HTML reading output as Markdown

The HTML reading candidate already converts local/shared HTML into Markdown internally. Add an explicit **Save Markdown as…** action that creates a new user-selected file. Keep original-file sharing distinct from exporting generated content. Explain deliberate omissions and retain the converter's limits, cancellation and safe-link policy.

Choose the destination through Android's document picker, handle failed/cancelled/partial writes, and verify the generated file can be reopened. Do not replace or rename the source document. This explicit output-file operation is planned under [#73](https://github.com/utrost/Textr/issues/73); [#85](https://github.com/utrost/Textr/issues/85) defines its scope, permission and UI behavior while preserving the input.

## 4. Compare two documents

Offer bounded text comparison of two explicitly chosen documents, using existing open documents where possible. Show additions/deletions and jump to the next difference. A wide layout can show side-by-side text; compact windows need a readable unified view.

Define byte/line/diff-work limits and cancellation before choosing an algorithm. Provide a clear fallback for very different or oversized inputs. Comparison remains read-only; it does not imply merging files or a Git client. Existing diff/patch highlighting displays an already-created diff and does not compute this comparison.

## 5. Editing: useful, but a distinct mode

Light editing follows reader reliability under [#75](https://github.com/utrost/Textr/issues/75). It changes the current read-only promise, storage behavior, privacy documentation and validation requirements. Resolve the source/encoding/draft policy in [#90](https://github.com/utrost/Textr/issues/90) before the edit/save/recovery implementation tasks.

Start with **Edit a copy** for plain text and Markdown:

- Keep reading as the default, with an unmistakable edit state. Edit original source text, not rendered HTML/Markdown preview widgets.
- Provide undo/redo, selection, find/replace, dirty-state feedback and explicit Save as/Discard/Cancel behavior on navigation and exit.
- Start with a clearly defined encoding subset. Preserve supported encoding/BOM/line-ending choices or require an explicit output conversion choice. Never silently normalize the original document.
- Define recovery for unsaved changes across rotation and process termination. If private recovery drafts retain document contents, document retention/deletion, backup exclusions and cleanup; this is a change from the current metadata-only history policy.
- Create a new destination document through the system picker. Saving must handle provider denial, full storage, cancellation and interrupted writes. Confirm successful output before discarding the recovery draft.

Only consider **Edit original** after that is dependable. It additionally needs provider write capability and URI grants, external-change detection, clear failure/recovery behavior and provider-specific overwrite/atomicity checks. Offer Save as when safe replacement is unavailable. Do not add automatic formatting, executable previews, project management or an IDE toolchain to the first editing iteration.

Android's Storage Access Framework supports creating and editing user-selected documents; its write flow and provider capabilities need explicit handling. See [Android document storage guidance](https://developer.android.com/training/data-storage/shared/documents-files).

## Suggested first selection

Finish acceptance of the approved candidate, then choose **reading profiles + structured navigation + a small configuration-format batch**. Follow with **HTML-to-Markdown export** and **bounded comparison**. Treat editing as the next deliberate product decision, starting with copies and Markdown/plain text. This sequence adds practical capabilities while preserving a dependable reading workflow.

The linked implementation issues now define the work breakdown and acceptance criteria. Select and deliver small batches in the order above, recording tested source and updating #14. Additional languages, rich formats, in-place editing and large/live logs remain deferred ideas rather than part of the five epics.
