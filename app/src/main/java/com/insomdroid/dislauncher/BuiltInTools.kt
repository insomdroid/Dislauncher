package com.insomdroid.dislauncher

import android.Manifest
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.NoteAdd
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.delay

internal enum class BuiltInTool { NOTES, CALCULATOR, CALENDAR }

private data class LocalNote(
    val id: String,
    val title: String,
    val content: String,
    val updatedAt: Long
)

private data class LocalTask(
    val id: String,
    val title: String,
    val type: String,
    val date: String,
    val time: String,
    val reminderAt: Long,
    val priority: Int,
    val completed: Boolean = false
)

private fun notesStore(context: Context) =
    context.getSharedPreferences("dislauncher_notes", Context.MODE_PRIVATE)

private fun loadNotes(context: Context): List<LocalNote> = runCatching {
    val array = JSONArray(notesStore(context).getString("notes_json", "[]") ?: "[]")
    (0 until array.length()).mapNotNull { i ->
        val o = array.optJSONObject(i) ?: return@mapNotNull null
        LocalNote(o.optString("id"), o.optString("title"), o.optString("content"), o.optLong("updatedAt"))
    }.filter { it.id.isNotBlank() }
}.getOrDefault(emptyList()).sortedByDescending { it.updatedAt }

private fun saveNotes(context: Context, notes: List<LocalNote>) {
    val array = JSONArray()
    notes.forEach { note ->
        array.put(JSONObject().put("id", note.id).put("title", note.title)
            .put("content", note.content).put("updatedAt", note.updatedAt))
    }
    notesStore(context).edit().putString("notes_json", array.toString()).apply()
}

private fun tasksStore(context: Context) =
    context.getSharedPreferences("dislauncher_local_tasks", Context.MODE_PRIVATE)

private fun loadTasks(context: Context): List<LocalTask> = runCatching {
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

private fun saveTasks(context: Context, tasks: List<LocalTask>) {
    val array = JSONArray()
    tasks.forEach { task ->
        array.put(JSONObject().put("id", task.id).put("title", task.title)
            .put("type", task.type).put("date", task.date).put("time", task.time)
            .put("reminderAt", task.reminderAt).put("priority", task.priority)
            .put("completed", task.completed))
    }
    tasksStore(context).edit().putString("tasks_json", array.toString()).apply()
}

private fun priorityLabel(priority: Int): String = when (Locale.getDefault().language) {
    "id" -> when (priority) { 1 -> "Rendah"; 3 -> "Tinggi"; else -> "Sedang" }
    else -> when (priority) { 1 -> "Low"; 3 -> "High"; else -> "Medium" }
}

private fun priorityColor(priority: Int): Color = when (priority) {
    1 -> Color(0xFFEAEAEA)
    2 -> Color(0xFFD5D5D5)
    else -> Color(0xFFBEBEBE)
}

private fun builtInToolScheme(base: androidx.compose.material3.ColorScheme): androidx.compose.material3.ColorScheme = base


@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BuiltInToolScreen(tool: BuiltInTool, onBack: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val baseScheme = MaterialTheme.colorScheme
    val monoScheme = remember(baseScheme) { builtInToolScheme(baseScheme) }
    var notesEditing by remember { mutableStateOf(false) }
    var notesBackSignal by remember { mutableStateOf(0) }
    var visible by remember(tool) { mutableStateOf(false) }
    var closing by remember(tool) { mutableStateOf(false) }

    fun requestClose() {
        if (!closing) {
            closing = true
            visible = false
        }
    }
    LaunchedEffect(tool) { visible = true }
    LaunchedEffect(closing) {
        if (closing) {
            delay(210)
            onBack()
        }
    }
    BackHandler(enabled = (tool != BuiltInTool.NOTES || !notesEditing) && !closing) {
        requestClose()
    }
    val title = when (tool) {
        BuiltInTool.NOTES -> if (notesEditing) "Note" else "Notes"
        BuiltInTool.CALCULATOR -> "Calculator"
        BuiltInTool.CALENDAR -> "Calendar"
    }
    MaterialTheme(colorScheme = monoScheme) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(animationSpec = tween(260)) +
                scaleIn(initialScale = 0.88f, animationSpec = tween(260)),
            exit = fadeOut(animationSpec = tween(220)) +
                scaleOut(targetScale = 0.92f, animationSpec = tween(220))
        ) {
            Scaffold(
                containerColor = MaterialTheme.colorScheme.background,
                topBar = {
                    TopAppBar(
                        title = { Text(title, fontWeight = FontWeight.Medium) },
                        navigationIcon = {
                            IconButton(onClick = {
                                if (tool == BuiltInTool.NOTES && notesEditing) notesBackSignal++
                                else requestClose()
                            }) {
                                Icon(Icons.Outlined.ArrowBack, contentDescription = "Back")
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
                    )
                }
            ) { padding ->
                Box(Modifier.fillMaxSize().padding(padding).padding(horizontal = 18.dp, vertical = 8.dp)) {
                    when (tool) {
                        BuiltInTool.NOTES -> NotesTool(
                            context = context,
                            backSignal = notesBackSignal,
                            onEditingChanged = { notesEditing = it },
                            modifier = Modifier.fillMaxSize()
                        )
                        BuiltInTool.CALCULATOR -> CalculatorTool(Modifier.fillMaxSize())
                        BuiltInTool.CALENDAR -> CalendarTool(context, Modifier.fillMaxSize())
                    }
                }
            }
        }
    }
}

@Composable
private fun NotesTool(
    context: Context,
    backSignal: Int,
    onEditingChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    var notes by remember(context) { mutableStateOf(loadNotes(context)) }
    var selectedId by remember { mutableStateOf<String?>(null) }
    var title by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    var deleteTarget by remember { mutableStateOf<String?>(null) }

    fun saveCurrent() {
        val id = selectedId ?: return
        val current = LocalNote(id, title.trim(), body, System.currentTimeMillis())
        notes = (notes.filterNot { it.id == id } + current).sortedByDescending { it.updatedAt }
        saveNotes(context, notes)
    }
    fun openNote(note: LocalNote) {
        selectedId = note.id
        title = note.title
        body = note.content
        onEditingChanged(true)
    }
    fun closeEditor() {
        saveCurrent()
        selectedId = null
        onEditingChanged(false)
    }

    LaunchedEffect(backSignal) {
        if (backSignal > 0 && selectedId != null) closeEditor()
    }
    BackHandler(enabled = selectedId != null) { closeEditor() }

    Box(modifier = modifier) {
    if (selectedId == null) {
        Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    Text("Your notes", style = MaterialTheme.typography.titleLarge)
                    Text("Saved on this device", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (notes.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No notes yet", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(notes, key = { it.id }) { note ->
                        Surface(
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
                                .clickable { openNote(note) },
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            shape = RoundedCornerShape(18.dp)
                        ) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                Text(note.title.ifBlank { "Untitled note" }, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                                Text(note.content.ifBlank { "No content" }, style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
                                Text(
                                    java.time.Instant.ofEpochMilli(note.updatedAt).atZone(ZoneId.systemDefault())
                                        .format(DateTimeFormatter.ofPattern("d MMM, HH:mm", Locale.getDefault())),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    } else {
        Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it; saveCurrent() },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Title (optional)") },
                singleLine = true,
                textStyle = MaterialTheme.typography.titleLarge
            )
            OutlinedTextField(
                value = body,
                onValueChange = { body = it; saveCurrent() },
                modifier = Modifier.weight(1f).fillMaxWidth(),
                placeholder = { Text("Write something…") },
                minLines = 8,
                maxLines = Int.MAX_VALUE
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { deleteTarget = selectedId }) {
                    Icon(Icons.Outlined.DeleteOutline, contentDescription = null)
                    Spacer(Modifier.size(5.dp))
                    Text("Delete note")
                }
            }
        }
    }
        if (selectedId == null) {
            FloatingActionButton(
                onClick = {
                    val note = LocalNote(UUID.randomUUID().toString(), "", "", System.currentTimeMillis())
                    notes = listOf(note) + notes
                    saveNotes(context, notes)
                    openNote(note)
                },
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 8.dp, bottom = 24.dp),
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            ) { Icon(Icons.Outlined.NoteAdd, contentDescription = "New note") }
    }
    }

    if (deleteTarget != null) {
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete note?") },
            text = { Text("This note will be permanently deleted from this device.") },
            confirmButton = {
                TextButton(onClick = {
                    val id = deleteTarget
                    notes = notes.filterNot { it.id == id }
                    saveNotes(context, notes)
                    deleteTarget = null
                    selectedId = null
                    title = ""
                    body = ""
                    onEditingChanged(false)
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun CalculatorTool(modifier: Modifier = Modifier) {
    var expression by remember { mutableStateOf("") }
    var result by remember { mutableStateOf("") }
    val keyRows = listOf(
        listOf("C", "⌫", "%", "÷"),
        listOf("7", "8", "9", "×"),
        listOf("4", "5", "6", "−"),
        listOf("1", "2", "3", "+"),
        listOf("(", "0", ")", "."),
        listOf("±", "00", "=", " ")
    )
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(
            modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 4.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.Bottom,
            horizontalAlignment = Alignment.End
        ) {
            Text(expression.ifBlank { "0" }, style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.End, maxLines = 2)
            if (result.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(result, style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.primary, textAlign = TextAlign.End, maxLines = 1)
            }
        }
        keyRows.forEach { rowKeys ->
            Row(Modifier.fillMaxWidth().height(58.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                rowKeys.forEach { key ->
                    if (key.isBlank()) {
                        Spacer(Modifier.size(50.dp))
                    } else {
                        val operator = key in listOf("÷", "×", "−", "+", "=")
                        val action: () -> Unit = {
                            when (key) {
                                "C" -> { expression = ""; result = "" }
                                "⌫" -> { expression = expression.dropLast(1); result = "" }
                                "=" -> result = runCatching { formatCalculation(ExpressionParser(expression).parse()) }
                                    .getOrElse { "Invalid expression" }
                                "%" -> {
                                    if (expression.lastOrNull()?.isDigit() == true) expression += "/100"
                                    result = ""
                                }
                                "±" -> {
                                    if (expression.isNotBlank()) expression = "-(" + expression + ")"
                                    result = ""
                                }
                                else -> {
                                    expression += when (key) {
                                        "×" -> "*"
                                        "÷" -> "/"
                                        "−" -> "-"
                                        else -> key
                                    }
                                    result = ""
                                }
                            }
                        }
                        if (operator) {
                            Button(onClick = action, modifier = Modifier.size(50.dp),
                                shape = CircleShape, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                                Text(key, fontSize = if (key.length > 1) 16.sp else 18.sp, lineHeight = 20.sp)
                            }
                        } else {
                            FilledTonalButton(onClick = action, modifier = Modifier.size(50.dp),
                                shape = CircleShape, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                                Text(key, fontSize = if (key.length > 1) 16.sp else 18.sp, lineHeight = 20.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatCalculation(value: Double): String =
    if (!value.isFinite()) "Cannot calculate"
    else if (value == value.toLong().toDouble()) value.toLong().toString()
    else "%.8f".format(Locale.US, value).trimEnd('0').trimEnd('.')

private class ExpressionParser(raw: String) {
    private val input = raw.replace("×", "*").replace("÷", "/").replace("−", "-").replace(" ", "")
    private var index = 0
    fun parse(): Double {
        require(input.isNotBlank())
        val value = expression()
        require(index == input.length)
        require(value.isFinite())
        return value
    }
    private fun expression(): Double {
        var value = term()
        while (index < input.length && (input[index] == '+' || input[index] == '-')) {
            val op = input[index++]
            val rhs = term()
            value = if (op == '+') value + rhs else value - rhs
        }
        return value
    }
    private fun term(): Double {
        var value = primary()
        while (index < input.length && (input[index] == '*' || input[index] == '/')) {
            val op = input[index++]
            val rhs = primary()
            value = if (op == '*') value * rhs else {
                require(rhs != 0.0)
                value / rhs
            }
        }
        return value
    }
    private fun primary(): Double {
        if (index < input.length && (input[index] == '+' || input[index] == '-')) {
            val op = input[index++]
            val value = primary()
            return if (op == '-') -value else value
        }
        if (index < input.length && input[index] == '(') {
            index++
            val value = expression()
            require(index < input.length && input[index] == ')')
            index++
            return value
        }
        val start = index
        var dots = 0
        while (index < input.length && (input[index].isDigit() || input[index] == '.')) {
            if (input[index] == '.') dots++
            require(dots <= 1)
            index++
        }
        require(index > start)
        return input.substring(start, index).toDouble()
    }
}

@Composable
private fun CalendarTool(context: Context, modifier: Modifier = Modifier) {
    var tasks by remember(context) { mutableStateOf(loadTasks(context)) }
    var month by remember { mutableStateOf(YearMonth.now()) }
    var selectedDate by remember { mutableStateOf(LocalDate.now()) }
    var showEditor by remember { mutableStateOf(false) }
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
        if (schedule && task.type == "reminder" && task.reminderAt > System.currentTimeMillis()) {
            scheduleLocalReminder(context, task.id, task.title, task.reminderAt)
        }
        selectedDate = LocalDate.parse(task.date)
        month = YearMonth.from(selectedDate)
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val task = pendingReminder
        pendingReminder = null
        if (task != null) {
            addTask(task, granted)
            if (!granted) Toast.makeText(context, "Reminder saved without a notification. Allow notifications in Android settings to receive reminders.", Toast.LENGTH_LONG).show()
        }
    }

    fun beginNewTask() {
        title = ""
        type = "todo"
        date = selectedDate
        time = LocalTime.of(9, 0)
        priority = 2
        showEditor = true
    }

    val dateFormatter = DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", Locale.getDefault())
    val taskDate = selectedDate.toString()
    val dayTasks = tasks.filter { it.date == taskDate }
        .sortedWith(compareBy<LocalTask> { it.completed }.thenByDescending { it.priority }
            .thenBy { if (it.time.isBlank()) "99:99" else it.time })

    Box(modifier = modifier) {
    Column(modifier = Modifier.fillMaxSize().padding(bottom = 72.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            IconButton(onClick = { month = month.minusMonths(1); selectedDate = month.atDay(1) }) {
                Text("‹", fontSize = 27.sp)
            }
            Text(month.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault())),
                modifier = Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.titleLarge)
            IconButton(onClick = { month = month.plusMonths(1); selectedDate = month.atDay(1) }) {
                Text("›", fontSize = 27.sp)
            }
        }
        Row(Modifier.fillMaxWidth()) {
            listOf("M", "T", "W", "T", "F", "S", "S").forEach { day ->
                Text(day, Modifier.weight(1f), textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
            }
        }
        val firstOffset = month.atDay(1).dayOfWeek.value - 1
        val cells = List(firstOffset) { 0 } + (1..month.lengthOfMonth()).toList()
        cells.chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth()) {
                week.forEach { day ->
                    Box(Modifier.weight(1f).height(37.dp).padding(1.dp), contentAlignment = Alignment.Center) {
                        if (day > 0) {
                            val dateValue = month.atDay(day)
                            val selected = dateValue == selectedDate
                            val hasTasks = tasks.any { it.date == dateValue.toString() }
                            Column(
                                modifier = Modifier.fillMaxSize().clip(CircleShape)
                                    .background(if (selected) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.Transparent)
                                    .clickable { selectedDate = dateValue },
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(day.toString(), color = when {
                                    selected -> MaterialTheme.colorScheme.onPrimary
                                    dateValue == LocalDate.now() -> MaterialTheme.colorScheme.primary
                                    else -> MaterialTheme.colorScheme.onSurface
                                }, style = MaterialTheme.typography.bodyMedium)
                                if (hasTasks && !selected) Box(Modifier.padding(top = 1.dp).size(3.dp)
                                    .clip(CircleShape).background(MaterialTheme.colorScheme.primary))
                            }
                        }
                    }
                }
                repeat(7 - week.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(selectedDate.format(dateFormatter), style = MaterialTheme.typography.titleMedium)
                Text(dayTasks.size.toString() + if (dayTasks.size == 1) " task" else " tasks",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (dayTasks.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("Nothing planned", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(dayTasks, key = { it.id }) { task ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerLow).padding(horizontal = 8.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (task.type == "todo") {
                            Checkbox(checked = task.completed, onCheckedChange = { checked ->
                                tasks = tasks.map { if (it.id == task.id) it.copy(completed = checked) else it }
                                saveTasks(context, tasks)
                            })
                        } else {
                            Icon(Icons.Outlined.Notifications, contentDescription = "Reminder",
                                tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(horizontal = 10.dp))
                        }
                        Column(Modifier.weight(1f).padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(task.title, style = MaterialTheme.typography.bodyLarge,
                                textDecoration = if (task.completed) TextDecoration.LineThrough else TextDecoration.None,
                                color = if (task.completed) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Surface(color = priorityColor(task.priority), shape = RoundedCornerShape(50)) {
                                    Text(priorityLabel(task.priority), modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                        style = MaterialTheme.typography.labelSmall, color = androidx.compose.ui.graphics.Color(0xFF353535))
                                }
                                if (task.time.isNotBlank()) Text(task.time, style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                if (task.type == "reminder") Text("Reminder", style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary)
                            }
                        }
                        IconButton(onClick = { deleteTarget = task }) {
                            Icon(Icons.Outlined.DeleteOutline, contentDescription = "Delete task",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
        FloatingActionButton(
            onClick = ::beginNewTask,
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 8.dp, bottom = 24.dp),
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        ) { Icon(Icons.Outlined.Add, contentDescription = "Add task") }
    }

    if (showEditor) {
        AlertDialog(
            onDismissRequest = { showEditor = false },
            title = { Text("New task") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(value = title, onValueChange = { title = it },
                        modifier = Modifier.fillMaxWidth(), label = { Text("Title") }, singleLine = true)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (type == "todo") Button(onClick = { type = "todo" }, modifier = Modifier.weight(1f)) { Text("To-do") }
                        else FilledTonalButton(onClick = { type = "todo" }, modifier = Modifier.weight(1f)) { Text("To-do") }
                        if (type == "reminder") Button(onClick = { type = "reminder" }, modifier = Modifier.weight(1f)) { Text("Reminder") }
                        else FilledTonalButton(onClick = { type = "reminder" }, modifier = Modifier.weight(1f)) { Text("Reminder") }
                    }
                    TextButton(onClick = {
                        DatePickerDialog(context, { _, y, m, d -> date = LocalDate.of(y, m + 1, d) },
                            date.year, date.monthValue - 1, date.dayOfMonth).show()
                    }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Outlined.Event, contentDescription = null)
                        Spacer(Modifier.size(8.dp))
                        Text(date.format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault())))
                    }
                    if (type == "reminder") {
                        TextButton(onClick = {
                            TimePickerDialog(context, { _, h, m -> time = LocalTime.of(h, m) },
                                time.hour, time.minute, true).show()
                        }, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Outlined.Schedule, contentDescription = null)
                            Spacer(Modifier.size(8.dp))
                            Text(time.format(DateTimeFormatter.ofPattern("HH:mm")))
                        }
                    }
                    Text(if (Locale.getDefault().language == "id") "Prioritas" else "Priority", style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        (1..3).forEach { level ->
                            val label = priorityLabel(level)
                            Surface(
                                modifier = Modifier.weight(1f).clip(RoundedCornerShape(50)).clickable { priority = level },
                                color = if (priority == level) priorityColor(level) else MaterialTheme.colorScheme.surfaceContainer,
                                shape = RoundedCornerShape(50)
                            ) {
                                Text(label, modifier = Modifier.padding(horizontal = 5.dp, vertical = 9.dp),
                                    textAlign = TextAlign.Center, style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (title.isBlank()) {
                        Toast.makeText(context, "Enter a task title", Toast.LENGTH_SHORT).show()
                    } else {
                        val at = if (type == "reminder") date.atTime(time).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli() else 0L
                        if (type == "reminder" && at <= System.currentTimeMillis()) {
                            Toast.makeText(context, "Choose a future time for the reminder", Toast.LENGTH_SHORT).show()
                        } else {
                            val task = LocalTask(UUID.randomUUID().toString(), title.trim(), type,
                                date.toString(), if (type == "reminder") time.format(DateTimeFormatter.ofPattern("HH:mm")) else "",
                                at, priority)
                            showEditor = false
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
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showEditor = false }) { Text("Cancel") } }
        )
    }

    if (deleteTarget != null) {
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete task?") },
            text = { Text("This task will be removed from your local calendar.") },
            confirmButton = {
                TextButton(onClick = {
                    val task = deleteTarget
                    if (task != null) {
                        cancelLocalReminder(context, task.id)
                        tasks = tasks.filterNot { it.id == task.id }
                        saveTasks(context, tasks)
                    }
                    deleteTarget = null
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("Cancel") } }
        )
    }
}
