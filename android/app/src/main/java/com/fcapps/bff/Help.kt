package com.fcapps.bff

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

/** Browser-hosted guide; editing the guide never requires an APK update. */
object Help {
    const val URL = "https://github.com/125ayy/BFF/blob/codex/v3.15-diagnostics/docs/HELP.md"
    fun open(context: Context) {
        try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(URL))) }
        catch (_: ActivityNotFoundException) {
            Toast.makeText(context, R.string.help_no_browser, Toast.LENGTH_LONG).show()
        }
    }
}
