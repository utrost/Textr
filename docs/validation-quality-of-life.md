# Quality-of-life implementation validation

Scope: #101, #109 and #120–#125, on `feat/quality-of-life`, stacked on Copy all PR #119. No release artifact is changed by this work.

## Checks

Debug app/test builds, lint and **88 JVM tests** pass (zero failures, errors or skips). Full Android API 29 and API 36 emulator regressions are in progress; results will be recorded before publishing the implementation PR.

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
