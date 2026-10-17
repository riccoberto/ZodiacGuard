package com.zodiacguard.app.data

object StatsHelper {
    
    const val DAILY_TARGET_MINUTES = 385 // 6h 25m

    data class DailyStats(
        val totalMinutes: Int,
        val ordinaryMinutes: Int,
        val extraMinutes: Int,
        val travelMinutes: Int,
        val isOvertime: Boolean, // Se true = Rosso (Straordinari), Se false = Blu (Banca Ore)
        val locations: Set<String>,
        val isOutOfTown: Boolean,
        val currentMinutes: Int = 0
    )

    fun calculateDailyStats(
        entries: List<WorkEntry>, 
        isProductionMode: Boolean, // NUOVO PARAMETRO
        currentTime: Long = System.currentTimeMillis()
    ): DailyStats {
        if (entries.isEmpty() && !isProductionMode) {
            return DailyStats(0, 0, 0, 0, false, emptySet(), false, 0)
        }

        val sorted = entries.sortedBy { it.timestamp }
        
        var workMinutes = 0
        var lastEntryTime: Long? = null
        var isCurrentlyWorking = false
        
        for (entry in sorted) {
            if (entry.type == "ENTRATA") {
                lastEntryTime = entry.timestamp
                isCurrentlyWorking = true
            } else if (entry.type == "USCITA" && lastEntryTime != null) {
                workMinutes += ((entry.timestamp - lastEntryTime) / 60000).toInt()
                lastEntryTime = null
                isCurrentlyWorking = false
            }
        }

        var currentMinutes = 0
        if (isCurrentlyWorking && lastEntryTime != null) {
            currentMinutes = ((currentTime - lastEntryTime) / 60000).toInt()
            workMinutes += currentMinutes
        }

        val travelMinutes = calculateTravelTime(sorted)
        val totalMinutes = workMinutes + travelMinutes

        val ordinaryMinutes = minOf(totalMinutes, DAILY_TARGET_MINUTES)
        val extraMinutes = maxOf(0, totalMinutes - DAILY_TARGET_MINUTES)

        val locations = sorted.map { it.location }.toSet()
        val isOutOfTown = locations.any { it != "Modena" }

        // SE PRODUZIONE È ATTIVA, TUTTO L'EXTRA (VIAGGIO + ESUBERO) È STRAORDINARIO (ROSSO)
        // ALTRIMENTI È BANCA ORE (BLU)
        val isOvertime = isProductionMode 

        return DailyStats(
            totalMinutes = totalMinutes,
            ordinaryMinutes = ordinaryMinutes,
            extraMinutes = extraMinutes,
            travelMinutes = travelMinutes,
            isOvertime = isOvertime, 
            locations = locations,
            isOutOfTown = isOutOfTown,
            currentMinutes = currentMinutes
        )
    }

    private fun calculateTravelTime(entries: List<WorkEntry>): Int {
        if (entries.isEmpty()) return 0
        val location = entries.first().location
        return when (location) {
            "Vignola", "Castelfranco" -> 90 
            "Bologna" -> 120 
            else -> 0 
        }
    }
    
    fun minutesToText(minutes: Int): String {
        val h = minutes / 60
        val m = minutes % 60
        return "${h}h ${m}m"
    }
}
