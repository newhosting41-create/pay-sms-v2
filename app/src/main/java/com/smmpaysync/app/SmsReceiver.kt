package com.smmpaysync.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony

/**
 * Receives the SMS broadcast and hands the work to the foreground service.
 * The receiver itself deliberately does no network I/O.
 */
class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val msgs = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (msgs.isNullOrEmpty()) return

        val app = context.applicationContext
        val from = msgs.first().originatingAddress ?: ""
        val provider = Providers.byHeader(from) ?: return
        val body = msgs.joinToString("") { it.messageBody ?: "" }

        try {
            SmsForegroundService.enqueueSms(app, provider.key, body)
        } catch (e: Exception) {
            // If this Android version/OEM refuses the FGS start, never lose the SMS.
            try {
                SmsIngest.handle(app, provider, body, sendInline = false)
                Prefs(app).note("SMS saved; foreground sync start failed, retry queued: ${e.message}")
            } catch (inner: Exception) {
                Prefs(app).note("error while saving SMS: ${inner.message}")
            }
        }
    }
}
