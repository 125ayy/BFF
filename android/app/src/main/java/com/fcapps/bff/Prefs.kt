package com.fcapps.bff

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

object Prefs {
    private const val PREF_NAME = "bff_prefs"
    private const val KEY_PHONE = "phone_number"
    private const val KEY_PASSWORD = "password"
    private const val KEY_OWNER_NAME = "owner_name"
    private const val KEY_OWNER_EMAIL = "owner_email"
    private const val KEY_SIREN_DURATION = "siren_duration"
    private const val KEY_BLACKLIST = "blacklist"
    private const val KEY_SETUP_DONE = "setup_done"
    private const val KEY_PIN = "pin"
    private const val KEY_HISTORY = "history"
    private const val KEY_TRUSTED_CONTACTS = "trusted_contacts"
    private const val KEY_MATRIX_ALL_ALARM = "matrix_all_alarm"
    private const val KEY_MATRIX_CONTACTS_ALARM = "matrix_contacts_alarm"
    private const val KEY_MATRIX_TRUSTED_ALARM = "matrix_trusted_alarm"

    // Matrix values
    const val MATRIX_YES = "YES"
    const val MATRIX_PIN = "PIN_REQUIRED"
    const val MATRIX_NO = "NO"

    private fun prefs(ctx: Context): SharedPreferences =
        ctx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    var Context.phoneNumber: String
        get() = prefs(this).getString(KEY_PHONE, "") ?: ""
        set(value) { prefs(this).edit().putString(KEY_PHONE, value).apply() }

    var Context.password: String
        get() = prefs(this).getString(KEY_PASSWORD, "BFF") ?: "BFF"
        set(value) { prefs(this).edit().putString(KEY_PASSWORD, value).apply() }

    var Context.ownerName: String
        get() = prefs(this).getString(KEY_OWNER_NAME, "") ?: ""
        set(value) { prefs(this).edit().putString(KEY_OWNER_NAME, value).apply() }

    var Context.ownerEmail: String
        get() = prefs(this).getString(KEY_OWNER_EMAIL, "") ?: ""
        set(value) { prefs(this).edit().putString(KEY_OWNER_EMAIL, value).apply() }

    var Context.sirenDuration: Int
        get() = prefs(this).getInt(KEY_SIREN_DURATION, 30)
        set(value) { prefs(this).edit().putInt(KEY_SIREN_DURATION, value).apply() }

    var Context.setupDone: Boolean
        get() = prefs(this).getBoolean(KEY_SETUP_DONE, false)
        set(value) { prefs(this).edit().putBoolean(KEY_SETUP_DONE, value).apply() }

    // PIN — empty means no PIN required beyond "BFF"
    var Context.pin: String
        get() = prefs(this).getString(KEY_PIN, "") ?: ""
        set(value) { prefs(this).edit().putString(KEY_PIN, value).apply() }

    // Permission matrix
    var Context.matrixAllAlarm: String
        get() = prefs(this).getString(KEY_MATRIX_ALL_ALARM, MATRIX_PIN) ?: MATRIX_PIN
        set(value) { prefs(this).edit().putString(KEY_MATRIX_ALL_ALARM, value).apply() }

    var Context.matrixContactsAlarm: String
        get() = prefs(this).getString(KEY_MATRIX_CONTACTS_ALARM, MATRIX_YES) ?: MATRIX_YES
        set(value) { prefs(this).edit().putString(KEY_MATRIX_CONTACTS_ALARM, value).apply() }

    var Context.matrixTrustedAlarm: String
        get() = prefs(this).getString(KEY_MATRIX_TRUSTED_ALARM, MATRIX_YES) ?: MATRIX_YES
        set(value) { prefs(this).edit().putString(KEY_MATRIX_TRUSTED_ALARM, value).apply() }

    // Blacklist
    fun Context.getBlacklist(): Set<String> =
        prefs(this).getStringSet(KEY_BLACKLIST, emptySet()) ?: emptySet()

    fun Context.addToBlacklist(number: String) {
        val current = getBlacklist().toMutableSet()
        current.add(number)
        prefs(this).edit().putStringSet(KEY_BLACKLIST, current).apply()
    }

    fun Context.removeFromBlacklist(number: String) {
        val current = getBlacklist().toMutableSet()
        current.remove(number)
        prefs(this).edit().putStringSet(KEY_BLACKLIST, current).apply()
    }

    fun Context.isBlacklisted(number: String): Boolean {
        val normalized = number.replace(Regex("[^0-9+]"), "")
        return getBlacklist().any { it.replace(Regex("[^0-9+]"), "") == normalized }
    }

    // History — last 10 entries as JSON array
    fun Context.addHistoryEntry(sender: String, name: String, status: String) {
        val arr = try {
            JSONArray(prefs(this).getString(KEY_HISTORY, "[]") ?: "[]")
        } catch (e: Exception) { JSONArray() }

        val entry = JSONObject().apply {
            put("sender", sender)
            put("name", name)
            put("status", status)
            put("timestamp", System.currentTimeMillis())
        }
        arr.put(entry)

        // Keep only last 10
        val trimmed = JSONArray()
        val start = maxOf(0, arr.length() - 10)
        for (i in start until arr.length()) trimmed.put(arr.get(i))

        prefs(this).edit().putString(KEY_HISTORY, trimmed.toString()).apply()
    }

    data class HistoryEntry(
        val sender: String,
        val name: String,
        val status: String,
        val timestamp: Long
    )

    fun Context.getHistory(): List<HistoryEntry> {
        val arr = try {
            JSONArray(prefs(this).getString(KEY_HISTORY, "[]") ?: "[]")
        } catch (e: Exception) { return emptyList() }
        val list = mutableListOf<HistoryEntry>()
        for (i in arr.length() - 1 downTo 0) {
            val obj = arr.getJSONObject(i)
            list.add(HistoryEntry(
                sender = obj.optString("sender", ""),
                name = obj.optString("name", "Unknown"),
                status = obj.optString("status", "Success"),
                timestamp = obj.optLong("timestamp", 0L)
            ))
        }
        return list
    }

    // Trusted contacts — JSON array of {name, number}
    data class TrustedContact(val name: String, val number: String)

    fun Context.getTrustedContacts(): List<TrustedContact> {
        val arr = try {
            JSONArray(prefs(this).getString(KEY_TRUSTED_CONTACTS, "[]") ?: "[]")
        } catch (e: Exception) { return emptyList() }
        val list = mutableListOf<TrustedContact>()
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            list.add(TrustedContact(
                name = obj.optString("name", ""),
                number = obj.optString("number", "")
            ))
        }
        return list
    }

    fun Context.addTrustedContact(name: String, number: String) {
        val arr = try {
            JSONArray(prefs(this).getString(KEY_TRUSTED_CONTACTS, "[]") ?: "[]")
        } catch (e: Exception) { JSONArray() }
        // Avoid duplicates
        for (i in 0 until arr.length()) {
            if (arr.getJSONObject(i).optString("number") == number) return
        }
        arr.put(JSONObject().apply { put("name", name); put("number", number) })
        prefs(this).edit().putString(KEY_TRUSTED_CONTACTS, arr.toString()).apply()
    }

    fun Context.removeTrustedContact(number: String) {
        val arr = try {
            JSONArray(prefs(this).getString(KEY_TRUSTED_CONTACTS, "[]") ?: "[]")
        } catch (e: Exception) { JSONArray() }
        val newArr = JSONArray()
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            if (obj.optString("number") != number) newArr.put(obj)
        }
        prefs(this).edit().putString(KEY_TRUSTED_CONTACTS, newArr.toString()).apply()
    }

    fun Context.resetToDefaults() {
        val phone = phoneNumber
        val pass = password
        val done = setupDone
        prefs(this).edit().clear().apply()
        phoneNumber = phone
        password = pass
        setupDone = done
        sirenDuration = 30
    }
}
