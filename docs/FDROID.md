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

## Network disclosures / AntiFeatures

The default daemon is user-configurable. Fiat rates and transaction-explorer
lookups are optional and disabled by default. The draft therefore does not
pre-label the app as `NonFreeNet` or `TetheredNet`; explain these behaviors in
the listing and let F-Droid maintainers make the final AntiFeature decision.

No proprietary Google SDKs ship in the APK.


## Build requirements (buildserver)

- JDK **21** (Kotlin-only modules emit Java 11 bytecode)
- Android SDK (compileSdk **36**), NDK (**r27b** recommended; pin in recipe)
- Rust **1.97.1** (the WalletCore toolchain pin) with targets `aarch64-linux-android`, `x86_64-linux-android`; the draft recipe installs this through fdroiddata's pinned `rustup` srclib
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
7. Run `fdroid lint` and a clean F-Droid build-server test; the Gradle native task adds `$CARGO_HOME/bin` to its subprocess PATH so the rustup-installed `cargo` works after F-Droid's setup shell exits. The recipe removes WalletCore's `resp.bin` test fixture because F-Droid rejects binaries in source; it is not needed to build or run the app.

## 1.0.5 buildserver verification

The source commit submitted to Google Play was also rebuilt successfully for
F-Droid as an unsigned APK:

- Version: `1.0.5` / code `7`
- App commit: `6af1f9e30b9e81077990254cceba91eb26a302d9`
- WalletCore submodule: `f51b3066f221d1f4e82e43afbf110bb9fad19af1`
- Environment: F-Droid's official `fdroidserver:buildserver` Docker image,
  Android SDK 36 / build-tools 36.1.0, NDK 27.1.12297006, JDK 21, Rust 1.97.1
- Command: `fdroid build --verbose com.nexatrode.nexawal:7`
- Result: `BUILD SUCCESSFUL`; F-Droid reported one successful build.
- Unsigned APK SHA-256:
  `183883fe8e074155d844fa8e0e6a4635b0bee92f6e15e807e53acad0e7ba6ad7`
- F-Droid source archive SHA-256:
  `034d4588426b1ad4328c8aa98951f2f283e56f83472d6fac33315a78e9a67e54`

This is a local clean buildserver test, not a build by F-Droid's hosted
infrastructure. The APK is unsigned and is not the Play-distributed APK; F-Droid
will sign its repository build after the metadata proposal is accepted.

## Local dry-run (developer machine)

```bash
git checkout v1.0.5   # only after device QA and the tag exists
git submodule update --init --recursive
export ANDROID_NDK_HOME=/path/to/ndk/27.1.12297006   # or ndk.dir in local.properties
./gradlew :app:assembleRelease --no-daemon
```

## Screenshots

Phone screenshots live under Fastlane `metadata/android/en-US/images/phoneScreenshots/`
so F-Droid can display the app in its Latest listing. The current release includes
one wallet screenshot.
