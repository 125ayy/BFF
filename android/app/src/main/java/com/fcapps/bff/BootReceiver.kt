package com.fcapps.bff

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            // SMS receiver is registered in manifest and will auto-start.
            // Nothing additional needed here for API 26+ as static receivers work.
        }
    }
}
