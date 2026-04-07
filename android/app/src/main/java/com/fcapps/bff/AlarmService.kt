package com.fcapps.bff

import android.app.*
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Build
import android.os.IBinder
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import com.fcapps.bff.Prefs.sirenDuration

class AlarmService : Service() {

    companion object {
        const val ACTION_STOP = "com.fcapps.bff.ACTION_STOP_ALARM"
        const val EXTRA_SENDER_NAME = "sender_name"
        const val EXTRA_SENDER_NUMBER = "sender_number"
        private const val CHANNEL_ID = "bff_alarm_channel"
        private const val NOTIF_ID = 1001
    }

    private val siren = SirenPlayer()
    private var originalVolume: Int = 0
    private var audioManager: AudioManager? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private val handler = Handler(Looper.getMainLooper())
    private var stopRunnable: Runnable? = null
    private var senderName: String = ""
    private var senderNumber: String = ""

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

        senderName = intent?.getStringExtra(EXTRA_SENDER_NAME) ?: ""
        senderNumber = intent?.getStringExtra(EXTRA_SENDER_NUMBER) ?: ""

        // Must call startForeground immediately (within 5s on Android 8+)
        startForeground(NOTIF_ID, buildNotification())
        acquireWakeLock()
        startAlarm()

        val duration = sirenDuration * 1000L
        stopRunnable = Runnable { stopAlarm(); stopSelf() }
        handler.postDelayed(stopRunnable!!, duration)

        return START_STICKY
    }

    private fun acquireWakeLock() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        @Suppress("DEPRECATION")
        wakeLock = pm.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
            "bff:alarm"
        )
        wakeLock?.acquire(sirenDuration * 1000L + 5000L)
    }

    private fun startAlarm() {
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager

        // Force max alarm volume — overrides silent/vibrate/DND
        originalVolume = audioManager!!.getStreamVolume(AudioManager.STREAM_ALARM)
        val maxVolume = audioManager!!.getStreamMaxVolume(AudioManager.STREAM_ALARM)
        audioManager!!.setStreamVolume(AudioManager.STREAM_ALARM, maxVolume, 0)

        // Start wailing siren
        siren.start()

        // Vibrate: 800ms on / 400ms off, repeat
        val pattern = longArrayOf(0, 800, 400)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vm.defaultVibrator.vibrate(VibrationEffect.createWaveform(pattern, 0))
        } else {
            @Suppress("DEPRECATION")
            val v = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                v.vibrate(VibrationEffect.createWaveform(pattern, 0))
            } else {
                @Suppress("DEPRECATION")
                v.vibrate(pattern, 0)
            }
        }

        // Show AlarmActivity over the lock screen via full-screen notification intent
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIF_ID, buildNotification())
    }

    private fun stopAlarm() {
        stopRunnable?.let { handler.removeCallbacks(it) }
        siren.stop()
        audioManager?.setStreamVolume(AudioManager.STREAM_ALARM, originalVolume, 0)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator.cancel()
        } else {
            @Suppress("DEPRECATION")
            (getSystemService(Context.VIBRATOR_SERVICE) as Vibrator).cancel()
        }
        wakeLock?.let { if (it.isHeld) it.release() }
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
                description = "BestFoneFinder alarm"
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val fullScreenIntent = Intent(this, AlarmActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra(AlarmActivity.EXTRA_SENDER_NAME, senderName)
            putExtra(AlarmActivity.EXTRA_SENDER_NUMBER, senderNumber)
        }
        val fullScreenPendingIntent = PendingIntent.getActivity(
            this, 0, fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, AlarmService::class.java).apply { action = ACTION_STOP }
        val stopPendingIntent = PendingIntent.getService(
            this, 1, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("BestFoneFinder — ALARM")
            .setContentText("Your phone is ringing! Tap FOUND to stop.")
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "FOUND", stopPendingIntent)
            .setOngoing(true)
            .setAutoCancel(false)
            .build()
    }
}
