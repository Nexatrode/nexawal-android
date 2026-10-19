# Focused security fixes — September 2026

## WalletCore v0.1.19 dependency update

The source submodule now pins `c5430e9ba86ca962aae07e6744f8eaa78abff905` and builds WalletCore
0.1.19 from source with Rustls 0.23.46. Android unit tests and the unsigned Release APK build pass;
signed-device authentication, lifecycle, and send/recovery QA remain required before distribution.
No WalletCore prebuilt binary replaces the from-source F-Droid and Wallet Scrutiny path.

## October 13 release-candidate corrections — WalletCore v0.1.18

- Create uses the configured scan node and HTTP-proxy policy, a five-second whole-call timeout
  and a 64 KiB response cap. A synced node's `target_height: 0` falls back to `height`; an absent
  tip is an error, not a silent genesis-height suggestion. Restore/import heights stay user-owned.
- Debug and Release share Compose BOM 2025.09.01 (foundation-layout 1.9.2). The app, not just its
  native library module, restricts packaging to arm64-v8a and x86_64. Source builds remain default.
- Foreground-service timeout requests cancellation and stops the service immediately. Native
  checkpoint cleanup drains separately; the Service never waits for network or signing work.
- Already-authorized sends belong to the retained manager. Rotation cancels only the UI waiter,
  not prepare/journal/relay, and completion/errors are retained for the next screen. A duplicate
  send is refused until the worker finishes. Destroyed Activities remove their old idle timers;
  background/idle session authority still locks.
- The locked surface offers explicit wallet replacement using translated backup warnings and
  device authentication when available. It does not require decrypting the lost seed. Native
  cleanup must complete and signed journals must be archived before exact scan/metadata files
  and the app's two Keystore aliases are removed. Failures do not silently open setup.

Synthetic tests cover creation-height fallback, retained send success/failure after UI cancellation,
duplicate refusal, cancelled authorization, reset authority/foreground fencing, and Service timeout.
The creation tests also exercise real bounded HTTP responses through WalletManager and reject an
invalid configured proxy before any direct network attempt.
Verification: 98 logic and 56 app tests pass; Debug and unsigned Release APKs build. A temporary
Release package containing the changed local Core was also built and checked against both native
library hashes. The source submodule now pins the final v0.1.18 release commit
`c3cc89519ff88e68322fcddbcc6227669b943a34`, including native quarantine/FFI hardening.
The default build remains from source for F-Droid and Wallet Scrutiny. No real wallet was opened
or payment sent; no app release or submission is performed by this rollout.

Additional signed-device gates: rotate during preview, auth, signing and relay; background a send
and reopen to its retained result; kill/restart after durable journal creation; simulate lost
Keystore access with a disposable wallet and verify explicit recovery never auto-relays archived
journals. Verify Android 11 and current Android release APK installation, full-recipient wrapping,
and timeout cancellation. Host tests do not replace these checks or a reproducibility comparison.

## October 2026 replay and persistence follow-up (WalletCore v0.1.17)

The source submodule pins `79c8ceb3ba8629bce72738c52e360c9c4fd8bed5`.
Coverage replay preserves receives re-mined before their previous height without
resetting their key images or observed spends. Native regression tests include a
cache round-trip checking the receive's balance, height, timestamp and fee.
Receive books are saved atomically; save failures abort address issuance. Reads
do not overwrite corrupt books or race issuance by rewriting stale snapshots.
An authorized CSV copy is owned by the retained Activity ViewModel, not a
cancellable Compose effect; rotation/relocking cannot dispose of its staged file.
Starting a copy still requires the same unlocked wallet. These exports do not
survive process death, and filesystem errors are reported to the user.

Verification: 87 logic tests and 51 app tests pass, including a fresh uncached run.
Debug and Release APKs build from source with Rust 1.97.1 and NDK 27.1.12297006;
both native ABIs retain 16 KB alignment. No device was installed or rescanned,
no payment was sent, and signed-device QA remains required before distribution.

## October 2026 parity follow-up (WalletCore v0.1.16)

The source submodule now pins `8b01cc3e321a5aa62a80797ef75d33052be583b2`.
Receive-address issuance and reopening require native coverage of all saved indices;
coverage growth rescans from the original restore height without deleting known history.
Recovered pending payments are reported separately: they cannot become success for a new send.
Fee previews are bound to the payment/session/node and checked again after authentication.
CSV chooser results survive background locking and are only written for the same unlocked wallet.
Rates responses and decimal precision are bounded before decoding.

Verification: 87 logic tests and 46 app tests pass; Debug and Release APKs build from
source using Rust 1.97.1 and NDK 27.1.12297006. The signed-device QA gates below still apply.
No personal wallet was opened or payment sent, and no store submission was performed.

## WalletCore v0.1.15 rollout: session authority and seed buffers

- Unlocks are generation-fenced before native open/unseal and UI publication, including before
  the first wallet ID exists. Native and plaintext metadata ownership are tracked separately.
- Failed/stale opens clean up their own authority without tearing down a newer authenticated session.
- Metadata operations cannot repopulate the plaintext session after background locking.
- Seed setup drops input/generated/challenge references on background/disposal. FLAG_SECURE
  covers the Activity; secret keyboard requests disable personalized learning/extract UI.
  These requests do not make a malicious keyboard trustworthy.
- AES plaintext arrays and owned JNI seed buffers are wiped. Kotlin/VM strings and framework
  copies cannot be guaranteed erased; do not advertise complete RAM wiping.
- Signed-device auth, first unlock/background timing, rotation and native teardown QA remain required.

Release verification: 127 logic/app tests pass; Debug and Release APKs build from the pinned
WalletCore v0.1.15 source with Rust 1.97.1 and NDK 27.1.12297006. Both ABIs retain 16 KB alignment.
Neither release JNI nor native library links a Logcat writer. No device was installed, opened,
or rescanned. F-Droid and Wallet Scrutiny keep the normal source-build path.

Additional device QA: background while the first authentication is visible and immediately after
it succeeds; cancel/retry without reopening an old attempt. Check seed input, Recents/screenshots,
keyboard no-learning requests, setup persistence failure, and idle lock with a disposable wallet.

## Behaviors covered

- New wallets default to device authentication when a secure biometric/device credential is
  available. The persisted mnemonic is wrapped by a distinct Android Keystore key that requires
  recent strong biometric or device-credential authentication; the old key was encryption-only.
- Existing encrypted metadata is migrated from format v2 to authenticated format v3 only after a
  successful system prompt. The replacement file is written atomically and the old Keystore key is
  retained, so an interrupted migration does not destroy the wallet.
- Protected sessions lock on Activity stop or after five minutes without interaction, clear the
  app-layer plaintext metadata reference, and require another prompt. An authentication that
  finishes after the lifecycle lock cannot reopen UI.
- Disabling device authentication requires successful authentication before the preference changes.
  Cancellation, unavailable authentication, and coroutine cancellation leave protection enabled.
- Each Activity starts with a locked access gate; a process-retained wallet ID does not unlock the UI.
  An already-running wallet is not reopened/reset after authentication on Activity recreation.
- Exact and filtered sends, plus whole-wallet and filtered sweeps, require an explicit approved
  maximum fee. The prepared fee is checked before BOTH pending-send persistence and relay.
  Higher fees stop the send and require another preview/confirmation; lower fees remain allowed.
  Recovery of an already-persisted, immutable signed transaction is still idempotent.
- Kotlin and JNI wallet diagnostics are off in release and opt-in in debug. The shared Rust library
  separately disables sensitive logs unless built with `diagnostic-logging` and opted in at runtime.
- WalletCore bounds HTTP bodies before decoding, including chunked responses and the output-index fallback.

## Local checks

```sh
./gradlew :logic:test :app:testDebugUnitTest :app:assembleDebug :app:assembleRelease
```

`WalletAccessGateTest`, `FeeApprovalTest`, and `WalletDiagnosticsTest` use test-only data;
they do not send transactions or read a real wallet. WalletCore has additional response-limit tests.

The September 20 follow-up ran the complete logic and app unit suites plus Debug Kotlin compilation
after the authenticated-metadata migration. An actual device check remains required because host JVM
tests cannot emulate Android Keystore authentication tokens.

Previously verified: 68 logic tests and 29 app tests pass, and both Debug and Release APKs build.
The source-built arm64-v8a and x86_64 release core/JNI libraries have no Logcat writer linked in.
No real wallet was opened and no transaction was sent during verification.

The follow-up hardening pass additionally bounds cache/journal file reads, preserves pending-send
identity during JSON round-trips, and archives pending journals on explicit wallet replacement.
The matching native core rejects legacy/unbound and foreign-wallet journals before any RPC.
See the sibling WalletCore `docs/production-hardening-2026-09-05.md` for synthetic scale results,
dependency advisories and the remaining release gates. The Android submodule pins the published
WalletCore v0.1.15 commit `7f7153ecc902380130c8c9461c3310f1bfddab83` and builds it from source.

## Required device checks before release

Use a disposable test wallet, not a user's funds:

1. Enable protection, toggle it off, and cancel the prompt. Verify it remains enabled.
2. Repeat with successful authentication; verify the setting changes only afterward.
3. Upgrade an existing v2 wallet, authenticate once, force-stop, and reopen. Confirm metadata reports
   format v3 without ever displaying or rewriting a plaintext mnemonic.
4. Unlock a wallet and rotate/recreate its Activity while the process continues syncing. Cancel the
   new prompt: no balance, history, seed/setup controls, or tabs should be visible. Retry and unlock.
5. Background the protected app and return. Verify the unlock surface replaces wallet UI in the app
   switcher and that cancelling the new prompt remains locked while background sync state is retained.
6. Cancel a send authentication prompt and verify no pending-send file or transaction is produced.
7. With a controlled test RPC, increase the fee after preview. Exact and sweep sends must stop
   without a pending file or broadcast; preview again before proceeding.

## Rollout

The security fixes and WalletCore v0.1.15 source pin are on main. Native release assets remain an
explicit opt-in, not the default Android build input. No F-Droid/Wallet Scrutiny submission or
app-store release is performed by this rollout. Signed-device QA above remains a release gate.
