package com.smmpaysync.app

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.content.ContextCompat
import java.util.concurrent.Executors

/**
 * Short-lived foreground execution path for a newly received payment SMS.
 * Starts foreground immediately, saves the SMS locally, then performs an immediate
 * server sync. Pending items are retained and WorkManager/MonitorService retry them.
 */
class SmsForegroundService : Service() {
    private val executor = Executors.newSingleThreadExecutor()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = Notifier.build(this)
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(Notifier.ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(Notifier.ID, notification)
        }

        val providerKey = intent?.getStringExtra(EXTRA_PROVIDER)
        val body = intent?.getStringExtra(EXTRA_BODY)
        if (providerKey.isNullOrBlank() || body.isNullOrBlank()) {
            stopSelf(startId)
            return START_NOT_STICKY
        }

        executor.execute {
            try {
                val provider = Providers.byKey(providerKey)
                // Save first, then sync immediately from the service process.
                SmsIngest.handle(applicationContext, provider, body, sendInline = true)
            } catch (e: Exception) {
                Prefs(applicationContext).note("foreground SMS sync error: ${e.message}")
                SyncWorker.enqueue(applicationContext)
            } finally {
                stopSelf(startId)
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        executor.shutdown()
        super.onDestroy()
    }

    companion object {
        private const val EXTRA_PROVIDER = "provider"
        private const val EXTRA_BODY = "body"

        fun enqueueSms(ctx: Context, providerKey: String, body: String) {
            val i = Intent(ctx.applicationContext, SmsForegroundService::class.java)
                .putExtra(EXTRA_PROVIDER, providerKey)
                .putExtra(EXTRA_BODY, body)
            ContextCompat.startForegroundService(ctx.applicationContext, i)
        }
    }
}
