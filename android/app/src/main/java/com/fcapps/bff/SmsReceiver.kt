package com.fcapps.bff

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.app.NotificationManager
import android.os.Build
import android.telephony.SubscriptionManager
import android.provider.ContactsContract
import android.provider.Telephony
import androidx.core.content.ContextCompat
import com.fcapps.bff.Prefs.addHistoryEntry
import com.fcapps.bff.Prefs.isBlacklisted
import com.fcapps.bff.Prefs.isTrusted
import com.fcapps.bff.Prefs.locationRepliesOn
import com.fcapps.bff.Prefs.matrixAllAlarm
import com.fcapps.bff.Prefs.matrixAllLoc
import com.fcapps.bff.Prefs.matrixContactsAlarm
import com.fcapps.bff.Prefs.matrixContactsLoc
import com.fcapps.bff.Prefs.matrixTrustedAlarm
import com.fcapps.bff.Prefs.matrixTrustedLoc
import com.fcapps.bff.Prefs.password
import com.fcapps.bff.Prefs.pin

class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        Diagnostics.record(context, Diagnostics.Event.SMS_RECEIVED)
        val messages = try { Telephony.Sms.Intents.getMessagesFromIntent(intent) } catch (e: RuntimeException) {
            Diagnostics.record(context, Diagnostics.Event.SMS_PARSE_FAILED, e); return
        }
        if (messages.isNullOrEmpty()) { Diagnostics.record(context, Diagnostics.Event.SMS_EMPTY); return }

        // Multipart texts arrive as several parts from the same sender: join them.
        val bySender = messages.filter { it.originatingAddress != null }
            .groupBy { it.originatingAddress!! }
            .mapValues { (_, parts) -> parts.joinToString("") { it.messageBody ?: "" } }

        for ((sender, body) in bySender) {
            if (context.isBlacklisted(sender)) { Diagnostics.record(context, Diagnostics.Event.SMS_BLACKLISTED); continue }          // silently ignored
            val word = context.password
            if (!SmsRules.matchesActivation(body, word, context.pin)) { Diagnostics.record(context, Diagnostics.Event.ACTIVATION_NOT_MATCHED); continue }
            Diagnostics.record(context, Diagnostics.Event.ACTIVATION_MATCHED)

            val name = lookupContactName(context, sender)
            val group = when {
                context.isTrusted(sender) -> Group.TRUSTED
                name != null -> Group.CONTACTS
                else -> Group.ALL
            }
            Diagnostics.record(context, when (group) {
                Group.TRUSTED -> Diagnostics.Event.SENDER_TRUSTED
                Group.CONTACTS -> Diagnostics.Event.SENDER_CONTACT
                Group.ALL -> Diagnostics.Event.SENDER_OTHER
            })
            // The switches never lie: both stored choices and the live grants must all hold.
            val locationOn = context.locationRepliesOn
            val decision = SmsRules.decide(
                body = body, word = word, pin = context.pin,
                alarmGate = alarmGate(context, group), locGate = locGate(context, group),
                locationEnabled = locationOn
            )
            Diagnostics.record(context, when (decision.alarmBlock) {
                SmsRules.AlarmBlock.NONE -> Diagnostics.Event.ALARM_ALLOWED
                SmsRules.AlarmBlock.SILENT -> Diagnostics.Event.ALARM_SUPPRESSED_SILENT
                SmsRules.AlarmBlock.PIN -> Diagnostics.Event.ALARM_BLOCKED_PIN
                SmsRules.AlarmBlock.RULE -> Diagnostics.Event.ALARM_BLOCKED_RULE
                SmsRules.AlarmBlock.NO_MATCH -> Diagnostics.Event.ACTIVATION_NOT_MATCHED
            })
            val subscription = intent.getIntExtra("subscription", SubscriptionManager.INVALID_SUBSCRIPTION_ID)
            context.addHistoryEntry(sender, name ?: "Unknown", when {
                decision.location -> context.getString(R.string.history_location_requested)
                // "where" asked while Location is off in Settings: alarm only, and a hint for the owner.
                decision.alarm && decision.whereRequested && !locationOn -> context.getString(R.string.history_location_off)
                decision.alarm -> "Alarm requested"
                else -> decision.status
            })
            if (decision.alarm) AlarmLauncher.launch(context, sender, name ?: sender)
            if (decision.location && !LocationService.start(context, sender, subscription)) {
                context.addHistoryEntry(sender, name ?: sender, context.getString(R.string.history_location_failed))
                SmsReplies.send(context, sender, context.getString(R.string.location_start_failed), subscription)
                runCatching {
                    context.getSystemService(NotificationManager::class.java).notify(Notifier.ID_LOCATION_FAILURE, Notifier.locationFailure(context))
                }
            }
            break
        }
    }

    private enum class Group { ALL, CONTACTS, TRUSTED }

    private fun alarmGate(c: Context, g: Group) = when (g) {
        Group.TRUSTED -> c.matrixTrustedAlarm; Group.CONTACTS -> c.matrixContactsAlarm; Group.ALL -> c.matrixAllAlarm
    }

    private fun locGate(c: Context, g: Group) = when (g) {
        Group.TRUSTED -> c.matrixTrustedLoc; Group.CONTACTS -> c.matrixContactsLoc; Group.ALL -> c.matrixAllLoc
    }

    private fun granted(c: Context, p: String) = ContextCompat.checkSelfPermission(c, p) == PackageManager.PERMISSION_GRANTED

    /** Display name if the number is in the phone's contacts, else null. */
    private fun lookupContactName(context: Context, number: String): String? {
        if (!granted(context, Manifest.permission.READ_CONTACTS)) return null
        return try {
            val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number))
            context.contentResolver.query(uri, arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME), null, null, null)?.use {
                if (it.moveToFirst()) it.getString(0) else null
            }
        } catch (e: Exception) { null }
    }
}
