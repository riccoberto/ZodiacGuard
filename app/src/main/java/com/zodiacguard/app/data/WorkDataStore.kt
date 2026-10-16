package com.zodiacguard.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// Estensione per creare il DataStore
val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "work_prefs")

class WorkDataStore(private val context: Context) {

    companion object {
        val KEY_CHECK_IN = stringPreferencesKey("check_in")
        val KEY_CHECK_OUT = stringPreferencesKey("check_out")
    }

    // Salva l'ora di entrata
    suspend fun saveCheckIn(time: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_CHECK_IN] = time
        }
    }

    // Salva l'ora di uscita
    suspend fun saveCheckOut(time: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_CHECK_OUT] = time
        }
    }

    // Cancella i dati della giornata
    suspend fun clearDailyData() {
        context.dataStore.edit { prefs ->
            prefs.remove(KEY_CHECK_IN)
            prefs.remove(KEY_CHECK_OUT)
        }
    }

    // Legge l'ora di entrata
    val checkInFlow: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[KEY_CHECK_IN]
    }

    // Legge l'ora di uscita
    val checkOutFlow: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[KEY_CHECK_OUT]
    }
}
