package com.zodiacguard.app.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar

class MonthlyStatsHelperTest {

    @Test
    fun agendaPutsTodayAndUpcomingDaysBeforeMostRecentPastDays() {
        val summaries = listOf(
            dailySummary("2026-10-20"),
            dailySummary("2026-10-24"),
            dailySummary("2026-10-15"),
            dailySummary("2026-10-23"),
            dailySummary("2026-10-22")
        )

        val sorted = MonthlyStatsHelper.sortDailyStatsNearestFirst(
            summaries,
            referenceDate = "2026-10-23"
        )

        assertEquals(
            listOf("2026-10-23", "2026-10-24", "2026-10-22", "2026-10-20", "2026-10-15"),
            sorted.map { it.date }
        )
    }

    @Test
    fun chartDistributionCountsTravelOnlyOnce() {
        val stats = MonthlyStatsHelper.calculateMonthlyStats(
            allEntries = listOf(
                workEntry(8, "ENTRATA", "Vignola"),
                workEntry(16, "USCITA", "Vignola")
            ),
            productionDays = emptySet(),
            year = 2026,
            month = 10,
            weeklyHours = 38,
            workDays = 6
        )

        val distribution = MonthlyStatsHelper.getMonthlyChartDistribution(stats)

        assertEquals(570, stats.totalWorkedMinutes)
        assertEquals(90, stats.totalTravelMinutes)
        assertEquals(385, distribution.ordinaryMinutes)
        assertEquals(185, distribution.bankMinutes)
        assertEquals(
            stats.totalWorkedMinutes,
            distribution.ordinaryMinutes +
                distribution.overtimeMinutes +
                distribution.bankMinutes
        )
    }

    @Test
    fun getDatesInMonthIncludesEveryDayAndLeapDay() {
        assertEquals(28, MonthlyStatsHelper.getDatesInMonth(2025, 2).size)
        assertEquals("2025-02-01", MonthlyStatsHelper.getDatesInMonth(2025, 2).first())
        assertEquals("2025-02-28", MonthlyStatsHelper.getDatesInMonth(2025, 2).last())

        assertEquals(29, MonthlyStatsHelper.getDatesInMonth(2024, 2).size)
        assertEquals("2024-02-29", MonthlyStatsHelper.getDatesInMonth(2024, 2).last())
        assertEquals(30, MonthlyStatsHelper.getDatesInMonth(2026, 4).size)
        assertEquals(31, MonthlyStatsHelper.getDatesInMonth(2026, 10).size)
    }

    @Test
    fun productionDayOvertimeMatchesMonthlyOvertime() {
        val date = "2026-10-05"
        val entries = listOf(
            workEntry(8, "ENTRATA"),
            workEntry(16, "USCITA")
        )

        val stats = MonthlyStatsHelper.calculateMonthlyStats(
            allEntries = entries,
            productionDays = setOf(date),
            year = 2026,
            month = 10,
            weeklyHours = 38,
            workDays = 6
        )

        assertEquals(95, stats.dailyStats.single().overtimeMinutes)
        assertEquals(95, stats.totalOvertimeMinutes)
        assertEquals(0, stats.totalBankMinutes)
    }

    @Test
    fun nonProductionDayExcessIsBankNotDailyOvertime() {
        val entries = listOf(workEntry(8, "ENTRATA"), workEntry(16, "USCITA"))

        val stats = MonthlyStatsHelper.calculateMonthlyStats(
            allEntries = entries,
            productionDays = emptySet(),
            year = 2026,
            month = 10,
            weeklyHours = 38,
            workDays = 6
        )

        assertEquals(0, stats.dailyStats.single().overtimeMinutes)
        assertEquals(0, stats.totalOvertimeMinutes)
        assertEquals(95, stats.totalBankMinutes)
    }

    @Test
    fun weeklyContractMinutesIncludeTheConfiguredMinuteRemainder() {
        val stats = MonthlyStatsHelper.calculateMonthlyStats(
            allEntries = emptyList(),
            productionDays = emptySet(),
            year = 2026,
            month = 10,
            weeklyHours = 38,
            workDays = 6,
            weeklyMinutes = 30
        )

        assertEquals(
            385 * stats.workingDaysInMonth,
            stats.totalContractMinutes
        )
    }

    private fun dailySummary(date: String) = MonthlyStatsHelper.DailySummary(
        date = date,
        totalMinutes = 0,
        travelMinutes = 0,
        isProduction = false,
        location = "",
        activity = "",
        hasMealTicket = false,
        isTeatrino = false
    )

    private fun workEntry(hour: Int, type: String, location: String = "Modena"): WorkEntry {
        val timestamp = Calendar.getInstance().apply {
            set(2026, Calendar.OCTOBER, 5, hour, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        return WorkEntry(timestamp, type, "Servizio", location, "2026-10-05")
    }
}
