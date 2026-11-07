package com.fcapps.bff

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import com.fcapps.bff.Prefs.finderMessage

class SettingsActivity : AppCompatActivity() {

    private lateinit var etMessage: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        etMessage = findViewById(R.id.etFinderMessage)
        etMessage.setText(finderMessage)

        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<View>(R.id.rowPrivacy).setOnClickListener { startActivity(Intent(this, PrivacyActivity::class.java)) }
        findViewById<View>(R.id.rowSound).setOnClickListener { startActivity(Intent(this, SoundActivity::class.java)) }
    }

    override fun onPause() {
        super.onPause()
        finderMessage = etMessage.text.toString()
    }
}
