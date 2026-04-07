package com.fcapps.bff

import android.app.Activity
import android.content.Intent
import android.database.Cursor
import android.os.Bundle
import android.provider.ContactsContract
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ListView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.fcapps.bff.Prefs.addTrustedContact
import com.fcapps.bff.Prefs.getTrustedContacts
import com.fcapps.bff.Prefs.removeTrustedContact

class TrustedContactsActivity : AppCompatActivity() {

    companion object {
        private const val PICK_CONTACT = 3001
    }

    private val contacts = mutableListOf<Prefs.TrustedContact>()
    private lateinit var adapter: TrustedAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_trusted_contacts)

        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }

        val listView = findViewById<ListView>(R.id.lvTrustedContacts)
        val emptyView = findViewById<TextView>(R.id.tvEmpty)

        contacts.addAll(getTrustedContacts())
        adapter = TrustedAdapter(contacts)
        listView.adapter = adapter

        updateEmptyState(listView, emptyView)

        // Add trusted contact button — opens contacts picker
        findViewById<Button>(R.id.btnAddTrustedContact).setOnClickListener {
            val intent = Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI)
            @Suppress("DEPRECATION")
            startActivityForResult(intent, PICK_CONTACT)
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PICK_CONTACT && resultCode == Activity.RESULT_OK) {
            data?.data?.let { uri ->
                val cursor: Cursor? = contentResolver.query(uri, null, null, null, null)
                cursor?.use {
                    if (it.moveToFirst()) {
                        val numberIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                        val nameIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                        val number = it.getString(numberIdx) ?: return
                        val name = it.getString(nameIdx) ?: number
                        addTrustedContact(name, number)
                        contacts.clear()
                        contacts.addAll(getTrustedContacts())
                        adapter.notifyDataSetChanged()
                        updateEmptyState(
                            findViewById(R.id.lvTrustedContacts),
                            findViewById(R.id.tvEmpty)
                        )
                    }
                }
            }
        }
    }

    private fun updateEmptyState(list: ListView, empty: TextView) {
        if (contacts.isEmpty()) {
            list.visibility = View.GONE
            empty.visibility = View.VISIBLE
        } else {
            list.visibility = View.VISIBLE
            empty.visibility = View.GONE
        }
    }

    inner class TrustedAdapter(private val items: MutableList<Prefs.TrustedContact>) :
        ArrayAdapter<Prefs.TrustedContact>(this, 0, items) {

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view = convertView ?: LayoutInflater.from(context)
                .inflate(R.layout.item_trusted_contact, parent, false)
            val item = items[position]

            // Colored avatar with initials
            val tvAvatar = view.findViewById<TextView>(R.id.tvAvatar)
            val initials = item.name.split(" ")
                .filter { it.isNotEmpty() }
                .take(2)
                .map { it[0].uppercaseChar() }
                .joinToString("")
                .ifEmpty { "#" }
            tvAvatar.text = initials

            // Consistent color based on name hash
            val colors = listOf(0xFF1976D2.toInt(), 0xFF388E3C.toInt(), 0xFFF57C00.toInt(),
                0xFF7B1FA2.toInt(), 0xFFD32F2F.toInt(), 0xFF0097A7.toInt())
            tvAvatar.setBackgroundResource(R.drawable.avatar_circle)
            tvAvatar.background?.setTint(colors[Math.abs(item.name.hashCode()) % colors.size])

            view.findViewById<TextView>(R.id.tvContactName).text = item.name
            view.findViewById<TextView>(R.id.tvContactNumber).text = item.number

            // Remove button
            view.findViewById<Button>(R.id.btnRemove).setOnClickListener {
                removeTrustedContact(item.number)
                items.removeAt(position)
                notifyDataSetChanged()
                updateEmptyState(
                    this@TrustedContactsActivity.findViewById(R.id.lvTrustedContacts),
                    this@TrustedContactsActivity.findViewById(R.id.tvEmpty)
                )
            }

            // Invite button
            view.findViewById<Button>(R.id.btnInvite).setOnClickListener {
                val shareText = "Hey ${item.name}! I'm using BestFoneFinder to help find my phone. " +
                        "If I text you and ask you to find my phone, just text 'BFF' back to my number!"
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, shareText)
                }
                startActivity(Intent.createChooser(shareIntent, "Invite via"))
            }

            return view
        }
    }
}
