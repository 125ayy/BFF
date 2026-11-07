package com.fcapps.bff

import android.os.Bundle
import android.view.View
import android.widget.Button
import com.fcapps.bff.Prefs.sirenDuration

class SoundActivity : BffActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sound)
        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }
        val p15 = findViewById<Button>(R.id.pill15)
        val p30 = findViewById<Button>(R.id.pill30)
        val p60 = findViewById<Button>(R.id.pill60)
        fun paint() {
            for ((btn, sec) in listOf(p15 to 15, p30 to 30, p60 to 60)) {
                val on = sirenDuration == sec
                btn.setBackgroundResource(if (on) R.drawable.pill_selected else R.drawable.pill_unselected)
                btn.setTextColor(if (on) 0xFF000000.toInt() else 0xFFFFFFFF.toInt())
            }
        }
        p15.setOnClickListener { sirenDuration = 15; paint() }
        p30.setOnClickListener { sirenDuration = 30; paint() }
        p60.setOnClickListener { sirenDuration = 60; paint() }
        paint()
    }
}
