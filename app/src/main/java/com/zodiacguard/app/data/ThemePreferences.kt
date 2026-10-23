package com.zodiacguard.app.data

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate

object ThemePreferences {
    const val MODE_SYSTEM = "system"
    const val MODE_LIGHT = "light"
    const val MODE_DARK = "dark"

    private const val PREFERENCES_NAME = "zguard_prefs"
    private const val KEY_THEME_MODE = "theme_mode"

    fun getMode(context: Context): String =
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .getString(KEY_THEME_MODE, MODE_DARK)
            .orEmpty()
            .takeIf { it in setOf(MODE_SYSTEM, MODE_LIGHT, MODE_DARK) }
            ?: MODE_DARK

    fun saveMode(context: Context, mode: String) {
        require(mode in setOf(MODE_SYSTEM, MODE_LIGHT, MODE_DARK))
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_THEME_MODE, mode)
            .apply()
        apply(context)
    }

    fun apply(context: Context) {
        AppCompatDelegate.setDefaultNightMode(
            when (getMode(context)) {
                MODE_SYSTEM -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                MODE_LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
                else -> AppCompatDelegate.MODE_NIGHT_YES
            }
        )
    }
}
