package com.fcapps.bff

import android.Manifest
import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.SmsManager
import android.telephony.SubscriptionManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.fcapps.bff.Prefs.addHistoryEntry
import java.util.UUID

/** Replies use the subscription that received the command; sent callbacks report modem results. */
object SmsReplies {
    fun send(context: Context, to: String, text: String, subscription: Int): Boolean {
        try {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
                context.addHistoryEntry(to, to, context.getString(R.string.history_sms_failed))
                return false
            }
            @Suppress("DEPRECATION")
            val manager = if (subscription >= 0) {
                if (Build.VERSION.SDK_INT >= 31) context.getSystemService(SmsManager::class.java).createForSubscriptionId(subscription)
                else SmsManager.getSmsManagerForSubscriptionId(subscription)
            } else if (Build.VERSION.SDK_INT >= 31) context.getSystemService(SmsManager::class.java) else SmsManager.getDefault()
            val parts = manager.divideMessage(text)
            val id = UUID.randomUUID().toString()
            val sent = ArrayList(parts.indices.map { index ->
                val intent = Intent(context, SmsSentReceiver::class.java).apply {
                    action = "com.fcapps.bff.SMS_SENT.$id.$index"
                    putExtra("id", id); putExtra("to", to); putExtra("part", index); putExtra("total", parts.size)
                }
                PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE)
            })
            if (parts.size == 1) manager.sendTextMessage(to, null, text, sent[0], null)
            else manager.sendMultipartTextMessage(to, null, parts, sent, null)
            return true
        } catch (e: RuntimeException) {
            Log.w("BFF", "SMS reply failed", e)
            context.addHistoryEntry(to, to, context.getString(R.string.history_sms_failed))
            return false
        }
    }
}

class SmsSentReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra("id") ?: return
        val to = intent.getStringExtra("to") ?: return
        val part = intent.getIntExtra("part", -1)
        val total = intent.getIntExtra("total", 0)
        if (part !in 0 until total) return
        val results = context.getSharedPreferences("sms_results", Context.MODE_PRIVATE)
        val seen = results.getStringSet("$id.parts", emptySet())!!.toMutableSet()
        if (!seen.add(part.toString())) return
        val failed = results.getBoolean("$id.failed", false) || resultCode != Activity.RESULT_OK
        if (seen.size == total) {
            context.addHistoryEntry(to, to, context.getString(if (failed) R.string.history_sms_failed else R.string.history_sms_sent))
            results.edit().remove("$id.parts").remove("$id.failed").apply()
        } else {
            results.edit().putStringSet("$id.parts", seen).putBoolean("$id.failed", failed).apply()
        }
    }
}
