# Physical beta checklist

[#56](https://github.com/utrost/Textr/issues/56) is open. No physical device is connected, and no physical TalkBack speech or Magic V6 validation is claimed. Emulator results are documented separately.

For each session record commit/APK checksum, device model, Android version, WebView version, provider app/version, locale, system font size and navigation mode. Use synthetic documents and keep findings separate from expectations.

- [ ] Open, share and reopen files from the system picker and common file managers/providers.
- [ ] Test an unavailable/offline/slow provider; cancel loading, select another file, and confirm stale results do not replace it.
- [ ] Use actual TalkBack speech to navigate source, Markdown, CSV cells and header relationships, menus, hints, dialogs, search and error recovery. Record #59 reproductions by WebView version.
- [ ] Select/copy and share exact text; share original bytes and verify receiving-app read access.
- [ ] Test English/German and 200% system font in compact, landscape and split-screen windows.
- [ ] On Magic V6, opt into the sidebar; fold/unfold, rotate and resize while reading/searching. Check position, mode, focus and content continuity. Check the central crease with actual hardware.
- [ ] Test keyboard navigation where available, including fields/dialogs retaining shortcuts.
- [ ] Exercise per-document typography, JSON/XML navigation and HTML-to-Markdown reading/export with synthetic files.
- [ ] Compare two files in compact/wide layouts. Edit a TXT/Markdown copy, recover after process loss, Save as and verify original bytes stay unchanged.
- [ ] Upgrade 0.1-alpha to 0.2-alpha with the same signing identity; verify history, bookmarks and settings on the physical device.
- [ ] Record failures and fixes, and retest the exact final candidate.

Any Play testing track or tester invitations require the owner’s account and distribution decision. Do not count emulator sessions as the account’s required tester cohort.

## Roadmap-completion acceptance — awaiting physical evidence

Use synthetic files and record device/Android/WebView/provider versions. These are pending checks, not emulator passes:

- [ ] Save a TXT/Markdown copy with each real provider and use Open saved copy. A write-success/read-denied outcome must preserve the completed save and explain read recovery.
- [ ] Add a picker document to two collections, clear recent history, restart, open both references, then remove the memberships separately. Reconnect a moved/revoked reference with explicit confirmation; cancel reselect without changing membership.
- [ ] Capture original/draft comparison, change the provider’s file independently and confirm the comparison still uses the initial editing snapshot. Return to editing with unchanged undo/selection. Test outer/inner display transitions while editing and comparing.
- [ ] Export an exact patch with each display filter active. Confirm hidden changes are included, both source documents remain unchanged, cancellation preserves the comparison and successful output reopens when readable.
- [ ] With human TalkBack, navigate collection controls, JSONL valid/invalid/blank records, Go to line and comparison filters/export; verify names, state, focus order and actionable errors.
- [ ] Read checked/unchecked nested Markdown tasks with human spoken feedback in light/dark themes and at 200% text. Tasks must convey state without implying editable checkboxes.
- [ ] Recheck source/CSV virtual nodes across lifecycle transitions on a current stable WebView; retain provider/version and synthetic hierarchy evidence separately from native fallback success (#59).

The original private-recovery and provider files must stay intact through cancellation or failed writes. Save needed recovery before switching signing channels: standard F-Droid signing requires reinstalling development-signed alphas and removes local app data.
