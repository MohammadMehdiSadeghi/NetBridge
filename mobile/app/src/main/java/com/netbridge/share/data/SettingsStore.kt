package com.netbridge.share.data

import android.content.Context
import android.content.SharedPreferences

class SettingsStore(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("netbridge_settings", Context.MODE_PRIVATE)

    var httpPort: Int
        get() = prefs.getInt(KEY_HTTP_PORT, 8080)
        set(value) = prefs.edit().putInt(KEY_HTTP_PORT, value).apply()

    var socksPort: Int
        get() = prefs.getInt(KEY_SOCKS_PORT, 1080)
        set(value) = prefs.edit().putInt(KEY_SOCKS_PORT, value).apply()

    var controlPort: Int
        get() = prefs.getInt(KEY_CONTROL_PORT, 7777)
        set(value) = prefs.edit().putInt(KEY_CONTROL_PORT, value).apply()

    var apiToken: String
        get() = prefs.getString(KEY_TOKEN, "") ?: ""
        set(value) = prefs.edit().putString(KEY_TOKEN, value).apply()

    var pairingCode: String
        get() = prefs.getString(KEY_CODE, "") ?: ""
        set(value) = prefs.edit().putString(KEY_CODE, value).apply()

    var autoShare: Boolean
        get() = prefs.getBoolean(KEY_AUTO_SHARE, false)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_SHARE, value).apply()

    var preferredDns: String
        get() = prefs.getString(KEY_DNS, "auto") ?: "auto"
        set(value) = prefs.edit().putString(KEY_DNS, value).apply()

    /** "fa" | "en" — the in-app language, independent of the system locale. */
    var appLang: String
        get() = prefs.getString(KEY_LANG, "fa") ?: "fa"
        set(value) = prefs.edit().putString(KEY_LANG, value).apply()

    private companion object {
        const val KEY_HTTP_PORT = "httpPort"
        const val KEY_SOCKS_PORT = "socksPort"
        const val KEY_CONTROL_PORT = "controlPort"
        const val KEY_TOKEN = "apiToken"
        const val KEY_CODE = "pairingCode"
        const val KEY_AUTO_SHARE = "autoShare"
        const val KEY_DNS = "preferredDns"
        const val KEY_LANG = "appLang"
    }
}
