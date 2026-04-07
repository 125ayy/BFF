package com.fcapps.bff

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.fcapps.bff.Prefs.setupDone

class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        // Style BFF letters in app name (both splash and home share the same layout)
        val appNameView = findViewById<TextView>(R.id.tvAppName)
        if (appNameView != null) {
            val fullName = "BestFoneFinder"
            val spannable = SpannableString(fullName)
            val green = 0xFF00C853.toInt()
            spannable.setSpan(ForegroundColorSpan(green), 0, 1, 0)  // B
            spannable.setSpan(ForegroundColorSpan(green), 4, 5, 0)  // F
            spannable.setSpan(ForegroundColorSpan(green), 8, 9, 0)  // F
            appNameView.text = spannable
        }

        // Determine if this is the initial splash (no home buttons shown yet)
        // We check setupDone after 2 seconds
        Handler(Looper.getMainLooper()).postDelayed({
            if (!setupDone) {
                startActivity(Intent(this, SetupActivity::class.java))
                finish()
                return@postDelayed
            }
            // Show home UI
            showHomeUI()
        }, 2000L)
    }

    private fun showHomeUI() {
        setContentView(R.layout.activity_home)

        val appNameView = findViewById<TextView>(R.id.tvAppName)
        val fullName = "BestFoneFinder"
        val spannable = SpannableString(fullName)
        val green = 0xFF00C853.toInt()
        spannable.setSpan(ForegroundColorSpan(green), 0, 1, 0)  // B
        spannable.setSpan(ForegroundColorSpan(green), 4, 5, 0)  // F
        spannable.setSpan(ForegroundColorSpan(green), 8, 9, 0)  // F
        appNameView.text = spannable

        findViewById<android.view.View>(R.id.btnSettings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        findViewById<android.view.View>(R.id.btnHistory).setOnClickListener {
            startActivity(Intent(this, HistoryActivity::class.java))
        }
        findViewById<android.view.View>(R.id.btnFound).setOnClickListener {
            startActivity(Intent(this, FoundActivity::class.java))
        }
        findViewById<android.view.View>(R.id.btnShare).setOnClickListener {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "BestFoneFinder App")
                putExtra(Intent.EXTRA_TEXT, "Check out BestFoneFinder - find your lost phone via SMS!\nPackage: com.fcapps.bff")
            }
            startActivity(Intent.createChooser(shareIntent, "Share BFF via"))
        }
        findViewById<android.view.View>(R.id.btnAbout).setOnClickListener {
            startActivity(Intent(this, AboutActivity::class.java))
        }
    }
}
