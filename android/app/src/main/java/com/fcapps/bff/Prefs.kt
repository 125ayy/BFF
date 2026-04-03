package com.fcapps.bff

import android.content.Context
import android.content.SharedPreferences

object Prefs {
    private const val PREF_NAME = "bff_prefs"
    private const val KEY_PHONE = "phone_number"
    private const val KEY_PASSWORD = "password"
    private const val KEY_OWNER_NAME = "owner_name"
    private const val KEY_OWNER_EMAIL = "owner_email"
    private const val KEY_SIREN_DURATION = "siren_duration"
    private const val KEY_BLACKLIST = "blacklist"
    private const val KEY_SETUP_DONE = "setup_done"

    private fun prefs(ctx: Context): SharedPreferences =
        ctx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    var Context.phoneNumber: String
        get() = prefs(this).getString(KEY_PHONE, "") ?: ""
        set(value) { prefs(this).edit().putString(KEY_PHONE, value).apply() }

    var Context.password: String
        get() = prefs(this).getString(KEY_PASSWORD, "") ?: ""
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

    fun Context.isBlacklisted(number: String): Boolean =
        getBlacklist().contains(number)

    fun Context.resetToDefaults() {
        val phone = phoneNumber
        val pass = password
        prefs(this).edit().clear().apply()
        phoneNumber = phone
        password = pass
        sirenDuration = 30
    }
}
