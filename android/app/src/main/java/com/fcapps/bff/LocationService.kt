package com.fcapps.bff

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import com.fcapps.bff.Prefs.addHistoryEntry
import java.util.Locale

/** One acquisition service, independent reply deadlines/recipients for each active sender. */
class LocationService : Service() {
    companion object {
        const val EXTRA_TO = "to"
        const val EXTRA_SUBSCRIPTION = "subscription"
        fun start(ctx: Context, to: String, subscription: Int): Boolean = try {
            if (!LocationPermissions.canReply(ctx)) false
            else {
                ctx.startForegroundService(Intent(ctx, LocationService::class.java)
                    .putExtra(EXTRA_TO, to).putExtra(EXTRA_SUBSCRIPTION, subscription))
                true
            }
        } catch (e: RuntimeException) {
            Log.w("BFF", "Location foreground start refused", e)
            false
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private val requests = LocationRequests()
    private val subscriptions = mutableMapOf<String, Int>()
    private val fixes = mutableMapOf<String, LocationFix>()
    private val manager get() = getSystemService(LocationManager::class.java)
    private var wakeLock: PowerManager.WakeLock? = null
    private var listening = false
    private var destroyed = false
    private var latestStartId = 0
    private val listener = LocationListener { location -> remember(location) }
    private val tick = object : Runnable {
        override fun run() {
            if (destroyed) return
            val now = SystemClock.elapsedRealtime()
            for (reply in requests.due(now)) {
                val fix = LocationFix.best(fixes.values, now)
                val text = if (fix == null) {
                    getString(if (reply.final) R.string.location_failed else R.string.location_waiting)
                } else {
                    val link = String.format(Locale.US, "https://maps.google.com/?q=%.6f,%.6f", fix.latitude, fix.longitude)
                    getString(if (reply.final) R.string.location_final else R.string.location_first,
                        link, fix.accuracy.toInt(), (now - fix.elapsedMs) / 1000)
                }
                SmsReplies.send(this@LocationService, reply.recipient, text, subscriptions[reply.recipient] ?: -1)
                if (reply.final) subscriptions.remove(reply.recipient)
            }
            if (requests.isEmpty) stopEverything() else handler.postDelayed(this, 1000)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        latestStartId = startId
        val recipient = intent?.getStringExtra(EXTRA_TO)
        if (recipient.isNullOrBlank()) { stopEverything(); return START_NOT_STICKY }
        try {
            // The grant is checked before promotion and again when requesting updates.
            if (!LocationPermissions.canReply(this)) {
                addHistoryEntry(recipient, recipient, getString(R.string.history_location_failed))
                stopEverything(); return START_NOT_STICKY
            }
            if (Build.VERSION.SDK_INT >= 29) startForeground(Notifier.ID_LOCATION, Notifier.location(this), ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
            else startForeground(Notifier.ID_LOCATION, Notifier.location(this))
            val subscription = intent.getIntExtra(EXTRA_SUBSCRIPTION, -1)
            if (requests.add(recipient, SystemClock.elapsedRealtime())) {
                subscriptions[recipient] = subscription
                // Bound acquisition to the request lifetime, including a sleeping phone.
                if (wakeLock == null) wakeLock = getSystemService(PowerManager::class.java)
                    .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "bff:location").apply { setReferenceCounted(false) }
                wakeLock?.acquire(65_000L)
            }
            if (!listening) requestUpdates()
            handler.removeCallbacks(tick)
            handler.post(tick)
        } catch (e: RuntimeException) {
            Log.w("BFF", "Location permission or foreground state changed", e)
            addHistoryEntry(recipient, recipient, getString(R.string.history_location_failed))
            stopEverything()
        }
        return START_NOT_STICKY
    }

    private fun remember(location: Location) {
        if (location.hasAccuracy()) fixes[location.provider ?: "unknown"] = LocationFix(
            location.latitude, location.longitude, location.accuracy, location.elapsedRealtimeNanos / 1_000_000)
    }

    private fun requestUpdates() {
        for (provider in listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)) {
            try {
                if (manager.isProviderEnabled(provider)) {
                    manager.getLastKnownLocation(provider)?.let(::remember)
                    manager.requestLocationUpdates(provider, 2000L, 0f, listener, Looper.getMainLooper())
                    listening = true
                }
            } catch (e: IllegalArgumentException) {
                Log.w("BFF", "Location provider unavailable: $provider", e)
            } catch (e: SecurityException) {
                // Approximate-only permission can reject GPS; still try the network provider.
                Log.w("BFF", "Location provider permission unavailable: $provider", e)
            }
        }
    }

    private fun cleanup() {
        handler.removeCallbacksAndMessages(null)
        requests.clear()
        subscriptions.clear()
        fixes.clear()
        runCatching { manager.removeUpdates(listener) }
        listening = false
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }

    private fun stopEverything() {
        cleanup()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelfResult(latestStartId)
    }

    override fun onDestroy() {
        destroyed = true
        cleanup()
        super.onDestroy()
    }
}
