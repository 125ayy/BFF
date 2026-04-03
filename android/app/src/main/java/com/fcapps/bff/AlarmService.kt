package com.fcapps.bff

import android.app.*
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.RingtoneManager
import android.media.Ringtone
import android.os.Build
import android.os.IBinder
import android.os.Handler
import android.os.Looper
import androidx.core.app.NotificationCompat
import com.fcapps.bff.Prefs.sirenDuration

class AlarmService : Service() {

    companion object {
        const val ACTION_STOP = "com.fcapps.bff.ACTION_STOP_ALARM"
        private const val CHANNEL_ID = "bff_alarm_channel"
        private const val NOTIF_ID = 1001
    }

    private var ringtone: Ringtone? = null
    private var originalVolume: Int = 0
    private var audioManager: AudioManager? = null
    private val handler = Handler(Looper.getMainLooper())
    private var stopRunnable: Runnable? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopAlarm()
            stopSelf()
            return START_NOT_STICKY
        }

        startForeground(NOTIF_ID, buildNotification())

        startAlarm()

        val duration = sirenDuration * 1000L
        stopRunnable = Runnable {
            stopAlarm()
            stopSelf()
        }
        handler.postDelayed(stopRunnable!!, duration)

        return START_STICKY
    }

    private fun startAlarm() {
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        originalVolume = audioManager!!.getStreamVolume(AudioManager.STREAM_ALARM)

        // Force max alarm volume
        val maxVolume = audioManager!!.getStreamMaxVolume(AudioManager.STREAM_ALARM)
        audioManager!!.setStreamVolume(AudioManager.STREAM_ALARM, maxVolume, 0)

        // Get alarm ringtone
        val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        ringtone = RingtoneManager.getRingtone(applicationContext, alarmUri)
        ringtone?.streamType = AudioManager.STREAM_ALARM
        ringtone?.play()

        // Launch AlarmActivity for visual flash
        val activityIntent = Intent(this, AlarmActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        startActivity(activityIntent)
    }

    private fun stopAlarm() {
        stopRunnable?.let { handler.removeCallbacks(it) }
        ringtone?.stop()
        audioManager?.setStreamVolume(AudioManager.STREAM_ALARM, originalVolume, 0)
    }

    override fun onDestroy() {
        stopAlarm()
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "BFF Alarm",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "BestFoneFinder alarm notifications"
            }
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val stopIntent = Intent(this, AlarmService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 0, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("BestFoneFinder")
            .setContentText("ALARM ACTIVE - Tap Found button to stop")
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "FOUND", stopPendingIntent)
            .setOngoing(true)
            .build()
    }
}
