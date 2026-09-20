# Focused security fixes — September 2026

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
dependency advisories and the remaining release gates. Public pins are not updated yet.

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

The fixes are local. The Android submodule working tree contains the same core source changes as
the primary WalletCore checkout so local from-source builds include them. Do not discard those
changes with a submodule update. Publish the next WalletCore release and then replace this local
submodule diff with its final commit pin. No F-Droid source-build path has been replaced by binaries.
