# nexawal-android

`nexawal-android` is the Android version of the NexaWal Monero wallet, built on top of `monero-oxide` and the shared wallet core.

- Android app: this repository
- iOS app: [nexawal](https://github.com/cacaosteve/nexawal)
- Shared wallet core (git submodule): [MoneroWalletCoreFFI](https://github.com/cacaosteve/MoneroWalletCoreFFI) (`main`)
- Monero library work: [monero-oxide](https://github.com/cacaosteve/monero-oxide) (fork pin used by the core)

## Setup

```bash
git clone --recurse-submodules https://github.com/cacaosteve/nexawal-android.git
cd nexawal-android
./gradlew :app:assembleDebug
```

If you already cloned without submodules:

```bash
git submodule update --init --recursive
```

To move the submodule to the tip of `main`:

```bash
git submodule update --remote MoneroWalletCoreFFI
```

Gradle copies prebuilt `libmonerowalletcore.so` from `MoneroWalletCoreFFI/Artifacts/android/` into `walletcore/src/main/jniLibs/` on each build (no Rust required for a normal app build). You still need an Android NDK for `libc++_shared.so` and the JNI shim.

## Screenshots

| Wallet | Receive |
| --- | --- |
| ![Android wallet](docs/screenshots/android1.png) | ![Android receive](docs/screenshots/android2.png) |

| Send | Settings |
| --- | --- |
| ![Android send](docs/screenshots/android3.png) | ![Android settings](docs/screenshots/android4.png) |

## Features

- Single-wallet Monero app (create or import)
- Create-flow seed backup gate (write-down confirmation + word check) before the wallet is persisted
- Optional device biometrics / screen-lock auth for unlock and send
- Techno Theme toggle (on = neon terminal look; off/default = standard look)
- Clearnet / I2P / hybrid node routing
- Sync status with honest tip/scanned progress; node errors surface when refresh fails
- Receive: QR, copy address, copy payment URI when an amount is set, subaddresses
- Send / send-max with fee preview; prepare → durable persist → relay under the hood
- Transaction details with copy txid and optional explorer link

## Notes

- Uses a native wallet core built from `monero-oxide` via `MoneroWalletCoreFFI`
- Android consumes the shared core through the submodule + JNI `.so` integration
- Syncs against standard Monero nodes (local or remote), including the configured I2P RPC path when enabled
- Default daemon is `https://rpc.nexatrode.com` (type a full `http://` or `https://` URL to override)
- Feature parity target: [nexawal](https://github.com/cacaosteve/nexawal)
- Unaudited software. You are responsible for backups and funds. A remote node can see your IP and sync queries.

## Privacy

See [docs/PRIVACY.md](docs/PRIVACY.md). Play Console data-safety / privacy policy can use that GitHub URL.

## License

[MIT](LICENSE). Downstream code such as `monero-oxide` remains under its own MIT terms; keep those notices when you redistribute.
