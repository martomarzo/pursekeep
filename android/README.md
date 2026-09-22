# PurseKeep Android companion

Native shell for PurseKeep (application id `app.pursekeep`). Today it forwards
payment notifications (Google Wallet by default) to your PurseKeep server;
later it adds a biometric lock and embeds the web app (see the design spec in
`docs/superpowers/specs/2026-09-22-pursekeep-android-companion-design.md`).

## Build locally
```bash
export JAVA_HOME=~/.local/android-toolchain/jdk17 ANDROID_HOME=~/.local/android-toolchain/sdk
export PATH=$JAVA_HOME/bin:$ANDROID_HOME/platform-tools:$PATH
cp keystore.properties.example keystore.properties   # point at your release keystore
echo "sdk.dir=$ANDROID_HOME" > local.properties
./gradlew :app:testDebugUnitTest :app:assembleRelease
# → app/build/outputs/apk/release/app-release.apk
```
No keystore → the release APK is debug-signed (warning printed) and cannot upgrade a release install.

Requires JDK 17.

## Install on a phone
`adb install -r app/build/outputs/apk/release/app-release.apk`, or copy the APK to the phone and open it
(allow installs from this source once). Official builds: GitHub Releases, tags `android-v*`.

## Release
1. Bump `versionName` (semver) and `versionCode` (+1) in `version.properties`; add an "Android app" line to `CHANGELOG.md`.
2. Commit, push, then `git tag android-vX.Y.Z && git push origin android-vX.Y.Z` — CI attaches `pursekeep-X.Y.Z.apk` to a GitHub Release.
3. Phones show an "Update available" banner on next open.

## Wire contract
`docs/wallet-capture-contract.md`; fixtures in `contract/fixtures/` are tested by both the server (`tests/wallet-contract.test.ts`) and `ContractTest.kt`.
