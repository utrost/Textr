# Data safety preparation

Draft for owner review, based on current source behavior. Not a submitted declaration or a guarantee of Play approval. Recheck every shipped dependency and final artifact before submission.

| Topic | Current behavior / proposed response |
| --- | --- |
| App collection | No developer collection or transmission; documents and reading state are processed on device. No app network permission or analytics SDK. |
| Sharing | Explicit user actions can share original-file copies or selected text with chosen apps. Review the user-initiated sharing exception when answering the form; do not describe the app as incapable of sharing. |
| Local data | URI/name/timestamp/settings, reading state, bookmark/collection names and source fingerprints; reading/comparison contents are held in memory. Explicit copy editing retains one content-bearing private recovery checkpoint (current draft and initial original snapshot), excluded from backup, until explicit discard or successful saving of its matching revision. Temporary share copies are bounded and cleaned as described in the policy. Explicit Save as creates a separate document with the selected provider. |
| Accounts | No accounts or account creation; no server-side account-deletion mechanism. Clearing app storage/uninstalling removes local app data. |
| Security claims | Do not claim encryption in transit for an app that does not transmit data. Android storage and provider behavior are separate from app features. |
| External components | Document providers and user-opened browsers/apps have their own behavior. WebView metrics are opted out; Android/WebView crash settings remain platform-controlled. |

The proposed **no data collected** answer reflects on-device processing, not an absence of local data. Any **no data shared** answer must be reviewed against the form’s explicit user-action exceptions and the final implementation. [Google’s form guidance](https://support.google.com/googleplay/android-developer/answer/10787469?hl=en) defines these terms and makes the developer responsible for the declarations.

The [app policy](../privacy.md) is publicly readable through the repository and linked from About. Developer **utrost** and the public issue tracker are the confirmed privacy/support identity and contact. Before Play submission, the owner must check the live policy URL and matching console declarations under [Google’s User Data policy](https://support.google.com/googleplay/android-developer/answer/10144311?hl=en). No account or legal identity has been invented.
