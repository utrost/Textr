# Roadmap implementation validation

Implementation started 19 November 2026, with an eight-hour work window. Features were delivered in tested batches and are included in 0.2-alpha. The original 0.1-alpha artifact remains unchanged.

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

## TXT/Markdown copy editing (#90–#94)

The pre-implementation design is [editing-copies.md](editing-copies.md). The source/privacy/About/help/backup contract now distinguishes unchanged originals, output documents, metadata-only history and the sole content-bearing no-backup recovery draft.

- **85 JVM tests** pass, including five editor tests for exact BOM metadata, newline insertion policy, undo/redo/selection/dirty behavior, bounded history/overflow, strict UTF-16 fingerprints and literal case/word matching with one-step bounded replacement. Debug compilation and lint pass.
- EditorDeviceTest: **9/9 on API 29 and 9/9 on API 36**. Covers native source edits and exact CRLF, keyboard undo/redo, rotation with content-free saved state, private checkpoint location/model-loss recovery without the original source, one-draft protection, dirty Cancel/Discard, real picker output with UTF-16LE/BOM and exact bytes, revision-matching cleanup/new recovery after later edits, cancellation/denial, explicit legacy conversion failure, stale-save refusal, literal replace-all undo, German labels, compact 200% text and format/size rejection.
- The initial editor run caught all-caps result labels and a misconfigured denied-output fixture. Its checkpoint failure simulation was specific to the newer AtomicFile implementation; the corrected test denies directory/file writes on both platforms and verifies the previous checkpoint survives before retry. All nine checks pass after correction.
- A complete **117-test matrix per API** is running with all features together. Targeted results do not substitute for that complete regression. Physical foldable/speech-quality checks remain under #56; no production signing or release artifact is changed.

## Final editor review and performance bounds

The intermediate 117-test run finished **116/117 on both APIs**. Its only failure was the About license link moving below the initial viewport after the privacy explanation grew. About now presents version/links before detailed policy text; assertions are unchanged. The expanded More menu also requires the existing menu reachability test to scroll its native popup on shorter displays.

Review added preservation when cancelling a blocked new-copy request, explicit discard for unreadable checkpoints, caret recovery, one active editor/comparison screen, captured BOM indicators, a saved-output reference/open action, retained in-memory find controls and confirmation above 20 replacements. Reader/editor search now shares the reader’s linear-time Unicode KMP matcher; replacements avoid allocating a range for every match.

A new 200% software-keyboard test exposed an Android text-layout timeout for a 256 Ki-character single paragraph. Editor eligibility and every mutation now additionally enforce **4,096 characters per physical line and 10,000 lines**. The source reader keeps its 5 MiB limit. The unchanged maximum-character stress check passes on both APIs using 64-character physical lines, including visible Back while the software keyboard is open and atomic overflow rejection. Replacement entry is separately capped at 4 Ki characters to bound its native text control. No timeout was extended.

**86 JVM tests** and lint pass for this implementation. The expanded targeted matrix contains 13 editor checks, four comparison checks, two reader-entry/handoff checks and the existing About/menu check (20 per API). Editor fixture cleanup waits for intentionally finishing checkpoint workers; dialog assertions wait for posted click callbacks. A final complete matrix contains **123 tests per API**. Completion evidence follows below.

## Completed full validation and final navigation follow-up

| Check | Android 10 / API 29 | Android 16 / API 36 |
| --- | --- | --- |
| Complete local regression, source `f1cad07` | **123/123 passed** | **123/123 passed** |
| Complete [hosted run 44573677985](https://github.com/utrost/Textr/actions/runs/44573677985), source `f1cad07` | **123/123 passed** | **123/123 passed** |
| Subsequent find-navigation corrections, focused native checks | **2/2 passed** | **2/2 passed** |
| Actual forced process loss, task reopening, exact recovered text and explicit discard | **Passed** | **Passed** |
| Unfinished checkpoint-file recovery | **Legacy `.bak` restored** | **Incomplete `.new` ignored/removed** |

The full local Gradle command completed successfully in 15m47s, with debug build, **86 JVM tests**, lint and both connected suites. The hosted run also passed its build/unit/lint job using the pinned emulator. Reports are retained in the GitHub run artifacts. The final PR checks verify the pushed head after the small navigation follow-up.

Final native checks correct Previous from an unselected search to select the last match, and make single Replace advance past inserted replacement text when that text itself contains the query. Both operations retain undo/redo and UTF-16 selection positions. The focused tests cover single/all replacement, first Previous/Next wrapping, confirmation and rotation; no assertion or deadline was relaxed.

The process-loss probes use synthetic shared text and a completed private checkpoint, move the app to the background, forcibly kill its PID, simulate the platform’s unfinished checkpoint layout with app ownership preserved, then reopen the task/resume action. A new process restores exact text without the original in-memory shared source. Explicit Discard then removes the checkpoint. These probes run on both APIs; screenshots also confirm the recovered editor and native aligned wide comparison.

All 19 roadmap implementation issues (#76–#94) have candidate implementations on PR #96. Epics/children remain open for integration and acceptance. Physical foldable and human speech-quality review under #56, production signing and release/publication gates remain separate. The existing `v0.1-alpha` artifact is unchanged.

## Security gesture probe and WebView accessibility independence

Final application source `9a84ef4` passed all 123 hosted API 29 checks. Its first API 36 attempt was obstructed by a System UI ANR dialog during the initial unchanged accessibility tests. A fresh-runner retry passed **122/123**; its only failure was the legacy offline-safety probe finding no rendered link in Android’s accessibility subtree. The captured hierarchy contained Preview mode and empty WebView descendants, consistent with the existing WebView 133 investigation (#59), rather than establishing an offline-policy failure. Failed attempt logs/reports are retained.

The security test now monitors the complete activity launch/render lifecycle, verifies zero external BROWSABLE launches while loading, while tapping blank document space and while focusing a link, then uses real Tab/Enter browser activation and requires exactly one launch to the fixture’s precise HTTPS URL. It does not enable JavaScript, query the DOM through a bridge, fake a trusted navigation callback or extend deadlines. Existing network/image/file/content/storage restrictions, blocked-request checks, shared-content disk scans and original-source integrity assertions remain. Reader fixture metadata is reset and a blank-area touch establishes consistent DOM/input focus before keyboard navigation. This tests user-gesture security independently of the known virtual-tree omission; it does not claim that #59 or human spoken-feedback acceptance is resolved. No application code changed for this follow-up.

The revised two-check offline suite passed three consecutive runs on **each API** (6/6 checks per API), and the final editor → HTML reading → HTML export → offline sequence passed **21/21 on API 29 and 21/21 on API 36**. Instrumentation compilation and lint pass. The full hosted matrix retains all 123 checks per API for the final revision.

## Final hosted acceptance and private release

[Hosted run 44590125674](https://github.com/utrost/Textr/actions/runs/44590125674) at `352d178254749316fd71972148629d785297d4f2` passed build/lint, **86 JVM tests**, and **123/123 device tests on each API 29 and API 36**. This supersedes the intermediate failures recorded above; reports remain attached to the run. The WebView investigation and physical/human acceptance remain open.

On 20 November 2026, release commit `e4d3ce7182978d235f6035f439e4f169adff5b60` changed only version defaults and release documentation. Its clean release build/lint passed. The `v0.2-alpha` APK was verified for signature, ZIP alignment, package/version, non-debuggable status, absent network/broad-storage permissions and packaged notices. On API 36, upgrading the original signed 0.1-alpha preserved its dismissed hint setting; fresh installation and cold launch also passed. All downloaded draft assets passed their uploaded SHA-256 checksums and the APK signature was verified again. See the [draft release](https://github.com/utrost/Textr/releases/tag/untagged-91b226e699ea4047e362) for artifact-specific hashes and verification output. These release smoke checks do not substitute for physical-device review.
