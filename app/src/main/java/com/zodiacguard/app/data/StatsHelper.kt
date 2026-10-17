package com.zodiacguard.app.data

object StatsHelper {
    
    const val DAILY_TARGET_MINUTES = 385 // 6h 25m

    data class DailyStats(
        val totalMinutes: Int,
        val ordinaryMinutes: Int,
        val extraMinutes: Int,
        val travelMinutes: Int, // NUOVO: ore di viaggio
        val isOvertime: Boolean, 
        val locations: Set<String>,
        val isOutOfTown: Boolean,
        val currentMinutes: Int = 0 // NUOVO: minuti attuali per la linea
    )

    fun calculateDailyStats(
        entries: List<WorkEntry>, 
        currentActivity: String, 
        currentTime: Long = System.currentTimeMillis()
    ): DailyStats {
        if (entries.isEmpty()) {
            return DailyStats(0, 0, 0, 0, false, emptySet(), false, 0)
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

        // Calcolo live
        var currentMinutes = 0
        if (isCurrentlyWorking && lastEntryTime != null) {
            currentMinutes = ((currentTime - lastEntryTime) / 60000).toInt()
            totalMinutes += currentMinutes
        }

        // NUOVO: Calcolo ore viaggio in base alla sede
        val travelMinutes = calculateTravelTime(sorted)
        totalMinutes += travelMinutes

        val isProduzione = currentActivity == "Produzione"
        val ordinaryMinutes = minOf(totalMinutes, DAILY_TARGET_MINUTES)
        val extraMinutes = maxOf(0, totalMinutes - DAILY_TARGET_MINUTES)

        val locations = sorted.map { it.location }.toSet()
        val isOutOfTown = locations.any { it != "Modena" }

        return DailyStats(
            totalMinutes = totalMinutes,
            ordinaryMinutes = ordinaryMinutes,
            extraMinutes = extraMinutes,
            travelMinutes = travelMinutes,
            isOvertime = isProduzione, 
            locations = locations,
            isOutOfTown = isOutOfTown,
            currentMinutes = currentMinutes
        )
    }

    // NUOVO: Calcola le ore di viaggio in base alla sede
    private fun calculateTravelTime(entries: List<WorkEntry>): Int {
        if (entries.isEmpty()) return 0
        
        // Prendi la sede dalla prima marcatura
        val location = entries.first().location
        
        return when (location) {
            "Vignola", "Castelfranco" -> 90 // 1,5 ore = 90 minuti
            "Bologna" -> 120 // 2 ore = 120 minuti
            else -> 0 // Modena = 0
        }
    }
    
    fun minutesToText(minutes: Int): String {
        val h = minutes / 60
        val m = minutes % 60
        return "${h}h ${m}m"
    }
}
