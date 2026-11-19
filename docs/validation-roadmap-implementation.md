# Roadmap implementation validation

Implementation started 19 November 2026, with an eight-hour work window. Features are delivered in tested batches; the original private alpha release is unchanged.

## Reading profiles and typography (#76, #77)

- Debug build, unit tests and lint passed for the first implementation.
- Targeted connected run: ReadingProfileDeviceTest and ReleasePolishTest, **13/13 on API 29 and 13/13 on API 36**. This covers profile isolation, switching, rotation, repository/open-session reconstruction, reset, shared-text privacy, legacy metadata, bounds and bookmark cleanup, plus existing cancellation, offline/source and 200% compact/wide dialog checks.
- Renderer test checks prose font/spacing, monospace code, bounded CSS values and unchanged source text.
- English and German controls/help are provided. Human speech-quality acceptance with TalkBack and physical-device review remain part of the existing beta acceptance issue #56.

## CI emulator baseline

The pre-existing API 29 hosted failure included an explicit allocation failure at a 16 MiB heap and subsequent large-file timeouts. The emulator action's pinned implementation sets `hw.heapSize` for its heap input, while the emulator uses `vm.heapSize`. The pre-launch configuration script sets 2048 MiB RAM and a 256 MiB VM heap using the actual AVD keys. Device jobs log the effective runtime heap properties. Application heap flags, source limits, assertions and deadlines are unchanged.

The native CSV 200% dialog test now waits for its asynchronously prepared window to attach and lay out before checking the existing full visibility/text-fit assertions.

## Structured navigation (#78, #79)

- Five JVM parser tests cover source-order duplicate JSON keys, exact numeric/escape spelling, UTF-16/CRLF offsets, XML namespaces/attributes/whitespace, malformed and over-budget input, large numbers, cancellation and file/network/internal-entity XXE rejection.
- StructureDeviceTest: **3/3 on API 29 and 3/3 on API 36**. Native tests cover exact path/value clipboard actions, expand/collapse, source jumps, source integrity, rotation, Android SAX namespace behavior, XXE refusal, compact/wide 200% text with dark theme, full action visibility and keyboard activation. Tests wait for native action dismissal before checking asynchronous clipboard/list effects.
- Debug build, JVM tests and lint passed. See [navigation syntax/security limits](structured-navigation.md).

## Everyday formats (#80–#84)

- Six JVM tests cover name/MIME precedence, special-name variants, exact-source escaped decoration, continuations/escapes, TSX/generic ambiguity, malformed syntax, character/token/cancellation limits, TSV quoting/defaults, binary `.ts` rejection and explicit legacy-properties encoding recovery.
- Initial RoadmapFormatsDeviceTest: **3/3 on API 29 and 3/3 on API 36**, covering source-only/offline rendering, original-byte copying, recreation, MIME intent resolution, TSV source-first/Tab defaults, explicit Comma/header persistence and metadata migration. Added source-search/manual-override checks are included in the following complete regression run.
- Debug build, JVM tests and lint passed. See [recognition and approximation rules](everyday-formats.md).

The local emulator currently selects a **512 MiB effective heap** from the configured 2048 MiB RAM / 256 MiB requested VM heap. Runtime properties are recorded rather than assuming the requested value is the final VM limit.

## HTML Markdown export and output flow (#85, #86)

- Strict streaming encoding JVM tests cover UTF-8/UTF-16 BOM choices, line endings, Unicode chunk boundaries, legacy encoding, unrepresentable/invalid text, output size and cancellation. The HTML export test verifies reuse of the actual converted Markdown, successful Markdown reopening, headings/tables, omissions and original-source integrity.
- Initial OutputDeviceTest: **5/5 on API 36**, covering successful reopen/BOM, read-only original/source-alias refusal, picker cancellation, denied/broken writes, unsupported cleanup, strict encoding failure, cancellable stalled pipes, metadata-only interrupted state and process-death picker cleanup. An additional slow-cleanup/reused-URI check is included in the following full run.
- HtmlExportDeviceTest: **3/3 on API 36**, including explicit picker integration, exact Markdown output, reopening/source bytes, pending snapshot rotation, shared-content cancellation, unavailable conversion and German resource labels. These also passed on the corrected API 29 runtime within the targeted regression run.
- Debug build, **80 JVM tests** and lint passed before the complete regression run.

## Corrected regression environment

The initial complete 94-test run exposed an intentional CSV metadata migration mismatch, stale 200% display state, animation timing and the API 29 system-server 16 MiB runtime problem. Non-default programmatic CSV options now correctly count as explicit choices, while automatically detected TSV defaults remain unconfigured. The disposable bootstrap now restarts Android with explicit ART budgets, resets display/font state and disables animations; instrumentation records the effective runtime. See [emulator diagnosis](test-emulator.md). Assertions, source limits and deadlines remain unchanged. A targeted API 29 rerun passed 43/44, including the unchanged 5 MiB check, loading cancellation, pathological fixtures, output and export; its sole sidebar-hint failure was traced to the old crash leaving font scale at 2.0. The following full run starts both emulators at 1.0.

## Expanded regression and comparison engine

The expanded run completed **104/104 on API 36**. API 29 exposed a popup-attachment race in `nativeTablePreparationCanBeCancelledAtTheCellBudget`: the UiAutomator click can return before the menu callback attaches its native window. The test now explicitly waits for that popup before performing the existing synchronous cancellation assertion; no assertion or deadline is weakened. API 29 completion and the corrected targeted check are recorded in the next validation update.

The bounded line-diff engine adds five JVM tests (included in the **80 passing JVM tests**): exact decoded-text/newline semantics, empty inputs, deterministic repeated-line behavior, 400 randomized round trips checked against an independent LCS distance, all configured budgets (including adversarial equal-hash character work), display alignment and cancellation. The native comparison UI remains a subsequent batch.

The expanded API 29 run finished **103/104**; the popup race was its only failure. The corrected cancellation check subsequently passed on **both API 29 and API 36**, together with all four native comparison checks on each platform. Full reports are retained for the failed run rather than presenting the targeted rerun as an entirely green full run. A final complete regression follows the remaining feature batches.
