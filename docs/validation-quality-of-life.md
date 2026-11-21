# Quality-of-life implementation validation

Scope: #101, #109 and #120–#125, on `feat/quality-of-life`, stacked on Copy all PR #119. No release artifact is changed by this work.

## Checks

Debug app/test builds, lint and **88 JVM tests** pass (zero failures, errors or skips). The final 18-case focused regression set passes on each of Android API 29 and API 36, followed by a further format-metadata/filtering check on each API. Full-suite outcomes and immutable hosted-run links are recorded in [PR #126](https://github.com/utrost/Textr/pull/126) and its [checks](https://github.com/utrost/Textr/pull/126/checks). The PR description records the exact tested commit alongside its immutable hosted run. Navigation retention also has a JVM regression for keeping the most recently reopened document when the bounded history evicts an older entry.

New coverage checks combined recent-file filtering/recreation, bounded navigation and changed fingerprints, previous-position restoration after Go to line, exact converted/source clipboard behavior and oversized-copy rejection, bounded/session-only closed history and duplicate handling, UI reopen after recreation, foreground screen-on lifecycle, actual native selection-to-source-search with an unchanged clipboard, Unicode/multiline queries and rejection without option loss, unsaved draft preview/selection/undo/recreation, and successful-only save-location updates.

Draft preview tests require a visible viewport and restored preview scroll, with JavaScript/file access disabled and network loads blocked. Both physical provider behavior and human accessibility acceptance remain open in #56; emulator results do not establish those outcomes.

## Findings resolved during implementation

- Native text-processing actions must be discovered by WebView, rather than added as arbitrary menu intents. The handler uses a signature permission, verifies its same-app caller, and consumes a weak reference to the initiating reader snapshot. No clipboard or JavaScript bridge is involved.
- An AlertDialog custom panel could measure a weighted preview at zero height. A dedicated full-window dialog now gives the preview a real viewport; readiness and scroll restoration wait for a draw.
- Reopening an already open reference preserves its newer entry instead of overwriting it with older closed metadata.
- Search state is included in previous-position entries, and changed fingerprints invalidate obsolete locations.
- UI tests wait for the native options popup before looking for an action, and identify the platform selection-toolbar overflow button explicitly.

## Platform contracts

The save hint uses [DocumentsContract.EXTRA_INITIAL_URI](https://developer.android.com/reference/android/provider/DocumentsContract#EXTRA_INITIAL_URI), passing the opaque successful document URI. Provider-specific support and location fallback are controlled by Android/the provider.

Selection integration follows [Android ACTION_PROCESS_TEXT](https://developer.android.com/reference/android/content/Intent#ACTION_PROCESS_TEXT); [Chromium's native selection helper](https://chromium.googlesource.com/chromium/src/+/refs/tags/133.0.6943.141/content/public/android/java/src/org/chromium/content/browser/selection/SelectActionMenuHelper.java) discovers exported text processors, which is why the destination additionally requires Textr's signature permission.

## Regression findings and first full runs

The first local full runs executed 142 cases per API: API 29 passed 139, and API 36 passed 140. Failures exposed menu helpers that assumed actions/settings remained on screen, a comparison check preceding the first resized layout, and a selection test depending on an intermittently missing WebView accessibility node. Helpers now scroll the native popup/settings container, wait for the same full-visibility assertion, and exercise native text selection even when the virtual text node is absent. The behavior assertions remain.

An additional active-Find regression reproduced a real position-restoration bug: the recorded position was 650, but Chromium later moved it to 0 while finding the restored query. Query changes no longer start searches on an outgoing page. Each rendered page retains the intended position through its first Find, waits for native scrolling to settle (200 ms quiet period, bounded to two seconds), and cancels restoration on user touch, keyboard or wheel input. The regression verifies query, position and recreation together. Inactive/pending restoration is cancelled with the page's generation. Search/navigation, settings, document switching, draft preview/recovery and comparison layout all pass in the 18-case runs.

Markdown classification now survives draft recovery without a filename extension; legacy checkpoints infer it from the name. Recent metadata retains the last detected format, so extensionless names can be filtered without provider reads. Explicit overrides take precedence. Both use backward-compatible optional metadata.

[First hosted run 44915162377](https://github.com/utrost/Textr/actions/runs/44915162377) passed build/unit/lint. API 29 failed only the settings menu helper. API 36's retained accessibility report shows a blocking **“System UI isn't responding”** dialog near the start of the suite, followed by widespread missing-control failures; that run also contains the now-fixed search-position and settings cases. It is retained as failed evidence, not treated as acceptance. The updated candidate receives a fresh full hosted run; use the linked PR checks for its result, rather than treating this earlier failed run as current acceptance.
