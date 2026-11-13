# Open documents and navigation

The owner authorized backlog issues #28–#33 on 2026-11-13. This extends the original single-document v1 scope while preserving read-only operation, Android 10+, the 5 MiB source limit, and no network permission. Public release remains deferred.

## Document switcher

The toolbar's document count and **Open documents** menu show up to five open documents. The active entry has a check mark and spoken label. Screens at least 600 dp wide with font scale at most 130% also show a scrolling tab strip when two or more documents are open. Larger fonts and phones retain the compact switcher; tabs are alphabetically ordered independently of LRU eviction. Select a row to switch or its labeled close action to close it. Opening the same URI moves its existing entry to the most recently used end. A sixth document evicts the least recently used entry; its recent-history entry remains independently available.

Only the active URI document retains loaded source, preview, and a WebView. Inactive URI documents are read again when selected. Each entry retains position, display mode, format and encoding overrides, search query, source-search options, and whether Find is open. Find starts at the first matching occurrence when the document is reloaded. Changed files and layout changes can make restored positions approximate; the WebView clamps positions to the new content height.

One incoming shared-text document can be retained in memory alongside URI references. Another incoming text replaces it. It counts toward the five-document limit and is lost when the process dies. No document contents are saved as session state on disk.

URI references, names, and reading metadata are saved separately from recent history. A fresh launch starts on Home; the switcher can reopen retained references. Android activity restoration can restore the active URI after process death. Search queries and shared text are not saved in the session preferences; Android's existing activity-state restoration retains the current Find query during activity recreation. Temporary grants may expire, in which case reopening shows the existing access-expired message and picker action.

Back first closes Find, then returns to Home, then exits. Returning Home leaves document entries open. Closing a document removes its session state, not its recent entry or source file. Clearing history likewise leaves open documents alone. A persisted URI grant is released only when history, open documents, and a pending Undo no longer need it.

## Recent files and Undo

Home shows the last-opened time and the provider authority or file directory where available. Up to ten of the thirty history entries can be pinned. Pinned entries appear first and survive ordinary history eviction; unpinned entries retain recency order. The star controls have named accessibility labels.

Removing an entry offers **Undo removal** for ten seconds. The reference, reading metadata, pin state, and URI grant remain available during that window. A subsequent removal or Clear history commits the pending removal. If the process stops before expiry cleanup, the next launch or history interaction commits it. Undo restores an entry unpinned if ten other entries have since been pinned. Removing history never deletes source files.

## Reload and navigation

**Reload** explicitly rereads a URI-backed file; there is no background polling. Mode, format and encoding overrides, search query, and position are retained where applicable. A preview that becomes invalid falls back to a supported source mode. Missing or revoked files show a readable error and offer reopening through the picker. Incoming text without a URI has no Reload action.

**Reading settings → Show source line numbers** adds logical line numbers to plain/highlighted source. Wrapped visual rows keep the same logical line number. Decorations are excluded from selection and accessibility text, and sharing always uses original bytes. Numbering is limited to 10,000 logical lines to bound DOM work; larger sources remain readable. **Go to line…** works across the complete source by rendering only the requested anchor when numbering is unavailable. LF, CRLF, and standalone CR are recognized as line endings. JSON and Markdown previews have no source-line numbering.

**Heading outline** is available for supported Markdown previews. Labels include inline text/code and heading depth; duplicate titles receive distinct generated anchors, and empty headings receive an “Untitled heading” label. The outline shares the existing Markdown input/node/depth budgets. Jumps use local fragments, with JavaScript, network loading, and external resources still disabled.

## Open with

**Open with…** resolves compatible Android VIEW handlers and excludes Textr. The dialog explicitly identifies the handoff as a read-only copy. Selection starts that external activity directly, avoiding a resolver loop; no compatible handler or a disappeared handler produces an understandable message.

The copy preserves original source bytes and uses the same bounded private cache and per-URI read-only grants as Share file. A receiving editor can make its own copy, but is not granted write access to Textr's copy or the original. Android package visibility declares VIEW intent queries; no broad package-list permission is requested. Copies expire on the next start or copy operation after 24 hours, with at most twenty retained.

See [search and encoding details](everyday-reading.md) for recovery, metadata, and source-results navigation.
