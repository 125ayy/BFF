package com.fcapps.bff

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

object Prefs {
    private const val PREF_NAME = "bff_prefs"
    private const val KEY_PASSWORD = "password"
    private const val KEY_SIREN_DURATION = "siren_duration"
    private const val KEY_BLACKLIST = "blacklist"
    private const val KEY_SETUP_DONE = "setup_done"
    private const val KEY_PIN = "pin"
    private const val KEY_HISTORY = "history"
    private const val KEY_TRUSTED_CONTACTS = "trusted_contacts"
    private const val KEY_MATRIX_ALL_ALARM = "matrix_all_alarm"
    private const val KEY_MATRIX_CONTACTS_ALARM = "matrix_contacts_alarm"
    private const val KEY_MATRIX_TRUSTED_ALARM = "matrix_trusted_alarm"
    private const val KEY_MATRIX_ALL_LOC = "matrix_all_loc"
    private const val KEY_MATRIX_CONTACTS_LOC = "matrix_contacts_loc"
    private const val KEY_MATRIX_TRUSTED_LOC = "matrix_trusted_loc"
    private const val KEY_LOCATION_ENABLED = "location_enabled"
    private const val KEY_FINDER_MESSAGE = "finder_message"

    const val MATRIX_YES = "YES"
    const val MATRIX_PIN = "PIN_REQUIRED"
    const val MATRIX_NO = "NO"

    const val STATUS_SUCCESS = "Success"
    const val STATUS_LOCATED = "Located"
    const val STATUS_BOTH = "Success + Located"
    const val STATUS_BLOCKED = "Blocked"

    private fun prefs(ctx: Context): SharedPreferences = ctx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    /** The activation word. Default BFF. */
    var Context.password: String
        get() = prefs(this).getString(KEY_PASSWORD, "BFF") ?: "BFF"
        set(value) { prefs(this).edit().putString(KEY_PASSWORD, value).apply() }

    var Context.sirenDuration: Int
        get() = prefs(this).getInt(KEY_SIREN_DURATION, 30)
        set(value) { prefs(this).edit().putInt(KEY_SIREN_DURATION, value).apply() }

    var Context.setupDone: Boolean
        get() = prefs(this).getBoolean(KEY_SETUP_DONE, false)
        set(value) { prefs(this).edit().putBoolean(KEY_SETUP_DONE, value).apply() }

    /** Empty = no PIN. */
    var Context.pin: String
        get() = prefs(this).getString(KEY_PIN, "") ?: ""
        set(value) { prefs(this).edit().putString(KEY_PIN, value).apply() }

    /** Owner's choice on the GPS row of setup; the Android grant is checked separately. */
    var Context.locationEnabled: Boolean
        get() = prefs(this).getBoolean(KEY_LOCATION_ENABLED, true)
        set(value) { prefs(this).edit().putBoolean(KEY_LOCATION_ENABLED, value).apply() }

    /** Shown on the alarm screen so the finder can reach the owner. */
    var Context.finderMessage: String
        get() = prefs(this).getString(KEY_FINDER_MESSAGE, "") ?: ""
        set(value) { prefs(this).edit().putString(KEY_FINDER_MESSAGE, value).apply() }

    var Context.matrixAllAlarm: String
        get() = prefs(this).getString(KEY_MATRIX_ALL_ALARM, MATRIX_PIN) ?: MATRIX_PIN
        set(value) { prefs(this).edit().putString(KEY_MATRIX_ALL_ALARM, value).apply() }
    var Context.matrixContactsAlarm: String
        get() = prefs(this).getString(KEY_MATRIX_CONTACTS_ALARM, MATRIX_YES) ?: MATRIX_YES
        set(value) { prefs(this).edit().putString(KEY_MATRIX_CONTACTS_ALARM, value).apply() }
    var Context.matrixTrustedAlarm: String
        get() = prefs(this).getString(KEY_MATRIX_TRUSTED_ALARM, MATRIX_YES) ?: MATRIX_YES
        set(value) { prefs(this).edit().putString(KEY_MATRIX_TRUSTED_ALARM, value).apply() }
    var Context.matrixAllLoc: String
        get() = prefs(this).getString(KEY_MATRIX_ALL_LOC, MATRIX_NO) ?: MATRIX_NO
        set(value) { prefs(this).edit().putString(KEY_MATRIX_ALL_LOC, value).apply() }
    var Context.matrixContactsLoc: String
        get() = prefs(this).getString(KEY_MATRIX_CONTACTS_LOC, MATRIX_PIN) ?: MATRIX_PIN
        set(value) { prefs(this).edit().putString(KEY_MATRIX_CONTACTS_LOC, value).apply() }
    var Context.matrixTrustedLoc: String
        get() = prefs(this).getString(KEY_MATRIX_TRUSTED_LOC, MATRIX_YES) ?: MATRIX_YES
        set(value) { prefs(this).edit().putString(KEY_MATRIX_TRUSTED_LOC, value).apply() }

    fun normalizeNumber(number: String): String = number.replace(Regex("[^0-9+]"), "")

    fun Context.getBlacklist(): List<String> = (prefs(this).getStringSet(KEY_BLACKLIST, emptySet()) ?: emptySet()).sorted()
    fun Context.addToBlacklist(number: String) {
        val cur = getBlacklist().toMutableSet(); cur.add(number); prefs(this).edit().putStringSet(KEY_BLACKLIST, cur).apply()
    }
    fun Context.removeFromBlacklist(number: String) {
        val cur = getBlacklist().toMutableSet(); cur.remove(number); prefs(this).edit().putStringSet(KEY_BLACKLIST, cur).apply()
    }
    fun Context.isBlacklisted(number: String): Boolean { val n = normalizeNumber(number); return getBlacklist().any { normalizeNumber(it) == n } }

    data class HistoryEntry(val sender: String, val name: String, val status: String, val timestamp: Long)

    fun Context.addHistoryEntry(sender: String, name: String, status: String) {
        val arr = try { JSONArray(prefs(this).getString(KEY_HISTORY, "[]") ?: "[]") } catch (e: Exception) { JSONArray() }
        arr.put(JSONObject().apply { put("sender", sender); put("name", name); put("status", status); put("timestamp", System.currentTimeMillis()) })
        val trimmed = JSONArray()
        for (i in maxOf(0, arr.length() - 10) until arr.length()) trimmed.put(arr.get(i))
        prefs(this).edit().putString(KEY_HISTORY, trimmed.toString()).apply()
    }

    fun Context.getHistory(): List<HistoryEntry> {
        val arr = try { JSONArray(prefs(this).getString(KEY_HISTORY, "[]") ?: "[]") } catch (e: Exception) { return emptyList() }
        val list = mutableListOf<HistoryEntry>()
        for (i in arr.length() - 1 downTo 0) {
            val o = arr.getJSONObject(i)
            list.add(HistoryEntry(o.optString("sender", ""), o.optString("name", "Unknown"), o.optString("status", STATUS_SUCCESS), o.optLong("timestamp", 0L)))
        }
        return list
    }

    data class TrustedContact(val name: String, val number: String)

    fun Context.getTrustedContacts(): List<TrustedContact> {
        val arr = try { JSONArray(prefs(this).getString(KEY_TRUSTED_CONTACTS, "[]") ?: "[]") } catch (e: Exception) { return emptyList() }
        val list = mutableListOf<TrustedContact>()
        for (i in 0 until arr.length()) { val o = arr.getJSONObject(i); list.add(TrustedContact(o.optString("name", ""), o.optString("number", ""))) }
        return list
    }
    fun Context.addTrustedContact(name: String, number: String) {
        val arr = try { JSONArray(prefs(this).getString(KEY_TRUSTED_CONTACTS, "[]") ?: "[]") } catch (e: Exception) { JSONArray() }
        for (i in 0 until arr.length()) if (normalizeNumber(arr.getJSONObject(i).optString("number")) == normalizeNumber(number)) return
        arr.put(JSONObject().apply { put("name", name); put("number", number) })
        prefs(this).edit().putString(KEY_TRUSTED_CONTACTS, arr.toString()).apply()
    }
    fun Context.removeTrustedContact(number: String) {
        val arr = try { JSONArray(prefs(this).getString(KEY_TRUSTED_CONTACTS, "[]") ?: "[]") } catch (e: Exception) { JSONArray() }
        val out = JSONArray()
        for (i in 0 until arr.length()) { val o = arr.getJSONObject(i); if (o.optString("number") != number) out.put(o) }
        prefs(this).edit().putString(KEY_TRUSTED_CONTACTS, out.toString()).apply()
    }
    fun Context.isTrusted(number: String): Boolean { val n = normalizeNumber(number); return getTrustedContacts().any { normalizeNumber(it.number) == n } }
}
