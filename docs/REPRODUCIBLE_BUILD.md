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

```bash
./gradlew :app:assembleRelease
# optional Play upload artifact:
./gradlew :app:bundleRelease
```

Outputs (typical):

- APK: `app/build/outputs/apk/release/app-release-unsigned.apk`
  (or signed if you configured `signingConfigs` locally — do **not** commit keystores)
- AAB: `app/build/outputs/bundle/release/app-release.aab`

### What the build does with native code

On each build, the `:walletcore` module:

1. Copies prebuilt `libmonerowalletcore.so` from  
   `MoneroWalletCoreFFI/Artifacts/android/{arm64-v8a,x86_64}/`  
   into `walletcore/src/main/jniLibs/`.
2. Copies `libc++_shared.so` from the configured **NDK**.
3. Builds `libwalletcore_jni.so` via CMake (`walletcore/src/main/cpp`).

A normal app build does **not** recompile Rust/`monero-oxide`. Reproducibility of
the prebuilt `.so` files is therefore tied to the **MoneroWalletCoreFFI**
submodule commit (and however those artifacts were produced upstream).

To rebuild the core artifacts from that submodule (when needed):

```bash
cd MoneroWalletCoreFFI
# See that repo’s Scripts/build_android.sh (INSTALL_TO_NEXAWAL_ANDROID=1, etc.)
```

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
2. **Prebuilt `libmonerowalletcore.so`** — verified as “same submodule artifacts,”
   not “rebuilt from Rust on every app CI run” unless you document and pin that
   rebuild path for the release.
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
