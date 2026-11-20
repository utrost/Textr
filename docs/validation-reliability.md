# Reliable daily use validation

Follow-up to 0.2-alpha under epic #98, addressing #97 and #99. The alpha tag and APK remain unchanged. Physical provider/foldable/TalkBack acceptance (#56) and the intermittent WebView accessibility investigation (#59) remain open.

## Position-loss reproduction and fix

The original [main CI run 44716759569](https://github.com/utrost/Textr/actions/runs/44716759569), source `07a1f7c4165bbb8fe00e8dd84e9826f957d39877`, failed API 29's `windowTransitionsPreserveDocumentModePositionAndFind`: expected scroll 600, actual 0. Its API 29 JUnit artifact was downloaded and inspected; the recorded failure is an assertion, not a dependency-download failure.

A fresh API 29 run of the three original foldable cases passed on base `8129a45`. That does not disprove the intermittent failure. A new deterministic regression, `recreationDuringPageLayoutKeepsPendingPosition`, observes layout in a recreated activity while the replacement WebView is unfinished, saves instance state and recreates again. Against the unchanged base application it fails: **expected pending position 600, saved 0**. This establishes an application state-loss path independently of test input timing; it does not prove which callback ordering occurred in the earlier hosted run.

Previously, rendering cleared `pendingScroll` when loading the page, and state saving read the unfinished WebView's temporary `scrollY`. The fix retains pending scroll until restoration completes and reads native scroll only from a ready WebView. It also preserves that pending value across another render. All existing position, mode, sidebar and find assertions remain; no deadlines were increased. The regression and original three cases passed together on API 29 after the fix.

## Recovery coverage

- `RecoveryDeviceTest`: denied access and retry after grants return; failed opens/recreation and return to the exact previous snapshot without provider access; cancellation of a slow replacement and rejection of its late completion; picker cancellation and different-URI selection preserving original settings, bookmarks and private draft.
- `EditorDeviceTest`: explicit retry of a failed checkpoint without another edit; unreadable recovery retained and retried; cancelled/denied save preserving dirty recovery across recreation, followed by successful retry to a new destination; existing encoding/BOM, atomic recovery, original integrity and large-text checks.
- `OutputDeviceTest`: permission/encoding/generic failure categories survive model loss; existing failed/partial/cancelled/interrupted output cleanup and matching-source safeguards remain covered.
- Existing reader integration/reload/bookmark checks now assert the specific access messages and explicit bookmark-reconnection choice.

## Validation status

Application implementation `9339194` passes build, **86 JVM tests and lint**, plus full local suites of **129/129 on API 29 and 129/129 on API 36**, with zero failures/errors/skips. Three further runs per API each repeat both the original transition test and the new regression: **6/6 additional cases per API pass**. Focused API 36 recovery/foldable/save-retry checks passed 9/9. A visual API 36 check also confirmed that the recovery explanation and actions fit at 200% system text.

[Initial hosted run 44764775777](https://github.com/utrost/Textr/actions/runs/44764775777) passes build/unit/lint and API 36 (129/129). API 29 passed 128/129: the existing `activeDocumentDirectFormatAndOfflineHelpAreDiscoverable` test could not find About Textr in the options popup. Its generic scroll helper could select the scrollable reader behind the menu. The test now targets the native popup ListView explicitly, retaining the same item and help assertions and timeouts. The six-case class and three standalone repetitions pass on API 29, and lint passes. The application is unchanged by this test correction; a new complete hosted matrix remains pending.

Intermediate checks caught a test-selector error (the save-retry test selected a dialog title sharing the action label); the test now selects the actual button. Another harness was corrected to use the picker because ActivityScenario cannot follow an activity after a new intent changes its identity. One overlapping local invocation interrupted a menu repetition; it was excluded and rerun serially. No application assertion was weakened or skipped.

Disposable Google APIs x86_64 emulators use API 29/WebView 74 and API 36/WebView 133, 2 GiB RAM and the repository's emulator-only ART setup (256 MiB growth limit). No physical-device or spoken TalkBack acceptance is claimed. No JavaScript, network permission, source-write access, new persistent reader-content cache or additional private draft is introduced.
