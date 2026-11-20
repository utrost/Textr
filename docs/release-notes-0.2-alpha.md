# Textr 0.2-alpha — private testing

Version code 2; Android 10/API 29 or later. This draft prerelease uses the same local development signing identity as 0.1-alpha, allowing an in-place update. Keep the draft unpublished for private testing. Production signing remains deferred.

Includes the roadmap implementation from PR #96:

- Convert HTML documents to Markdown and export the converted text.
- Per-document reading profiles, typography and spacing controls.
- Native JSON/XML navigation and additional everyday text formats, including TSV.
- Read-only document comparison in unified and wide side-by-side views.
- Explicit TXT/Markdown copy editing with undo/redo, find/replace, private crash recovery and Save as. Original documents remain unchanged.

The editor accepts up to 256 Ki UTF-16 characters, 10,000 lines and 4,096 characters per line. One recovery draft is retained privately and excluded from backup. The application remains offline, with no network permission.

Validation of the implementation: 86 JVM tests, build/lint, and 123 device tests on each of Android 10/API 29 and Android 16/API 36 passed in [CI](https://github.com/utrost/Textr/actions/runs/44590125674). Release packaging and upgrade verification are recorded in the GitHub release notes.

Physical-device and human TalkBack review remain follow-ups (#56). WebView accessibility omissions remain tracked in #59; use the native accessible table/source views where needed.

Install `Textr-0.2-alpha.apk` over the previous alpha after verifying `SHA256SUMS`. The release includes the public signing fingerprint and matching GPLv3 source; license notices are bundled in the APK. Private keys and signing properties are never uploaded.
