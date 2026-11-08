package com.fcapps.bff

import android.Manifest
import android.app.ActivityManager
import android.app.KeyguardManager
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Opt-in, bounded local trace. Only enum values enter the trace: never SMS or exception text. */
object Diagnostics {
    const val MAX_EVENTS = 150
    const val SESSION_MS = 30 * 60 * 1000L
    enum class Event {
        RECORDING_STARTED, RECORDING_STOPPED, SMS_RECEIVED, SMS_PARSE_FAILED, SMS_EMPTY,
        SMS_BLACKLISTED, ACTIVATION_NOT_MATCHED, ACTIVATION_MATCHED,
        SENDER_TRUSTED, SENDER_CONTACT, SENDER_OTHER,
        ALARM_ALLOWED, ALARM_SUPPRESSED_SILENT, ALARM_BLOCKED_RULE, ALARM_BLOCKED_PIN,
        SERVICE_REQUESTED, SERVICE_REQUEST_ACCEPTED, SERVICE_REQUEST_FAILED,
        SERVICE_ENTERED, SERVICE_ALREADY_RUNNING, FOREGROUND_STARTED, SERVICE_START_FAILED,
        OVERLAY_NOT_ALLOWED, ALARM_SCREEN_REQUESTED, ALARM_SCREEN_OPENED, ALARM_SCREEN_FAILED,
        FALLBACK_POSTED, FALLBACK_FAILED, AUDIO_REQUESTED, AUDIO_PLAYING, AUDIO_WRITE_FAILED,
        AUDIO_FAILED, ALARM_STOPPED, LOCAL_TEST_REQUESTED, LOCAL_TEST_HEARD, LOCAL_TEST_NOT_HEARD
    }
    enum class Failure { NONE, SECURITY, ILLEGAL_STATE, ILLEGAL_ARGUMENT, OTHER }
    private fun prefs(c: Context) = c.getSharedPreferences("bff_diagnostics", Context.MODE_PRIVATE)
    fun active(c: Context, now: Long = System.currentTimeMillis()) = prefs(c).getLong("until", 0) > now
    @Synchronized fun start(c: Context) {
        prefs(c).edit().putLong("until", System.currentTimeMillis() + SESSION_MS).putString("events", "[]").apply()
        record(c, Event.RECORDING_STARTED)
    }
    @Synchronized fun stop(c: Context) {
        record(c, Event.RECORDING_STOPPED)
        prefs(c).edit().remove("until").apply()
    }
    @Synchronized fun clear(c: Context) { prefs(c).edit().clear().apply() }
    @Synchronized fun record(c: Context, event: Event, error: Throwable? = null) {
        // Diagnostics must not prevent the actual alarm from running.
        runCatching {
            if (!active(c)) return
            val events = JSONArray(prefs(c).getString("events", "[]"))
            val failure = when (error) {
                null -> Failure.NONE
                is SecurityException -> Failure.SECURITY
                is IllegalArgumentException -> Failure.ILLEGAL_ARGUMENT
                is IllegalStateException -> Failure.ILLEGAL_STATE
                else -> Failure.OTHER
            }
            events.put(JSONObject().put("time", System.currentTimeMillis()).put("event", event.name).put("failure", failure.name))
            val tail = JSONArray()
            for (i in maxOf(0, events.length() - MAX_EVENTS) until events.length()) tail.put(events.get(i))
            prefs(c).edit().putString("events", tail.toString()).apply()
        }
    }
    fun report(c: Context): String = buildString {
        appendLine("BFF diagnostic report — ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
        appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
        appendLine("Android: ${Build.VERSION.RELEASE}; API ${Build.VERSION.SDK_INT}; build ${Build.DISPLAY}")
        appendLine("Recording: ${if (active(c)) "ON (30-minute session)" else "OFF"}")
        appendLine("Current checks (not historical values):")
        fun check(label: String, value: () -> Any?) {
            appendLine("$label: ${runCatching(value).getOrElse { "unavailable" }}")
        }
        fun granted(p: String) = ContextCompat.checkSelfPermission(c, p) == PackageManager.PERMISSION_GRANTED
        check("Receive SMS") { granted(Manifest.permission.RECEIVE_SMS) }
        check("Read contacts") { granted(Manifest.permission.READ_CONTACTS) }
        check("Notifications") { NotificationManagerCompat.from(c).areNotificationsEnabled() }
        check("Display over apps") { Settings.canDrawOverlays(c) }
        val nm = c.getSystemService(NotificationManager::class.java)
        check("Full-screen alerts") { if (Build.VERSION.SDK_INT >= 34) nm.canUseFullScreenIntent() else "not separately required" }
        check("Battery optimization exemption") { c.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(c.packageName) }
        check("Battery saver") { c.getSystemService(PowerManager::class.java).isPowerSaveMode }
        check("Background restricted") { if (Build.VERSION.SDK_INT >= 28) c.getSystemService(ActivityManager::class.java).isBackgroundRestricted else "not available" }
        check("Phone locked") { c.getSystemService(KeyguardManager::class.java).isKeyguardLocked }
        check("Do Not Disturb filter") { nm.currentInterruptionFilter }
        val audio = c.getSystemService(AudioManager::class.java)
        check("Alarm volume / maximum") { "${audio.getStreamVolume(AudioManager.STREAM_ALARM)} / ${audio.getStreamMaxVolume(AudioManager.STREAM_ALARM)}" }
        check("Alarm service running") { AlarmService.running }
        for (id in listOf(Notifier.CH_ALARM, Notifier.CH_FALLBACK)) {
            check("Notification channel $id importance") { nm.getNotificationChannel(id)?.importance ?: "not created" }
        }
        appendLine("\nEvents (UTC, oldest first; maximum $MAX_EVENTS):")
        val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
        val events = runCatching { JSONArray(prefs(c).getString("events", "[]")) }.getOrElse { JSONArray() }
        if (events.length() == 0) appendLine("No captured events. Start recording before sending the test SMS.")
        for (i in 0 until events.length()) {
            val e = events.getJSONObject(i)
            appendLine("${time.format(Date(e.getLong("time")))} ${e.getString("event")} ${e.getString("failure")}")
        }
        appendLine("\nAUDIO_PLAYING means Android accepted playback, not proof it was audible.")
        appendLine("No SMS_RECEIVED during an active test means BFF did not record delivery; it does not identify why.")
        appendLine("No message text, sender identity, activation word, PIN or location is included.")
    }
}
