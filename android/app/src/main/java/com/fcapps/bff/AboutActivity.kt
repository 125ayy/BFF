package com.fcapps.bff

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class AboutActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_about)

        val tvVersion = findViewById<TextView>(R.id.tvVersion)
        tvVersion.text = "Version 1.0.0"

        val tvPrivacy = findViewById<TextView>(R.id.tvPrivacy)
        tvPrivacy.text = "Privacy: BestFoneFinder does not collect or transmit any personal data to external servers. " +
                "All data (phone number, password, owner info) is stored locally on your device only. " +
                "SMS messages are only used to trigger the alarm on the target device."

        val btnDonate = findViewById<Button>(R.id.btnDonate)
        btnDonate.setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://paypal.me/fcapps"))
            startActivity(intent)
        }

        val btnBack = findViewById<Button>(R.id.btnBack)
        btnBack.setOnClickListener { finish() }
    }
}
