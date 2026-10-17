package com.zodiacguard.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "work_prefs")

class WorkDataStore(private val context: Context) {

    companion object {
        val KEY_ENTRIES = stringPreferencesKey("work_entries")
        val KEY_NOTES = stringPreferencesKey("daily_notes")
        val KEY_PRODUCTION_DAYS = stringPreferencesKey("production_days") // NUOVO
    }

    suspend fun addEntry(entry: WorkEntry) {
        context.dataStore.edit { prefs ->
            val currentEntries = prefs[KEY_ENTRIES] ?: ""
            val newEntry = WorkEntry.toJson(entry)
            prefs[KEY_ENTRIES] = if (currentEntries.isEmpty()) newEntry else "$currentEntries\n$newEntry"
        }
    }

    suspend fun saveAllEntries(entries: List<WorkEntry>) {
        context.dataStore.edit { prefs ->
            prefs[KEY_ENTRIES] = entries.joinToString("\n") { WorkEntry.toJson(it) }
        }
    }

    suspend fun deleteEntry(entry: WorkEntry) {
        val currentList = entriesFlow.first()
        val newList = currentList.filter { it.timestamp != entry.timestamp }
        saveAllEntries(newList)
    }

    suspend fun saveNote(date: String, note: String) {
        context.dataStore.edit { prefs ->
            val currentNotes = prefs[KEY_NOTES] ?: ""
            val entries = currentNotes.split("|").filter { it.isNotEmpty() }.toMutableList()
            entries.removeAll { it.startsWith("$date:") }
            if (note.isNotEmpty()) entries.add("$date:$note")
            prefs[KEY_NOTES] = entries.joinToString("|")
        }
    }

    // NUOVO: Salva se un giorno è di Produzione
    suspend fun saveProductionMode(date: String, isProduction: Boolean) {
        context.dataStore.edit { prefs ->
            val current = prefs[KEY_PRODUCTION_DAYS] ?: ""
            val dates = current.split(",").filter { it.isNotEmpty() }.toMutableSet()
            if (isProduction) dates.add(date) else dates.remove(date)
            prefs[KEY_PRODUCTION_DAYS] = dates.joinToString(",")
        }
    }

    val entriesFlow: Flow<List<WorkEntry>> = context.dataStore.data.map { prefs ->
        val entriesString = prefs[KEY_ENTRIES] ?: ""
        if (entriesString.isEmpty()) emptyList()
        else entriesString.split("\n").mapNotNull { WorkEntry.fromJson(it) }
    }

    fun getEntriesForDate(date: String): Flow<List<WorkEntry>> = entriesFlow.map { entries ->
        entries.filter { it.date == date }
    }

    fun getNoteForDate(date: String): Flow<String> = context.dataStore.data.map { prefs ->
        val notesString = prefs[KEY_NOTES] ?: ""
        if (notesString.isEmpty()) ""
        else notesString.split("|").firstOrNull { it.startsWith("$date:") }?.substringAfter("$date:") ?: ""
    }

    // NUOVO: Legge se un giorno è di Produzione
    fun getProductionModeForDate(date: String): Flow<Boolean> = context.dataStore.data.map { prefs ->
        val current = prefs[KEY_PRODUCTION_DAYS] ?: ""
        current.split(",").contains(date)
    }
}
