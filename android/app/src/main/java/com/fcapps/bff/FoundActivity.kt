package com.fcapps.bff

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class FoundActivity : AppCompatActivity() {

    private var selectedAmount: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_found)

        val btn1 = findViewById<Button>(R.id.btn1Dollar)
        val btn3 = findViewById<Button>(R.id.btn3Dollar)
        val btn5 = findViewById<Button>(R.id.btn5Dollar)

        // Donation pill selection (UI only)
        fun updateDonationPills(selected: Int) {
            selectedAmount = selected
            btn1.setBackgroundResource(if (selected == 1) R.drawable.pill_selected else R.drawable.pill_unselected)
            btn1.setTextColor(if (selected == 1) 0xFF000000.toInt() else 0xFFFFFFFF.toInt())
            btn3.setBackgroundResource(if (selected == 3) R.drawable.pill_selected else R.drawable.pill_unselected)
            btn3.setTextColor(if (selected == 3) 0xFF000000.toInt() else 0xFFFFFFFF.toInt())
            btn5.setBackgroundResource(if (selected == 5) R.drawable.pill_selected else R.drawable.pill_unselected)
            btn5.setTextColor(if (selected == 5) 0xFF000000.toInt() else 0xFFFFFFFF.toInt())
        }

        btn1.setOnClickListener {
            updateDonationPills(1)
            Toast.makeText(this, "Stripe coming soon!", Toast.LENGTH_SHORT).show()
        }
        btn3.setOnClickListener {
            updateDonationPills(3)
            Toast.makeText(this, "Stripe coming soon!", Toast.LENGTH_SHORT).show()
        }
        btn5.setOnClickListener {
            updateDonationPills(5)
            Toast.makeText(this, "Stripe coming soon!", Toast.LENGTH_SHORT).show()
        }

        // Share BFF
        findViewById<Button>(R.id.btnShareBff).setOnClickListener {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "BestFoneFinder App")
                putExtra(Intent.EXTRA_TEXT, "Check out BestFoneFinder - find your lost phone via SMS!\nPackage: com.fcapps.bff")
            }
            startActivity(Intent.createChooser(shareIntent, "Share BFF via"))
        }

        // Back
        findViewById<android.view.View>(R.id.btnBack).setOnClickListener { finish() }
    }
}
