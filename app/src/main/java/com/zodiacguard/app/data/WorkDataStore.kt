package com.zodiacguard.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "work_prefs")

class WorkDataStore(private val context: Context) {

    companion object {
        val KEY_DAILY_LOG = stringPreferencesKey("daily_log")
    }

    // Aggiunge una nuova voce al registro di oggi
    suspend fun addLogEntry(time: String, type: String, category: String) {
        context.dataStore.edit { prefs ->
            val currentLog = prefs[KEY_DAILY_LOG] ?: ""
            val newEntry = "$time | $type | $category\n"
            prefs[KEY_DAILY_LOG] = currentLog + newEntry
        }
    }

    // Cancella il registro di oggi (per il giorno dopo)
    suspend fun clearDailyLog() {
        context.dataStore.edit { prefs ->
            prefs.remove(KEY_DAILY_LOG)
        }
    }

    // Legge il registro di oggi
    val dailyLogFlow: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_DAILY_LOG] ?: "Nessun registro per oggi."
    }
}
