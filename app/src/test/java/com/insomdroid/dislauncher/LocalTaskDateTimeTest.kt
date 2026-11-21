package com.insomdroid.dislauncher

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LocalTaskDateTimeTest {
    @Test
    fun parsesIsoDateAndTime() {
        assertEquals(LocalDate.of(2026, 3, 15), parseLocalTaskDate("2026-03-15"))
        assertEquals(LocalTime.of(9, 30), parseLocalTaskTime("09:30"))
    }

    @Test
    fun rejectsMalformedDateAndTime() {
        assertNull(parseLocalTaskDate("2026-02-30"))
        assertNull(parseLocalTaskDate("15/03/2026"))
        assertNull(parseLocalTaskTime("25:00"))
        assertNull(parseLocalTaskTime(""))
    }

    @Test
    fun convertsToEpochMillisInSystemZone() {
        val expected = LocalDate.of(2026, 3, 15).atTime(9, 30)
            .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        assertEquals(expected, localTaskDateTimeToEpochMillis("2026-03-15", "09:30"))
    }

    @Test
    fun returnsNullForLegacyCorruptValuesInsteadOfThrowing() {
        assertNull(localTaskDateTimeToEpochMillis("not-a-date", "09:30"))
        assertNull(localTaskDateTimeToEpochMillis("2026-03-15", "garbage"))
        assertNull(localTaskDateTimeToEpochMillis("", ""))
    }
}
