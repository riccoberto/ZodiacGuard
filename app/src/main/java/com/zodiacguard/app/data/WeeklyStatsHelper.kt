package com.zodiacguard.app.data
   import java.util.Calendar
object WeeklyStatsHelper {

    data class WeeklyStats(
        val totalOrdinaryMinutes: Int,
        val totalOvertimeMinutes: Int,
        val totalBankMinutes: Int,
        val totalTravelMinutes: Int,
        val totalWorkedMinutes: Int,
        val totalContractMinutes: Int,
        val balanceMinutes: Int,
        val teatriniCount: Map<String, Int>,
        val locationsCount: Map<String, Int>,
        val outOfTownDays: Int,
        val mealTickets: Int
    )

    fun calculateWeeklyStats(
        allEntries: List<WorkEntry>,
        productionDays: Set<String>,
        year: Int,
        weekOfYear: Int,
        weeklyHours: Int,
        workDays: Int
    ): WeeklyStats {
        // Filtra le entry per la settimana selezionata
        val weekEntries = allEntries.filter { entry ->
            val cal = Calendar.getInstance()
            cal.timeInMillis = entry.timestamp
            cal.get(Calendar.YEAR) == year && cal.get(Calendar.WEEK_OF_YEAR) == weekOfYear
        }
        
        if (weekEntries.isEmpty()) {
            return WeeklyStats(0, 0, 0, 0, 0, 0, 0, emptyMap(), emptyMap(), 0, 0)
        }

        val entriesByDate = weekEntries.groupBy { it.date }
        
        var totalOrdinary = 0
        var totalOvertime = 0
        var totalBank = 0
        var totalTravel = 0
        var totalWorked = 0
        val teatriniCount = mutableMapOf<String, Int>()
        val locationsCount = mutableMapOf<String, Int>()
        var outOfTownDays = 0
        var mealTickets = 0

        val workingDaysInWeek = workDays.coerceAtMost(entriesByDate.size)
        val totalContractMinutes = (weeklyHours * 60) / 6 * workingDaysInWeek

        for ((date, entries) in entriesByDate) {
            val sorted = entries.sortedBy { it.timestamp }
            val isProduction = productionDays.contains(date)
            
            var workMinutes = 0
            var lastEntryTime: Long? = null
            for (entry in sorted) {
                if (entry.type == "ENTRATA") {
                    lastEntryTime = entry.timestamp
                } else if (entry.type == "USCITA" && lastEntryTime != null) {
                    workMinutes += ((entry.timestamp - lastEntryTime) / 60000).toInt()
                    lastEntryTime = null
                }
            }

            val location = sorted.first().location
            val travelMinutes = when (location) {
                "Vignola", "Castelfranco" -> 90
                "Bologna" -> 120
                else -> 0
            }

            val totalDayMinutes = workMinutes + travelMinutes
            val ordinaryDay = minOf(totalDayMinutes, 385)
            val extraDay = maxOf(0, totalDayMinutes - 385)

            totalWorked += totalDayMinutes
            totalTravel += travelMinutes

            if (isProduction) {
                totalOvertime += extraDay
            } else {
                totalBank += extraDay
            }
            totalOrdinary += ordinaryDay

            val activity = sorted.first().activity
            if (activity.startsWith("Teatrino")) {
                teatriniCount[activity] = teatriniCount.getOrDefault(activity, 0) + 1
            }

            locationsCount[location] = locationsCount.getOrDefault(location, 0) + 1
            if (location != "Modena") outOfTownDays++

            var lastExitTime: Long? = null
            for (entry in sorted) {
                if (entry.type == "USCITA") {
                    lastExitTime = entry.timestamp
                } else if (entry.type == "ENTRATA" && lastExitTime != null) {
                    val pauseMinutes = ((entry.timestamp - lastExitTime) / 60000).toInt()
                    if (pauseMinutes < 120 && pauseMinutes > 0) {
                        mealTickets += 2
                    }
                    lastExitTime = null
                }
            }
        }

        val balance = totalWorked - totalContractMinutes

        return WeeklyStats(
            totalOrdinary, totalOvertime, totalBank, totalTravel,
            totalWorked, totalContractMinutes, balance,
            teatriniCount, locationsCount, outOfTownDays, mealTickets
        )
    }
}
