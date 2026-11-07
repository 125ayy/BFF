package com.fcapps.bff

import android.Manifest
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationManager
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.IBinder
import android.telephony.SmsManager
import android.util.Log
import androidx.core.content.ContextCompat
import java.util.Locale

/**
 * Answers "BFF where": two SMS replies with a Google Maps link — a quick fix at ~10 s
 * and a better one at ~60 s. Runs as a foregroundServiceType=location service so it may
 * read location while the app is in the background (the app holds ACCESS_BACKGROUND_LOCATION).
 */
class LocationService : Service() {

    companion object {
        const val EXTRA_TO = "to"
        private const val TAG = "BFF"
        private const val FIRST_MS = 10_000L
        private const val SECOND_MS = 60_000L
        private const val MAX_WAIT_MS = 90_000L

        fun start(ctx: Context, to: String) {
            val i = Intent(ctx, LocationService::class.java).putExtra(EXTRA_TO, to)
            try { ctx.startForegroundService(i) }
            catch (e: Exception) { Log.w(TAG, "LocationService refused from background: $e") }
        }

        fun mapsLink(l: Location): String =
            "https://maps.google.com/?q=" + String.format(Locale.US, "%.6f,%.6f", l.latitude, l.longitude)

        fun sendSms(ctx: Context, to: String, text: String) {
            try {
                @Suppress("DEPRECATION")
                val sm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) ctx.getSystemService(SmsManager::class.java)
                         else SmsManager.getDefault()
                val parts = sm.divideMessage(text)
                if (parts.size > 1) sm.sendMultipartTextMessage(to, null, parts, null, null)
                else sm.sendTextMessage(to, null, text, null, null)
            } catch (e: Exception) { Log.w(TAG, "sendSms failed: $e") }
        }

        fun batteryPercent(ctx: Context): Int =
            (ctx.getSystemService(Context.BATTERY_SERVICE) as BatteryManager)
                .getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
    }

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var to: String
    private var firstSent = false
    private var secondSent = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        to = intent?.getStringExtra(EXTRA_TO) ?: run { stopSelf(); return START_NOT_STICKY }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(Notifier.ID_LOCATION, Notifier.location(this), ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } else {
            startForeground(Notifier.ID_LOCATION, Notifier.location(this))
        }

        if (!hasPermission()) { stopEverything(); return START_NOT_STICKY }

        requestUpdates()
        handler.postDelayed({ sendFirst() }, FIRST_MS)
        handler.postDelayed({ sendSecond() }, SECOND_MS)
        handler.postDelayed({ stopEverything() }, MAX_WAIT_MS)
        return START_NOT_STICKY
    }

    private fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private val lm get() = getSystemService(Context.LOCATION_SERVICE) as LocationManager

    private fun requestUpdates() {
        try {
            if (lm.isProviderEnabled(LocationManager.GPS_PROVIDER))
                lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 2000L, 0f, listener, Looper.getMainLooper())
            if (lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER))
                lm.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 2000L, 0f, listener, Looper.getMainLooper())
        } catch (e: SecurityException) { Log.w(TAG, "requestLocationUpdates: $e") }
    }

    private val listener = android.location.LocationListener { }  // updates are pulled via getLastKnownLocation

    private fun bestLocation(): Location? = try {
        listOfNotNull(
            lm.getLastKnownLocation(LocationManager.GPS_PROVIDER),
            lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
        ).maxByOrNull { it.time }
    } catch (e: SecurityException) { null }

    private fun sendFirst() {
        if (firstSent) return; firstSent = true
        val l = bestLocation()
        val text = if (l != null)
            "BFF: here I am. ${mapsLink(l)} (±${l.accuracy.toInt()} m). Battery ${batteryPercent(this)}%."
        else "BFF: getting a location fix, will text it shortly."
        sendSms(this, to, text)
    }

    private fun sendSecond() {
        if (secondSent) return; secondSent = true
        val l = bestLocation()
        if (l != null) sendSms(this, to, "BFF: better fix. ${mapsLink(l)} (±${l.accuracy.toInt()} m). Battery ${batteryPercent(this)}%.")
        stopEverything()
    }

    private fun stopEverything() {
        handler.removeCallbacksAndMessages(null)
        try { lm.removeUpdates(listener) } catch (e: Exception) {}
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        try { lm.removeUpdates(listener) } catch (e: Exception) {}
        super.onDestroy()
    }
}
