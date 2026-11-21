# Textr 0.5-alpha — private testing candidate

Version code **5**, Android 10/API 29 and newer, target API 36. This candidate completes the ten remaining feature/automation implementations. Private draft assets require repository write access. Public APK/store publication and production signing remain deferred; older versions stay immutable.

## New behavior

Reading-position recovery now captures a snapshot before Android framework state-save callbacks can reset an old WebView, fixing a deterministically reproduced 600 → 0 path. Source-search controls also stay stationary as asynchronous result rows change, preventing the dialog from moving between option taps.

- Editor Go to line, selected-line indentation/unindentation and keyboard shortcuts, plus reliable **Open saved copy** after successful Save as.
- Review the unsaved draft against its initial original snapshot. Display-only spaces/tabs and line-ending comparison filters remain separate from exact patch export.
- Save a bounded standard UTF-8 unified diff through the system picker, with anonymous headers and explicit confirmation when filters hide differences.
- Organize references in named collections, independently of recent history. Limits: 20 collections, 30 references each, 200 memberships total; no document contents are stored in collections.
- Navigate JSONL/NDJSON records, including invalid and blank lines; read highlighted AsciiDoc/rST source; view checked/unchecked Markdown tasks as read-only content.
- Prepare private drafts from an explicit tested commit with matching source, version, signature, manifest, checksums and downloaded-byte verification.

Includes all eight QoL improvements from 0.4-alpha. Native controls and generated labels are English/German. See [usage and limits](roadmap.md), [validation](validation-roadmap-completion.md) and [current release assets](https://github.com/utrost/Textr/releases).

## Installation and privacy

A candidate signed with the same approved development identity can update earlier private alphas in place; verify `SIGNING.txt` and `SHA256SUMS`. A different signing key requires reinstalling and removes local settings, references and recovery. Save needed drafts first. The selected standard F-Droid signing channel will use F-Droid’s separate identity and cannot update these development-signed alphas in place. There is no F-Droid publication yet.

Copy editing still uses one bounded private recovery file; it now also contains the initial original decoded text/encoding/BOM for comparison. Legacy recovery remains editable and clearly disables unavailable original comparison. Collections retain references/read grants independently from recent history. Patch export creates a separate user-selected output. See the [privacy policy](privacy.md); developer **utrost**, privacy/support through [public Textr issues](https://github.com/utrost/Textr/issues).

## Acceptance limits

Original files remain unchanged. No accounts, advertising, analytics or app network permission. JSONL/highlighting/render/editor/diff/output limits remain explicit. Patch application, in-place editing, multiple recovery drafts and direct website fetching are outside this version.

Emulator and automated accessibility checks do not establish human TalkBack speech quality, physical-provider behavior or foldable acceptance (#56). The underlying intermittent WebView virtual-node investigation (#59) remains open despite the native CSV/source fallback. F-Droid eligibility/submission, Play account choices and public publication remain separate.
