package com.zodiacguard.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

class WorkEntryMergerTest {
    @Test
    fun preservesDistinctEntriesWithTheSameTimestamp() {
        val timestamp = 1_797_000_000_000L
        val incoming = listOf(
            entry(timestamp, "ENTRATA"),
            entry(timestamp, "USCITA")
        )

        val result = WorkEntryMerger.merge(emptyList(), incoming)

        assertEquals(incoming, result.entries)
        assertEquals(2, result.addedCount)
    }

    @Test
    fun preservesRepeatedIdenticalEntriesOnFirstImport() {
        val duplicate = entry(1_797_000_000_000L, "ENTRATA")

        val result = WorkEntryMerger.merge(emptyList(), listOf(duplicate, duplicate))

        assertEquals(listOf(duplicate, duplicate), result.entries)
        assertEquals(2, result.addedCount)
    }

    @Test
    fun repeatedImportDoesNotAddEntriesAgain() {
        val duplicate = entry(1_797_000_000_000L, "ENTRATA")
        val backup = listOf(duplicate, duplicate)
        val firstImport = WorkEntryMerger.merge(emptyList(), backup)

        val secondImport = WorkEntryMerger.merge(firstImport.entries, backup)

        assertEquals(backup, secondImport.entries)
        assertEquals(0, secondImport.addedCount)
    }

    private fun entry(timestamp: Long, type: String) = WorkEntry(
        timestamp = timestamp,
        type = type,
        activity = "Servizio",
        location = "Modena",
        date = "2026-10-06"
    )
}
