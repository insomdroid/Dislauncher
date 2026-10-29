package com.insomdroid.dislauncher

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.CalendarContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

private data class CalendarAgendaEvent(
    val id: Long,
    val title: String,
    val begin: Long,
    val end: Long,
    val allDay: Boolean,
    val meetingUrl: String?
)

@Composable
internal fun CalendarAgenda(timeFormat: String, compact: Boolean = false, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val primary = MaterialTheme.colorScheme.onBackground
    val secondary = MaterialTheme.colorScheme.onSurfaceVariant
    var hasPermission by remember { mutableStateOf(hasCalendarReadPermission(context)) }
    var refreshKey by remember { mutableStateOf(0) }
    var events by remember { mutableStateOf<List<CalendarAgendaEvent>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(false) }
    var pendingMeetingUrl by remember { mutableStateOf<String?>(null) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted || hasCalendarReadPermission(context)
        refreshKey++
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refreshKey++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(hasPermission, refreshKey) {
        if (!hasPermission) {
            events = emptyList()
            loading = false
        } else {
            loading = true
            events = withContext(Dispatchers.IO) { readUpcomingCalendarEvents(context) }
            loading = false
        }
    }
    if (compact) {
        val label = when {
            !hasPermission -> "Allow Calendar"
            loading -> "Calendar…"
            events.isEmpty() -> "No Event"
            else -> events.first().title
        }
        Text(
            label,
            color = if (events.isEmpty() || !hasPermission || loading) secondary else primary,
            fontSize = 12.sp,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
            modifier = modifier.clickable {
                if (!hasPermission) permissionLauncher.launch(Manifest.permission.READ_CALENDAR)
                else if (events.isEmpty()) {
                    openCalendar(context)
                    refreshKey++
                } else openCalendarEvent(context, events.first())
            }
        )
    } else {
        Column(
            modifier = modifier.widthIn(max = 150.dp).padding(top = 3.dp, bottom = 3.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            when {
                !hasPermission -> {
                    Text(
                        "ALLOW CALENDAR ACCESS",
                        color = primary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.clickable {
                            permissionLauncher.launch(Manifest.permission.READ_CALENDAR)
                        }.padding(vertical = 5.dp)
                    )
                }
                loading -> Text("Loading calendar…", color = secondary, fontSize = 11.sp)
                events.isEmpty() -> {
                    Text(
                        "No upcoming events",
                        color = secondary,
                        fontSize = 11.sp,
                        modifier = Modifier.clickable {
                            openCalendar(context)
                            refreshKey++
                        }.padding(vertical = 3.dp)
                    )
                }
                else -> {
                    val visibleEvents = if (expanded) events.take(5) else events.take(1)
                    visibleEvents.forEachIndexed { index, event ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                eventTime(context, event, timeFormat),
                                color = secondary,
                                fontSize = 10.sp,
                                maxLines = 1,
                                modifier = Modifier.width(72.dp).clickable { openCalendarEvent(context, event) }
                            )
                            Text(
                                event.title,
                                color = primary,
                                fontSize = 12.sp,
                                fontWeight = if (index == 0) FontWeight.Medium else FontWeight.Normal,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f).clickable { openCalendarEvent(context, event) }
                            )
                            if (!event.meetingUrl.isNullOrBlank()) {
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    "↗",
                                    color = secondary,
                                    fontSize = 13.sp,
                                    modifier = Modifier.clickable { pendingMeetingUrl = validateCalendarMeetingUrl(event.meetingUrl) }
                                        .padding(horizontal = 2.dp)
                                )
                            }
                        }
                    }
                    if (events.size > 1) {
                        Text(
                            if (expanded) "SHOW LESS" else "SHOW ${(events.size - 1).coerceAtMost(4)} MORE",
                            color = secondary,
                            fontSize = 9.sp,
                            letterSpacing = 0.5.sp,
                            modifier = Modifier.clickable { expanded = !expanded }.padding(vertical = 3.dp)
                        )
                    }
                }
            }
        }
    }
    pendingMeetingUrl?.let { url ->
        val host = runCatching { java.net.URI(url).host }.getOrNull().orEmpty()
        AlertDialog(
            onDismissRequest = { pendingMeetingUrl = null },
            title = { Text("Open meeting link?") },
            text = { Text("This event links to $host. Continue only if you trust this destination.") },
            confirmButton = {
                TextButton(onClick = {
                    pendingMeetingUrl = null
                    openMeetingUrl(context, url)
                }) { Text("Open link") }
            },
            dismissButton = {
                TextButton(onClick = { pendingMeetingUrl = null }) { Text("Cancel") }
            }
        )
    }
}

internal fun validateCalendarMeetingUrl(raw: String?): String? {
    if (raw.isNullOrBlank()) return null
    val parsed = runCatching { java.net.URI(raw.trim()) }.getOrNull() ?: return null
    if (!parsed.scheme.equals("https", ignoreCase = true)) return null
    if (parsed.host.isNullOrBlank() || parsed.rawUserInfo != null) return null
    return parsed.toASCIIString()
}

private fun hasCalendarReadPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) ==
        PackageManager.PERMISSION_GRANTED

private fun readUpcomingCalendarEvents(context: Context): List<CalendarAgendaEvent> {
    if (!hasCalendarReadPermission(context)) return emptyList()
    val calendar = java.util.Calendar.getInstance(TimeZone.getDefault())
    calendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
    calendar.set(java.util.Calendar.MINUTE, 0)
    calendar.set(java.util.Calendar.SECOND, 0)
    calendar.set(java.util.Calendar.MILLISECOND, 0)
    val startMillis = calendar.timeInMillis
    calendar.add(java.util.Calendar.DAY_OF_YEAR, 7)
    val endMillis = calendar.timeInMillis
    val uriBuilder = CalendarContract.Instances.CONTENT_URI.buildUpon()
    ContentUris.appendId(uriBuilder, startMillis)
    ContentUris.appendId(uriBuilder, endMillis)
    val events = mutableListOf<CalendarAgendaEvent>()
    val projection = arrayOf(
        CalendarContract.Instances.EVENT_ID,
        CalendarContract.Instances.TITLE,
        CalendarContract.Instances.BEGIN,
        CalendarContract.Instances.END,
        CalendarContract.Instances.ALL_DAY,
        CalendarContract.Events.DESCRIPTION
    )
    try {
        context.contentResolver.query(
            uriBuilder.build(), projection, null, null,
            "${CalendarContract.Instances.BEGIN} ASC"
        )?.use { cursor ->
            val eventIdIndex = cursor.getColumnIndexOrThrow(CalendarContract.Instances.EVENT_ID)
            val titleIndex = cursor.getColumnIndexOrThrow(CalendarContract.Instances.TITLE)
            val beginIndex = cursor.getColumnIndexOrThrow(CalendarContract.Instances.BEGIN)
            val endIndex = cursor.getColumnIndexOrThrow(CalendarContract.Instances.END)
            val allDayIndex = cursor.getColumnIndexOrThrow(CalendarContract.Instances.ALL_DAY)
            val descriptionIndex = cursor.getColumnIndex(CalendarContract.Events.DESCRIPTION)
            val now = System.currentTimeMillis()
            while (cursor.moveToNext()) {
                val id = cursor.getLong(eventIdIndex)
                val end = cursor.getLong(endIndex)
                if (end < now) continue
                val description = if (descriptionIndex >= 0) cursor.getString(descriptionIndex) else null
                val candidateUrl = Regex("https?://[^\\s<>\\\"']+", RegexOption.IGNORE_CASE)
                    .find(description.orEmpty())?.value?.trimEnd('.', ',', ')', ']', ';')
                val url = validateCalendarMeetingUrl(candidateUrl)
                events += CalendarAgendaEvent(
                    id = id,
                    title = cursor.getString(titleIndex)?.trim().takeUnless { it.isNullOrEmpty() } ?: "Untitled event",
                    begin = cursor.getLong(beginIndex),
                    end = end,
                    allDay = cursor.getInt(allDayIndex) != 0,
                    meetingUrl = url
                )
            }
        }
    } catch (_: SecurityException) {
        return emptyList()
    } catch (_: IllegalArgumentException) {
        return emptyList()
    }
    return events.distinctBy { "${it.id}:${it.begin}" }.sortedBy { it.begin }.take(30)
}

private fun eventTime(context: Context, event: CalendarAgendaEvent, timeFormat: String): String {
    if (event.allDay) return "ALL DAY"
    val pattern = when (timeFormat) {
        "12" -> "h:mm a"
        "24" -> "HH:mm"
        else -> if (android.text.format.DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm a"
    }
    val formatter = SimpleDateFormat(pattern, Locale.getDefault())
    formatter.timeZone = TimeZone.getDefault()
    return formatter.format(Date(event.begin))
}

private fun openCalendarEvent(context: Context, event: CalendarAgendaEvent) {
    val eventUri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, event.id)
        .buildUpon()
        .appendQueryParameter("beginTime", event.begin.toString())
        .appendQueryParameter("endTime", event.end.toString())
        .build()
    val intent = Intent(Intent.ACTION_VIEW, eventUri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching {
        if (intent.resolveActivity(context.packageManager) != null) context.startActivity(intent)
        else openCalendar(context)
    }.onFailure { openCalendar(context) }
}

private fun openMeetingUrl(context: Context, url: String) {
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}
