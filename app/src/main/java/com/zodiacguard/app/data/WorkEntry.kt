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
    }
    
    // Converte in stringa JSON semplice per il salvataggio
    fun toJson(): String {
        return "$timestamp|$type|$activity|$location|$date"
    }
    
    // Crea un WorkEntry da una stringa
    companion object {
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
