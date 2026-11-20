# Optional Google Play preparation

Checked against public documentation on 15 November 2026. This is preparation only: no Play account has been accessed, no production/upload key created, and no bundle uploaded. [#57](https://github.com/utrost/Textr/issues/57) remains open for owner/account-dependent work. APK publication remains separately deferred under #13.

## Build path

The existing owner-controlled release signing configuration applies to both APK and Android App Bundle builds. With a privately retained signing identity and the external properties file described in [release signing](../releasing.md):

```sh
TEXTR_SIGNING_PROPERTIES=/secure/path/textr-signing.properties ./gradlew :app:bundleRelease
jarsigner -verify -verbose -certs app/build/outputs/bundle/release/app-release.aab
```

A bundle is an upload artifact, not an APK you can install with `adb install`. Debug bundles can be built with `./gradlew :app:bundleDebug`. Production signing inputs remain excluded from source and CI. A local rehearsal verified that missing credentials fail and that a bundle signed with the existing development key passes `jarsigner` verification. That development-only artifact is not a release candidate; no production key was created.

[Play App Signing](https://developer.android.com/studio/publish/app-signing) distinguishes the upload key from the app signing key used for installed APKs. Decide how GitHub APKs and Play installations should update each other before enrollment; their installed signing identities must be compatible. Keep custody and recovery records. No choice has been made on the owner’s behalf.

## Requirements checked

- Textr targets API 36. Google’s current [target API policy](https://support.google.com/googleplay/android-developer/answer/11926878?hl=en) requires Android 16/API 36 for new phone apps and updates from 31 August 2026. Recheck at submission.
- Some personal developer accounts require a closed test with at least 12 continuously opted-in testers for 14 days before applying for production access. See [account-specific testing requirements](https://support.google.com/googleplay/android-developer/answer/14151465?hl=en). The owner’s account type and status are unknown.
- Review the final privacy URL, developer identity/contact, Data safety form, app access, ads, content rating, target audience and distribution choices in the console. Drafts here do not replace those declarations.

## Prepared material

- [English/German listing copy](listing.md)
- [Data safety draft](data-safety.md) and [privacy policy](../privacy.md)
- [Emulator screenshots with synthetic files](../screenshots.md)
- Vector sources and PNG exports for the existing app icon and a simple feature graphic in this directory. They are candidates for owner review, not a claim of store acceptance.
- [Physical beta checklist](../beta-checklist.md), with untested cases clearly marked

Still open: physical provider/TalkBack/Magic V6 evidence (#56), CSV accessibility (#59), production signing decisions, account-specific gates, owner approval and publication. Check the exact candidate source, tests and artifacts again before any future upload.
