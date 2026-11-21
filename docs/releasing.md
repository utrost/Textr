# Release signing

Public release and production-key selection are deferred at the owner's request. Signing support and same-key upgrades were validated with a disposable test identity; see [validation](validation.md). The procedure below is for a future authorized release.

Release APKs use an owner-controlled signing key. The repository and pull-request CI never contain a key or password. Debug APKs use Android's separate development key and cannot replace a release installation (or vice versa) unless signed by the same identity; uninstalling removes private settings/history.

## Private 0.1-alpha test build

On 18 November 2026 the owner requested a release APK for private testing, uploaded to the repository. This authorizes a **draft prerelease**, not public publication or production-key selection. The release variant is version `0.1-alpha` (code 1), signed with the existing local Android development identity. Its private key and temporary properties file stay outside the repository and release assets. Future production builds use a separately retained owner key; changing identities requires uninstalling this alpha, which removes private settings/history.

The draft includes the APK, SHA-256 checksum, public signing certificate fingerprint, and exact tagged GPLv3 source. Draft assets require repository write access; publishing the draft would make the testing APK public.

## Private 0.2-alpha update

The owner requested a new release on 20 November 2026. Version `0.2-alpha` (code 2) includes the roadmap implementation from PR #96 and retains the 0.1-alpha development signing identity for in-place updates. It remains a draft prerelease for private testing; production signing is deferred. See [release notes](release-notes-0.2-alpha.md) and the [private draft release](https://github.com/utrost/Textr/releases/tag/untagged-91b226e699ea4047e362).

## Private 0.3-alpha update

The reliability release requested on 20 November 2026 is version `0.3-alpha` (code 3), including merged PR #117. It reuses the previous alphas’ development signing identity and remains a draft prerelease. See [release notes](release-notes-0.3-alpha.md). Earlier tags and artifacts remain immutable.

## Prepare the signing identity

Reuse an existing Textr release key if one exists. Losing it prevents updates to installations signed with it. For a first release, the owner can create a key outside the checkout with JDK 17's `keytool`:

```sh
keytool -genkeypair -keystore /secure/path/textr-release.p12 \
  -storetype PKCS12 -alias textr -keyalg RSA -keysize 4096 -validity 10000
```

This command prompts for credentials; do not put passwords in shell arguments, logs, issues, or GitHub comments. Use restrictive filesystem permissions and retain an encrypted backup and recovery instructions under the owner's control. Keep the same key for future versions. A disposable test key must never be used for public releases.

Create a private properties file outside the checkout, readable only by the owner:

```properties
storeFile=/secure/path/textr-release.p12
storePassword=YOUR_PRIVATE_PASSWORD
keyAlias=textr
keyPassword=YOUR_PRIVATE_PASSWORD
```

This uses Java properties syntax: escape backslashes and special characters as necessary. `storeFile` should be an absolute path. Do not commit this file, even temporarily. No credentials are needed for debug builds or CI.

## Build and verify

From a clean checkout of the intended release commit, with JDK 17 and SDK 36 configured:

```sh
TEXTR_SIGNING_PROPERTIES=/secure/path/textr-signing.properties ./gradlew :app:assembleRelease
```

Without this environment variable or required fields, release builds fail with an explanation. The output is `app/build/outputs/apk/release/app-release.apk`. Check the signature with the SDK's Build Tools 36.0.0:

```sh
"$ANDROID_HOME/build-tools/36.0.0/apksigner" verify --verbose --print-certs app/build/outputs/apk/release/app-release.apk
"$ANDROID_HOME/build-tools/36.0.0/aapt" dump badging app/build/outputs/apk/release/app-release.apk
"$ANDROID_HOME/build-tools/36.0.0/aapt" dump permissions app/build/outputs/apk/release/app-release.apk
sha256sum app/build/outputs/apk/release/app-release.apk
```

Record the public certificate SHA-256 fingerprint with the release. Confirm `de.utrost.textr`, the intended version, minimum API 29, target API 36, no network/broad-storage permission, and packaged licenses. Confirm the release is not debuggable. Test clean installation and replacement of a previous version signed with this identity using `adb install -r`; settings and recent references must survive.

Version defaults are in `app/build.gradle.kts`. For a disposable upgrade rehearsal only, `-PtextrVersionCode=4 -PtextrVersionName=0.3.1-test` produces a higher-version test APK. Public upgrades must increment the committed version code and use the retained release key.

## Publish

Complete the [roadmap](roadmap.md) release gates and record emulator evidence in the validation record. Create a version tag pointing at the tested source commit, publish the signed APK with a SHA-256 checksum, certificate fingerprint, release notes, and matching GPLv3 source. GitHub's tag source archives must correspond to that exact commit. Keep private keys and properties files outside release assets. Store publication is outside v1.

## Optional Play bundle preparation

The same private signing configuration supports `:app:bundleRelease`. Output: `app/build/outputs/bundle/release/app-release.aab`; verify with JDK `jarsigner -verify -verbose -certs`. Use an owner-approved upload/signing identity only. AAB files are not directly installable APKs. See [Play preparation](store/readiness.md) for key compatibility, listing drafts and the remaining account-dependent gates. Preparation does not authorize key creation, upload or publication.

## Verified private-draft automation

Version defaults advance to **0.5-alpha / code 5**, above the immutable private 0.4-alpha QoL candidate (code 4). `scripts/prepare-release.py` packages only an explicit clean source commit/tag with a successful CI run for that exact SHA. It never selects “latest”, overwrites a release/tag, publishes a draft, or creates a key. The owner-signed path and standard [F-Droid signing](fdroid/README.md) are separate: F-Droid uses the explicit unsigned release property and its own signing identity.

```sh
python3 scripts/prepare-release.py --ref EXACT_COMMIT --previous-version-code 4 --dry-run
ANDROID_HOME=/path/to/sdk python3 scripts/prepare-release.py \
  --ref EXACT_COMMIT --previous-version-code 4 --ci-run SUCCESSFUL_RUN_ID \
  --signing-properties /secure/path/textr-signing.properties \
  --expected-certificate APPROVED_SHA256 --output /private/path/Textr-0.5-alpha
python3 scripts/prepare-release.py --verify-dir /private/path/Textr-0.5-alpha \
  --expected-certificate APPROVED_SHA256
```

Check `--help` for the complete interface. Packaging builds a temporary archive of that commit using the committed version defaults, verifies package/version/non-debuggable manifest/permissions/notices and the explicitly anchored signing certificate, then includes exact source, build metadata, public fingerprint, notes and complete SHA-256 coverage. Output must be new and outside the checkout. Signing properties must be outside the checkout; retain keys and build logs privately. No credentials enter the package.

Only add `--upload-draft` when private-draft upload is authorized. It creates a new draft prerelease, downloads its assets again, verifies them and compares every downloaded byte with the local package. A failure leaves existing versions unchanged; inspect any newly created draft before retrying. This is not public publication. Version monotonicity compares against the explicit prior release code, so verify that input against existing releases before use. Physical-device acceptance and public/store publication gates remain separate.
