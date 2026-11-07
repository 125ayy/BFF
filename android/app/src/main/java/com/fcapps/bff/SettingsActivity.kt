package com.fcapps.bff

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.TextView
import com.fcapps.bff.Prefs.finderMessage
import com.fcapps.bff.Prefs.locationRepliesOn

class SettingsActivity : BffActivity() {

    private lateinit var etMessage: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        etMessage = findViewById(R.id.etFinderMessage)
        etMessage.setText(finderMessage)

        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<View>(R.id.rowPrivacy).setOnClickListener { startActivity(Intent(this, PrivacyActivity::class.java)) }
        findViewById<View>(R.id.rowLocation).setOnClickListener { startActivity(Intent(this, LocationActivity::class.java)) }
        findViewById<View>(R.id.rowPermissions).setOnClickListener { startActivity(Intent(this, SetupActivity::class.java)) }
        findViewById<View>(R.id.rowSound).setOnClickListener { startActivity(Intent(this, SoundActivity::class.java)) }
    }

    override fun onResume() {
        super.onResume()
        val on = locationRepliesOn
        findViewById<TextView>(R.id.tvLocationRowState).setText(if (on) R.string.location_state_on else R.string.location_state_off)
    }

    override fun onPause() {
        super.onPause()
        finderMessage = etMessage.text.toString()
    }
}
