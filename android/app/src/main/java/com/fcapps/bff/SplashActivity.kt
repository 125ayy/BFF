package com.fcapps.bff

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.widget.TextView
import com.fcapps.bff.Prefs.setupDone
import com.fcapps.bff.Prefs.permissionSetupVersion
import com.fcapps.bff.Prefs.locationEnabled
import com.fcapps.bff.Prefs.smsEnabled

class SplashActivity : BffActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)
        styleAppName(R.id.tvAppName)

        // v3.11: location moved out of setup and is off by default for everyone, once.
        if (permissionSetupVersion < Prefs.SETUP_VERSION) {
            locationEnabled = false
            smsEnabled = false
            if (permissionSetupVersion >= 31) permissionSetupVersion = Prefs.SETUP_VERSION
        }

        Handler(Looper.getMainLooper()).postDelayed({
            if (!setupDone || permissionSetupVersion < 31) startActivity(Intent(this, SetupActivity::class.java))
            else showHome()
            if (!setupDone || permissionSetupVersion < 31) finish()
        }, 1500L)
    }

    private fun showHome() {
        setContentView(R.layout.activity_home)
        styleAppName(R.id.tvAppName)
        findViewById<android.view.View>(R.id.btnSettings).setOnClickListener { startActivity(Intent(this, SettingsActivity::class.java)) }
        findViewById<android.view.View>(R.id.btnHistory).setOnClickListener { startActivity(Intent(this, HistoryActivity::class.java)) }
        findViewById<android.view.View>(R.id.btnShare).setOnClickListener { Share.open(this) }
        findViewById<android.view.View>(R.id.btnAbout).setOnClickListener { startActivity(Intent(this, AboutActivity::class.java)) }
    }

    private fun styleAppName(id: Int) {
        val v = findViewById<TextView>(id) ?: return
        val green = 0xFF00C853.toInt()
        v.text = SpannableString("BestFoneFinder").apply {
            setSpan(ForegroundColorSpan(green), 0, 1, 0)   // B
            setSpan(ForegroundColorSpan(green), 4, 5, 0)   // F
            setSpan(ForegroundColorSpan(green), 8, 9, 0)   // F
        }
    }
}
