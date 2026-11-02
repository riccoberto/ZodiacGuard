package com.zodiacguard.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.util.Calendar
import java.util.Locale

class PremiumDaysHelperTest {

    @Test
    fun countsWorkOnEasterSundayAsHolidayAndSundayOnlyOnceInOverlap() {
        val summary = PremiumDaysHelper.calculateMonthlySummary(
            allEntries = listOf(
                workEntry("2026-04-05", 9, "ENTRATA"),
                workEntry("2026-04-05", 12, "USCITA")
            ),
            year = 2026,
            month = 4
        )

        assertEquals(180, summary.holidayMinutes)
        assertEquals(180, summary.sundayMinutes)
        assertEquals(180, summary.holidayAndSundayMinutes)
        assertTrue(summary.specialDays.any {
            it.date == "2026-04-05" && it.labels == listOf("Pasqua", "Domenica")
        })
        assertTrue(summary.specialDays.any {
            it.date == "2026-04-06" && "Lunedì dell'Angelo" in it.labels
        })
    }

    @Test
    fun recognizesModenaPatronSaintAndItalianFixedHoliday() {
        val summary = PremiumDaysHelper.calculateMonthlySummary(
            allEntries = emptyList(),
            year = 2026,
            month = 1
        )

        assertTrue(summary.specialDays.any {
            it.date == "2026-01-01" && "Capodanno" in it.labels
        })
        assertTrue(summary.specialDays.any {
            it.date == "2026-01-31" && "Santo Patrono di Modena" in it.labels
        })
    }

    private fun workEntry(date: String, hour: Int, type: String): WorkEntry {
        val localDate = LocalDate.parse(date)
        val timestamp = Calendar.getInstance(Locale.ROOT).apply {
            clear()
            set(localDate.year, localDate.monthValue - 1, localDate.dayOfMonth, hour, 0)
        }.timeInMillis
        return WorkEntry(timestamp, type, "Servizio", "Modena", date)
    }
}
