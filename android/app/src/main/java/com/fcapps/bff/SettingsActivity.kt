package com.fcapps.bff

import android.content.Intent
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.fcapps.bff.Prefs.addToBlacklist
import com.fcapps.bff.Prefs.getBlacklist
import com.fcapps.bff.Prefs.matrixAllAlarm
import com.fcapps.bff.Prefs.matrixContactsAlarm
import com.fcapps.bff.Prefs.matrixTrustedAlarm
import com.fcapps.bff.Prefs.MATRIX_YES
import com.fcapps.bff.Prefs.MATRIX_PIN
import com.fcapps.bff.Prefs.MATRIX_NO
import com.fcapps.bff.Prefs.password
import com.fcapps.bff.Prefs.pin
import com.fcapps.bff.Prefs.removeFromBlacklist
import com.fcapps.bff.Prefs.resetToDefaults
import com.fcapps.bff.Prefs.sirenDuration

class SettingsActivity : AppCompatActivity() {

    private lateinit var etNewPassword: EditText
    private lateinit var etConfirmNewPassword: EditText
    private lateinit var etPin: EditText
    private lateinit var btn15: Button
    private lateinit var btn30: Button
    private lateinit var btn60: Button
    private lateinit var lvBlacklist: ListView
    private lateinit var etAddBlacklist: EditText
    private lateinit var blacklistAdapter: ArrayAdapter<String>
    private val blacklistItems = mutableListOf<String>()

    // Matrix badge buttons
    private lateinit var badgeAllAlarm: TextView
    private lateinit var badgeContactsAlarm: TextView
    private lateinit var badgeTrustedAlarm: TextView

    private var selectedDuration: Int = 30

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        etNewPassword = findViewById(R.id.etNewPassword)
        etConfirmNewPassword = findViewById(R.id.etConfirmNewPassword)
        etPin = findViewById(R.id.etPin)
        btn15 = findViewById(R.id.radio15)
        btn30 = findViewById(R.id.radio30)
        btn60 = findViewById(R.id.radio60)
        lvBlacklist = findViewById(R.id.lvBlacklist)
        etAddBlacklist = findViewById(R.id.etAddBlacklist)
        badgeAllAlarm = findViewById(R.id.badgeAllAlarm)
        badgeContactsAlarm = findViewById(R.id.badgeContactsAlarm)
        badgeTrustedAlarm = findViewById(R.id.badgeTrustedAlarm)

        // Load PIN
        etPin.setText(pin)

        // Load current siren duration
        selectedDuration = sirenDuration
        updateDurationPills(selectedDuration)

        btn15.setOnClickListener { selectDuration(15) }
        btn30.setOnClickListener { selectDuration(30) }
        btn60.setOnClickListener { selectDuration(60) }

        // Matrix badges
        updateMatrixBadges()
        badgeAllAlarm.setOnClickListener { showMatrixPicker("All People") { v -> matrixAllAlarm = v; updateMatrixBadges() } }
        badgeContactsAlarm.setOnClickListener { showMatrixPicker("My Contacts") { v -> matrixContactsAlarm = v; updateMatrixBadges() } }
        badgeTrustedAlarm.setOnClickListener { showMatrixPicker("Trusted Contacts") { v -> matrixTrustedAlarm = v; updateMatrixBadges() } }

        // Load blacklist
        blacklistItems.clear()
        blacklistItems.addAll(getBlacklist())
        blacklistAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, blacklistItems)
        lvBlacklist.adapter = blacklistAdapter

        lvBlacklist.setOnItemLongClickListener { _, _, pos, _ ->
            AlertDialog.Builder(this)
                .setTitle("Remove from blacklist?")
                .setMessage(blacklistItems[pos])
                .setPositiveButton("Remove") { _, _ ->
                    removeFromBlacklist(blacklistItems[pos])
                    blacklistItems.removeAt(pos)
                    blacklistAdapter.notifyDataSetChanged()
                }
                .setNegativeButton("Cancel", null)
                .show()
            true
        }

        // Trusted contacts
        findViewById<android.view.View>(R.id.rowTrustedContacts).setOnClickListener {
            startActivity(Intent(this, TrustedContactsActivity::class.java))
        }

        // PIN save
        findViewById<Button>(R.id.btnSavePin).setOnClickListener {
            pin = etPin.text.toString().trim()
            Toast.makeText(this, if (pin.isEmpty()) "PIN disabled" else "PIN saved", Toast.LENGTH_SHORT).show()
        }

        findViewById<Button>(R.id.btnSavePassword).setOnClickListener {
            val newPass = etNewPassword.text.toString()
            val confirm = etConfirmNewPassword.text.toString()
            if (newPass.isEmpty()) {
                Toast.makeText(this, "Password cannot be empty", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (newPass != confirm) {
                Toast.makeText(this, "Passwords do not match", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            password = newPass
            Toast.makeText(this, "Password updated", Toast.LENGTH_SHORT).show()
            etNewPassword.text.clear()
            etConfirmNewPassword.text.clear()
        }

        findViewById<Button>(R.id.btnAddBlacklist).setOnClickListener {
            val num = etAddBlacklist.text.toString().trim()
            if (num.isEmpty()) {
                Toast.makeText(this, "Enter a phone number", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            addToBlacklist(num)
            if (!blacklistItems.contains(num)) {
                blacklistItems.add(num)
                blacklistAdapter.notifyDataSetChanged()
            }
            etAddBlacklist.text.clear()
            Toast.makeText(this, "Added to blacklist", Toast.LENGTH_SHORT).show()
        }

        findViewById<Button>(R.id.btnResetDefaults).setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Reset to Defaults")
                .setMessage("This will reset siren duration and clear the blacklist. Your phone number and password will be kept.")
                .setPositiveButton("Reset") { _, _ ->
                    resetToDefaults()
                    selectDuration(30)
                    blacklistItems.clear()
                    blacklistAdapter.notifyDataSetChanged()
                    Toast.makeText(this, "Reset to defaults", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        findViewById<Button>(R.id.btnBack).setOnClickListener { finish() }
    }

    override fun onResume() {
        super.onResume()
        updateMatrixBadges()
    }

    private fun showMatrixPicker(rowLabel: String, onPick: (String) -> Unit) {
        val options = arrayOf("Yes — Always allow", "PIN Required", "No — Always block")
        AlertDialog.Builder(this)
            .setTitle("$rowLabel — Alarm")
            .setItems(options) { _, which ->
                val value = when (which) {
                    0 -> MATRIX_YES
                    1 -> MATRIX_PIN
                    else -> MATRIX_NO
                }
                onPick(value)
            }
            .show()
    }

    private fun updateMatrixBadges() {
        setBadge(badgeAllAlarm, matrixAllAlarm)
        setBadge(badgeContactsAlarm, matrixContactsAlarm)
        setBadge(badgeTrustedAlarm, matrixTrustedAlarm)
    }

    private fun setBadge(view: TextView, value: String) {
        when (value) {
            MATRIX_YES -> {
                view.text = "YES"
                view.setBackgroundResource(R.drawable.badge_green)
                view.setTextColor(0xFF000000.toInt())
            }
            MATRIX_PIN -> {
                view.text = "PIN"
                view.setBackgroundResource(R.drawable.badge_amber)
                view.setTextColor(0xFF000000.toInt())
            }
            else -> {
                view.text = "NO"
                view.setBackgroundResource(R.drawable.badge_gray)
                view.setTextColor(0xFFFFFFFF.toInt())
            }
        }
    }

    private fun selectDuration(seconds: Int) {
        selectedDuration = seconds
        sirenDuration = seconds
        updateDurationPills(seconds)
    }

    private fun updateDurationPills(selected: Int) {
        val greenBg = R.drawable.pill_selected
        val darkBg = R.drawable.pill_unselected
        val black = 0xFF000000.toInt()
        val white = 0xFFFFFFFF.toInt()

        btn15.setBackgroundResource(if (selected == 15) greenBg else darkBg)
        btn15.setTextColor(if (selected == 15) black else white)

        btn30.setBackgroundResource(if (selected == 30) greenBg else darkBg)
        btn30.setTextColor(if (selected == 30) black else white)

        btn60.setBackgroundResource(if (selected == 60) greenBg else darkBg)
        btn60.setTextColor(if (selected == 60) black else white)
    }
}
