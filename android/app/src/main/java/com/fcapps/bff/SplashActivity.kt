package com.fcapps.bff

import android.content.Intent
import android.os.Bundle
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.fcapps.bff.Prefs.setupDone

class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!setupDone) {
            startActivity(Intent(this, SetupActivity::class.java))
            finish()
            return
        }

        setContentView(R.layout.activity_splash)

        // Style BFF letters in app name
        val appNameView = findViewById<TextView>(R.id.tvAppName)
        val fullName = "BestFoneFinder"
        val spannable = SpannableString(fullName)
        val green = 0xFF00C853.toInt()
        spannable.setSpan(ForegroundColorSpan(green), 0, 1, 0)  // B
        spannable.setSpan(ForegroundColorSpan(green), 4, 5, 0)  // F
        spannable.setSpan(ForegroundColorSpan(green), 8, 9, 0)  // F
        appNameView.text = spannable

        findViewById<android.view.View>(R.id.btnFindPhone).setOnClickListener {
            startActivity(Intent(this, FindPhoneActivity::class.java))
        }
        findViewById<android.view.View>(R.id.btnSettings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
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
