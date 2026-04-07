package com.fcapps.bff

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.fcapps.bff.Prefs.getHistory
import java.text.SimpleDateFormat
import java.util.*

class HistoryActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_history)

        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }

        val history = getHistory()
        val listView = findViewById<ListView>(R.id.lvHistory)
        val emptyView = findViewById<TextView>(R.id.tvEmpty)

        if (history.isEmpty()) {
            listView.visibility = View.GONE
            emptyView.visibility = View.VISIBLE
        } else {
            listView.visibility = View.VISIBLE
            emptyView.visibility = View.GONE
            listView.adapter = HistoryAdapter(history)
        }
    }

    inner class HistoryAdapter(private val items: List<Prefs.HistoryEntry>) :
        ArrayAdapter<Prefs.HistoryEntry>(this, 0, items) {

        private val dateFormat = SimpleDateFormat("MMM d, HH:mm", Locale.getDefault())

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view = convertView ?: LayoutInflater.from(context)
                .inflate(R.layout.item_history, parent, false)
            val item = items[position]

            view.findViewById<TextView>(R.id.tvContactName).text =
                if (item.name.isNotEmpty() && item.name != "Unknown") item.name else "Unknown"
            view.findViewById<TextView>(R.id.tvPhoneNumber).text = item.sender
            view.findViewById<TextView>(R.id.tvDateTime).text =
                if (item.timestamp > 0) dateFormat.format(Date(item.timestamp)) else ""

            val badge = view.findViewById<TextView>(R.id.tvStatusBadge)
            if (item.status == "Success") {
                badge.text = "Success"
                badge.setBackgroundResource(R.drawable.badge_green)
                badge.setTextColor(0xFF000000.toInt())
            } else {
                badge.text = "Blocked"
                badge.setBackgroundResource(R.drawable.badge_red)
                badge.setTextColor(0xFFFFFFFF.toInt())
            }

            return view
        }
    }
}
