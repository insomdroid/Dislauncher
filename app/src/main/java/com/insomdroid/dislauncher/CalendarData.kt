package com.insomdroid.dislauncher

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.CalendarContract
import android.Manifest
import android.widget.Toast
import androidx.core.content.ContextCompat
import java.time.LocalDate
import java.time.ZoneId

/** Calendar provider access and event navigation, separate from Calendar's Compose UI. */
internal data class DeviceCalendarEvent(
    val id: Long, val title: String, val begin: Long, val end: Long, val allDay: Boolean
)

internal fun readDeviceCalendarEvents(context: Context, start: LocalDate, endExclusive: LocalDate): List<DeviceCalendarEvent> {
    if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED) return emptyList()
    val zone = ZoneId.systemDefault()
    val startMillis = start.atStartOfDay(zone).toInstant().toEpochMilli()
    val endMillis = endExclusive.atStartOfDay(zone).toInstant().toEpochMilli()
    val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().also {
        ContentUris.appendId(it, startMillis)
        ContentUris.appendId(it, endMillis)
    }.build()
    val result = mutableListOf<DeviceCalendarEvent>()
    try {
        context.contentResolver.query(uri, arrayOf(CalendarContract.Instances.EVENT_ID, CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN, CalendarContract.Instances.END, CalendarContract.Instances.ALL_DAY),
            null, null, "${CalendarContract.Instances.BEGIN} ASC")?.use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow(CalendarContract.Instances.EVENT_ID)
            val titleIndex = cursor.getColumnIndexOrThrow(CalendarContract.Instances.TITLE)
            val beginIndex = cursor.getColumnIndexOrThrow(CalendarContract.Instances.BEGIN)
            val endIndex = cursor.getColumnIndexOrThrow(CalendarContract.Instances.END)
            val allDayIndex = cursor.getColumnIndexOrThrow(CalendarContract.Instances.ALL_DAY)
            while (cursor.moveToNext()) result += DeviceCalendarEvent(
                cursor.getLong(idIndex),
                cursor.getString(titleIndex)?.trim().takeUnless { it.isNullOrEmpty() } ?: "Untitled event",
                cursor.getLong(beginIndex), cursor.getLong(endIndex), cursor.getInt(allDayIndex) != 0
            )
        }
    } catch (_: SecurityException) { return emptyList() }
      catch (_: IllegalArgumentException) { return emptyList() }
    return result.distinctBy { "${it.id}:${it.begin}" }
}

internal fun openDeviceCalendarEvent(context: Context, event: DeviceCalendarEvent) {
    val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, event.id).buildUpon()
        .appendQueryParameter("beginTime", event.begin.toString()).appendQueryParameter("endTime", event.end.toString()).build()
    val intent = Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching {
        if (intent.resolveActivity(context.packageManager) != null) context.startActivity(intent)
        else Toast.makeText(context, "No calendar app available", Toast.LENGTH_SHORT).show()
    }.onFailure { Toast.makeText(context, "Could not open calendar event", Toast.LENGTH_SHORT).show() }
}
