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
        val KEY_ENTRIES = stringPreferencesKey("work_entries")
        val KEY_NOTES = stringPreferencesKey("daily_notes")
    }

    // Aggiunge una nuova marcatura
    suspend fun addEntry(entry: WorkEntry) {
        context.dataStore.edit { prefs ->
            val currentEntries = prefs[KEY_ENTRIES] ?: ""
            val newEntry = WorkEntry.toJson(entry)
            prefs[KEY_ENTRIES] = if (currentEntries.isEmpty()) newEntry else "$currentEntries\n$newEntry"
        }
    }

    // Salva le note per una data specifica
    suspend fun saveNote(date: String, note: String) {
        context.dataStore.edit { prefs ->
            val currentNotes = prefs[KEY_NOTES] ?: ""
            val entries = currentNotes.split("|").filter { it.isNotEmpty() }.toMutableList()
            entries.removeAll { it.startsWith("$date:") }
            if (note.isNotEmpty()) entries.add("$date:$note")
            prefs[KEY_NOTES] = entries.joinToString("|")
        }
    }

    // Ottieni tutte le marcature
    val entriesFlow: Flow<List<WorkEntry>> = context.dataStore.data.map { prefs ->
        val entriesString = prefs[KEY_ENTRIES] ?: ""
        if (entriesString.isEmpty()) emptyList()
        else entriesString.split("\n").mapNotNull { WorkEntry.fromJson(it) }
    }

    // Ottieni le marcature di una data specifica
    fun getEntriesForDate(date: String): Flow<List<WorkEntry>> = entriesFlow.map { entries ->
        entries.filter { it.date == date }
    }

    // Ottieni la nota per una data specifica
    fun getNoteForDate(date: String): Flow<String> = context.dataStore.data.map { prefs ->
        val notesString = prefs[KEY_NOTES] ?: ""
        if (notesString.isEmpty()) ""
        else {
            notesString.split("|").firstOrNull { it.startsWith("$date:") }
                ?.substringAfter("$date:") ?: ""
        }
    }
}
