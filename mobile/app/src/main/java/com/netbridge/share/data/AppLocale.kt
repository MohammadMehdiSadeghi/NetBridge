package com.netbridge.share.data

import android.content.Context
import java.util.Locale

/**
 * Applies the language chosen in Settings ("fa" / "en") so the app does not
 * follow the system locale. Used from the Application (notifications, service)
 * and from MainActivity (UI).
 */
object AppLocale {

    fun wrap(base: Context): Context {
        val lang = runCatching { SettingsStore(base).appLang }.getOrDefault("fa")
        if (lang.isBlank()) return base
        val locale = Locale(lang)
        Locale.setDefault(locale)
        val config = android.content.res.Configuration(base.resources.configuration)
        config.setLocale(locale)
        config.setLayoutDirection(locale)
        return base.createConfigurationContext(config)
    }
}
