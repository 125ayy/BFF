package com.fcapps.bff

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.fcapps.bff.Prefs.ownerEmail
import com.fcapps.bff.Prefs.ownerName
import com.fcapps.bff.Prefs.password
import com.fcapps.bff.Prefs.phoneNumber
import com.fcapps.bff.Prefs.setupDone

class SetupActivity : AppCompatActivity() {

    companion object {
        private const val REQ_STANDARD = 100
        private const val REQ_OVERLAY = 200
        private const val REQ_FULL_SCREEN = 201
    }

    private var currentStep = 0
    private var generatedPassword = ""

    // Step 0 (permissions) — no extra views needed beyond the layout
    private lateinit var step0Layout: View

    // Step 1 views
    private lateinit var step1Layout: View
    private lateinit var spinnerCountry: Spinner
    private lateinit var etPhoneNumber: EditText

    // Step 2 views
    private lateinit var step2Layout: View
    private lateinit var tvGeneratedPassword: TextView
    private lateinit var etConfirmPassword: EditText

    // Step 3 views
    private lateinit var step3Layout: View
    private lateinit var etOwnerName: EditText
    private lateinit var etOwnerEmail: EditText

    // Step 4 views
    private lateinit var step4Layout: View

    private lateinit var btnNext: Button
    private lateinit var tvStepTitle: TextView

    // Country spinner data
    private val countries = CountryCodes.list
    private var selectedCountry: Country = countries.firstOrNull { it.dialCode == "+1" } ?: countries[0]

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_setup)

        step0Layout = findViewById(R.id.step0Layout)
        step1Layout = findViewById(R.id.step1Layout)
        step2Layout = findViewById(R.id.step2Layout)
        step3Layout = findViewById(R.id.step3Layout)
        step4Layout = findViewById(R.id.step4Layout)
        btnNext = findViewById(R.id.btnNext)
        tvStepTitle = findViewById(R.id.tvStepTitle)

        spinnerCountry = findViewById(R.id.spinnerCountry)
        etPhoneNumber = findViewById(R.id.etPhoneNumber)
        tvGeneratedPassword = findViewById(R.id.tvGeneratedPassword)
        etConfirmPassword = findViewById(R.id.etConfirmPassword)
        etOwnerName = findViewById(R.id.etOwnerName)
        etOwnerEmail = findViewById(R.id.etOwnerEmail)

        // Setup country spinner
        val adapter = CountrySpinnerAdapter(this, countries)
        spinnerCountry.adapter = adapter
        // Default to US (+1)
        val defaultIndex = countries.indexOfFirst { it.dialCode == "+1" && it.name == "United States" }
        if (defaultIndex >= 0) spinnerCountry.setSelection(defaultIndex)

        spinnerCountry.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                selectedCountry = countries[position]
            }
            override fun onNothingSelected(parent: AdapterView<*>) {}
        }

        showStep(0)

        btnNext.setOnClickListener {
            when (currentStep) {
                0 -> handleStep0()
                1 -> handleStep1()
                2 -> handleStep2()
                3 -> handleStep3()
                4 -> handleStep4()
            }
        }
    }

    private fun showStep(step: Int) {
        currentStep = step
        step0Layout.visibility = if (step == 0) View.VISIBLE else View.GONE
        step1Layout.visibility = if (step == 1) View.VISIBLE else View.GONE
        step2Layout.visibility = if (step == 2) View.VISIBLE else View.GONE
        step3Layout.visibility = if (step == 3) View.VISIBLE else View.GONE
        step4Layout.visibility = if (step == 4) View.VISIBLE else View.GONE

        tvStepTitle.text = when (step) {
            0 -> "Permissions needed"
            1 -> "Step 1 of 3  —  Your phone number"
            2 -> "Step 2 of 3  —  Confirm your password"
            3 -> "Step 3 of 3  —  Owner info (optional)"
            4 -> "All done!"
            else -> ""
        }

        btnNext.text = when (step) {
            0 -> "GRANT PERMISSIONS"
            4 -> "START USING BFF"
            else -> "CONTINUE"
        }
    }

    // ── Step 0: request all permissions ──────────────────────────────────────

    private fun handleStep0() {
        requestStandardPermissions()
    }

    private fun requestStandardPermissions() {
        val standard = arrayOf(
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.READ_SMS,
            Manifest.permission.SEND_SMS,
            Manifest.permission.READ_CONTACTS
        )
        val missing = standard.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missing.toTypedArray(), REQ_STANDARD)
        } else {
            requestSpecialPermissions()
        }
    }

    private fun requestSpecialPermissions() {
        // SYSTEM_ALERT_WINDOW (display over other apps)
        if (!android.provider.Settings.canDrawOverlays(this)) {
            val intent = Intent(
                android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            @Suppress("DEPRECATION")
            startActivityForResult(intent, REQ_OVERLAY)
            return
        }
        requestFullScreenIntent()
    }

    private fun requestFullScreenIntent() {
        // USE_FULL_SCREEN_INTENT — Android 14+ only
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val nm = getSystemService(android.app.NotificationManager::class.java)
            if (!nm.canUseFullScreenIntent()) {
                val intent = Intent(
                    android.provider.Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
                    Uri.parse("package:$packageName")
                )
                @Suppress("DEPRECATION")
                startActivityForResult(intent, REQ_FULL_SCREEN)
                return
            }
        }
        // All permissions done — proceed
        showStep(1)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_STANDARD) {
            val denied = permissions.filterIndexed { i, _ ->
                grantResults[i] != PackageManager.PERMISSION_GRANTED
            }
            if (denied.isNotEmpty()) {
                Toast.makeText(
                    this,
                    "Some permissions were denied. BFF may not work correctly.",
                    Toast.LENGTH_LONG
                ).show()
            }
            requestSpecialPermissions()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            REQ_OVERLAY -> {
                if (!android.provider.Settings.canDrawOverlays(this)) {
                    Toast.makeText(
                        this,
                        "'Display over other apps' was not granted. The alarm screen may not appear.",
                        Toast.LENGTH_LONG
                    ).show()
                }
                requestFullScreenIntent()
            }
            REQ_FULL_SCREEN -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    val nm = getSystemService(android.app.NotificationManager::class.java)
                    if (!nm.canUseFullScreenIntent()) {
                        Toast.makeText(
                            this,
                            "'Full-screen notifications' was not granted. Alarm may not open on lock screen.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
                showStep(1)
            }
        }
    }

    // ── Steps 1–4 ─────────────────────────────────────────────────────────────

    private fun handleStep1() {
        val num = etPhoneNumber.text.toString().trim()
        if (num.isEmpty()) {
            Toast.makeText(this, "Please enter your phone number", Toast.LENGTH_SHORT).show()
            return
        }
        val cc = selectedCountry.dialCode
        val fullNumber = cc + num
        phoneNumber = fullNumber
        val digitsOnly = fullNumber.filter { it.isDigit() }
        generatedPassword = if (digitsOnly.length >= 7) digitsOnly.takeLast(7) else digitsOnly
        tvGeneratedPassword.text = generatedPassword
        showStep(2)
    }

    private fun handleStep2() {
        val confirmed = etConfirmPassword.text.toString().trim()
        if (confirmed != generatedPassword) {
            Toast.makeText(this, "Password does not match. Expected: $generatedPassword", Toast.LENGTH_LONG).show()
            return
        }
        password = generatedPassword
        showStep(3)
    }

    private fun handleStep3() {
        ownerName = etOwnerName.text.toString().trim()
        ownerEmail = etOwnerEmail.text.toString().trim()
        showStep(4)
    }

    private fun handleStep4() {
        setupDone = true
        startActivity(Intent(this, SplashActivity::class.java))
        finish()
    }
}
