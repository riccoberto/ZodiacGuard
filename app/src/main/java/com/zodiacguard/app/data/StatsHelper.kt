package com.zodiacguard.app.data

object StatsHelper {
    
    const val DAILY_TARGET_MINUTES = 385 // 6h 25m

    data class DailyStats(
        val totalMinutes: Int,
        val ordinaryMinutes: Int,
        val extraMinutes: Int,
        val isOvertime: Boolean, 
        val locations: Set<String>,
        val isOutOfTown: Boolean
    )

    // Aggiungiamo currentTime per il calcolo in tempo reale
    fun calculateDailyStats(entries: List<WorkEntry>, currentActivity: String, currentTime: Long = System.currentTimeMillis()): DailyStats {
        if (entries.isEmpty()) {
            return DailyStats(0, 0, 0, false, emptySet(), false)
        }

        val sorted = entries.sortedBy { it.timestamp }
        
        var totalMinutes = 0
        var lastEntryTime: Long? = null
        var isCurrentlyWorking = false
        
        for (entry in sorted) {
            if (entry.type == "ENTRATA") {
                lastEntryTime = entry.timestamp
                isCurrentlyWorking = true
            } else if (entry.type == "USCITA" && lastEntryTime != null) {
                totalMinutes += ((entry.timestamp - lastEntryTime) / 60000).toInt()
                lastEntryTime = null
                isCurrentlyWorking = false
            }
        }

        // CALCOLO LIVE: Se stiamo lavorando, aggiungi i minuti da ultimo ingresso a ORA
        if (isCurrentlyWorking && lastEntryTime != null) {
            val liveMinutes = ((currentTime - lastEntryTime) / 60000).toInt()
            totalMinutes += liveMinutes
        }

        val isProduzione = currentActivity == "Produzione"
        val ordinaryMinutes = minOf(totalMinutes, DAILY_TARGET_MINUTES)
        val extraMinutes = maxOf(0, totalMinutes - DAILY_TARGET_MINUTES)

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
    
    fun minutesToText(minutes: Int): String {
        val h = minutes / 60
        val m = minutes % 60
        return "${h}h ${m}m"
    }
}
