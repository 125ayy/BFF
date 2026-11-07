package com.fcapps.bff

import android.app.Activity
import android.content.Intent
import android.database.Cursor
import android.os.Bundle
import android.provider.ContactsContract
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.fcapps.bff.Prefs.MATRIX_NO
import com.fcapps.bff.Prefs.MATRIX_PIN
import com.fcapps.bff.Prefs.MATRIX_YES
import com.fcapps.bff.Prefs.addToBlacklist
import com.fcapps.bff.Prefs.addTrustedContact
import com.fcapps.bff.Prefs.getBlacklist
import com.fcapps.bff.Prefs.getTrustedContacts
import com.fcapps.bff.Prefs.matrixAllAlarm
import com.fcapps.bff.Prefs.matrixAllLoc
import com.fcapps.bff.Prefs.matrixContactsAlarm
import com.fcapps.bff.Prefs.matrixContactsLoc
import com.fcapps.bff.Prefs.matrixTrustedAlarm
import com.fcapps.bff.Prefs.matrixTrustedLoc
import com.fcapps.bff.Prefs.pin
import com.fcapps.bff.Prefs.removeFromBlacklist
import com.fcapps.bff.Prefs.removeTrustedContact
import com.fcapps.bff.Prefs.setupDone

class PrivacyActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_FROM_SETUP = "from_setup"
        private const val PICK_CONTACT = 3001
    }

    private val order = listOf(MATRIX_YES, MATRIX_PIN, MATRIX_NO)
    private var fromSetup = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_privacy)
        fromSetup = intent.getBooleanExtra(EXTRA_FROM_SETUP, false)
        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }

        etPin().setText(pin)
        bindBadge(R.id.badgeAllAlarm, { matrixAllAlarm }, { matrixAllAlarm = it })
        bindBadge(R.id.badgeAllLoc, { matrixAllLoc }, { matrixAllLoc = it })
        bindBadge(R.id.badgeContactsAlarm, { matrixContactsAlarm }, { matrixContactsAlarm = it })
        bindBadge(R.id.badgeContactsLoc, { matrixContactsLoc }, { matrixContactsLoc = it })
        bindBadge(R.id.badgeTrustedAlarm, { matrixTrustedAlarm }, { matrixTrustedAlarm = it })
        bindBadge(R.id.badgeTrustedLoc, { matrixTrustedLoc }, { matrixTrustedLoc = it })

        findViewById<View>(R.id.btnAddTrusted).setOnClickListener {
            @Suppress("DEPRECATION")
            startActivityForResult(Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI), PICK_CONTACT)
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

    private fun etPin() = findViewById<EditText>(R.id.etPin)

    private fun savePinAndFinish() {
        val a = etPin().text.toString().trim()
        val b = findViewById<EditText>(R.id.etPin2).text.toString().trim()
        if (a != b) { Toast.makeText(this, "PINs do not match", Toast.LENGTH_SHORT).show(); return }
        if (a.isNotEmpty() && !a.matches(Regex("^\\d{4,8}$"))) { Toast.makeText(this, "4 to 8 digits", Toast.LENGTH_SHORT).show(); return }
        pin = a
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

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION") super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PICK_CONTACT && resultCode == Activity.RESULT_OK) {
            data?.data?.let { uri ->
                val cur: Cursor? = contentResolver.query(uri, null, null, null, null)
                cur?.use {
                    if (it.moveToFirst()) {
                        val num = it.getString(it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)) ?: return
                        val name = it.getString(it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)) ?: num
                        addTrustedContact(name, num); renderTrusted()
                    }
                }
            }
        }
    }
}
