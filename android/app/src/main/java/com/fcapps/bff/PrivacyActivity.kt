package com.fcapps.bff

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.ContactsContract
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.fcapps.bff.Prefs.MATRIX_NO
import com.fcapps.bff.Prefs.MATRIX_PIN
import com.fcapps.bff.Prefs.MATRIX_YES
import com.fcapps.bff.Prefs.addToBlacklist
import com.fcapps.bff.Prefs.addTrustedContact
import com.fcapps.bff.Prefs.getBlacklist
import com.fcapps.bff.Prefs.getTrustedContacts
import com.fcapps.bff.Prefs.locationRepliesOn
import com.fcapps.bff.Prefs.pin
import com.fcapps.bff.Prefs.removeFromBlacklist
import com.fcapps.bff.Prefs.removeTrustedContact
import com.fcapps.bff.Prefs.setupDone
import com.fcapps.bff.Prefs.savePrivacy
import com.fcapps.bff.Prefs.privacyGates

class PrivacyActivity : BffActivity() {

    companion object {
        const val EXTRA_FROM_SETUP = "from_setup"
    }

    private val contactPicker = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) result.data?.data?.let { uri ->
            contentResolver.query(uri, arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val number = cursor.getString(0) ?: return@use
                    addTrustedContact(cursor.getString(1) ?: number, number)
                    renderTrusted()
                }
            }
        }
    }

    private val order = listOf(MATRIX_YES, MATRIX_PIN, MATRIX_NO)
    private var fromSetup = false
    private lateinit var draftGates: MutableMap<String, String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_privacy)
        fromSetup = intent.getBooleanExtra(EXTRA_FROM_SETUP, false)
        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }

        draftGates = privacyGates().mapValues { (key, value) -> savedInstanceState?.getString(key) ?: value }.toMutableMap()
        if (savedInstanceState == null) {
            etPin().setText(pin)
            findViewById<EditText>(R.id.etPin2).setText(pin)
        }
        val badges = listOf(R.id.badgeAllAlarm, R.id.badgeAllLoc,
            R.id.badgeContactsAlarm, R.id.badgeContactsLoc, R.id.badgeTrustedAlarm, R.id.badgeTrustedLoc)
        draftGates.keys.toList().zip(badges).forEach { (key, id) ->
            bindBadge(id, { draftGates.getValue(key) }, { draftGates[key] = it })
        }
        // Location column exists only while Settings › Location is on (v3.11 ruling).
        val locVisible = if (locationRepliesOn) View.VISIBLE else View.GONE
        for (id in listOf(R.id.tvLocHeader, R.id.badgeAllLoc, R.id.badgeContactsLoc, R.id.badgeTrustedLoc)) findViewById<View>(id).visibility = locVisible

        findViewById<View>(R.id.btnAddTrusted).setOnClickListener {
            contactPicker.launch(Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI))
        }
        findViewById<Button>(R.id.btnAddBlack).setOnClickListener {
            val et = findViewById<EditText>(R.id.etBlack)
            val v = et.text.toString().trim()
            if (v.isEmpty()) { Toast.makeText(this, "Enter a phone number", Toast.LENGTH_SHORT).show() }
            else { addToBlacklist(v); et.text.clear(); renderBlack() }
        }
        findViewById<Button>(R.id.btnDone).setOnClickListener { savePinAndFinish() }

        renderTrusted(); renderBlack()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        draftGates.forEach { (key, value) -> outState.putString(key, value) }
        super.onSaveInstanceState(outState)
    }

    private fun etPin() = findViewById<EditText>(R.id.etPin)

    private fun savePinAndFinish() {
        val a = etPin().text.toString().trim()
        val b = findViewById<EditText>(R.id.etPin2).text.toString().trim()
        if (a != b) { Toast.makeText(this, "PINs do not match", Toast.LENGTH_SHORT).show(); return }
        if (a.isNotEmpty() && !a.matches(Regex("^\\d{4,8}$"))) { Toast.makeText(this, "4 to 8 digits", Toast.LENGTH_SHORT).show(); return }
        val visibleGates = draftGates.filterKeys { locationRepliesOn || !it.endsWith("_loc") }
        if (a.isEmpty() && visibleGates.values.any { it == MATRIX_PIN }) {
            Toast.makeText(this, R.string.pin_required, Toast.LENGTH_LONG).show(); return
        }
        savePrivacy(a, draftGates)
        if (fromSetup) {
            setupDone = true
            startActivity(Intent(this, SplashActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK })
        }
        finish()
    }

    private fun bindBadge(id: Int, get: () -> String, set: (String) -> Unit) {
        val v = findViewById<TextView>(id)
        paint(v, get())
        v.setOnClickListener { val next = order[(order.indexOf(get()) + 1) % 3]; set(next); paint(v, next) }
    }

    private fun paint(v: TextView, value: String) {
        when (value) {
            MATRIX_YES -> { v.text = "YES"; v.setBackgroundResource(R.drawable.badge_green); v.setTextColor(0xFF000000.toInt()) }
            MATRIX_PIN -> { v.text = "PIN"; v.setBackgroundResource(R.drawable.badge_amber); v.setTextColor(0xFF000000.toInt()) }
            else -> { v.text = "NO"; v.setBackgroundResource(R.drawable.badge_gray); v.setTextColor(0xFFFFFFFF.toInt()) }
        }
    }

    private fun renderTrusted() {
        val host = findViewById<LinearLayout>(R.id.trustedList)
        host.removeAllViews()
        for (c in getTrustedContacts()) {
            val row = LayoutInflater.from(this).inflate(R.layout.row_trusted, host, false)
            row.findViewById<TextView>(R.id.tvName).text = c.name
            row.findViewById<TextView>(R.id.tvNumber).text = c.number
            val initials = c.name.split(" ").filter { it.isNotEmpty() }.take(2).joinToString("") { it[0].uppercase() }.ifEmpty { "#" }
            row.findViewById<TextView>(R.id.tvAvatar).text = initials
            row.findViewById<View>(R.id.btnRemove).setOnClickListener { removeTrustedContact(c.number); renderTrusted() }
            host.addView(row)
        }
    }

    private fun renderBlack() {
        val host = findViewById<LinearLayout>(R.id.blackList)
        host.removeAllViews()
        for (n in getBlacklist()) {
            val row = LayoutInflater.from(this).inflate(R.layout.row_black, host, false)
            row.findViewById<TextView>(R.id.tvNumber).text = n
            row.findViewById<View>(R.id.btnRemove).setOnClickListener { removeFromBlacklist(n); renderBlack() }
            host.addView(row)
        }
    }

}
