package com.fcapps.bff

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** The SMS receiver is a static manifest receiver, so it keeps working after a reboot with no action here. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) { /* no-op */ }
}
