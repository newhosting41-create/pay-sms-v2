package com.smmpaysync.app

import android.content.Context
import java.util.concurrent.Executors

/** One pass: read inbox for new bKash SMS, send pending to server, check which were used. */
object SyncRunner {
    private val exec = Executors.newSingleThreadExecutor { r ->
        Thread(r, "sync-runner").also { it.isDaemon = true }
    }

    @Synchronized
    fun runOnce(ctx: Context) {
        val app = ctx.applicationContext
        try {
            InboxScanner.scan(app)
        } catch (e: Exception) {
        }
        try {
            if (UiUtil.isOnline(app)) {
                SyncEngine.run(app)
                SyncEngine.pollStatus(app)
            }
        } catch (e: Exception) {
        }
        Notifier.update(app)
    }

    fun trigger(ctx: Context) {
        val app = ctx.applicationContext
        exec.execute { runOnce(app) }
    }
}
