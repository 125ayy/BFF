package com.fcapps.bff

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.Log

/**
 * Starts the alarm from the SMS receiver, i.e. from the background.
 * Android 12+ lets a background app start a foreground service only in a few cases;
 * BFF relies on the ones the owner grants during setup (battery optimisation off,
 * "display over other apps"). Each path is tried in turn; the notification is the safety net.
 */
object AlarmLauncher {
    private const val TAG = "BFF"

    fun launch(ctx: Context, senderNumber: String, senderName: String) {
        val service = Intent(ctx, AlarmService::class.java).apply {
            putExtra(AlarmService.EXTRA_SENDER_NAME, senderName)
            putExtra(AlarmService.EXTRA_SENDER_NUMBER, senderNumber)
        }
        var serviceStarted = false
        try { ctx.startForegroundService(service); serviceStarted = true }
        catch (e: Exception) { Log.w(TAG, "foreground service refused from background: $e") }

        val activity = Intent(ctx, AlarmActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra(AlarmActivity.EXTRA_SENDER_NAME, senderName)
            putExtra(AlarmActivity.EXTRA_SENDER_NUMBER, senderNumber)
        }
        var activityStarted = false
        if (Settings.canDrawOverlays(ctx)) {
            try { ctx.startActivity(activity); activityStarted = true }
            catch (e: Exception) { Log.w(TAG, "activity start refused: $e") }
        }

        if (!serviceStarted || !activityStarted) {
            // Full-screen intent: opens the alarm screen on a locked phone, heads-up otherwise.
            // AlarmActivity starts the siren itself if the receiver could not.
            val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.notify(Notifier.ID_ALARM, Notifier.alarm(ctx, senderName, senderNumber))
        }
    }
}
