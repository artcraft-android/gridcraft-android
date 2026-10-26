package io.github.artcraftandroid.gridcraft

import android.app.Activity
import android.app.LocaleManager
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList

/** Framework app locales on Android 13+, a persisted activity override on Android 10–12. */
object AppLanguages {
    private const val KEY = "language"

    internal fun supportedChoice(value: String?): String = when (value) {
        "ar", "en" -> value
        else -> "system"
    }

    private fun preferences(context: Context) = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    fun choice(context: Context): String = if (Build.VERSION.SDK_INT >= 33) {
        supportedChoice(context.getSystemService(LocaleManager::class.java).applicationLocales.get(0)?.language)
    } else {
        supportedChoice(preferences(context).getString(KEY, "system"))
    }

    /** Called before activity resources are loaded; returning the base clears a prior override. */
    fun attach(context: Context): Context {
        if (Build.VERSION.SDK_INT >= 33) return context
        val language = choice(context)
        if (language == "system") return context
        val config = Configuration(context.resources.configuration)
        config.setLocales(LocaleList.forLanguageTags(language))
        return context.createConfigurationContext(config)
    }

    fun choose(context: Context, value: String) {
        val language = supportedChoice(value)
        if (language == choice(context)) return
        if (Build.VERSION.SDK_INT >= 33) {
            context.getSystemService(LocaleManager::class.java).applicationLocales =
                if (language == "system") LocaleList.getEmptyLocaleList() else LocaleList.forLanguageTags(language)
        } else {
            preferences(context).edit().putString(KEY, language).apply()
            // Recreation retains AppViewModel, including the workbook and uncommitted cell input.
            generateSequence(context) { (it as? ContextWrapper)?.baseContext }
                .filterIsInstance<Activity>().firstOrNull()?.recreate()
        }
    }

    /** Preserve a choice saved before an OS upgrade to Android 13. */
    fun migrate(context: Context) {
        if (Build.VERSION.SDK_INT < 33) return
        val prefs = preferences(context)
        if (!prefs.contains(KEY)) return
        val manager = context.getSystemService(LocaleManager::class.java)
        val language = supportedChoice(prefs.getString(KEY, "system"))
        prefs.edit().remove(KEY).apply()
        if (manager.applicationLocales.isEmpty && language != "system") {
            manager.applicationLocales = LocaleList.forLanguageTags(language)
        }
    }
}
