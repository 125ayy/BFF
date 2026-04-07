package com.fcapps.bff

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.ContactsContract
import android.provider.Telephony
import com.fcapps.bff.Prefs.addHistoryEntry
import com.fcapps.bff.Prefs.getTrustedContacts
import com.fcapps.bff.Prefs.matrixAllAlarm
import com.fcapps.bff.Prefs.matrixContactsAlarm
import com.fcapps.bff.Prefs.matrixTrustedAlarm
import com.fcapps.bff.Prefs.MATRIX_YES
import com.fcapps.bff.Prefs.MATRIX_PIN
import com.fcapps.bff.Prefs.password
import com.fcapps.bff.Prefs.pin
import com.fcapps.bff.Prefs.setupDone

class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        if (!context.setupDone) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        messageLoop@ for (message in messages) {
            val sender = message.originatingAddress ?: continue
            val body = message.messageBody ?: continue

            // Must contain activation word (case-insensitive)
            val storedPassword = context.password
            if (!body.contains(storedPassword, ignoreCase = true)) continue

            // Resolve sender name and group
            val senderName = lookupContactName(context, sender)
            val group = determineGroup(context, sender)
            val matrixValue = getMatrixValue(context, group)

            when (matrixValue) {
                MATRIX_YES -> {
                    triggerAlarm(context, sender, senderName)
                    context.addHistoryEntry(sender, senderName, "Success")
                    break@messageLoop
                }
                MATRIX_PIN -> {
                    val currentPin = context.pin
                    val activationOk = if (currentPin.isEmpty()) {
                        true
                    } else {
                        body.contains("$storedPassword$currentPin", ignoreCase = true) ||
                        body.contains("$storedPassword $currentPin", ignoreCase = true)
                    }
                    if (activationOk) {
                        triggerAlarm(context, sender, senderName)
                        context.addHistoryEntry(sender, senderName, "Success")
                    } else {
                        context.addHistoryEntry(sender, senderName, "Blocked")
                    }
                    break@messageLoop
                }
                else -> {
                    // MATRIX_NO
                    context.addHistoryEntry(sender, senderName, "Blocked")
                    break@messageLoop
                }
            }
        }
    }

    private fun triggerAlarm(context: Context, senderNumber: String, senderName: String) {
        val alarmIntent = Intent(context, AlarmService::class.java).apply {
            putExtra(AlarmService.EXTRA_SENDER_NAME, senderName)
            putExtra(AlarmService.EXTRA_SENDER_NUMBER, senderNumber)
        }
        context.startForegroundService(alarmIntent)
    }

    private fun determineGroup(context: Context, number: String): String {
        val trusted = context.getTrustedContacts()
        val normalizedNumber = normalizeNumber(number)
        for (tc in trusted) {
            if (normalizeNumber(tc.number) == normalizedNumber) {
                return "TRUSTED"
            }
        }
        if (lookupContactName(context, number) != "Unknown") {
            return "CONTACTS"
        }
        return "ALL"
    }

    private fun getMatrixValue(context: Context, group: String): String {
        return when (group) {
            "TRUSTED" -> context.matrixTrustedAlarm
            "CONTACTS" -> context.matrixContactsAlarm
            else -> context.matrixAllAlarm
        }
    }

    private fun lookupContactName(context: Context, number: String): String {
        return try {
            val uri = android.net.Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                android.net.Uri.encode(number)
            )
            val cursor = context.contentResolver.query(
                uri,
                arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME),
                null, null, null
            )
            cursor?.use {
                if (it.moveToFirst()) {
                    it.getString(0) ?: "Unknown"
                } else "Unknown"
            } ?: "Unknown"
        } catch (e: Exception) {
            "Unknown"
        }
    }

    private fun normalizeNumber(number: String): String {
        return number.replace(Regex("[^0-9+]"), "")
    }
}
