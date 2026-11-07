package com.fcapps.bff

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat

/** Notification channels and builders shared by the receiver and the services. */
object Notifier {
    const val CH_ALARM = "bff_alarm_channel"
    const val CH_LOCATION = "bff_location_channel"
    const val ID_ALARM = 1001
    const val ID_LOCATION = 1002

    fun ensureChannels(ctx: Context) {
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(NotificationChannel(CH_ALARM, "BFF Alarm", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Rings when someone texts the activation word"
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            setSound(null, null)   // the siren comes from the service
        })
        nm.createNotificationChannel(NotificationChannel(CH_LOCATION, "BFF Location", NotificationManager.IMPORTANCE_LOW).apply {
            description = "Shown while BFF sends this phone's location by SMS"
        })
    }

    fun alarm(ctx: Context, senderName: String, senderNumber: String): Notification {
        ensureChannels(ctx)
        val open = Intent(ctx, AlarmActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra(AlarmActivity.EXTRA_SENDER_NAME, senderName)
            putExtra(AlarmActivity.EXTRA_SENDER_NUMBER, senderNumber)
        }
        val fullScreen = PendingIntent.getActivity(ctx, 0, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val stop = PendingIntent.getService(ctx, 1,
            Intent(ctx, AlarmService::class.java).apply { action = AlarmService.ACTION_STOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(ctx, CH_ALARM)
            .setContentTitle("BestFoneFinder — ALARM")
            .setContentText("Someone is looking for this phone. Tap FOUND to stop.")
            .setSmallIcon(R.drawable.ic_bff_mark)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(fullScreen)
            .setFullScreenIntent(fullScreen, true)
            .addAction(0, "FOUND", stop)
            .setOngoing(true)
            .setAutoCancel(false)
            .build()
    }

    fun location(ctx: Context): Notification {
        ensureChannels(ctx)
        return NotificationCompat.Builder(ctx, CH_LOCATION)
            .setContentTitle("BestFoneFinder")
            .setContentText("Sending this phone's location by SMS…")
            .setSmallIcon(R.drawable.ic_bff_mark)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }
}
