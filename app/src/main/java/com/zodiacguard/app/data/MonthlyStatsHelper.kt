package com.zodiacguard.app.data

import java.text.SimpleDateFormat
import java.util.*

object MonthlyStatsHelper {

    data class MonthlyStats(
        val totalOrdinaryMinutes: Int,
        val totalOvertimeMinutes: Int,
        val totalBankMinutes: Int,
        val totalTravelMinutes: Int,
        val totalWorkedMinutes: Int,
        val totalContractMinutes: Int,
        val balanceMinutes: Int, // Positivo = extra, Negativo = debito
        val teatriniCount: Map<String, Int>, // Montaggio, Smontaggio, Intero
        val locationsCount: Map<String, Int>, // Modena, Vignola, ecc.
        val outOfTownDays: Int, // Giornate con 🚗
        val mealTickets: Int, // Buoni pasto
        val dailyStats: List<DailySummary> // Per il dettaglio giorno per giorno
    )

    data class DailySummary(
        val date: String,
        val totalMinutes: Int,
        val travelMinutes: Int,
        val isProduction: Boolean,
        val location: String,
        val activity: String,
        val hasMealTicket: Boolean // true se pausa < 2 ore
    )

    fun calculateMonthlyStats(
        allEntries: List<WorkEntry>,
        productionDays: Set<String>,
        year: Int,
        month: Int, // 1-12
        weeklyHours: Int,
        workDays: Int
    ): MonthlyStats {
        // Filtra le entry per il mese selezionato
        val monthPrefix = String.format("%04d-%02d", year, month)
        val monthEntries = allEntries.filter { it.date.startsWith(monthPrefix) }
        
        if (monthEntries.isEmpty()) {
            return MonthlyStats(0, 0, 0, 0, 0, 0, 0, emptyMap(), emptyMap(), 0, 0, emptyList())
        }

        // Raggruppa per data
        val entriesByDate = monthEntries.groupBy { it.date }
        
        var totalOrdinary = 0
        var totalOvertime = 0
        var totalBank = 0
        var totalTravel = 0
        var totalWorked = 0
        val teatriniCount = mutableMapOf<String, Int>()
        val locationsCount = mutableMapOf<String, Int>()
        var outOfTownDays = 0
        var mealTickets = 0
        val dailySummaries = mutableListOf<DailySummary>()

        // Calcola giorni lavorativi nel mese (approssimazione: 4 settimane)
        val workingDaysInMonth = (workDays * 4).coerceAtMost(entriesByDate.size)
        val totalContractMinutes = (weeklyHours * 60 * workingDaysInMonth) / 6

        for ((date, entries) in entriesByDate) {
            val sorted = entries.sortedBy { it.timestamp }
            val isProduction = productionDays.contains(date)
            
            // Calcola ore lavorate
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

            // Calcola viaggio
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
                totalOrdinary += ordinaryDay
            } else {
                totalBank += extraDay
                totalOrdinary += ordinaryDay
            }

            // Conteggia teatrini
            val activity = sorted.first().activity
            if (activity.startsWith("Teatrino")) {
                teatriniCount[activity] = teatriniCount.getOrDefault(activity, 0) + 1
            }

            // Conteggia sedi
            locationsCount[location] = locationsCount.getOrDefault(location, 0) + 1

            if (location != "Modena") outOfTownDays++

            // Calcola buoni pasto (pause < 2 ore tra turni)
            var hasMealTicket = false
            var lastExitTime: Long? = null
            for (entry in sorted) {
                if (entry.type == "USCITA") {
                    lastExitTime = entry.timestamp
                } else if (entry.type == "ENTRATA" && lastExitTime != null) {
                    val pauseMinutes = ((entry.timestamp - lastExitTime) / 60000).toInt()
                    if (pauseMinutes < 120 && pauseMinutes > 0) {
                        hasMealTicket = true
                        mealTickets += 2
                    }
                    lastExitTime = null
                }
            }

            dailySummaries.add(
                DailySummary(date, totalDayMinutes, travelMinutes, isProduction, location, activity, hasMealTicket)
            )
        }

        val balance = totalWorked - totalContractMinutes

        return MonthlyStats(
            totalOrdinary, totalOvertime, totalBank, totalTravel,
            totalWorked, totalContractMinutes, balance,
            teatriniCount, locationsCount, outOfTownDays, mealTickets,
            dailySummaries
        )
    }

    fun minutesToText(minutes: Int): String {
        val h = minutes / 60
        val m = minutes % 60
        return "${h}h ${m}m"
    }
}
