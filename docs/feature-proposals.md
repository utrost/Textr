# Proposed feature roadmap

Drafted 19 November 2026 for discussion. This is a product recommendation, not implementation authorization or a dated release commitment. The [delivery roadmap](roadmap.md) tracks accepted work and integration. Textr currently remains read-only, offline and limited to 5 MiB source files; editing, exports, additional formats and comparison below are proposed scope extensions where applicable.

## Recommended direction

Make Textr a convenient reader and inspector for notes, saved web documents, source and configuration files. Improve navigation and reading comfort before widening the product into an editor. Add formats in small, useful batches; distinguish opening text, syntax highlighting and a semantic preview rather than promising all three for every extension.

## Delivery order

| Phase | Recommended work | User benefit | Relative effort |
| --- | --- | --- | --- |
| Finish the current candidate | Accept reader-polish fixes, resolve hosted validation and integrate HTML reading (#68/#69) | A dependable base and a testing APK that includes the approved work | Existing work; acceptance pending |
| Reader improvements | Per-document font/wrapping preferences; line-spacing/font choices; navigation within JSON/XML | More comfortable prose/code reading and faster inspection of structured files | Medium |
| Focused format batch | Explicit `.env`, `.properties`, Dockerfile and TypeScript recognition/highlighting; TSV recognition using existing tab-delimited CSV support | Better everyday configuration/source handling without new document engines | Small–medium; select a subset |
| Export and compare | Save converted HTML as a new Markdown file; bounded comparison of two text documents | Reuse saved articles and inspect configuration/text changes | Export: small–medium; compare: medium–large |
| Optional light editing | Explicit edit mode for plain text and Markdown, starting with Save as copy | Make small corrections without leaving Textr | Large; requires a product/scope decision |
| Later decisions | In-place editing, additional languages, rich document formats or large/live logs | Broader workflows if actual use warrants them | Evaluate separately |

These phases need not become separate app versions. Feedback can change the order; no deadline or next version number is assigned.

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

Choose the destination through Android's document picker, handle failed/cancelled/partial writes, and verify the generated file can be reopened. Do not replace or rename the source document. This introduces an explicit output-file operation and needs an approved scope/permission/UI extension, even though the input remains unchanged.

## 4. Compare two documents

Offer bounded text comparison of two explicitly chosen documents, using existing open documents where possible. Show additions/deletions and jump to the next difference. A wide layout can show side-by-side text; compact windows need a readable unified view.

Define byte/line/diff-work limits and cancellation before choosing an algorithm. Provide a clear fallback for very different or oversized inputs. Comparison remains read-only; it does not imply merging files or a Git client. Existing diff/patch highlighting displays an already-created diff and does not compute this comparison.

## 5. Editing: useful, but a distinct mode

Recommend light editing after reader reliability. It would change Textr's read-only promise, storage behavior, privacy documentation and validation requirements; discussion of this proposal does not authorize implementation.

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

After selecting a batch, create focused issues with supported input/output, limits, acceptance tests and migration/privacy changes. Keep proposals separate from accepted work in #14 and the delivery roadmap.
