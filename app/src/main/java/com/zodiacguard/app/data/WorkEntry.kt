package com.zodiacguard.app.data

import java.text.SimpleDateFormat
import java.util.*

// Modello per una singola marcatura
data class WorkEntry(
    val timestamp: Long,           // Data e ora in millisecondi
    val type: String,              // "ENTRATA" o "USCITA"
    val activity: String,          // Servizio, Teatrino Montaggio, ecc.
    val location: String,          // Modena, Vignola, ecc.
    val date: String               // Data in formato "yyyy-MM-dd" per raggruppare
) {
    companion object {
        fun getCurrentDate(): String {
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            return sdf.format(Date())
        }
        
        fun getCurrentTime(): String {
            val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
            return sdf.format(Date())
        }
        
        // Converte in stringa per il salvataggio
        fun toJson(entry: WorkEntry): String {
            return "${entry.timestamp}|${entry.type}|${entry.activity}|${entry.location}|${entry.date}"
        }
        
        // Crea un WorkEntry da una stringa
        fun fromJson(json: String): WorkEntry? {
            return try {
                val parts = json.split("|")
                if (parts.size == 5) {
                    WorkEntry(
                        timestamp = parts[0].toLong(),
                        type = parts[1],
                        activity = parts[2],
                        location = parts[3],
                        date = parts[4]
                    )
                } else null
            } catch (e: Exception) {
                null
            }
        }

    }
}

data class WorkEntryMergeResult(
    val entries: List<WorkEntry>,
    val addedCount: Int
)

object WorkEntryMerger {
    fun merge(
        existing: List<WorkEntry>,
        incoming: List<WorkEntry>
    ): WorkEntryMergeResult {
        val existingCounts = existing.groupingBy { it }.eachCount()
        val incomingCounts = mutableMapOf<WorkEntry, Int>()
        val merged = existing.toMutableList()
        var addedCount = 0

        incoming.forEach { entry ->
            val occurrence = incomingCounts.getOrDefault(entry, 0)
            incomingCounts[entry] = occurrence + 1
            if (occurrence >= existingCounts.getOrDefault(entry, 0)) {
                merged.add(entry)
                addedCount++
            }
        }

        return WorkEntryMergeResult(
            entries = merged.sortedBy { it.timestamp },
            addedCount = addedCount
        )
    }
}
