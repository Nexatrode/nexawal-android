package com.nexatrode.nexawal

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat

/**
 * Foreground service that keeps wallet refresh eligible to run with the screen off.
 *
 * Type [ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC] matches long blockchain scans.
 * Progress text is updated from [WalletManager]; Stop calls [WalletManager.cancelRefresh].
 */
class WalletSyncForegroundService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopForegroundAndSelf()
                return START_NOT_STICKY
            }
            ACTION_CANCEL -> {
                runCatching {
                    (applicationContext as? NexaWalApp)?.walletManager?.cancelRefresh()
                }.onFailure { t ->
                    Log.w(TAG, "Cancel from notification failed: ${t.message ?: t.javaClass.simpleName}")
                }
                stopForegroundAndSelf()
                return START_NOT_STICKY
            }
            ACTION_UPDATE -> {
                val percent = intent.getIntExtra(EXTRA_PERCENT, 0).coerceIn(0, 100)
                val detail = intent.getStringExtra(EXTRA_DETAIL)
                    ?: getString(R.string.sync_notification_progress_fmt, percent)
                val nm = getSystemService(NotificationManager::class.java)
                nm?.notify(NOTIFICATION_ID, buildNotification(percent, detail))
                return START_STICKY
            }
            else -> {
                // ACTION_START or null (system restart)
                ensureChannel()
                val percent = intent?.getIntExtra(EXTRA_PERCENT, 0)?.coerceIn(0, 100) ?: 0
                val detail = intent?.getStringExtra(EXTRA_DETAIL)
                    ?: getString(R.string.sync_notification_progress_fmt, percent)
                val notification = buildNotification(percent, detail)
                ServiceCompat.startForeground(
                    this,
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
                )
                return START_STICKY
            }
        }
    }

    private fun stopForegroundAndSelf() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun ensureChannel() {
        val nm = getSystemService(NotificationManager::class.java) ?: return
        val existing = nm.getNotificationChannel(CHANNEL_ID)
        if (existing != null) return
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.sync_notification_channel),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = getString(R.string.sync_notification_channel_desc)
                setShowBadge(false)
            },
        )
    }

    private fun buildNotification(percent: Int, detail: String): Notification {
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, WalletSyncForegroundService::class.java).setAction(ACTION_CANCEL),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_sync_notification)
            .setContentTitle(getString(R.string.sync_notification_title))
            .setContentText(detail)
            .setContentIntent(openApp)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setProgress(100, percent.coerceIn(0, 100), percent <= 0)
            .addAction(0, getString(R.string.sync_notification_stop), stopIntent)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    companion object {
        private const val TAG = "WalletSyncFgs"
        const val CHANNEL_ID = "wallet_sync"
        const val NOTIFICATION_ID = 42

        const val ACTION_START = "com.nexatrode.nexawal.action.SYNC_START"
        const val ACTION_UPDATE = "com.nexatrode.nexawal.action.SYNC_UPDATE"
        const val ACTION_STOP = "com.nexatrode.nexawal.action.SYNC_STOP"
        const val ACTION_CANCEL = "com.nexatrode.nexawal.action.SYNC_CANCEL"

        const val EXTRA_PERCENT = "percent"
        const val EXTRA_DETAIL = "detail"

        fun start(context: Context, percent: Int = 0, detail: String? = null) {
            val app = context.applicationContext
            val intent = Intent(app, WalletSyncForegroundService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_PERCENT, percent)
                if (detail != null) putExtra(EXTRA_DETAIL, detail)
            }
            ContextCompat.startForegroundService(app, intent)
        }

        fun update(context: Context, percent: Int, detail: String) {
            val app = context.applicationContext
            val intent = Intent(app, WalletSyncForegroundService::class.java).apply {
                action = ACTION_UPDATE
                putExtra(EXTRA_PERCENT, percent)
                putExtra(EXTRA_DETAIL, detail)
            }
            // Service already running: deliver update without another startForegroundService call.
            runCatching { app.startService(intent) }
                .onFailure { t ->
                    Log.w(TAG, "update failed: ${t.message ?: t.javaClass.simpleName}")
                }
        }

        fun stop(context: Context) {
            val app = context.applicationContext
            val intent = Intent(app, WalletSyncForegroundService::class.java).setAction(ACTION_STOP)
            runCatching { app.startService(intent) }
                .onFailure {
                    runCatching { app.stopService(Intent(app, WalletSyncForegroundService::class.java)) }
                }
        }
    }
}
