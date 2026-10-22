package com.zodiacguard.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

private val Context.manualCounterDataStore: DataStore<Preferences> by
    preferencesDataStore(name = "manual_counters")

data class ManualCounter(
    val id: String,
    val title: String,
    val initialMinutes: Int,
    val currentMinutes: Int
)

class ManualCounterDataStore(private val context: Context) {

    private val countersKey = stringPreferencesKey("counters")

    val countersFlow: Flow<List<ManualCounter>> =
        context.manualCounterDataStore.data.map { preferences ->
            decodeCounters(preferences[countersKey].orEmpty())
        }

    suspend fun addCounter(title: String, initialMinutes: Int) {
        require(title.isNotBlank())
        require(initialMinutes >= 0)
        context.manualCounterDataStore.edit { preferences ->
            val counters = decodeCounters(preferences[countersKey].orEmpty()).toMutableList()
            counters.add(
                ManualCounter(
                    id = UUID.randomUUID().toString(),
                    title = title.trim(),
                    initialMinutes = initialMinutes,
                    currentMinutes = initialMinutes
                )
            )
            preferences[countersKey] = encodeCounters(counters)
        }
    }

    suspend fun adjustCounter(id: String, deltaMinutes: Int) {
        context.manualCounterDataStore.edit { preferences ->
            val counters = decodeCounters(preferences[countersKey].orEmpty()).map { counter ->
                if (counter.id == id) {
                    val updatedMinutes = counter.currentMinutes.toLong() + deltaMinutes
                    require(updatedMinutes >= 0) { "Il contatore non può scendere sotto zero" }
                    require(updatedMinutes <= Int.MAX_VALUE) { "Il totale supera il limite consentito" }
                    counter.copy(currentMinutes = updatedMinutes.toInt())
                } else {
                    counter
                }
            }
            preferences[countersKey] = encodeCounters(counters)
        }
    }

    suspend fun deleteCounter(id: String) {
        context.manualCounterDataStore.edit { preferences ->
            preferences[countersKey] = encodeCounters(
                decodeCounters(preferences[countersKey].orEmpty()).filterNot { it.id == id }
            )
        }
    }

    suspend fun replaceCounters(counters: List<ManualCounter>) {
        context.manualCounterDataStore.edit { preferences ->
            preferences[countersKey] = encodeCounters(counters)
        }
    }

    private fun decodeCounters(serialized: String): List<ManualCounter> {
        if (serialized.isBlank()) return emptyList()
        val json = JSONArray(serialized)
        return (0 until json.length()).map { index ->
            val item = json.getJSONObject(index)
            ManualCounter(
                id = item.getString("id"),
                title = item.getString("title"),
                initialMinutes = item.getInt("initialMinutes"),
                currentMinutes = item.getInt("currentMinutes")
            )
        }
    }

    private fun encodeCounters(counters: List<ManualCounter>): String =
        JSONArray().apply {
            counters.forEach { counter ->
                put(
                    JSONObject()
                        .put("id", counter.id)
                        .put("title", counter.title)
                        .put("initialMinutes", counter.initialMinutes)
                        .put("currentMinutes", counter.currentMinutes)
                )
            }
        }.toString()
}
