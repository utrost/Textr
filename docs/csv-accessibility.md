# CSV accessibility investigation

[#59](https://github.com/utrost/Textr/issues/59) now has an explicit native compatibility route: **More options → Accessible table…**, with selectable native cells and **Show source**. The WebView defect itself remains reproducible. [18 November validation](validation-reader-polish.md) distinguishes native cell/source guarantees from browser diagnostics. Hosted acceptance and broader accessibility review remain pending.

## Reproduction

The API 36 Google APIs emulator ships Android System WebView **133.0.6943.137** in the local validation environment. Previous #58 runs intermittently exposed empty rows or an empty WebView subtree through Android accessibility after document/mode/activity changes. The rendered table remained visible; Chromium’s browser-level accessibility tree contained cell names. The cause has not been established.

A standalone, script-free [HTML fixture](fixtures/csv-accessibility.html) preserves the table/header/scroll structure for browser comparison. It is not an Android lifecycle reproducer by itself. Use the instrumentation probe to exercise that lifecycle:

```sh
adb shell am instrument -w \
  -e class de.utrost.textr.ReleasePolishTest#csvSourceFallbackRemainsAccessibleAcrossPreviewChanges \
  de.utrost.textr.test/androidx.test.runner.AndroidJUnitRunner
adb logcat -d -s TextrCsvAccessibility
adb exec-out run-as de.utrost.textr cat cache/csv-accessibility-0.xml
```

The test uses synthetic CSV, toggles preview/source and recreates the activity three times. It records whether the preview cell appears and saves each Android hierarchy. It refreshes Android accessibility nodes on each probe (and clears the automation cache where supported), matching the native CSV selection test. It **asserts source accessibility through Accessible table → Show source, preserved contents and disabled JavaScript**, not successful WebView cell navigation. These are acceptance assertions, not a guarantee across all WebView versions or lifecycle sequences. Longer candidate runs have also intermittently missed source and link nodes; a passing short probe is insufficient to close the issue.

## Investigation boundaries

Removing the nested scroll container, adding explicit table roles, adding cell focusability, refreshing accessibility nodes, clearing UiAutomation’s cache, and longer waits did not consistently resolve the earlier failure. Some wrapper labels collapsed descendants and were removed. The application now keeps the WebView accessibility bridge enabled during loading and sends a subtree-change event after the completed draw, avoiding a descendant-exposure toggle at the loading boundary. Loading controls occupy a separate native panel rather than covering the WebView while its virtual tree is built. These lifecycle changes require full-suite verification and do not by themselves establish a CSV fix. No JavaScript bridge, network permission or semantic workaround has been added.

The native compatibility view is also exercised with the installed TalkBack service; see the new validation record. Retest the standalone WebView fixture and lifecycle on newer versions before concluding that this is a version-specific defect. The available emulator version is not evidence about current stable WebView. Real spoken feedback and Magic V6 behavior belong to [#56’s beta](beta-checklist.md). Keep #59 open until cell-navigation evidence supports a fix or an accessible compatibility fallback.

## Current probe result

On 15 November 2026, the local API 36 / WebView 133.0.6943.137 probe exposed the target cell in all three iterations and passed source-fallback assertions. The API 29 / WebView 74.0.3729.185 probe also exposed cells in all three iterations. This small successful run does not disprove the previously intermittent failure or establish its cause. #59 remains open.

## Full-suite counterexample

The final hosted API 36 run at commit `c0c8bc42ef840b45638fb9c4e4575222f49e916b` fails the source assertion even with fresh platform nodes. Iteration 0 does not expose the preview cell; iteration 1 exposes it, then Plain text exposes only reader/progress labels and omits source text. [Retained run and reports](https://github.com/utrost/Textr/actions/runs/43719037135). This disproves treating the short local passes as a reliable source-accessibility guarantee. This was a merge blocker for the earlier PR #60 candidate. Its later native compatibility route was integrated through PR #96; the browser defect remains open, and no browser assertion was disabled to establish a fix.

## Native compatibility route (18 November 2026)

The local follow-up adds native table and source widgets under the same CSV parser budgets. Table dimensions, cell row/column indices and headings are exposed through Android collection metadata. Native search/copy, explicit original-byte sharing, recreation and large text are covered independently of Chromium's virtual nodes. Large tables yield between construction batches and can be cancelled. Selectable document widgets exclude text-state saving and autofill.

The probe still logs whether WebView exposes a target cell and retains its hierarchy snapshots. Source assertions now deliberately enter the user-facing native fallback; they do not reclassify a missing browser subtree as a pass. `accessibleCsvCellsKeepTableSemanticsAndFocusAcrossModeChanges` covers actual native cell focus with TalkBack bound and touch exploration enabled. See [commands, matrix and remaining limits](validation-reader-polish.md).


## Roadmap-completion counterexample (21 November 2026)

The 161-case roadmap matrix reproduces a missing plain-source subtree on API 36 / WebView 133.0.6943.137. `ReaderUxTest.nativeSelectionAndIncomingTextShareThroughAndroid` sees the correctly rendered “Copyable Textr selection” in its screenshot, but a refreshed platform-node traversal exposes the native toolbar/progress labels and an empty WebView body. The loaded source is independently asserted exact. Eight isolated repetitions and all nine Reader UX cases pass; the full sequence fails. Individual node refreshes therefore do not establish a fix. Failure XML, screenshot and node diagnostics are retained; the source-exposure and native selected-text Share assertions remain required.

API 29 / WebView 74.0.3729.185 passes all 161 cases, including the selection flow. These emulator versions do not establish current stable WebView behavior or human spoken feedback. See [current validation evidence](validation-roadmap-completion.md); keep #59 open.
