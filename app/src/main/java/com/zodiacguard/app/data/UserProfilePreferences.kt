package com.zodiacguard.app.data

import android.content.Context

object UserProfilePreferences {
    private const val PREFERENCES_NAME = "zguard_prefs"
    private const val KEY_DISPLAY_NAME = "display_name"

    fun getDisplayName(context: Context): String =
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .getString(KEY_DISPLAY_NAME, "")
            .orEmpty()

    fun saveDisplayName(context: Context, displayName: String) {
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_DISPLAY_NAME, displayName.trim())
            .apply()
    }
}
