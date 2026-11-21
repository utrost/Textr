# F-Droid preparation

The maintainer selected **standard F-Droid signing** on 21 November 2026. F-Droid builds the app from source and signs its distributed APK with F-Droid’s key. Textr supplies no production key, `Binaries` entry or developer-signature reproducibility promise. F-Droid builds cannot update the development-signed private alphas in place. Switching channel requires uninstalling/reinstalling; this removes Textr’s local settings, references and private edit recovery. Save needed drafts first; original/provider files remain with their provider.

The proposed recipe is [de.utrost.textr.yml](de.utrost.textr.yml). It targets version code 5 / 0.5-alpha and tag `v0.5-alpha`; do not submit it until that tag exists at the tested source. `AutoUpdateMode` and `UpdateCheckMode` are intentionally disabled for initial review. Runtime/build dependencies and licenses are documented in [Attributions.txt](../../app/src/main/assets/licenses/Attributions.txt). There are no proprietary SDKs, network permission or required external service accounts.

## Secret-free release build

```sh
env -u TEXTR_SIGNING_PROPERTIES ./gradlew :app:assembleRelease -PtextrUnsignedRelease=true
python3 scripts/verify-unsigned-apk.py app/build/outputs/apk/release/app-release-unsigned.apk
python3 scripts/validate-store-metadata.py
```

Use JDK 17, SDK platform 36, Build Tools 36.0.0 and the checked-in Gradle wrapper. The explicit property selects an unsigned, non-debuggable release; the normal owner-signed release path still fails without credentials. This is a source-build input for F-Droid, not an installable alpha. The verifier rejects any APK/JAR signing material, unexpected package/version, debuggability, network permission or missing licenses.

## fdroidserver rehearsal

Copy the proposed recipe into `metadata/de.utrost.textr.yml` in a disposable fdroiddata checkout with its current official category configuration. Configure the local Android SDK and matching Gradle 9.3.1. Use the final public tested commit/tag, without signing credentials:

```sh
fdroid lint --force-yamllint -f de.utrost.textr
fdroid build --test --scan-binary --no-tarball --stop de.utrost.textr:5
fdroid scanner --exit-code path/to/app-release-unsigned.apk
```

Do not suppress source scanning or remove signature-protected components to satisfy a build. A local rehearsal does not establish official build-server acceptance or reproducibility across hosts. Record exact commit, fdroidserver version, SDK/Gradle/JDK versions, scan result and APK verification in the validation record.

## Metadata and submission gates

English and German Fastlane metadata live in `fastlane/metadata/android/`, with localized descriptions/changelogs, the app icon and synthetic screenshots. Developer: **utrost**. Privacy/support contact: [Textr issues](https://github.com/utrost/Textr/issues). The public [privacy policy](https://github.com/utrost/Textr/blob/main/docs/privacy.md) is also linked inside About. Only submit once these files exist at the final referenced source tag and their visible content matches the candidate.

The [F-Droid inclusion policy](https://f-droid.org/en/docs/Inclusion_Policy/) normally expects at least three months of active public development, with stated exceptions. Textr’s current public history is younger; no exception or eligibility is assumed. The maintainer must authorize an external fdroiddata submission separately (#66). Prepared files do not submit a request or publish an APK.

References checked 21 November 2026: [submission guide](https://f-droid.org/en/docs/Submitting_to_F-Droid_Quick_Start_Guide/), [metadata and screenshots](https://f-droid.org/en/docs/All_About_Descriptions_Graphics_and_Screenshots/), [developer-signature/reproducible route](https://f-droid.org/en/docs/Reproducible_Builds/) (not the selected signing route).
