package com.example.opsc6312_poe

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/** Persists the selected app language and applies localized resources on the next Activity creation. */
object LocaleHelper {
    private const val PREFS = "saferoute_preferences"
    private const val KEY_LANGUAGE = "language"
    fun selected(context: Context): String = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_LANGUAGE, "en") ?: "en"
    fun save(context: Context, language: String) { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_LANGUAGE, language).apply() }
    fun wrap(context: Context): Context {
        val code = selected(context)
        val configuration = Configuration(context.resources.configuration)
        configuration.setLocale(Locale.forLanguageTag(code))
        return context.createConfigurationContext(configuration)
    }
}
