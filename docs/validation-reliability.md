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

Build, 86 JVM tests and lint pass. Final full API 29/36 device suites are in progress; this record will be updated with the exact implementation commit and completed results before delivery. Intermediate checks included one test-selector error (the save-retry test selected a dialog title sharing the action label); the test now selects the actual button. A separate test harness was corrected to use the picker because ActivityScenario cannot follow an activity after a new intent changes its identity.

Disposable Google APIs x86_64 emulators use API 29/WebView 74 and API 36/WebView 133, 2 GiB RAM and the repository's emulator-only ART setup (256 MiB growth limit). No physical-device or spoken TalkBack acceptance is claimed. No JavaScript, network permission, source-write access, new persistent reader-content cache or additional private draft is introduced.
