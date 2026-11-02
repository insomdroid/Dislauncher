package com.insomdroid.dislauncher

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.kizitonwose.calendar.compose.HorizontalCalendar
import com.kizitonwose.calendar.compose.rememberCalendarState
import com.kizitonwose.calendar.core.DayPosition
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.WeekFields
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

internal data class LocalTask(
    val id: String,
    val title: String,
    val type: String,
    val date: String,
    val time: String,
    val reminderAt: Long,
    val priority: Int,
    val completed: Boolean = false
)


/** A single agenda row, sorted all-day first, timed items chronologically, then untimed tasks. */
internal data class CalendarAgendaItem(
    val key: String,
    val group: Int,
    val timestamp: Long,
    val event: DeviceCalendarEvent? = null,
    val task: LocalTask? = null
)


internal fun tasksStore(context: Context) =
    context.getSharedPreferences("dislauncher_local_tasks", Context.MODE_PRIVATE)


internal fun loadTasks(context: Context): List<LocalTask> = runCatching {
    val array = JSONArray(tasksStore(context).getString("tasks_json", "[]") ?: "[]")
    (0 until array.length()).mapNotNull { i ->
        val o = array.optJSONObject(i) ?: return@mapNotNull null
        LocalTask(
            id = o.optString("id"),
            title = o.optString("title"),
            type = o.optString("type", "todo"),
            date = o.optString("date"),
            time = o.optString("time"),
            reminderAt = o.optLong("reminderAt"),
            priority = o.optInt("priority", 2).coerceIn(1, 3),
            completed = o.optBoolean("completed")
        )
    }.filter { it.id.isNotBlank() && it.date.isNotBlank() }
}.getOrDefault(emptyList())


internal fun saveTasks(context: Context, tasks: List<LocalTask>) {
    val store = tasksStore(context)
    val existingRaw = store.getString("tasks_json", "[]") ?: "[]"
    val existingStoreIsSafe = runCatching {
        val existing = JSONArray(existingRaw)
        (0 until existing.length()).all { index ->
            val item = existing.optJSONObject(index)
            item != null && item.optString("id").isNotBlank() && item.optString("date").isNotBlank()
        }
    }.getOrDefault(false)
    if (!existingStoreIsSafe) {
        Toast.makeText(
            context,
            builtInToolText(
                "Calendar data could not be saved because existing data is invalid. Original data was preserved.",
                "Data kalender tidak dapat disimpan karena data lama tidak valid. Data asli tetap dipertahankan."
            ),
            Toast.LENGTH_LONG
        ).show()
        return
    }

    val array = JSONArray()
    tasks.forEach { task ->
        array.put(JSONObject().put("id", task.id).put("title", task.title)
            .put("type", task.type).put("date", task.date).put("time", task.time)
            .put("reminderAt", task.reminderAt).put("priority", task.priority)
            .put("completed", task.completed))
    }
    store.edit().putString("tasks_json", array.toString()).apply()
}


internal fun priorityLabel(priority: Int): String = when (priority) {
    1 -> "Low"
    3 -> "High"
    else -> "Medium"
}


@Composable
internal fun priorityColor(priority: Int): Color = when (priority) {
    1 -> MaterialTheme.colorScheme.surfaceContainerLow
    2 -> MaterialTheme.colorScheme.surfaceContainer
    else -> MaterialTheme.colorScheme.surfaceContainerHigh
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CalendarTool(
    context: Context,
    month: YearMonth,
    onMonthChange: (YearMonth) -> Unit,
    modifier: Modifier = Modifier
) {
    var tasks by remember(context) { mutableStateOf(loadTasks(context)) }
    var calendarPermission by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED) }
    var deviceEvents by remember { mutableStateOf<List<DeviceCalendarEvent>>(emptyList()) }
    val calendarPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        calendarPermission = granted || ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED
    }
    var selectedDate by remember { mutableStateOf(LocalDate.now()) }
    var showWeekView by remember { mutableStateOf(false) }
    var showEditor by remember { mutableStateOf(false) }
    var editingTaskId by remember { mutableStateOf<String?>(null) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var title by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("todo") }
    var date by remember { mutableStateOf(LocalDate.now()) }
    var time by remember { mutableStateOf(LocalTime.of(9, 0)) }
    var priority by remember { mutableStateOf(2) }
    var pendingReminder by remember { mutableStateOf<LocalTask?>(null) }
    var deleteTarget by remember { mutableStateOf<LocalTask?>(null) }

    fun addTask(task: LocalTask, schedule: Boolean) {
        tasks = (tasks.filterNot { it.id == task.id } + task)
        saveTasks(context, tasks)
        // Replacing a reminder must also replace its previously scheduled alarm.
        cancelLocalReminder(context, task.id)
        if (schedule && task.type == "reminder" && task.reminderAt > System.currentTimeMillis()) {
            scheduleLocalReminder(context, task.id, task.title, task.reminderAt)
        }
        selectedDate = parseLocalTaskDate(task.date) ?: selectedDate
        onMonthChange(YearMonth.from(selectedDate))
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val task = pendingReminder
        pendingReminder = null
        if (task != null) {
            addTask(task, granted)
            if (!granted) Toast.makeText(context, builtInToolText("Reminder saved without a notification. Allow notifications in Android settings to receive reminders.", "Pengingat disimpan tanpa notifikasi. Izinkan notifikasi di pengaturan Android untuk menerima pengingat."), Toast.LENGTH_LONG).show()
        }
    }

    fun beginNewTask() {
        editingTaskId = null
        title = ""
        type = "todo"
        date = selectedDate
        time = LocalTime.of(9, 0)
        priority = 2
        showEditor = true
    }

    fun beginEditTask(task: LocalTask) {
        editingTaskId = task.id
        title = task.title
        type = task.type
        date = parseLocalTaskDate(task.date) ?: selectedDate
        time = parseLocalTaskTime(task.time) ?: LocalTime.of(9, 0)
        priority = task.priority
        showEditor = true
    }

    LaunchedEffect(month, calendarPermission) {
        deviceEvents = if (calendarPermission) withContext(Dispatchers.IO) {
            readDeviceCalendarEvents(context, month.atDay(1), month.plusMonths(1).atDay(1))
        } else emptyList()
    }

    val dateFormatter = DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", Locale.getDefault())
    val firstDayOfWeek = WeekFields.of(Locale.getDefault()).firstDayOfWeek
    val taskDate = selectedDate.toString()
    val selectedDayStart = selectedDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    val selectedDayEnd = selectedDate.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    // Show multi-day events on every day they overlap, not only on their start date.
    val dayEvents = deviceEvents.filter { it.begin < selectedDayEnd && it.end > selectedDayStart }
        .sortedWith(compareBy<DeviceCalendarEvent> { if (it.allDay) 0 else 1 }.thenBy { it.begin })
    val dayTasks = tasks.filter { it.date == taskDate }
        .sortedWith(compareBy<LocalTask> { if (it.time.isBlank()) 2 else 1 }
            .thenBy { it.time.ifBlank { "99:99" } }.thenBy { it.completed }.thenByDescending { it.priority })

    // Interleave timed device events and local reminders by clock time. Keep all-day
    // events first and untimed to-dos last, regardless of their stored priority.
    val agendaItems = buildList {
        dayEvents.forEach { event ->
            add(CalendarAgendaItem(
                key = "device-${event.id}-${event.begin}",
                group = if (event.allDay) 0 else 1,
                timestamp = event.begin,
                event = event
            ))
        }
        dayTasks.forEach { task ->
            val timed = task.time.isNotBlank()
            val timestamp = if (timed) {
                localTaskDateTimeToEpochMillis(task.date, task.time) ?: Long.MAX_VALUE
            } else Long.MAX_VALUE
            add(CalendarAgendaItem(
                key = "task-${task.id}",
                group = if (timed) 1 else 2,
                timestamp = timestamp,
                task = task
            ))
        }
    }.sortedWith(compareBy<CalendarAgendaItem> { it.group }.thenBy { it.timestamp }
        .thenBy { it.task?.completed ?: false }.thenByDescending { it.task?.priority ?: 0 })

    Box(modifier = modifier) {
    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(BuiltInToolSpacing.space4)) {
        LaunchedEffect(month) {
            if (YearMonth.from(selectedDate) != month) selectedDate = month.atDay(1)
        }
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    month.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault())),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                TextButton(onClick = { showWeekView = !showWeekView }) {
                    Text(if (showWeekView) builtInToolText("Month", "Bulan") else builtInToolText("Week", "Minggu"))
                }
                TextButton(onClick = { onMonthChange(YearMonth.now()); selectedDate = LocalDate.now() }) {
                    Text(builtInToolText("Today", "Hari ini"))
                }
            }
        val calendarFirstDay = remember { WeekFields.of(Locale.getDefault()).firstDayOfWeek }
        val calendarInitialMonth = remember { month }
        val calendarStartMonth = remember { calendarInitialMonth.minusMonths(120) }
        val calendarEndMonth = remember { calendarInitialMonth.plusMonths(120) }
        val calendarState = rememberCalendarState(
            startMonth = calendarStartMonth,
            endMonth = calendarEndMonth,
            firstVisibleMonth = calendarInitialMonth,
            firstDayOfWeek = calendarFirstDay
        )
        LaunchedEffect(month) {
            if (calendarState.firstVisibleMonth.yearMonth != month) calendarState.scrollToMonth(month)
        }
        LaunchedEffect(calendarState.firstVisibleMonth.yearMonth) {
            val visibleMonth = calendarState.firstVisibleMonth.yearMonth
            if (visibleMonth != month) onMonthChange(visibleMonth)
        }
        if (showWeekView) {
            Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(BuiltInToolDesign.cardCornerRadius),
                color = MaterialTheme.colorScheme.surfaceContainerLow) {
                Row(Modifier.fillMaxWidth().padding(horizontal = BuiltInToolSpacing.space8, vertical = BuiltInToolSpacing.space10),
                    horizontalArrangement = Arrangement.spacedBy(BuiltInToolSpacing.space4)) {
                    val weekStart = selectedDate.minusDays((0..6).first { calendarFirstDay.plus(it.toLong()) == selectedDate.dayOfWeek }.toLong())
                    (0..6).forEach { offset ->
                        val dayDate = weekStart.plusDays(offset.toLong())
                        val selected = dayDate == selectedDate
                        val today = dayDate == LocalDate.now()
                        val dayStart = dayDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                        val dayEnd = dayDate.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                        val hasItems = tasks.any { it.date == dayDate.toString() } ||
                            deviceEvents.any { it.begin < dayEnd && it.end > dayStart }
                        Column(
                            Modifier.weight(1f).clip(RoundedCornerShape(BuiltInToolDesign.controlCornerRadius)).background(when {
                                selected -> MaterialTheme.colorScheme.primary
                                today -> MaterialTheme.colorScheme.secondaryContainer
                                else -> Color.Transparent
                            }).clickable { selectedDate = dayDate; onMonthChange(YearMonth.from(dayDate)) }
                                .padding(vertical = BuiltInToolSpacing.space10),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(BuiltInToolSpacing.space5)
                        ) {
                            Text(dayDate.dayOfWeek.getDisplayName(java.time.format.TextStyle.NARROW, Locale.getDefault()),
                                color = when {
                                    selected -> MaterialTheme.colorScheme.onPrimary
                                    today -> MaterialTheme.colorScheme.onSecondaryContainer
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                }, style = MaterialTheme.typography.labelSmall)
                            Text(dayDate.dayOfMonth.toString(),
                                color = when {
                                    selected -> MaterialTheme.colorScheme.onPrimary
                                    today -> MaterialTheme.colorScheme.onSecondaryContainer
                                    else -> MaterialTheme.colorScheme.onSurface
                                }, style = MaterialTheme.typography.titleMedium,
                                fontWeight = if (selected || today) FontWeight.SemiBold else FontWeight.Normal)
                            Box(Modifier.size(BuiltInToolSpacing.space4).clip(CircleShape).background(if (hasItems) {
                                if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary
                            } else Color.Transparent))
                        }
                    }
                }
            }
        } else {
            Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(BuiltInToolDesign.cardCornerRadius),
            color = MaterialTheme.colorScheme.surfaceContainerLow) {
            Column(Modifier.padding(BuiltInToolSpacing.gridInset)) {
                Row(Modifier.fillMaxWidth()) {
                    (0..6).map { calendarFirstDay.plus(it.toLong()) }.forEach { weekDay ->
                        Text(weekDay.getDisplayName(java.time.format.TextStyle.NARROW, Locale.getDefault()),
                            Modifier.weight(1f).padding(vertical = BuiltInToolSpacing.space4), textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
                    }
                }
                HorizontalCalendar(state = calendarState, dayContent = { day ->
                    val dateValue = day.date
                    if (day.position == DayPosition.MonthDate) {
                        val selected = dateValue == selectedDate
                        val today = dateValue == LocalDate.now()
                        val dayStart = dateValue.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                        val dayEnd = dateValue.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                        val hasTasks = tasks.any { it.date == dateValue.toString() } ||
                            deviceEvents.any { it.begin < dayEnd && it.end > dayStart }
                        Column(modifier = Modifier.fillMaxWidth().height(38.dp).padding(BuiltInToolSpacing.space1)
                            .clip(RoundedCornerShape(BuiltInToolDesign.controlCornerRadius)).background(when {
                                selected -> MaterialTheme.colorScheme.primary
                                today -> MaterialTheme.colorScheme.secondaryContainer
                                else -> Color.Transparent
                            }).clickable { selectedDate = dateValue },
                            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                            Text(dateValue.dayOfMonth.toString(), color = when {
                                selected -> MaterialTheme.colorScheme.onPrimary
                                today -> MaterialTheme.colorScheme.onSecondaryContainer
                                else -> MaterialTheme.colorScheme.onSurface
                            }, style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (selected || today) FontWeight.SemiBold else FontWeight.Normal)
                            Box(Modifier.padding(top = BuiltInToolSpacing.space2).size(BuiltInToolSpacing.space4).clip(CircleShape).background(if (hasTasks) {
                                if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary
                            } else Color.Transparent))
                        }
                    } else Box(Modifier.fillMaxWidth().height(38.dp))
                })
            }
        }
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(BuiltInToolSpacing.space3)) {
                Text(if (selectedDate == LocalDate.now()) builtInToolText("Today", "Hari ini") else selectedDate.format(dateFormatter),
                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                Text(buildString {
                    if (dayEvents.isNotEmpty()) append(dayEvents.size).append(if (dayEvents.size == 1) " event" else " events")
                    if (dayEvents.isNotEmpty() && dayTasks.isNotEmpty()) append(" · ")
                    if (dayTasks.isNotEmpty() || dayEvents.isEmpty()) append(dayTasks.size).append(if (dayTasks.size == 1) " task" else " tasks")
                }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (!calendarPermission) {
            TextButton(onClick = { calendarPermissionLauncher.launch(Manifest.permission.READ_CALENDAR) }) {
                Text(builtInToolText("Allow calendar access to show device events", "Izinkan akses kalender untuk menampilkan acara perangkat"))
            }
        }
        if (dayTasks.isEmpty() && dayEvents.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                BuiltInEmptyState(title = if (!calendarPermission) builtInToolText("Calendar access is off", "Akses kalender nonaktif") else builtInToolText("Nothing scheduled", "Tidak ada jadwal"))
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = 72.dp),
                verticalArrangement = Arrangement.spacedBy(BuiltInToolSpacing.space4)
            ) {
                items(agendaItems, key = { it.key }) { item ->
                    val event = item.event
                    val task = item.task
                    if (event != null) {
                        Row(Modifier.fillMaxWidth().clickable { openDeviceCalendarEvent(context, event) }
                            .padding(horizontal = BuiltInToolSpacing.agendaRowHorizontal, vertical = BuiltInToolSpacing.agendaRowVertical), verticalAlignment = Alignment.CenterVertically) {
                            Text(if (event.allDay) builtInToolText("All day", "Sepanjang hari") else SimpleDateFormat(
                                if (android.text.format.DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm a",
                                Locale.getDefault()).format(Date(event.begin)),
                                modifier = Modifier.width(72.dp), style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary)
                            Box(Modifier.width(BuiltInToolSpacing.space3).height(34.dp).clip(RoundedCornerShape(LauncherShapeTokens.extraSmall))
                                .background(MaterialTheme.colorScheme.tertiary))
                            Text(event.title, modifier = Modifier.weight(1f).padding(start = BuiltInToolSpacing.space12), style = MaterialTheme.typography.bodyLarge,
                                maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
                    } else if (task != null) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = BuiltInToolSpacing.agendaRowHorizontal, vertical = BuiltInToolSpacing.agendaRowVertical),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (task.type == "todo") {
                            Checkbox(checked = task.completed, onCheckedChange = { checked ->
                                tasks = tasks.map { if (it.id == task.id) it.copy(completed = checked) else it }
                                saveTasks(context, tasks)
                            })
                        } else {
                            Icon(painterResource(R.drawable.ic_symbol_notifications), contentDescription = builtInToolText("Reminder", "Pengingat"),
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = BuiltInToolSpacing.space10).size(LauncherIconTokens.compact))
                        }
                        Column(Modifier.weight(1f).padding(start = BuiltInToolSpacing.compact),
                            verticalArrangement = Arrangement.spacedBy(BuiltInToolSpacing.space3)) {
                            Text(task.title, style = MaterialTheme.typography.bodyLarge,
                                textDecoration = if (task.completed) TextDecoration.LineThrough else TextDecoration.None,
                                color = if (task.completed) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
                            Row(horizontalArrangement = Arrangement.spacedBy(BuiltInToolSpacing.space6), verticalAlignment = Alignment.CenterVertically) {
                                Surface(color = priorityColor(task.priority), shape = RoundedCornerShape(LauncherShapeTokens.pill)) {                                    Text(priorityLabel(task.priority), modifier = Modifier.padding(horizontal = BuiltInToolSpacing.space8, vertical = BuiltInToolSpacing.space3),
                                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface)
                                }
                                if (task.time.isNotBlank()) {
                                    Text(task.time, style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                } else {
                                    Text(builtInToolText("No time", "Tanpa waktu"), style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                if (task.type == "reminder") Text(builtInToolText("Reminder", "Pengingat"), style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary)
                            }
                        }
                        IconButton(onClick = { beginEditTask(task) }) {
                            Icon(painterResource(R.drawable.ic_symbol_edit), contentDescription = builtInToolText("Edit task", "Edit tugas"),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(LauncherIconTokens.compact))
                        }
                        IconButton(onClick = { deleteTarget = task }) {
                            Icon(painterResource(R.drawable.ic_symbol_delete), contentDescription = builtInToolText("Delete task", "Hapus tugas"),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(LauncherIconTokens.compact))
                        }
                    }
                }
            }
        }
    }
    }
        FloatingActionButton(
            onClick = ::beginNewTask,
            modifier = Modifier.align(Alignment.BottomEnd).padding(bottom = BuiltInToolDesign.fabBottomPadding).size(BuiltInToolDesign.fabSize),
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        ) { Icon(painterResource(R.drawable.ic_symbol_add), contentDescription = builtInToolText("Add task", "Tambah tugas"), modifier = Modifier.size(LauncherIconTokens.standard)) }
    }

    if (showEditor) {
        AlertDialog(
            onDismissRequest = { showEditor = false },
            title = { Text(if (editingTaskId == null) builtInToolText("New task", "Tugas baru") else builtInToolText("Edit task", "Edit tugas")) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(BuiltInToolSpacing.space10)) {
                    OutlinedTextField(value = title, onValueChange = { title = it },
                        modifier = Modifier.fillMaxWidth(), label = { Text(builtInToolText("Title", "Judul")) }, singleLine = true)
                    Row(horizontalArrangement = Arrangement.spacedBy(BuiltInToolSpacing.space8)) {
                        if (type == "todo") {
                            Button(
                                onClick = { type = "todo" },
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = BuiltInToolSpacing.space8, vertical = BuiltInToolSpacing.space10)
                            ) { Text(builtInToolText("To-do", "Tugas"), maxLines = 1, softWrap = false, style = MaterialTheme.typography.labelLarge) }
                        } else {
                            FilledTonalButton(
                                onClick = { type = "todo" },
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = BuiltInToolSpacing.space8, vertical = BuiltInToolSpacing.space10)
                            ) { Text(builtInToolText("To-do", "Tugas"), maxLines = 1, softWrap = false, style = MaterialTheme.typography.labelLarge) }
                        }
                        if (type == "reminder") {
                            Button(
                                onClick = { type = "reminder" },
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = BuiltInToolSpacing.space8, vertical = BuiltInToolSpacing.space10)
                            ) { Text(builtInToolText("Reminder", "Pengingat"), maxLines = 1, softWrap = false, style = MaterialTheme.typography.labelLarge) }
                        } else {
                            FilledTonalButton(
                                onClick = { type = "reminder" },
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = BuiltInToolSpacing.space8, vertical = BuiltInToolSpacing.space10)
                            ) { Text(builtInToolText("Reminder", "Pengingat"), maxLines = 1, softWrap = false, style = MaterialTheme.typography.labelLarge) }
                        }
                    }
                    TextButton(onClick = { showDatePicker = true }, modifier = Modifier.fillMaxWidth()) {
                        Icon(painterResource(R.drawable.ic_symbol_event), contentDescription = null, modifier = Modifier.size(LauncherIconTokens.compact))
                        Spacer(Modifier.size(BuiltInToolSpacing.space8))
                        Text(date.format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault())))
                    }
                    if (type == "reminder") {
                        TextButton(onClick = { showTimePicker = true }, modifier = Modifier.fillMaxWidth()) {
                            Icon(painterResource(R.drawable.ic_symbol_schedule), contentDescription = null, modifier = Modifier.size(LauncherIconTokens.compact))
                            Spacer(Modifier.size(BuiltInToolSpacing.space8))
                            Text(time.format(DateTimeFormatter.ofPattern("HH:mm")))
                        }
                    }
                    Text(builtInToolText("Priority", "Prioritas"), style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(BuiltInToolSpacing.space6)) {
                        (1..3).forEach { level ->
                            val label = priorityLabel(level)
                            val isSelected = priority == level
                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(LauncherShapeTokens.pill))
                                    .clickable { priority = level },
                                color = if (isSelected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.surfaceContainerLow
                                },
                                contentColor = if (isSelected) {
                                    MaterialTheme.colorScheme.onPrimary
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                                shape = RoundedCornerShape(LauncherShapeTokens.pill)
                            ) {
                                Text(
                                    text = label,
                                    modifier = Modifier.padding(horizontal = BuiltInToolSpacing.space5, vertical = 9.dp),
                                    textAlign = TextAlign.Center,
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (title.isBlank()) {
                        Toast.makeText(context, builtInToolText("Enter a task title", "Masukkan judul tugas"), Toast.LENGTH_SHORT).show()
                    } else {
                        val at = if (type == "reminder") date.atTime(time).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli() else 0L
                        if (type == "reminder" && at <= System.currentTimeMillis()) {
                            Toast.makeText(context, builtInToolText("Choose a future time for the reminder", "Pilih waktu mendatang untuk pengingat"), Toast.LENGTH_SHORT).show()
                        } else {
                            val existing = editingTaskId?.let { id -> tasks.firstOrNull { it.id == id } }
                            val task = LocalTask(
                                id = editingTaskId ?: UUID.randomUUID().toString(),
                                title = title.trim(),
                                type = type,
                                date = date.toString(),
                                time = if (type == "reminder") time.format(DateTimeFormatter.ofPattern("HH:mm")) else "",
                                reminderAt = at,
                                priority = priority,
                                completed = existing?.completed ?: false
                            )
                            showEditor = false
                            editingTaskId = null
                            val permissionMissing = Build.VERSION.SDK_INT >= 33 &&
                                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                            if (type == "reminder" && permissionMissing) {
                                pendingReminder = task
                                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                addTask(task, type == "reminder")
                            }
                        }
                    }
                }) { Text(builtInToolText("Save", "Simpan")) }
            },
            dismissButton = { TextButton(onClick = { showEditor = false; editingTaskId = null }) { Text(builtInToolText("Cancel", "Batal")) } }
        )
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        )
        Dialog(
            onDismissRequest = { showDatePicker = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth().padding(horizontal = BuiltInToolSpacing.space12),
                shape = RoundedCornerShape(LauncherShapeTokens.dialog),
                color = MaterialTheme.colorScheme.surfaceContainerHigh
            ) {
                Column {
                    DatePicker(
                        state = datePickerState,
                        title = null,
                        headline = {
                            Text(
                                builtInToolText("Select date", "Pilih tanggal"),
                                style = MaterialTheme.typography.titleLarge,
                                modifier = Modifier.padding(horizontal = BuiltInToolSpacing.space24, vertical = BuiltInToolSpacing.space12)
                            )
                        }
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(start = BuiltInToolSpacing.space16, end = BuiltInToolSpacing.space16, bottom = BuiltInToolSpacing.space12),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = { showDatePicker = false }) {
                            Text(builtInToolText("Cancel", "Batal"))
                        }
                        TextButton(onClick = {
                            datePickerState.selectedDateMillis?.let { millis ->
                                date = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                            }
                            showDatePicker = false
                        }) {
                            Text(builtInToolText("Select", "Pilih"))
                        }
                    }
                }
            }
        }
    }

    if (showTimePicker) {
        val timePickerState = rememberTimePickerState(
            initialHour = time.hour,
            initialMinute = time.minute,
            is24Hour = true
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            title = { Text(builtInToolText("Select time", "Pilih waktu")) },
            text = { TimePicker(state = timePickerState) },
            confirmButton = {
                TextButton(onClick = {
                    time = LocalTime.of(timePickerState.hour, timePickerState.minute)
                    showTimePicker = false
                }) { Text(builtInToolText("Select", "Pilih")) }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) {
                    Text(builtInToolText("Cancel", "Batal"))
                }
            }
        )
    }

    if (deleteTarget != null) {
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(builtInToolText("Delete task?", "Hapus tugas?")) },
            text = { Text(builtInToolText("This task will be removed from your local calendar.", "Tugas ini akan dihapus dari kalender lokal Anda.")) },
            confirmButton = {
                TextButton(onClick = {
                    val task = deleteTarget
                    if (task != null) {
                        cancelLocalReminder(context, task.id)
                        tasks = tasks.filterNot { it.id == task.id }
                        saveTasks(context, tasks)
                    }
                    deleteTarget = null
                }) { Text(builtInToolText("Delete", "Hapus")) }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text(builtInToolText("Cancel", "Batal")) } }
        )
    }
}
