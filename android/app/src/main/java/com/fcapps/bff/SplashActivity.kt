package com.fcapps.bff

import android.content.Intent
import android.os.Bundle
import android.widget.Button
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

        findViewById<Button>(R.id.btnFindPhone).setOnClickListener {
            startActivity(Intent(this, FindPhoneActivity::class.java))
        }
        findViewById<Button>(R.id.btnSettings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        findViewById<Button>(R.id.btnShare).setOnClickListener {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "BestFoneFinder App")
                putExtra(Intent.EXTRA_TEXT, "Check out BestFoneFinder - find your lost phone via SMS!\nPackage: com.fcapps.bff")
            }
            startActivity(Intent.createChooser(shareIntent, "Share BFF via"))
        }
        findViewById<Button>(R.id.btnAbout).setOnClickListener {
            startActivity(Intent(this, AboutActivity::class.java))
        }
    }
}
