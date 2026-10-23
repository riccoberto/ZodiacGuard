package com.zodiacguard.app.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class WeeklyStatsHelperTest {

    @Test
    fun weekOneIncludesDatesAcrossYearBoundaryAndExcludesFollowingWeek() {
        val stats = WeeklyStatsHelper.calculateWeeklyStats(
            allEntries = listOf(
                workEntry("2025-12-29", 8, "ENTRATA"),
                workEntry("2025-12-29", 12, "USCITA"),
                workEntry("2026-01-05", 8, "ENTRATA"),
                workEntry("2026-01-05", 17, "USCITA")
            ),
            productionDays = emptySet(),
            year = 2026,
            weekOfYear = 1,
            weeklyHours = 38,
            weeklyMinutes = 30
        )

        assertEquals("29/12", stats.weekStartDate)
        assertEquals("04/01", stats.weekEndDate)
        assertEquals(240, stats.totalWorkedMinutes)
        assertEquals(2310, stats.totalContractMinutes)
        assertEquals(-2070, stats.balanceMinutes)
    }

    @Test
    fun weeklyChartOverlaysTravelOnBankOrProductionOvertimeWithoutAddingItTwice() {
        val entries = listOf(
            workEntry("2025-12-29", 8, "ENTRATA", "Vignola"),
            workEntry("2025-12-29", 16, "USCITA", "Vignola")
        )
        val nonProduction = WeeklyStatsHelper.calculateWeeklyStats(
            allEntries = entries,
            productionDays = emptySet(),
            year = 2026,
            weekOfYear = 1,
            weeklyHours = 38,
            weeklyMinutes = 30
        )
        val production = WeeklyStatsHelper.calculateWeeklyStats(
            allEntries = entries,
            productionDays = setOf("2025-12-29"),
            year = 2026,
            weekOfYear = 1,
            weeklyHours = 38,
            weeklyMinutes = 30
        )

        assertEquals(570, nonProduction.totalWorkedMinutes)
        assertEquals(90, nonProduction.totalTravelMinutes)
        assertEquals(90, nonProduction.chartDistribution.bankTravelMinutes)
        assertEquals(0, nonProduction.chartDistribution.overtimeTravelMinutes)
        assertEquals(90, production.chartDistribution.overtimeTravelMinutes)
        assertEquals(0, production.chartDistribution.bankTravelMinutes)
        assertEquals(
            production.totalWorkedMinutes,
            production.chartDistribution.totalMinutes
        )
        assertEquals(
            production.totalTravelMinutes,
            production.chartDistribution.totalTravelMinutes
        )
    }

    private fun workEntry(
        date: String,
        hour: Int,
        type: String,
        location: String = "Modena"
    ): WorkEntry {
        val timestamp = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).parse(date)!!
            .let { parsed ->
                Calendar.getInstance().apply {
                    time = parsed
                    set(Calendar.HOUR_OF_DAY, hour)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis
            }
        return WorkEntry(timestamp, type, "Servizio", location, date)
    }
}
