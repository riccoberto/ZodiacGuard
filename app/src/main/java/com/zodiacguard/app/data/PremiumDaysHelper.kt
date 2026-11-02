package com.zodiacguard.app.data

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

object PremiumDaysHelper {

    data class SpecialDay(
        val date: String,
        val labels: List<String>,
        val workedMinutes: Int,
        val isSunday: Boolean,
        val isHoliday: Boolean
    )

    data class MonthlySummary(
        val holidayMinutes: Int,
        val sundayMinutes: Int,
        val holidayAndSundayMinutes: Int,
        val specialDays: List<SpecialDay>
    )

    private val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE
    private val displayDateFormatter = DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.ITALIAN)

    fun calculateMonthlySummary(
        allEntries: List<WorkEntry>,
        year: Int,
        month: Int
    ): MonthlySummary {
        val daysInMonth = LocalDate.of(year, month, 1).lengthOfMonth()
        val monthDates = (1..daysInMonth).map { LocalDate.of(year, month, it) }
        val workMinutesByDate = calculateWorkMinutesByDate(allEntries)

        val specialDays = monthDates.mapNotNull { date ->
            val holiday = holidayLabels(date).orEmpty()
            val isSunday = date.dayOfWeek == DayOfWeek.SUNDAY
            if (holiday.isEmpty() && !isSunday) return@mapNotNull null

            val labels = buildList {
                addAll(holiday)
                if (isSunday) add("Domenica")
            }
            SpecialDay(
                date = date.format(dateFormatter),
                labels = labels,
                workedMinutes = workMinutesByDate[date.format(dateFormatter)] ?: 0,
                isSunday = isSunday,
                isHoliday = holiday.isNotEmpty()
            )
        }

        return MonthlySummary(
            holidayMinutes = specialDays.filter { it.isHoliday }.sumOf { it.workedMinutes },
            sundayMinutes = specialDays.filter { it.isSunday }.sumOf { it.workedMinutes },
            holidayAndSundayMinutes = specialDays
                .filter { it.isHoliday && it.isSunday }
                .sumOf { it.workedMinutes },
            specialDays = specialDays
        )
    }

    fun formatDate(date: String): String =
        LocalDate.parse(date, dateFormatter)
            .format(displayDateFormatter)
            .replaceFirstChar { it.uppercase(Locale.ITALIAN) }

    private fun calculateWorkMinutesByDate(entries: List<WorkEntry>): Map<String, Int> =
        entries.groupBy { it.date }.mapValues { (_, dailyEntries) ->
            var totalMinutes = 0
            var entryTime: Long? = null
            dailyEntries.sortedBy { it.timestamp }.forEach { entry ->
                when (entry.type) {
                    "ENTRATA" -> entryTime = entry.timestamp
                    "USCITA" -> {
                        val start = entryTime
                        if (start != null && entry.timestamp >= start) {
                            totalMinutes += ((entry.timestamp - start) / 60_000L).toInt()
                        }
                        entryTime = null
                    }
                }
            }
            totalMinutes
        }

    private fun holidayLabels(date: LocalDate): List<String>? {
        val fixedHoliday = when (date.monthValue to date.dayOfMonth) {
            1 to 1 -> "Capodanno"
            1 to 6 -> "Epifania"
            1 to 31 -> "Santo Patrono di Modena"
            4 to 25 -> "Festa della Liberazione"
            5 to 1 -> "Festa dei Lavoratori"
            6 to 2 -> "Festa della Repubblica"
            8 to 15 -> "Ferragosto"
            11 to 1 -> "Tutti i Santi"
            12 to 8 -> "Immacolata Concezione"
            12 to 25 -> "Natale"
            12 to 26 -> "Santo Stefano"
            else -> null
        }
        val easter = easterSunday(date.year)
        val movableHoliday = when (date) {
            easter -> "Pasqua"
            easter.plusDays(1) -> "Lunedì dell'Angelo"
            else -> null
        }
        return listOfNotNull(fixedHoliday, movableHoliday).ifEmpty { null }
    }

    private fun easterSunday(year: Int): LocalDate {
        val a = year % 19
        val b = year / 100
        val c = year % 100
        val d = b / 4
        val e = b % 4
        val f = (b + 8) / 25
        val g = (b - f + 1) / 3
        val h = (19 * a + b - d - g + 15) % 30
        val i = c / 4
        val k = c % 4
        val l = (32 + 2 * e + 2 * i - h - k) % 7
        val m = (a + 11 * h + 22 * l) / 451
        val month = (h + l - 7 * m + 114) / 31
        val day = (h + l - 7 * m + 114) % 31 + 1
        return LocalDate.of(year, month, day)
    }
}
