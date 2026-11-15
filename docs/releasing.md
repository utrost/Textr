# Release signing

Public release and production-key selection are deferred at the owner's request. Signing support and same-key upgrades were validated with a disposable test identity; see [validation](validation.md). The procedure below is for a future authorized release.

Owner-published release APKs use an owner-controlled signing key. F-Droid can build an unsigned release APK from source and sign it after its own checks. The repository and pull-request CI never contain a key or password. Debug APKs use Android's separate development key and cannot replace a release installation (or vice versa) unless signed by the same identity; uninstalling removes private settings/history.

## Unsigned build for F-Droid

From a clean checkout with JDK 17 and SDK 36 configured, build the APK without signing credentials:

```sh
./gradlew :app:assembleRelease -PtextrUnsignedRelease=true
```

The output is `app/build/outputs/apk/release/app-release-unsigned.apk`. The flag explicitly removes the release signing configuration and bypasses only the owner-credential check. It does not change the application ID, version, build type, or debuggable setting. Verify the unsigned artifact with `apksigner verify` (which must fail because there is no signature) and inspect its package/version, permissions, and manifest with `aapt`. This APK is an intermediate build input, not an installable public release. The F-Droid submission and signing/update-channel choice are tracked in [#64](https://github.com/utrost/Textr/issues/64) and [#65](https://github.com/utrost/Textr/issues/65); no app-store publication is authorized by this build path.

Without `-PtextrUnsignedRelease=true`, release APK and bundle builds still require the owner-controlled signing file described below.

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

Without this environment variable or required fields, owner-signed release builds fail with an explanation. The output is `app/build/outputs/apk/release/app-release.apk`. Check the signature with the SDK's Build Tools 36.0.0:

```sh
"$ANDROID_HOME/build-tools/36.0.0/apksigner" verify --verbose --print-certs app/build/outputs/apk/release/app-release.apk
"$ANDROID_HOME/build-tools/36.0.0/aapt" dump badging app/build/outputs/apk/release/app-release.apk
"$ANDROID_HOME/build-tools/36.0.0/aapt" dump permissions app/build/outputs/apk/release/app-release.apk
sha256sum app/build/outputs/apk/release/app-release.apk
```

Record the public certificate SHA-256 fingerprint with the release. Confirm `de.utrost.textr`, the intended version, minimum API 29, target API 36, no network/broad-storage permission, and packaged licenses. Confirm the release is not debuggable. Test clean installation and replacement of a previous version signed with this identity using `adb install -r`; settings and recent references must survive.

Version defaults are in `app/build.gradle.kts`. For a disposable upgrade rehearsal only, `-PtextrVersionCode=2 -PtextrVersionName=0.1.1-test` produces a higher-version test APK. Public upgrades must increment the committed version code and use the retained release key.

## Publish

Complete the [roadmap](roadmap.md) release gates and record emulator evidence in the validation record. Create a version tag pointing at the tested source commit, publish the signed APK with a SHA-256 checksum, certificate fingerprint, release notes, and matching GPLv3 source. GitHub's tag source archives must correspond to that exact commit. Keep private keys and properties files outside release assets. Store publication is outside v1.

## Optional Play bundle preparation

The same private signing configuration supports `:app:bundleRelease`. Output: `app/build/outputs/bundle/release/app-release.aab`; verify with JDK `jarsigner -verify -verbose -certs`. Use an owner-approved upload/signing identity only. AAB files are not directly installable APKs. See [Play preparation](store/readiness.md) for key compatibility, listing drafts and the remaining account-dependent gates. Preparation does not authorize key creation, upload or publication.
