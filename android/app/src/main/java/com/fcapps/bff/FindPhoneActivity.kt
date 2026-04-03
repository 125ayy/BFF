package com.fcapps.bff

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import android.telephony.SmsManager
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.fcapps.bff.Prefs.password

class FindPhoneActivity : AppCompatActivity() {

    companion object {
        private const val REQUEST_CONTACTS = 1001
        private const val REQUEST_SMS = 1002
        private const val PICK_CONTACT = 2001
    }

    private lateinit var tvSelectedContact: TextView
    private lateinit var etPassword: EditText
    private lateinit var btnAndroid: Button
    private lateinit var btnIphone: Button
    private lateinit var btnFindNow: Button

    private var selectedPhoneNumber: String = ""
    private var isAndroidSelected: Boolean = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_find_phone)

        tvSelectedContact = findViewById(R.id.tvSelectedContact)
        etPassword = findViewById(R.id.etPassword)
        btnAndroid = findViewById(R.id.radioAndroid)
        btnIphone = findViewById(R.id.radioIphone)
        btnFindNow = findViewById(R.id.btnFindNow)

        val btnSelectContact = findViewById<Button>(R.id.btnSelectContact)
        btnSelectContact.setOnClickListener { pickContact() }

        btnFindNow.setOnClickListener { onFindNow() }

        val btnBack = findViewById<Button>(R.id.btnBack)
        btnBack.setOnClickListener { finish() }

        // Segmented toggle logic
        btnAndroid.setOnClickListener { selectDevice(true) }
        btnIphone.setOnClickListener { selectDevice(false) }
        selectDevice(true)
    }

    private fun selectDevice(androidSelected: Boolean) {
        isAndroidSelected = androidSelected
        if (androidSelected) {
            btnAndroid.setBackgroundResource(R.drawable.pill_selected)
            btnAndroid.setTextColor(0xFF000000.toInt())
            btnIphone.setBackgroundResource(R.drawable.pill_unselected)
            btnIphone.setTextColor(0xFFFFFFFF.toInt())
        } else {
            btnIphone.setBackgroundResource(R.drawable.pill_selected)
            btnIphone.setTextColor(0xFF000000.toInt())
            btnAndroid.setBackgroundResource(R.drawable.pill_unselected)
            btnAndroid.setTextColor(0xFFFFFFFF.toInt())
        }
    }

    private fun pickContact() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.READ_CONTACTS), REQUEST_CONTACTS)
            return
        }
        val intent = Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI)
        startActivityForResult(intent, PICK_CONTACT)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PICK_CONTACT && resultCode == RESULT_OK) {
            data?.data?.let { uri ->
                val cursor: Cursor? = contentResolver.query(uri, null, null, null, null)
                cursor?.use {
                    if (it.moveToFirst()) {
                        val numberIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                        val nameIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                        selectedPhoneNumber = it.getString(numberIdx) ?: ""
                        val name = it.getString(nameIdx) ?: ""
                        tvSelectedContact.text = "$name\n$selectedPhoneNumber"
                        tvSelectedContact.setTextColor(0xFFFFFFFF.toInt())
                    }
                }
            }
        }
    }

    private fun onFindNow() {
        val enteredPassword = etPassword.text.toString()
        val storedPassword = password

        if (storedPassword.isEmpty()) {
            Toast.makeText(this, "No password set. Please complete setup.", Toast.LENGTH_SHORT).show()
            return
        }
        if (enteredPassword != storedPassword) {
            Toast.makeText(this, "Incorrect password.", Toast.LENGTH_SHORT).show()
            return
        }
        if (selectedPhoneNumber.isEmpty()) {
            Toast.makeText(this, "Please select a contact first.", Toast.LENGTH_SHORT).show()
            return
        }

        if (isAndroidSelected) {
            sendFindSms()
        } else {
            // iPhone placeholder
            Toast.makeText(this, "iPhone finding: Feature coming soon (API integration required).", Toast.LENGTH_LONG).show()
        }
    }

    private fun sendFindSms() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.SEND_SMS), REQUEST_SMS)
            return
        }
        try {
            val smsManager = SmsManager.getDefault()
            smsManager.sendTextMessage(selectedPhoneNumber, null, password, null, null)
            Toast.makeText(this, "Find SMS sent to $selectedPhoneNumber", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to send SMS: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            REQUEST_CONTACTS -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    pickContact()
                }
            }
            REQUEST_SMS -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    sendFindSms()
                }
            }
        }
    }
}
