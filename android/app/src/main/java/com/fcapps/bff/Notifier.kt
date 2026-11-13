package com.fcapps.bff

import android.app.ActivityOptions
import android.os.Build
import android.media.AudioAttributes
import android.media.RingtoneManager
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
    const val CH_FALLBACK = "bff_alarm_recovery_v31"
    const val ID_ALARM_FALLBACK = 1004
    const val CH_LOCATION = "bff_location_channel"
    const val ID_ALARM = 1001
    const val ID_LOCATION = 1002
    const val ID_LOCATION_FAILURE = 1003

    fun ensureChannels(ctx: Context) {
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(NotificationChannel(CH_ALARM, "BFF Alarm", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Rings when someone texts the activation word"
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            setSound(null, null)   // the siren comes from the service
        })
        nm.createNotificationChannel(NotificationChannel(CH_FALLBACK, ctx.getString(R.string.alarm_recovery_channel), NotificationManager.IMPORTANCE_HIGH).apply {
            setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM), AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
            enableVibration(true)
        })
        nm.createNotificationChannel(NotificationChannel(CH_LOCATION, "BFF Location", NotificationManager.IMPORTANCE_LOW).apply {
            description = "Shown while BFF sends this phone's location by SMS"
        })
    }

    fun alarm(ctx: Context, senderName: String, senderNumber: String, fallback: Boolean = false): Notification {
        ensureChannels(ctx)
        val open = Intent(ctx, AlarmActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra(AlarmActivity.EXTRA_SENDER_NAME, senderName)
            putExtra(AlarmActivity.EXTRA_SENDER_NUMBER, senderNumber)
        }
        val options = ActivityOptions.makeBasic().apply {
            if (Build.VERSION.SDK_INT >= 36) setPendingIntentCreatorBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOW_ALWAYS)
            else if (Build.VERSION.SDK_INT >= 35) setPendingIntentCreatorBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
        }
        val fullScreen = PendingIntent.getActivity(ctx, 0, open,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE, options.toBundle())
        val stop = PendingIntent.getService(ctx, 1,
            Intent(ctx, AlarmService::class.java).apply { action = AlarmService.ACTION_STOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(ctx, if (fallback) CH_FALLBACK else CH_ALARM)
            .setContentTitle("BestFoneFinder — ALARM")
            .setContentText(ctx.getString(if (fallback) R.string.alarm_recovery_message else R.string.alarm_message))
            .setSmallIcon(R.drawable.ic_bff_mark)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(fullScreen)
            .apply {
                if (Build.VERSION.SDK_INT < 34 || ctx.getSystemService(NotificationManager::class.java).canUseFullScreenIntent())
                    setFullScreenIntent(fullScreen, true)
            }
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

    fun locationFailure(ctx: Context): Notification {
        ensureChannels(ctx)
        val open = PendingIntent.getActivity(ctx, 3, Intent(ctx, SetupActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(ctx, CH_LOCATION)
            .setContentTitle(ctx.getString(R.string.app_name))
            .setContentText(ctx.getString(R.string.location_start_failed))
            .setSmallIcon(R.drawable.ic_bff_mark)
            .setContentIntent(open).setAutoCancel(true).build()
    }
}
