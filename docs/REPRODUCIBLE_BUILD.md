# Reproducible builds (Android)

This document is for Wallet Scrutiny reviewers and anyone verifying that a
Play Store / GitHub release of **NexaWal Android** matches public source.

Application id: `com.nexatrode.nexawal`  
Repository: https://github.com/Nexatrode/nexawal-android  
Website: https://nexatrode.com

## What “matching” means here

NexaWal Android is normally shipped to Google Play as an **AAB**. With Play App
Signing, Google re-signs the installable APKs users receive. Do not expect the
developer-signed APK hash to equal the Play-installed APK hash.

A useful verification today:

1. Check out the **git tag** that corresponds to the Play `versionName` /
   `versionCode`.
2. Build release from that exact commit (including the pinned submodule).
3. Compare **unsigned / content** of the AAB (or split APKs derived from it)
   against the Play artifact, allowing for signing-block differences — the
   approach Wallet Scrutiny uses for AABs (`testAAB.sh`-style).

## Version ↔ source mapping

| Field | Where |
| --- | --- |
| `versionName` | `app/build.gradle.kts` → `defaultConfig.versionName` |
| `versionCode` | `app/build.gradle.kts` → `defaultConfig.versionCode` |
| Release tag | Prefer `v<versionName>` (e.g. `v1.0.0` for `1.0`) on the commit uploaded to Play |
| Native core | Git submodule `MoneroWalletCoreFFI` at a **specific commit** (recorded in the parent repo) |

Before tagging a Play release:

```bash
git status          # clean tree
git submodule status
git rev-parse HEAD
```

Record those values in the GitHub Release notes.

## Prerequisites

- JDK **17** (AGP 9.x / current Android tooling)
- Android SDK with **compileSdk 36**
- Android **NDK** (for `libc++_shared.so` and the CMake JNI shim)
- Network once to resolve Gradle / Maven deps (offline rebuilds need a warm cache)

Pinned tooling in-repo:

- Gradle wrapper: **9.6.1** (`gradle/wrapper/gradle-wrapper.properties`)
- Android Gradle Plugin: **9.3.1** (`gradle/libs.versions.toml`)
- Kotlin: **2.2.10**

## Fetch exact source

```bash
git clone --recurse-submodules https://github.com/Nexatrode/nexawal-android.git
cd nexawal-android
git checkout v1.0.0          # use the release tag under test
git submodule update --init --recursive
```

Confirm the submodule commit matches the release notes:

```bash
git submodule status
# expect: <sha> MoneroWalletCoreFFI (…pinned…)
```

## Build (unsigned release APK / AAB)

Configure SDK/NDK via `local.properties` (gitignored), for example:

```properties
sdk.dir=/path/to/Android/sdk
ndk.dir=/path/to/Android/sdk/ndk/<version>
```

Or export `ANDROID_NDK_HOME` / `ANDROID_NDK_ROOT`.

### Default (local / Play): copy committed Artifacts

```bash
./gradlew :app:assembleRelease
# optional Play upload artifact:
./gradlew :app:bundleRelease
```

Fast path: no Rust required. Gradle copies submodule `Artifacts/` into `jniLibs`.

### F-Droid / Wallet Scrutiny: rebuild native from source

Do **not** trust the committed `.so` blobs. Force Gradle to run the submodule’s
`Scripts/build_android.sh` (Rust + NDK) before packaging:

```bash
# Rust stable with Android targets + NDK required
export ANDROID_NDK_HOME=/path/to/ndk
export NEXAWAL_BUILD_NATIVE_FROM_SOURCE=1
# optional overrides (defaults match CI):
# export ANDROID_API=26
# export CARGO_FEATURES=compile-time-generators
# export ABIS=arm64-v8a,x86_64

./gradlew :app:assembleRelease
```

`true` is also accepted for the env flag. When set, `:walletcore` skips the
Artifacts copy task and rebuilds + installs `libmonerowalletcore.so` into
`walletcore/src/main/jniLibs/` via `INSTALL_TO_NEXAWAL_ANDROID=1`.

Suggested **fdroiddata** / recipe sketch (exact metadata file is a separate MR):

```yaml
# In the build recipe:
sudo: false
# Install JDK 17, Android SDK/NDK, Rust (aarch64-linux-android, x86_64-linux-android)
# Then:
build:
  - export NEXAWAL_BUILD_NATIVE_FROM_SOURCE=1
  - export ANDROID_NDK_HOME=$$NDK
  - ./gradlew :app:assembleRelease
output: app/build/outputs/apk/release/app-release-unsigned.apk
```

Outputs (typical):

- APK: `app/build/outputs/apk/release/app-release-unsigned.apk`
  (or signed if you configured `signingConfigs` locally — do **not** commit keystores)
- AAB: `app/build/outputs/bundle/release/app-release.aab`

### What the build does with native code

On each build, the `:walletcore` module:

1. **Either** rebuilds `libmonerowalletcore.so` from the submodule
   (`NEXAWAL_BUILD_NATIVE_FROM_SOURCE=1`), **or** copies prebuilt
   `MoneroWalletCoreFFI/Artifacts/android/{arm64-v8a,x86_64}/` into
   `walletcore/src/main/jniLibs/`.
2. Copies `libc++_shared.so` from the configured **NDK**.
3. Builds `libwalletcore_jni.so` via CMake (`walletcore/src/main/cpp`).

Manual rebuild without Gradle (same script):

```bash
cd MoneroWalletCoreFFI
PROFILE=release CARGO_FEATURES="compile-time-generators" \
  INSTALL_TO_NEXAWAL_ANDROID=1 NEXAWAL_ANDROID_DIR=.. \
  ./Scripts/build_android.sh
```

### CI from-source rebuild (Wallet Scrutiny / F-Droid path)

GitHub Actions workflow [`.github/workflows/native-android.yml`](../.github/workflows/native-android.yml)
runs the **F-Droid path end-to-end**:

- Rust stable + Android NDK **r27b** + JDK 17
- `NEXAWAL_BUILD_NATIVE_FROM_SOURCE=1 ./gradlew :app:assembleRelease`
- Uploaded unsigned APK, `.so` files, and `native-android-SHA256SUMS.txt`
  (app commit, FFI commit, NDK)

Trigger: `workflow_dispatch`, or pushes/PRs that touch `MoneroWalletCoreFFI/**` /
the workflow file / `walletcore/build.gradle.kts`.

Local Play/debug builds still default to copying committed `Artifacts/` unless
you set the env flag.

For a Scrutiny note on a Play upload, attach the workflow’s SHA256SUMS (or a
matching local from-source rebuild) alongside the app/FFI commit SHAs.

## Fingerprints useful for reviewers

```bash
# APK / AAB content hash (after unzip, ignoring META-INF signatures if comparing to Play)
shasum -a 256 app/build/outputs/bundle/release/app-release.aab

# Native libs inside the unsigned APK
unzip -l app/build/outputs/apk/release/app-release-unsigned.apk | grep '\.so'

# Submodule + app commit
git rev-parse HEAD
git -C MoneroWalletCoreFFI rev-parse HEAD
```

## Known non-determinism / gaps (honest)

Call these out so Wallet Scrutiny is not surprised:

1. **Play App Signing** — installable APK signature ≠ developer upload signature.
2. **Prebuilt `libmonerowalletcore.so`** — default local Gradle still copies
   submodule `Artifacts/` for speed. F-Droid / Scrutiny must use
   `NEXAWAL_BUILD_NATIVE_FROM_SOURCE=1` (or the native CI workflow).
   Byte-identical match vs committed Artifacts is best-effort until release
   artifacts are produced by that same workflow.
3. **NDK host path / NDK version** — `libc++_shared.so` and the JNI shim can
   differ across NDK releases; pin the NDK version used for production tags in
   Release notes.
4. **Timestamps / build IDs** — unsigned APK zip entries or native build IDs may
   still differ; content-level / `apktool` / AAB split comparison is preferred.
5. **Release minify** — currently `isMinifyEnabled = false` for release; keep that
   stable or document changes.

## Provider checklist for each Play upload

- [ ] `versionName` / `versionCode` bumped as needed  
- [ ] Clean git tree; submodule commit intentional  
- [ ] Tag `v…` on the uploaded commit  
- [ ] GitHub Release lists: commit SHA, submodule SHA, NDK version, AGP/Gradle  
- [ ] nexatrode.com / Play listing point at https://github.com/Nexatrode/nexawal-android  
- [ ] Privacy: https://nexatrode.com/privacy/nexawal/  
- [ ] Terms: https://nexatrode.com/terms/

## Related

- App README setup: [../README.md](../README.md)
- iOS sibling: https://github.com/Nexatrode/nexawal
- Shared core: submodule `MoneroWalletCoreFFI` (see `.gitmodules`)
