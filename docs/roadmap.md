# Textr feature roadmap

Updated 22 November 2026. Textr is an offline Android reader and inspector with explicit export and editing of separate copies. Originals remain unchanged. [Tracker #14](https://github.com/utrost/Textr/issues/14) links the live epics; [feature design and acceptance](feature-proposals.md) describes their scope.

## Delivered baseline

The five 0.2-alpha epics and 19 implementation issues are merged (#96): reading preferences, JSON/XML navigation, everyday formats, HTML-to-Markdown output, read-only comparison and TXT/Markdown copy editing. Reliability fixes (#97/#99, PR #117) are included in private 0.3-alpha. All eight QoL improvements (#101/#109/#120–#125) and Copy all are merged through [PR #126](https://github.com/utrost/Textr/pull/126); the immutable private **0.4-alpha / code 4** candidate contains them. Earlier tags and artifacts are unchanged. [Roadmap history](roadmap-history.md) preserves earlier milestones.

## Delivered batch — private 0.5-alpha

The ten remaining feature/automation implementations are delivered in [PR #127](https://github.com/utrost/Textr/pull/127) and the verified **[private 0.5-alpha / code 5 draft](https://github.com/utrost/Textr/releases/tag/untagged-403cd7772a9feb20b00d)**. Tested source is `bedea968d6c8f641489813ee9e64c16986b4b5f0`, tagged `v0.5-alpha`. [Implementation CI](https://github.com/utrost/Textr/actions/runs/44970110287) passes 164/164 on both APIs, 102 JVM cases and lint. Exact APK installation, 0.4 upgrade, downloaded assets and tagged-source F-Droid build/scans are verified in the [validation record](validation-roadmap-completion.md). This is private testing; public publication and physical beta remain separate.

| Epic | Implemented behavior | Tracking |
| --- | --- | --- |
| Copy editor polish #100 | Go to line, selected-line indent/unindent with one undo step; robust Open saved copy | #102, #103; preview #101 already merged |
| Comparison improvements #104 | Initial original snapshot vs unsaved draft; explicit whitespace/ending filters; exact UTF-8 unified patch export | #105, #106, #107 |
| Reading organization #108 | Bounded named collections of references, separate from recents, with open/remove/reselect and grant ownership | #110; recent filtering #109 already merged |
| Focused formats #111 | JSONL/NDJSON record navigation including invalid/blank lines; AsciiDoc/rST source highlighting; read-only Markdown task states | #112, #113, #114 |
| Release preparation #115 | Explicit-commit private-draft packager with exact CI/version/signature/source/checksum validation and downloaded-byte verification | #116 |

Usage and limits: [editor](editing-copies.md), [comparison/export](comparison.md), [collections](collections.md), [formats](formats.md), [release tools](releasing.md). [Validation record](validation-roadmap-completion.md) distinguishes completed checks from pending acceptance.

## Distribution preparation

| Work | Current state | Tracking |
| --- | --- | --- |
| Privacy/contact | Policy and About link implemented; maintainer confirms **utrost** and public Textr issues as privacy/support contact | #53 |
| Secret-free F-Droid build | Explicit unsigned non-debuggable release path, guarded owner-signing path and repeatable verification | #62 |
| Listing metadata | Current English/German Fastlane text, icons, synthetic screenshots and version-code 5 changelogs | #63 |
| F-Droid recipe | Recipe lint and clean `v0.5-alpha` fdroidserver build/source/binary scans pass; unsigned APK properties verified | #64 |
| Signing policy | **Standard F-Droid signing selected**. No owner production key needed. Development-signed alphas and F-Droid cannot update each other in place | #65 |
| Optional Play | AAB path and listing/Data safety drafts prepared; account-specific declarations, signing and actual upload remain owner-dependent | #57 |
| External F-Droid submission | Deferred pending eligibility, final review and authorization; no external request submitted | #66 |
| Public APK | Public publication and production-key creation remain deferred | #13 |

See [F-Droid preparation](fdroid/README.md), [Play readiness](store/readiness.md), [privacy](privacy.md) and [release signing](releasing.md). Standard F-Droid signing is a documented owner choice, not permission to publish the app.

## Remaining acceptance

- **Real-device beta #56:** physical document providers, denied/revoked grants in real workflows, human TalkBack spoken feedback and foldable display transitions. No physical device is available here; emulator results cannot close these checks.
- **WebView accessibility #59:** native CSV/source fallback is included, but the intermittent omission of virtual nodes needs investigation across current WebView versions and actual human speech/navigation. A standalone Android fixture also reproduces omissions with fresh nodes, correct insets and native notifications; no claim that the bridge defect is fixed.
- **Reliability epic #98:** software recovery is delivered; #56/#59 keep its acceptance scope open.
- **Distribution epic #115:** feature automation can complete while external submission, account choices and publication remain separately open.

Prioritize private-beta findings and the isolated WebView investigation over another broad feature batch. In-place editing, multiple recovery drafts and EPUB need explicit scope/privacy/resource decisions; PDF/DOCX, direct URL fetching and large/live logs remain outside this roadmap. There is no promised public-release date.
