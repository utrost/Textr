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
- [ ] Record failures and fixes, and retest the exact final candidate.

Any Play testing track or tester invitations require the owner’s account and distribution decision. Do not count emulator sessions as the account’s required tester cohort.
