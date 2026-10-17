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
        val balanceMinutes: Int,
        val teatriniCount: Map<String, Int>,
        val teatriniDays: List<String>,
        val locationsCount: Map<String, Int>,
        val outOfTownDays: Int,
        val mealTickets: Int,
        val dailyStats: List<DailySummary>,
        val workingDaysInMonth: Int // NUOVO: giorni lavorativi reali del mese
    )

    data class DailySummary(
        val date: String,
        val totalMinutes: Int,
        val travelMinutes: Int,
        val isProduction: Boolean,
        val location: String,
        val activity: String,
        val hasMealTicket: Boolean,
        val isTeatrino: Boolean
    )

    // NUOVO: Conta i giorni lavorativi (lun-sab) in un mese
    fun countWorkingDaysInMonth(year: Int, month: Int): Int {
        val cal = Calendar.getInstance()
        cal.set(year, month - 1, 1)
        val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        var workingDays = 0
        for (day in 1..daysInMonth) {
            cal.set(Calendar.DAY_OF_MONTH, day)
            val dayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
            // Escludi domenica (Calendar.SUNDAY = 1)
            if (dayOfWeek != Calendar.SUNDAY) {
                workingDays++
            }
        }
        return workingDays
    }

    fun calculateMonthlyStats(
        allEntries: List<WorkEntry>,
        productionDays: Set<String>,
        year: Int,
        month: Int,
        weeklyHours: Int,
        workDays: Int
    ): MonthlyStats {
        val monthPrefix = String.format("%04d-%02d", year, month)
        val monthEntries = allEntries.filter { it.date.startsWith(monthPrefix) }

        val workingDaysInMonth = countWorkingDaysInMonth(year, month)
        
        // Ore contrattuali mensili = (ore settimanali / giorni lavorativi settimanali) * giorni lavorativi del mese
        val dailyContractMinutes = (weeklyHours * 60) / workDays
        val totalContractMinutes = dailyContractMinutes * workingDaysInMonth

        if (monthEntries.isEmpty()) {
            return MonthlyStats(0, 0, 0, 0, 0, totalContractMinutes, -totalContractMinutes, 
                emptyMap(), emptyList(), emptyMap(), 0, 0, emptyList(), workingDaysInMonth)
        }

        val entriesByDate = monthEntries.groupBy { it.date }
        
        var totalOrdinary = 0
        var totalOvertime = 0
        var totalBank = 0
        var totalTravel = 0
        var totalWorked = 0
        val teatriniCount = mutableMapOf<String, Int>()
        val teatriniDaysList = mutableListOf<String>()
        val locationsCount = mutableMapOf<String, Int>()
        var outOfTownDays = 0
        var mealTickets = 0
        val dailySummaries = mutableListOf<DailySummary>()

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
            val isTeatrino = activity.startsWith("Teatrino")
            
            if (isTeatrino) {
                teatriniCount[activity] = teatriniCount.getOrDefault(activity, 0) + 1
                teatriniDaysList.add(date)
            }

            locationsCount[location] = locationsCount.getOrDefault(location, 0) + 1
            if (location != "Modena") outOfTownDays++

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
                DailySummary(date, totalDayMinutes, travelMinutes, isProduction, location, activity, hasMealTicket, isTeatrino)
            )
        }

        val balance = totalWorked - totalContractMinutes

        return MonthlyStats(
            totalOrdinary, totalOvertime, totalBank, totalTravel,
            totalWorked, totalContractMinutes, balance,
            teatriniCount, teatriniDaysList.sorted(), locationsCount, outOfTownDays, mealTickets,
            dailySummaries, workingDaysInMonth
        )
    }

    fun minutesToText(minutes: Int): String {
        val h = minutes / 60
        val m = minutes % 60
        return "${h}h ${m}m"
    }
    
    fun formatDate(dateStr: String): String {
        val inputFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val outputFormat = SimpleDateFormat("dd MMM", Locale.getDefault())
        return try {
            val date = inputFormat.parse(dateStr)
            outputFormat.format(date!!)
        } catch (e: Exception) {
            dateStr
        }
    }
}
