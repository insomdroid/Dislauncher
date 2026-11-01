package com.insomdroid.dislauncher

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** Shared, non-throwing parsing for dates and times persisted by the Calendar tool. */
internal fun parseLocalTaskDate(value: String): LocalDate? =
    runCatching { LocalDate.parse(value) }.getOrNull()

internal fun parseLocalTaskTime(value: String): LocalTime? =
    runCatching { LocalTime.parse(value) }.getOrNull()

/** Returns null for malformed legacy data instead of crashing the Calendar or boot receiver. */
internal fun localTaskDateTimeToEpochMillis(date: String, time: String): Long? {
    val parsedDate = parseLocalTaskDate(date) ?: return null
    val parsedTime = parseLocalTaskTime(time) ?: return null
    return runCatching {
        parsedDate.atTime(parsedTime)
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
    }.getOrNull()
}
