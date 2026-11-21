# Roadmap completion validation

Work dated 21 November 2026 on `feat/roadmap-completion`, based on merged QoL main `7637613`. [PR #127](https://github.com/utrost/Textr/pull/127) tracks exact integration and hosted checks. Scope: #102/#103/#105–#107/#110/#112–#114/#116, distribution preparation #53/#62–#65, and reproduced #97/#128 follow-ups. This record does not certify physical providers, spoken TalkBack, foldables or store acceptance.

## Build and independent checks

- Debug/application-test builds, Android lint and **102 JVM tests** pass. Pure tests cover editor actions/undo/bounds, comparison filters, exact patches, format detection, JSONL invalid/deep/limited records, source-preserving markup and read-only tasks.
- **13 unified patch fixtures** apply independently with GNU `patch --binary --batch --fuzz=0`; resulting bytes match expected UTF-8 snapshots, including empty files, CR/CRLF and missing final newlines.
- **14 offline release guard tests** pass in disposable repositories: explicit source/version, clean checkout, monotonic versions, immutable local/remote tags, exact successful Android CI and PR merge/source-tree equivalence, required non-skipped matrix jobs, certificate/manifest protection, complete verified assets and unsafe/corrupt download refusal. Tests never upload or access keys.
- English/German metadata, code-5 changelogs, all PNG structure/dimensions and local Markdown file targets validate.
- Unsigned release inspection confirms non-debuggable `de.utrost.textr`, code **5 / 0.5-alpha**, minimum API **29**, target **36**, no network permission and bundled GPL/notices. Missing owner credentials refuse the default signed release path.
- The existing approved development identity successfully builds APK and AAB rehearsals outside Git; APK certificate/manifest inspection passes and `jarsigner -verify` reports **jar verified**. No production/upload key was created.

Runtime dependencies are unchanged: AndroidX/Kotlin/coroutines (Apache-2.0), CommonMark/GFM tables (BSD-2-Clause), Jackson Core (Apache-2.0 with notices) and jsoup (MIT). Notices remain bundled. Screenshots/artwork are synthetic GPL-3.0-only repository assets; no proprietary SDK or runtime service was added.

## Android matrix and coverage

Disposable API 29 (`emulator-5560`) and API 36 (`emulator-5562`) emulators use synthetic documents/providers. Local WebView providers are **74.0.3729.185** and **133.0.6943.137**, confirmed by `dumpsys webviewupdate`; neither is evidence about current stable WebView. The current suite has **162 device cases**, adding deterministic state-save and asynchronous-search-layout regressions. The earlier matrices below contain 161 cases. No application assertion is skipped or disabled.

The position-fixed full API 29 run passes **161/161** in **994.043 seconds**, followed by **10/10** repetitions of the unchanged window-transition test. Hosted run [44958947702](https://github.com/utrost/Textr/actions/runs/44958947702), source `1f7890fac6a245f30ff8e30d42b5d10186e0fdb9`, passes API 29 **161/161** and build/unit/lint; API 36 finishes **158/161** (no errors/skips), retaining two source-node failures and a search-option assertion. The newest cache-refresh probes and stronger option-click assertions require their own exact-head checks before packaging.

Two local full API 36 sequences pass **160/161**, retaining the source-node failure described below. Eight isolated repetitions of the old selection probe pass. All nine Reader UX cases then pass on both APIs using individually refreshed nodes (74.157 / 59.849 seconds), but another full sequence still fails; short successful probes do not establish a fix. A subsequent full run clears the global automation cache where supported before refreshed selection traversal: selection sharing passes, but the older line-number/copy test fails source exposure (**160/161**, 811.887 seconds). That test and the basic copy test now clear the automation cache within their original deadlines and retain their unchanged `longClick()` and clipboard assertions, with missing-node XML/PNG diagnostics. All 11 Backlog cases pass on API 29 after the first probe update. The search-option case additionally requires each checkbox to become checked after its native click, with explicit retained-state diagnostics; no click is retried and no assertion removed. Final exact-source CI and packaging evidence belong to PR #127 and will be recorded after completion.

New coverage includes line actions/keyboard/recreation, initial original despite provider changes, legacy recovery without a baseline, original encoding/BOM fidelity, comparison process loss, filtered-view exact patch export, cancellation/input-URI protection, collection limits/persistence/grant ownership/reselection/recreation, 200% keyboard reachability, JSONL invalid/blank navigation, saved-copy read denial, and dark/200% task semantics.

## Findings and fixes

**Reading position (#97):** [hosted run 44953110864](https://github.com/utrost/Textr/actions/runs/44953110864) records API 29 **159/160**, with a 600 → 0 window-transition failure. `stateSaveCapturesPositionBeforeFrameworkViewCallbacks` independently fails before the fix: framework state-save callbacks clamp the old WebView before Textr reads it. Textr now captures reading position before `super.onSaveInstanceState` and consistently uses that snapshot. Pending find restoration also uses its authoritative position. Nineteen focused position/recovery/QoL cases, the full API 29 suite and ten transition repetitions pass afterward.

**Saved-copy opening:** a write-only provider reproduced a SecurityException when an internal same-app handoff forwarded read/delegation flags the provider never granted. The handoff no longer invents URI grants. Existing read access is retained only after successful reading; a read denial explains recovery while the completed write remains successful. No exported component or signature permission was weakened.

**Collection layout:** replacing the root during navigation could miss insets and clip Done. Root installation now requests insets after attachment; explicit full-visible-button checks pass at normal and 200% text. Direct screenshots were inspected.

**WebView source exposure (#59):** `ReaderUxTest.nativeSelectionAndIncomingTextShareThroughAndroid` reproduces a visibly correct “Copyable Textr selection” with only native toolbar/progress labels and an empty WebView body in refreshed Android nodes. Loaded source contents are independently exact. The required probe still finds exact visible text, then performs a real long-press and selected-text Share; it does not substitute coordinates for missing source exposure. Missing-node failures retain hierarchy XML, screenshot and node diagnostics. Global cache clearing is now being evaluated, not claimed as a production bridge fix. See [investigation](csv-accessibility.md); #59 remains open.

**Source-search layout (#128):** changing three matches to none deterministically moves the option touch target by **257 screen pixels** before the fix (`SourceSearchLayoutTest.asyncResultsKeepOptionTouchTargetsInPlace`, API 29). Clearing/repopulating rows changes the centered dialog height. Source search now reserves full window space, matching Structure/Accessible table, so asynchronous results cannot move controls between taps. The regression and five existing source/options/native-copy/200%-landscape cases pass on API 29 (**6/6**, 48.745 seconds). Debug/application-test builds, 102 JVM tests and lint pass afterward. This confirmed layout bug fits the differing option click heights in the failed hosted run; it is separate from WebView accessibility. API 36 and the final 162-case hosted matrix remain pending.

**Emulator readiness:** API 36 in the earlier hosted run finishes **125/160** after an early System UI ANR. This is retained as failure. Preparation now keeps the guest offline through live radio services before/after restart, warms 200%/normal native chrome and requires a fresh parsed launcher hierarchy. Runtime-error dialogs fail immediately with diagnostics; transient null roots get a bounded readiness wait. No ANR is dismissed and no application assertion is retried. Host SDK/Gradle downloads remain available. The successful local API 29 matrix predates the corrected offline setup; do not describe that local run as offline.

The initial hosted credential-guard step failed because its runner lacked `rg`; it now uses required Python. The initial APK verifier omitted AndroidX's generated signature permission; it now requires exactly both expected app signature permissions, with signature protection verified in the compiled manifest. No application permission was changed to satisfy the verifier. Failed runs and deterministic counterexamples remain recorded.

## F-Droid and listing rehearsal

The maintainer selects **standard F-Droid signing** and **utrost / public Textr issues** as developer/privacy/support identity. The recipe has no `Binaries` entry or production credential. Switching from development-signed alphas requires reinstalling and removes local app data; originals stay with their providers.

fdroidserver **2.4.5**, Gradle **9.3.1**, local OpenJDK **21**, SDK platform **36** / Build Tools **36.0.0**, Linux x86_64: recipe lint passes with current official Reading/Text Editor categories. Clean public-source builds/scans pass for earlier public implementation commits `1fbb8d87baf2e4f8dc82b88bb97274560a81e196` and `d2f7b86`. Command: `fdroid build --test --scan-binary --no-tarball --stop de.utrost.textr:5`. Result: **1 build succeeded**, no known non-free classes or extra signing blocks; unsigned verification passes. No scanner suppression, skip or force option is used. fdroidserver itself removes the standard wrapper JAR. Hosted Android CI uses JDK 17.

An empty initial workspace hit fdroidserver's `SOURCE_DATE_EPOCH=None` before clone. Pre-seeding a disposable public clone allowed normal source preparation to reclone/clean the pin; no application/scanner check was bypassed. The final immutable-tag rerun remains pending. This is a local rehearsal, not official isolated build-server acceptance or an external submission.

All 11 primary `reading-*` screenshots and inherited Fastlane home/settings shots were refreshed from the current implementation using the committed script; additional collection/JSONL/dark-200%-task/filter captures use synthetic focused fixtures. Pixels are not retouched. [Capture provenance](screenshots.md) identifies the source before later disclosure strings and position fix; pictured UI is unchanged. English/German recovery confirmation and About explicitly disclose initial-original retention; offline help covers the new workflows. The public privacy URL resolves as HTML and identifies the app/contact.

## Pending packaging and external acceptance

- Successful exact-final-source hosted API 29/36 matrix and final package/tag/download verification.
- Exact signed APK fresh installation and 0.4 → 0.5 in-place upgrade, checking settings, recent references, legacy recovery and unchanged provider bytes.
- Final tagged-source fdroidserver clean build/scans and unsigned inspection.
- Physical-provider/human TalkBack/foldable acceptance #56 and underlying/current-WebView investigation #59.
- Account-dependent Play work #57, F-Droid eligibility/submission #66 and public APK/production-signing gate #13. Prepared files and private drafts do not authorize those publication steps.
