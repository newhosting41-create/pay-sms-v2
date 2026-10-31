package com.smmpaysync.app

import android.content.Context
import java.util.UUID

object SmsIngest {

    /** Save one bKash SMS. Same logic for live receiver and inbox scan. Returns true if a new row was saved. */
    fun handle(ctx: Context, provider: Provider, body: String, sendInline: Boolean = false): Boolean {
        val prefs = Prefs(ctx)
        if (!prefs.providerEnabled(provider)) {
            prefs.note("bKash SMS seen, but bKash switch is OFF")
            return false
        }
        val db = Db.get(ctx)
        val p = SmsParsers.parse(provider, body)

        if (p == null) {
            if (!body.contains("TrxID", ignoreCase = true)) {
                prefs.note("bKash SMS ignored (not a payment message)")
                return false
            }
            // Payment-looking SMS we could not read: keep it visible in Pending.
            val saved = db.insert(
                Ev(
                    localId = UUID.randomUUID().toString(),
                    provider = provider.key,
                    trxId = "UNREAD-" + Integer.toHexString(body.hashCode()),
                    amount = 0.0,
                    sender = "",
                    smsTime = "",
                    raw = body,
                    status = St.FAILED,
                    error = "Could not read this SMS. Copy it and use Send SMS Manually.",
                    createdAt = System.currentTimeMillis()
                )
            )
            prefs.note(if (saved) "payment SMS could not be read, kept in Pending" else "unreadable SMS already kept")
            if (saved) Notifier.update(ctx)
            return saved
        }

        val saved = db.insert(
            Ev(
                localId = UUID.randomUUID().toString(),
                provider = provider.key,
                trxId = p.trxId,
                amount = p.amount,
                sender = p.sender,
                smsTime = p.smsTime,
                raw = body,
                status = St.PENDING,
                error = null,
                createdAt = System.currentTimeMillis()
            )
        )
        prefs.note(if (saved) "payment SMS saved, TrxID ${p.trxId}" else "already saved, TrxID ${p.trxId}")
        if (saved) {
            Notifier.update(ctx)
            // From the SMS receiver we send inline, because Android may freeze the process
            // as soon as the receiver returns. Other callers use the background runner.
            if (sendInline) SyncRunner.runOnce(ctx) else SyncRunner.trigger(ctx)
            SyncWorker.enqueue(ctx)   // guaranteed backup, survives process death
        }
        return saved
    }
}
