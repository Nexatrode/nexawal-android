# F-Droid packaging

Application id: `com.nexatrode.nexawal`  
License: MIT  
Source: https://github.com/Nexatrode/nexawal-android  

This repo is set up so F-Droid can **rebuild** `libmonerowalletcore.so` from the
pinned `MoneroWalletCoreFFI` submodule. Native binaries are not committed to
the app or WalletCore repositories.

Companion docs:
- Wallet Scrutiny / reproducible builds: [REPRODUCIBLE_BUILD.md](REPRODUCIBLE_BUILD.md)
- Privacy: [PRIVACY.md](PRIVACY.md)
- Store copy (Fastlane): [`../fastlane/metadata/android/`](../fastlane/metadata/android/)
- Draft fdroiddata recipe: [`fdroid/com.nexatrode.nexawal.yml`](fdroid/com.nexatrode.nexawal.yml)

## AntiFeatures (expected)

Declare these honestly in fdroiddata:

| AntiFeature | Why |
| --- | --- |
| `NonFreeNet` | Default daemon is `https://rpc.nexatrode.com` (user-replaceable). Optional fiat estimates hit Kraken / Frankfurter when enabled. |

No proprietary Google SDKs ship in the APK.

## Build requirements (buildserver)

- JDK **17**
- Android SDK (compileSdk **36**), NDK (**r27b** recommended; pin in recipe)
- Rust **stable** with targets `aarch64-linux-android`, `x86_64-linux-android`
- Git submodules initialized
- Source build is the Gradle default. `NEXAWAL_BUILD_NATIVE_FROM_SOURCE=1` is
  still accepted for older recipes.
- Env: `ANDROID_NDK_HOME` pointing at the NDK used for the build
- Optional: `CARGO_FEATURES=compile-time-generators`, `ABIS=arm64-v8a,x86_64`, `ANDROID_API=26`

Gradle entrypoint:

```bash
./gradlew :app:assembleRelease
```

Unsigned APK (typical):

`app/build/outputs/apk/release/app-release-unsigned.apk`

CI reference build: [`.github/workflows/native-android.yml`](../.github/workflows/native-android.yml).

## Submission checklist (maintainer)

1. Bump `versionName` / `versionCode` in `app/build.gradle.kts` if needed.
2. Update [CHANGELOG.md](../CHANGELOG.md) and Fastlane `changelogs/<versionCode>.txt`.
3. Clean tree; confirm `git submodule status` pin.
4. Tag `v<versionName>` on the release commit and push the tag.
5. GitHub Release: app SHA, FFI SHA, NDK, AGP/Gradle, link to native CI SHA256SUMS.
6. Open / update an MR on [fdroiddata](https://gitlab.com/fdroid/fdroiddata) using
   [`fdroid/com.nexatrode.nexawal.yml`](fdroid/com.nexatrode.nexawal.yml) as the starting point.
7. Expect F-Droid maintainers to adjust `sudo:` / NDK / Rust paths for their buildserver.

## Local dry-run (developer machine)

```bash
git checkout v1.0.0
git submodule update --init --recursive
export ANDROID_NDK_HOME=/path/to/ndk/27.1.12297006   # or ndk.dir in local.properties
./gradlew :app:assembleRelease --no-daemon
```

## Screenshots

Phone screenshots live under `docs/screenshots/` and are linked from the README.
Copy into Fastlane `phoneScreenshots/` when preparing store assets if desired.
