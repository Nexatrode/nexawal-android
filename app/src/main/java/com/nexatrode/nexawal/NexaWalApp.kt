package com.nexatrode.nexawal

import android.app.Application

/**
 * Process-scoped [WalletManager] so an in-flight refresh (and its foreground service)
 * survives Activity recreation while the user leaves the app to sync.
 */
class NexaWalApp : Application() {
    lateinit var walletManager: WalletManager
        private set

    override fun onCreate() {
        super.onCreate()
        walletManager = WalletManager(applicationContext)
    }
}
