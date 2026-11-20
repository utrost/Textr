# Textr 0.3-alpha — private testing

Version code 3; Android 10/API 29 or later. This draft prerelease uses the same development signing identity as 0.1-alpha and 0.2-alpha for in-place updates. Keep it unpublished for private testing; production signing remains deferred.

This release includes the reliability work merged in PR #117:

- Preserve reading position when the activity is recreated during unfinished page layout.
- Explain file-access and output failures with retry, reselect and return actions.
- Restore the previous document after a failed or cancelled replacement load, rejecting late results.
- Ask before reconnecting an existing bookmark to a different file.
- Retry failed private checkpoints or unreadable draft recovery without discarding the draft.
- Offer a new save destination after failed output while preserving recovery.

All 0.2-alpha features remain included. Original documents stay read-only, the application remains offline without network permission, and existing input/editor limits and the single private recovery-draft policy remain in place. Recovery actions are available in English and German.

The implementation passed build/lint, 86 JVM tests and 129 device tests on each of API 29 and API 36 in [hosted CI](https://github.com/utrost/Textr/actions/runs/44774626510). See [the validation record](validation-reliability.md) for reproduction, intermediate failures and rerun evidence. The release changes only version defaults and documentation relative to that tested implementation. Release packaging, signing, fresh-install and upgrade evidence are recorded with the GitHub release.

Physical-device and human TalkBack acceptance (#56), and the intermittent WebView accessibility investigation (#59), remain open. Epic #98 remains open for those checks.

Install `Textr-0.3-alpha.apk` over your existing alpha after verifying `SHA256SUMS`. Assets include the public certificate fingerprint, APK verification details and matching GPLv3 source. Draft assets require repository write access; keys and signing credentials are never uploaded.
