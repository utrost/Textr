# Release signing

Public release and production-key selection are deferred at the owner's request. Signing support and same-key upgrades were validated with a disposable test identity; see [validation](validation.md). The procedure below is for a future authorized release.

Release APKs use an owner-controlled signing key. The repository and pull-request CI never contain a key or password. Debug APKs use Android's separate development key and cannot replace a release installation (or vice versa) unless signed by the same identity; uninstalling removes private settings/history.

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

Version defaults are in `app/build.gradle.kts`. For a disposable upgrade rehearsal only, `-PtextrVersionCode=2 -PtextrVersionName=0.1.1-test` produces a higher-version test APK. Public upgrades must increment the committed version code and use the retained release key.

## Publish

Complete the [roadmap](roadmap.md) release gates and record emulator evidence in the validation record. Create a version tag pointing at the tested source commit, publish the signed APK with a SHA-256 checksum, certificate fingerprint, release notes, and matching GPLv3 source. GitHub's tag source archives must correspond to that exact commit. Keep private keys and properties files outside release assets. Store publication is outside v1.
