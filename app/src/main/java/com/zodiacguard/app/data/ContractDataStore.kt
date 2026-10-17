package com.zodiacguard.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// Estensione per creare il DataStore del contratto
val Context.contractDataStore: DataStore<Preferences> by preferencesDataStore(name = "contract_prefs")

class ContractDataStore(private val context: Context) {

    companion object {
        val KEY_WEEKLY_HOURS = intPreferencesKey("weekly_hours")
        val KEY_WORK_DAYS = intPreferencesKey("work_days")
    }

    // Salva i parametri del contratto
    suspend fun saveContract(weeklyHours: Int, workDays: Int) {
        context.contractDataStore.edit { prefs ->
            prefs[KEY_WEEKLY_HOURS] = weeklyHours
            prefs[KEY_WORK_DAYS] = workDays
        }
    }

    // Legge le ore settimanali (default 38)
    val weeklyHoursFlow: Flow<Int> = context.contractDataStore.data.map { prefs ->
        prefs[KEY_WEEKLY_HOURS] ?: 38
    }

    // Legge i giorni lavorativi (default 6)
    val workDaysFlow: Flow<Int> = context.contractDataStore.data.map { prefs ->
        prefs[KEY_WORK_DAYS] ?: 6
    }
}
