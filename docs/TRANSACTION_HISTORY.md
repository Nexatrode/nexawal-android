# Transaction history

Wallet shows a 10-record preview, whole-ledger total, View all, and a pending shortcut.
Transactions is nested under Wallet; the four bottom tabs are unchanged. It filters/searches
all local records and lazily loads 50 at a time with a four-page/200-record cache.

HistoryPaging validates the native JSON page; TransactionsPager rejects late generations and
mixed revisions. A stale revision leaves visible rows intact and offers reload, anchored by txid.
Unknown/evicted rows load locally, not from the node. Sync-incomplete is not authoritative empty.
The receive/send/balance and restore-height semantics are unchanged.

The nested MoneroWalletCoreFFI source contains the new query C ABI. The Android build continues
building Rust from source; no precompiled WalletCore dependency was added. The submodule now includes
the post-0.1.9 `rustls` security update; publish WalletCore before publishing the Android pin.

Verification:

    ANDROID_NDK_HOME=/Users/steve/Library/Android/sdk/ndk/29.0.14206865 ./gradlew :logic:test :app:testDebugUnitTest :app:assembleDebug

67 logic and 27 app tests pass. New tests cover native page validation, 10k-record bounded caches,
generation/cancellation, stale revisions/retry, anchor reloads, and real Compose scrolling to far
indices and back. A rendered confirmation regression advances and rewinds the chain height while
verifying that only one page fetch occurs and the same cache/revision is retained. Row and detail
counts use the current height without extra JNI or node requests. A synthetic screenshot is generated at
app/build/reports/transaction-history/ten-thousand-neon.png.

No phone installation, real wallet mutation, mnemonic retrieval, or live node operation was done.
Before release, verify tab/back navigation, date entry, large fonts/TalkBack, and history changes
during a real sync. Coordinate the final WalletCore source pin with iOS and GPUI.
