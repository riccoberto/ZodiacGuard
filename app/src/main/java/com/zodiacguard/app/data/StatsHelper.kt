package com.zodiacguard.app.data

// Helper per calcolare le statistiche giornaliere
object StatsHelper {
    
    // 38 ore settimanali / 6 giorni = 6 ore e 20 minuti al giorno (380 minuti)
    const val DAILY_TARGET_MINUTES = 380 

    // Classe che contiene i risultati del calcolo per un giorno
    data class DailyStats(
        val totalMinutes: Int,
        val ordinaryMinutes: Int,
        val extraMinutes: Int,
        val isOvertime: Boolean, // true = Straordinari, false = Banca Ore
        val locations: Set<String>,
        val isOutOfTown: Boolean // true se ci sono sedi diverse da Modena
    )

    fun calculateDailyStats(entries: List<WorkEntry>): DailyStats {
        if (entries.isEmpty()) {
            return DailyStats(0, 0, 0, false, emptySet(), false)
        }

        // 1. Ordina le marcature per orario
        val sorted = entries.sortedBy { it.timestamp }
        
        // 2. Calcola i minuti totali lavorati (accoppiando ENTRATA e USCITA)
        var totalMinutes = 0
        var lastEntryTime: Long? = null
        
        for (entry in sorted) {
            if (entry.type == "ENTRATA") {
                lastEntryTime = entry.timestamp
            } else if (entry.type == "USCITA" && lastEntryTime != null) {
                // Differenza in millisecondi convertita in minuti
                totalMinutes += ((entry.timestamp - lastEntryTime) / 60000).toInt()
                lastEntryTime = null
            }
        }

        // 3. Determina la regola del giorno in base alla prima attività registrata
        // (Se la prima marcatura è Produzione, gli extra sono straordinari)
        val primaryActivity = sorted.first().activity
        val isProduzione = primaryActivity == "Produzione"

        // 4. Calcola ore ordinarie ed extra
        val ordinaryMinutes = minOf(totalMinutes, DAILY_TARGET_MINUTES)
        val extraMinutes = maxOf(0, totalMinutes - DAILY_TARGET_MINUTES)

        // 5. Rileva le sedi e se sei fuori Modena
        val locations = sorted.map { it.location }.toSet()
        val isOutOfTown = locations.any { it != "Modena" }

        return DailyStats(
            totalMinutes = totalMinutes,
            ordinaryMinutes = ordinaryMinutes,
            extraMinutes = extraMinutes,
            isOvertime = isProduzione, 
            locations = locations,
            isOutOfTown = isOutOfTown
        )
    }
    
    // Funzione utile per convertire i minuti in formato leggibile (es. "8h 30m")
    fun minutesToText(minutes: Int): String {
        val h = minutes / 60
        val m = minutes % 60
        return "${h}h ${m}m"
    }
}
