package com.fcapps.bff

import android.content.Intent
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.widget.TextView
import com.fcapps.bff.Prefs.password
import com.fcapps.bff.Prefs.setupDone

class ReadyActivity : BffActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ready)
        val word = password
        val green = 0xFF00C853.toInt()

        findViewById<TextView>(R.id.tvSubtitle).text = bold("Send ", word, " via SMS to find phone.", 0xFFFFFFFF.toInt())
        findViewById<TextView>(R.id.tvLine1).text = bold("Someone texts ", word, " → this phone screams.", green)
        findViewById<TextView>(R.id.tvLine2).text = bold("Add ", "where", " → it sends its location.", green)

        findViewById<android.view.View>(R.id.btnPrivacy).setOnClickListener {
            startActivity(Intent(this, PrivacyActivity::class.java).putExtra(PrivacyActivity.EXTRA_FROM_SETUP, true))
        }
        findViewById<android.view.View>(R.id.btnReady).setOnClickListener {
            setupDone = true
            startActivity(Intent(this, SplashActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            })
            finish()
        }
    }

    private fun bold(pre: String, mid: String, post: String, color: Int): SpannableStringBuilder {
        val sb = SpannableStringBuilder(pre).append(mid).append(post)
        val a = pre.length; val b = pre.length + mid.length
        sb.setSpan(StyleSpan(android.graphics.Typeface.BOLD), a, b, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        sb.setSpan(ForegroundColorSpan(color), a, b, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        return sb
    }
}
