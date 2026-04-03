package com.fcapps.bff

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.fcapps.bff.Prefs.ownerEmail
import com.fcapps.bff.Prefs.ownerName
import com.fcapps.bff.Prefs.password
import com.fcapps.bff.Prefs.phoneNumber
import com.fcapps.bff.Prefs.setupDone

class SetupActivity : AppCompatActivity() {

    private var currentStep = 1
    private var generatedPassword = ""

    // Step 1 views
    private lateinit var step1Layout: View
    private lateinit var etCountryCode: EditText
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_setup)

        step1Layout = findViewById(R.id.step1Layout)
        step2Layout = findViewById(R.id.step2Layout)
        step3Layout = findViewById(R.id.step3Layout)
        step4Layout = findViewById(R.id.step4Layout)
        btnNext = findViewById(R.id.btnNext)
        tvStepTitle = findViewById(R.id.tvStepTitle)

        etCountryCode = findViewById(R.id.etCountryCode)
        etPhoneNumber = findViewById(R.id.etPhoneNumber)

        tvGeneratedPassword = findViewById(R.id.tvGeneratedPassword)
        etConfirmPassword = findViewById(R.id.etConfirmPassword)

        etOwnerName = findViewById(R.id.etOwnerName)
        etOwnerEmail = findViewById(R.id.etOwnerEmail)

        showStep(1)

        btnNext.setOnClickListener {
            when (currentStep) {
                1 -> handleStep1()
                2 -> handleStep2()
                3 -> handleStep3()
                4 -> handleStep4()
            }
        }
    }

    private fun showStep(step: Int) {
        currentStep = step
        step1Layout.visibility = if (step == 1) View.VISIBLE else View.GONE
        step2Layout.visibility = if (step == 2) View.VISIBLE else View.GONE
        step3Layout.visibility = if (step == 3) View.VISIBLE else View.GONE
        step4Layout.visibility = if (step == 4) View.VISIBLE else View.GONE

        tvStepTitle.text = when (step) {
            1 -> "Step 1: Your Phone Number"
            2 -> "Step 2: Your Password"
            3 -> "Step 3: Owner Info (Optional)"
            4 -> "Setup Complete!"
            else -> ""
        }

        btnNext.text = if (step == 4) "START USING BFF" else "NEXT"
    }

    private fun handleStep1() {
        val cc = etCountryCode.text.toString().trim()
        val num = etPhoneNumber.text.toString().trim()
        if (cc.isEmpty() || num.isEmpty()) {
            Toast.makeText(this, "Please enter country code and phone number", Toast.LENGTH_SHORT).show()
            return
        }
        val fullNumber = cc + num
        phoneNumber = fullNumber

        // Auto-generate password: last 7 digits
        val digitsOnly = fullNumber.filter { it.isDigit() }
        generatedPassword = if (digitsOnly.length >= 7) digitsOnly.takeLast(7) else digitsOnly
        tvGeneratedPassword.text = "Your password: $generatedPassword"
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
