package com.fcapps.bff

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.fcapps.bff.Prefs.isBlacklisted
import com.fcapps.bff.Prefs.password
import com.fcapps.bff.Prefs.setupDone

class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        if (!context.setupDone) return

        val storedPassword = context.password
        if (storedPassword.isEmpty()) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        for (message in messages) {
            val sender = message.originatingAddress ?: continue
            val body = message.messageBody ?: continue

            // Check if message body CONTAINS the password as a substring (case-sensitive)
            if (body.contains(storedPassword)) {
                // Check if sender is blacklisted
                if (context.isBlacklisted(sender)) continue

                // Trigger alarm
                val alarmIntent = Intent(context, AlarmService::class.java)
                context.startForegroundService(alarmIntent)
                break
            }
        }
    }
}
