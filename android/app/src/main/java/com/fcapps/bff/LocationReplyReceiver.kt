package com.fcapps.bff

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Placeholder for a future exact-alarm fallback. Right now LocationService is the single
 * path for answering "BFF where"; this receiver exists so the manifest entry is stable
 * and a scheduled retry can be added without another manifest change.
 */
class LocationReplyReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val to = intent.getStringExtra(LocationService.EXTRA_TO) ?: return
        LocationService.start(context, to)
    }
}
