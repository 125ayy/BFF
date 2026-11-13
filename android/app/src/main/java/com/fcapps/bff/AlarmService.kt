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
import com.fcapps.bff.Prefs.sirenDuration

class AlarmService : Service() {

    companion object {
        const val EXTRA_DIAGNOSTIC_TEST = "diagnostic_test"
        const val ACTION_STOP = "com.fcapps.bff.ACTION_STOP_ALARM"
        const val EXTRA_SENDER_NAME = "sender_name"
        const val EXTRA_SENDER_NUMBER = "sender_number"
        private const val NOTIF_ID = Notifier.ID_ALARM
        @Volatile var running = false
            private set
    }

    private val siren = SirenPlayer { event, error -> Diagnostics.record(this, event, error) }
    private var durationSeconds = 30
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
        Notifier.ensureChannels(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopAlarm()
            getSystemService(NotificationManager::class.java).cancel(Notifier.ID_ALARM_FALLBACK)
            stopSelf()
            return START_NOT_STICKY
        }

        Diagnostics.record(this, Diagnostics.Event.SERVICE_ENTERED)
        if (running) { Diagnostics.record(this, Diagnostics.Event.SERVICE_ALREADY_RUNNING); return START_NOT_STICKY }   // receiver and activity may both ask; keep the first timer
        durationSeconds = if (intent?.getBooleanExtra(EXTRA_DIAGNOSTIC_TEST, false) == true) 5 else sirenDuration
        senderName = intent?.getStringExtra(EXTRA_SENDER_NAME) ?: ""
        senderNumber = intent?.getStringExtra(EXTRA_SENDER_NUMBER) ?: ""

        // Must call startForeground immediately (within 5s on Android 8+)
        try {
            startForeground(NOTIF_ID, buildNotification())
            running = true
            Diagnostics.record(this, Diagnostics.Event.FOREGROUND_STARTED)
            getSystemService(NotificationManager::class.java).cancel(Notifier.ID_ALARM_FALLBACK)
            acquireWakeLock()
            startAlarm()
        } catch (e: RuntimeException) {
            Diagnostics.record(this, Diagnostics.Event.SERVICE_START_FAILED, e)
            android.util.Log.w("BFF", "Alarm could not start", e)
            stopAlarm()
            runCatching { getSystemService(NotificationManager::class.java).notify(Notifier.ID_ALARM_FALLBACK,
                Notifier.alarm(this, senderName, senderNumber, fallback = true)) }
                .onSuccess { Diagnostics.record(this, Diagnostics.Event.FALLBACK_POSTED) }
                .onFailure { Diagnostics.record(this, Diagnostics.Event.FALLBACK_FAILED, it) }
            stopSelf()
            return START_NOT_STICKY
        }

        val duration = durationSeconds * 1000L
        stopRunnable = Runnable { stopAlarm(); stopSelf() }
        handler.postDelayed(stopRunnable!!, duration)

        return START_NOT_STICKY
    }

    private fun acquireWakeLock() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        @Suppress("DEPRECATION")
        wakeLock = pm.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "bff:alarm"
        )
        wakeLock?.acquire(durationSeconds * 1000L + 5000L)
    }

    private fun startAlarm() {
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager

        // Force max alarm volume — overrides silent/vibrate/DND
        originalVolume = audioManager!!.getStreamVolume(AudioManager.STREAM_ALARM)
        val maxVolume = audioManager!!.getStreamMaxVolume(AudioManager.STREAM_ALARM)
        audioManager!!.setStreamVolume(AudioManager.STREAM_ALARM, maxVolume, 0)

        // Start wailing siren
        Diagnostics.record(this, Diagnostics.Event.AUDIO_REQUESTED)
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

    }

    private fun stopAlarm() {
        if (running) Diagnostics.record(this, Diagnostics.Event.ALARM_STOPPED)
        running = false
        stopRunnable?.let { handler.removeCallbacks(it) }
        stopRunnable = null
        siren.stop()
        audioManager?.setStreamVolume(AudioManager.STREAM_ALARM, originalVolume, 0)
        audioManager = null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator.cancel()
        } else {
            @Suppress("DEPRECATION")
            (getSystemService(Context.VIBRATOR_SERVICE) as Vibrator).cancel()
        }
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    override fun onDestroy() {
        stopAlarm()
        super.onDestroy()
    }


    private fun buildNotification(): Notification = Notifier.alarm(this, senderName, senderNumber)
}
