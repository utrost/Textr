# Roadmap completion validation

Updated 22 November 2026. Accepted implementation source: **`bedea968d6c8f641489813ee9e64c16986b4b5f0`**, public immutable tag **`v0.5-alpha`**, [exact-source CI](https://github.com/utrost/Textr/actions/runs/44970110287) and [private 0.5-alpha/code 5 draft](https://github.com/utrost/Textr/releases/tag/untagged-403cd7772a9feb20b00d). [PR #127](https://github.com/utrost/Textr/pull/127) contains the ten remaining roadmap implementations, distribution preparation and validation follow-ups. Public APK/store publication and physical beta remain separate gates.

## Accepted checks

| Check | Result |
| --- | --- |
| Hosted API 29 | 164 tests; zero failures/errors/skips; 1057.538 s |
| Hosted API 36 | 164 tests; zero failures/errors/skips; 1121.286 s |
| JVM / lint | 102 JVM tests and Android lint pass |
| Exact patches | 13 fixtures independently apply with GNU `patch --binary --batch --fuzz=0`; output bytes match |
| Release tooling | 14 offline guard tests pass, including exact source/CI/jobs, monotonic version, immutable tags, signature/permission and complete artifact checks |
| Listing/assets | English/German metadata, code-5 changelogs, PNGs and local Markdown targets validate |
| Local full API 29 | 164/164; zero failures; 949.400 s |

Android coverage includes editor actions/undo/keyboard/recreation; initial original despite provider changes; legacy recovery and encoding/BOM fidelity; comparison process loss, filters and exact patch output; cancellation/input-URI protection; collection limits/grant ownership/reselection/recreation; 200% controls; JSONL valid/invalid/blank navigation; saved-copy read denial; read-only task states; native source-query keyboard/typing; and reading-position lifecycle regressions. No application assertion is removed, disabled or replaced with a missing-text coordinate fallback.

Local providers are WebView **74.0.3729.185** (API 29) and **133.0.6943.137** (API 36). These versions do not establish current-stable-provider or physical-device behavior.

## Exact private package and installation

The release tool builds the explicit tested commit, verifies the APK and archive, creates a draft prerelease, downloads every asset and verifies equality with local bytes. Assets include APK, matching source, BUILD.json, signing/manifest inspection, signing record, notes and complete SHA256SUMS. Existing alpha tags/assets remain immutable.

- APK: `Textr-0.5-alpha.apk`, version **5 / 0.5-alpha**, minimum API **29**, target **36**, non-debuggable.
- APK SHA-256: `6141513963fd1f06e0d5c2ae936b757fd2f9adcd2d6e3b833444e4b91cfcf38a`.
- Approved development certificate SHA-256: `35cb98bd5bb72616db4af92d9032865582c00b0b0b61143867f6c5394bbd7cb9`.
- Compiled manifest retains exactly Textr's FIND_SELECTION and AndroidX's generated receiver permission, both signature protected; no network permission. GPL and attribution notices are packaged.
- Archive Git/PAX commit and committed version match the APK. Downloaded bytes, hashes, signature and inspection match the local package. A copied package with altered APK bytes is refused; originals are untouched.
- The **exact signed APK** passes fresh API 29 installation: startup/collections entry and JSONL valid/blank/invalid navigation, preserving provider bytes.
- A real immutable **0.4-alpha** installation on API 36 is prepared with an actual edited recovery copy, recent reference and dark/22sp/no-wrap settings. Installing the exact 0.5 APK in place preserves all three private files byte-for-byte immediately after replacement. Launching retains settings/references and resumes exact legacy recovery text, correctly identifying its missing initial original snapshot; provider bytes remain unchanged.

The first fresh-install attempt stopped when Android 10's standalone `uiautomator dump` process crashed during shutdown (`Bad file descriptor`, exit 137). Its log is retained; it is not counted as a passing check. A separate complete fresh installation then passes all the same assertions. No application assertion is bypassed.

Fixtures are synthetic and emulators are disposable/rootable. The upgrade checks do not certify physical cloud/provider grants. The owner-selected **standard F-Droid signing** uses a separate identity and cannot update these development-signed alphas in place; reinstalling removes local app data. No production/upload key was created.

## Tagged-source F-Droid rehearsal

The recipe pins the public tested `v0.5-alpha` source, uses the explicit unsigned property and contains no Binaries entry or signing credentials. **fdroidserver 2.4.5**, Gradle **9.3.1**, local OpenJDK **21**, SDK platform **36** / Build Tools **36.0.0**, Linux x86_64. Hosted Android CI uses JDK 17.

Recipe lint passes against current official Reading/Text Editor categories. `fdroid build --test --scan-binary --no-tarball --stop de.utrost.textr:5` produces **1 successful clean build**, with source/binary scans reporting no known non-free classes or extra signing blocks. The resulting APK passes unsigned package/version/SDK/non-debuggable/permission/notices verification. No source-scan suppression, skip or force option is used; fdroidserver removes the standard wrapper JAR itself. The scratch checkout, public tag and release BUILD.json all resolve to `bedea968d6c8f641489813ee9e64c16986b4b5f0`.

An earlier empty scratch workspace hit fdroidserver's SOURCE_DATE_EPOCH=None before clone; seeding a disposable public clone let its normal preparation reclone/clean the pin. Earlier public-commit rehearsals also passed. This is local fdroidserver evidence, not official isolated-server acceptance, cross-host reproducibility or an external submission.

Runtime dependencies remain unchanged: AndroidX/Kotlin/coroutines (Apache-2.0), CommonMark/GFM tables (BSD-2-Clause), Jackson Core (Apache-2.0 with notices) and jsoup (MIT); notices remain bundled. Owner-signed APK/AAB paths were rehearsed with the approved development key, and `jarsigner` verifies the AAB. Missing owner credentials still refuse normal release builds; the unsigned path reads no signing environment.

## Findings and retained counterexamples

**Reading position #97:** [44953110864](https://github.com/utrost/Textr/actions/runs/44953110864) records API 29 159/160 with 600 → 0. Independent regressions reproduce framework pause/state-save callbacks clamping the old view and stop-time metadata overwriting a correct saved position. Both durable checkpoints now share a render-generation-bound last-visible snapshot. Loading positions remain pending and resume/new renders invalidate stale snapshots. Six focused layout/recreation cases pass on both APIs with checkpoint assertions (58.903 / 56.319 s).

[44964197795](https://github.com/utrost/Textr/actions/runs/44964197795) passes API 36 163/163 but API 29 162/163. [44967585254](https://github.com/utrost/Textr/actions/runs/44967585254) passes API 36 164/164 but API 29 163/164. The latter's retained trace establishes the observed hosted ordering cause: **before resize begins**, the outgoing reader is already at 0 and loading. A generic ready check accepted the previous mode before Android 10 handled its injected mode click. The test now waits for **Plain text plus its ready view**, then additionally asserts initial 600. Post-resize position/mode/source/find assertions and deadlines are unchanged. The corrected transition passes both APIs (16.500 / 16.557 s). Earlier ten-sample passes did not disprove this race. Defensive lifecycle hardening is documented separately from the observed test-ordering cause.

**Sharing fixture:** a local 163/164 run retains a chooser miss after an intentionally cancelled predecessor stopped in the case disabling its synthetic receiver. The normal handoff passes after restoration (5.474 s). reset-shared now also restores that owned component; explicitly seeding disabled state then running positive/no-handler cases passes 2/2 (10.182 s). No production export, permission or component state is changed. That failed local run is retained, not counted as a pass.

**Saved copies and layout:** internal handoff no longer invents read/delegation flags for write-only output providers; read denial preserves successful writing and explains recovery. Collection root installation requests insets after attachment, preventing clipped Done controls. Normal/200% visibility and keyboard checks pass.

**Source search #128/#129:** changing asynchronous result rows moved a touch target by 257 pixels before the stable-window fix. AlertDialog also failed to detect its editable list header and blocked keyboard connection; the native-tap regression fails before clearing the incorrect window flag. Corrected tests require actual keyboard visibility, this editor's active connection, real N-key input and fully visible portrait Done on both APIs. API 29 uses server visibility plus editor connection because its dialog insets omit the shown IME. Direct portrait/200%-landscape flows verify exact query/provider bytes; landscape returns from Android's fullscreen keyboard editor with Back. [Direct capture provenance](screenshots.md).

**Emulator/verification setup:** the earlier API 36 System UI ANR and resulting 125/160 failure remain recorded. Preflight now keeps guests offline through live services, warms normal/200% native chrome, requires a freshly parsed launcher and fails runtime-error dialogs with diagnostics. No ANR is dismissed and no application assertion is retried. The initial runner-missing-rg and incomplete verifier permission allowlist were corrected without weakening credential/signature checks or application permissions.

## Open acceptance and documentation

The [standalone Android bridge fixture](../tools/webview-probe/README.md) reproduces #59 without Textr reader classes: proper insets, native notifications and fresh nodes, 120 independent observations. API 29 passes all 120 (39.837 s); API 36 misses 55 and fails (304.198 s). Its [hierarchy](fixtures/webview-probe-source-omission.xml) omits a visibly rendered source marker in the [direct screenshot](images/webview-probe-source-omission.png). This does not identify the responsible framework/test-stack component or establish current-provider/human speech behavior. Main application browser/selection/header assertions remain required. **Keep #59 open.**

Privacy/About/store drafts consistently use the confirmed developer **utrost** and public Textr issue tracker. Recovery original/draft retention and independent collection grants are explicit. Eleven primary screenshots, new feature captures and source-search keyboard/large-text captures use synthetic emulator files without pixel retouching.

Physical providers, spoken TalkBack and foldable acceptance remain **#56**. F-Droid eligibility/submission **#66**, Play account/signing/console work **#57**, and public APK/production-signing **#13** remain separate. Prepared files and private drafts do not authorize those publication steps.
