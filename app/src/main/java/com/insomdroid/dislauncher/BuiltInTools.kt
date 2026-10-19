package com.insomdroid.dislauncher

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.NoteAdd
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
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
import java.time.LocalTime
import java.time.Instant
import java.time.ZoneOffset
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

internal enum class BuiltInTool { NOTES, CALCULATOR, CALENDAR, UNIT_CONVERTER }

internal const val EXTRA_BUILT_IN_TOOL = "com.insomdroid.dislauncher.BUILT_IN_TOOL"

internal fun launchBuiltInTool(context: Context, tool: BuiltInTool) {
    context.startActivity(
        Intent(context, BuiltInToolActivity::class.java)
            .putExtra(EXTRA_BUILT_IN_TOOL, tool.name)
    )
}

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

private data class CalculatorHistoryEntry(
    val id: String,
    val expression: String,
    val result: String,
    val createdAt: Long
)

private fun calculatorHistoryStore(context: Context) =
    context.getSharedPreferences("dislauncher_calculator_history", Context.MODE_PRIVATE)

private fun loadCalculatorHistory(context: Context): List<CalculatorHistoryEntry> = runCatching {
    val array = JSONArray(calculatorHistoryStore(context).getString("history_json", "[]") ?: "[]")
    (0 until array.length()).mapNotNull { index ->
        val item = array.optJSONObject(index) ?: return@mapNotNull null
        CalculatorHistoryEntry(
            id = item.optString("id"),
            expression = item.optString("expression"),
            result = item.optString("result"),
            createdAt = item.optLong("createdAt")
        ).takeIf { it.id.isNotBlank() && it.expression.isNotBlank() && it.result.isNotBlank() }
    }
}.getOrDefault(emptyList()).sortedByDescending { it.createdAt }.take(100)

private fun saveCalculatorHistory(context: Context, history: List<CalculatorHistoryEntry>) {
    val array = JSONArray()
    history.take(100).forEach { entry ->
        array.put(
            JSONObject()
                .put("id", entry.id)
                .put("expression", entry.expression)
                .put("result", entry.result)
                .put("createdAt", entry.createdAt)
        )
    }
    calculatorHistoryStore(context).edit().putString("history_json", array.toString()).apply()
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

@Composable
private fun priorityColor(priority: Int): Color = when (priority) {
    1 -> MaterialTheme.colorScheme.surfaceContainerLow
    2 -> MaterialTheme.colorScheme.surfaceContainer
    else -> MaterialTheme.colorScheme.surfaceContainerHigh
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BuiltInToolScreen(tool: BuiltInTool, onBack: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var notesEditing by remember { mutableStateOf(false) }
    var notesBackSignal by remember { mutableStateOf(0) }
    var calculatorShowingHistory by remember { mutableStateOf(false) }
    var calculatorHistoryHasItems by remember { mutableStateOf(false) }
    var calculatorClearHistorySignal by remember { mutableStateOf(0) }
    fun requestClose() = onBack()
    BackHandler(enabled = tool != BuiltInTool.NOTES || !notesEditing) {
        if (tool == BuiltInTool.CALCULATOR && calculatorShowingHistory) {
            calculatorShowingHistory = false
        } else {
            requestClose()
        }
    }
    val title = when (tool) {
        BuiltInTool.NOTES -> if (notesEditing) "Note" else "Notes"
        BuiltInTool.CALCULATOR -> if (calculatorShowingHistory) "History" else "Calculator"
        BuiltInTool.CALENDAR -> "Calendar"
        BuiltInTool.UNIT_CONVERTER -> "Unit Converter"
    }
    Scaffold(
                containerColor = MaterialTheme.colorScheme.background,
                topBar = {
                    TopAppBar(
                        title = { Text(title, fontWeight = FontWeight.Medium) },
                        navigationIcon = {
                            IconButton(onClick = {
                                when {
                                    tool == BuiltInTool.NOTES && notesEditing -> notesBackSignal++
                                    tool == BuiltInTool.CALCULATOR && calculatorShowingHistory -> calculatorShowingHistory = false
                                    else -> requestClose()
                                }
                            }) {
                                Icon(Icons.Outlined.ArrowBack, contentDescription = "Back")
                            }
                        },
                        actions = {
                            if (tool == BuiltInTool.CALCULATOR) {
                                if (calculatorShowingHistory) {
                                    IconButton(
                                        onClick = { calculatorClearHistorySignal++ },
                                        enabled = calculatorHistoryHasItems
                                    ) {
                                        Icon(Icons.Outlined.DeleteOutline, contentDescription = "Clear calculator history")
                                    }
                                } else {
                                    IconButton(onClick = { calculatorShowingHistory = true }) {
                                        Icon(Icons.Outlined.History, contentDescription = "Open calculator history")
                                    }
                                }
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
                    )
                }
            ) { padding ->
                Box(
                    Modifier.fillMaxSize().padding(padding)
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    when (tool) {
                        BuiltInTool.NOTES -> NotesTool(
                            context = context,
                            backSignal = notesBackSignal,
                            onEditingChanged = { notesEditing = it },
                            modifier = Modifier.fillMaxSize()
                        )
                        BuiltInTool.CALCULATOR -> CalculatorTool(
                            modifier = Modifier.fillMaxSize(),
                            showHistory = calculatorShowingHistory,
                            onShowHistoryChange = { calculatorShowingHistory = it },
                            clearHistorySignal = calculatorClearHistorySignal,
                            onHistoryAvailabilityChanged = { calculatorHistoryHasItems = it }
                        )
                        BuiltInTool.CALENDAR -> CalendarTool(context, Modifier.fillMaxSize())
                        BuiltInTool.UNIT_CONVERTER -> UnitConverterTool(Modifier.fillMaxSize())
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
        if (title.isBlank() && body.isBlank()) {
            notes = notes.filterNot { it.id == id }
            saveNotes(context, notes)
            return
        }
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
            if (notes.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No notes yet", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(notes, key = { it.id }) { note ->
                        Surface(
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
                                .clickable { openNote(note) },
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            shape = RoundedCornerShape(20.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(
                                    Modifier.weight(1f).padding(end = 8.dp),
                                    verticalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
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
                                IconButton(onClick = { deleteTarget = note.id }) {
                                    Icon(
                                        Icons.Outlined.DeleteOutline,
                                        contentDescription = "Delete note",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
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
                    selectedId = UUID.randomUUID().toString()
                    title = ""
                    body = ""
                    onEditingChanged(true)
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
private fun CalculatorTool(
    modifier: Modifier = Modifier,
    showHistory: Boolean,
    onShowHistoryChange: (Boolean) -> Unit,
    clearHistorySignal: Int,
    onHistoryAvailabilityChanged: (Boolean) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var expression by remember { mutableStateOf("") }
    var result by remember { mutableStateOf("") }
    var showClearHistoryDialog by remember { mutableStateOf(false) }
    var history by remember(context) { mutableStateOf(loadCalculatorHistory(context)) }

    LaunchedEffect(history) {
        onHistoryAvailabilityChanged(history.isNotEmpty())
    }
    LaunchedEffect(clearHistorySignal) {
        if (clearHistorySignal > 0) showClearHistoryDialog = true
    }
    val keyRows = listOf(
        listOf("C", "⌫", "%", "÷"),
        listOf("7", "8", "9", "×"),
        listOf("4", "5", "6", "−"),
        listOf("1", "2", "3", "+"),
        listOf("(", "0", ")", "."),
        listOf("±", "00", "=", " ")
    )

    BoxWithConstraints(modifier = modifier) {
        val keySize = minOf(72.dp, (maxWidth - 24.dp) / 4, (maxHeight - 190.dp) / 6)
            .coerceAtLeast(36.dp)

        if (showHistory) {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (history.isEmpty()) {
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text("No calculations yet", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    LazyColumn(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(history, key = { it.id }) { entry ->
                            Surface(
                                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable {
                                    expression = entry.expression
                                    result = entry.result
                                    onShowHistoryChange(false)
                                },
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Column(
                                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(entry.expression, style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
                                    Text(entry.result, style = MaterialTheme.typography.titleLarge,
                                        color = MaterialTheme.colorScheme.onSurface)
                                    Text(
                                        java.time.Instant.ofEpochMilli(entry.createdAt).atZone(ZoneId.systemDefault())
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
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Column(
                    Modifier.weight(1f).fillMaxWidth().padding(horizontal = 2.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.Bottom,
                    horizontalAlignment = Alignment.End
                ) {
                    if (expression.isNotBlank()) {
                        Text(expression, Modifier.fillMaxWidth(), style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.End, maxLines = 2)
                        Spacer(Modifier.height(8.dp))
                    }
                    Text(
                        result.ifBlank { expression.ifBlank { "0" } },
                        Modifier.fillMaxWidth(),
                        style = MaterialTheme.typography.displayMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.End,
                        maxLines = 2
                    )
                }
                keyRows.forEach { rowKeys ->
                    Row(
                        Modifier.fillMaxWidth().height(keySize),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        rowKeys.forEach { key ->
                            if (key.isBlank()) {
                                Spacer(Modifier.size(keySize))
                            } else {
                                val operator = key in listOf("÷", "×", "−", "+", "=")
                                val action: () -> Unit = {
                                    when (key) {
                                        "C" -> { expression = ""; result = "" }
                                        "⌫" -> { expression = expression.dropLast(1); result = "" }
                                        "=" -> {
                                            val calculated = runCatching {
                                                formatCalculation(ExpressionParser(expression).parse())
                                            }.getOrNull()
                                            if (calculated == null) {
                                                result = "Invalid expression"
                                            } else {
                                                result = calculated
                                                val entry = CalculatorHistoryEntry(
                                                    UUID.randomUUID().toString(), expression, calculated,
                                                    System.currentTimeMillis()
                                                )
                                                history = (listOf(entry) + history).take(100)
                                                saveCalculatorHistory(context, history)
                                            }
                                        }
                                        "%" -> {
                                            if (expression.lastOrNull()?.let { it.isDigit() || it == ')' } == true) {
                                                expression = applyCalculatorPercentage(expression)
                                            }
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
                                val labelSize = (keySize.value / 2.35f).coerceIn(18f, 28f).sp
                                if (operator) {
                                    Button(onClick = action, modifier = Modifier.size(keySize), shape = CircleShape,
                                        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                                        Text(key, fontSize = labelSize, lineHeight = labelSize)
                                    }
                                } else {
                                    FilledTonalButton(onClick = action, modifier = Modifier.size(keySize), shape = CircleShape,
                                        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                                        Text(key, fontSize = labelSize, lineHeight = labelSize)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showClearHistoryDialog) {
        AlertDialog(
            onDismissRequest = { showClearHistoryDialog = false },
            title = { Text("Clear calculator history?") },
            text = { Text("Saved calculations will be removed from this device.") },
            confirmButton = {
                TextButton(onClick = {
                    history = emptyList()
                    saveCalculatorHistory(context, history)
                    showClearHistoryDialog = false
                }) { Text("Clear") }
            },
            dismissButton = { TextButton(onClick = { showClearHistoryDialog = false }) { Text("Cancel") } }
        )
    }
}

private fun applyCalculatorPercentage(expression: String): String {
    var depth = 0
    var lastOperator = -1
    for (index in expression.indices) {
        when (expression[index]) {
            '(' -> depth++
            ')' -> depth--
            '+', '-', '*', '/' -> {
                val previous = expression.getOrNull(index - 1)
                if (
                    depth == 0 && index > 0 &&
                    previous != null && previous !in charArrayOf('+', '-', '*', '/', '(')
                ) {
                    lastOperator = index
                }
            }
        }
    }

    if (lastOperator <= 0) return "($expression)/100"
    val left = expression.substring(0, lastOperator)
    val operator = expression[lastOperator]
    val right = expression.substring(lastOperator + 1)
    return when (operator) {
        '+', '-' -> "$left$operator(($left)*($right)/100)"
        '*' -> "$left*(($right)/100)"
        '/' -> "$left/(($right)/100)"
        else -> "($expression)/100"
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CalendarTool(context: Context, modifier: Modifier = Modifier) {
    var tasks by remember(context) { mutableStateOf(loadTasks(context)) }
    var month by remember { mutableStateOf(YearMonth.now()) }
    var selectedDate by remember { mutableStateOf(LocalDate.now()) }
    var showEditor by remember { mutableStateOf(false) }
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
    Column(modifier = Modifier.fillMaxSize().padding(bottom = 72.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
                Text(day, Modifier.weight(1f).padding(vertical = 4.dp), textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
            }
        }
        val firstOffset = month.atDay(1).dayOfWeek.value - 1
        val cells = List(firstOffset) { 0 } + (1..month.lengthOfMonth()).toList()
        cells.chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth()) {
                week.forEach { day ->
                    Box(Modifier.weight(1f).height(41.dp).padding(1.dp), contentAlignment = Alignment.Center) {
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
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(selectedDate.format(dateFormatter), style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium)
                Text(dayTasks.size.toString() + if (dayTasks.size == 1) " task" else " tasks",
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerLow).padding(horizontal = 10.dp, vertical = 8.dp),
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
                                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface)
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
                    TextButton(onClick = { showDatePicker = true }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Outlined.Event, contentDescription = null)
                        Spacer(Modifier.size(8.dp))
                        Text(date.format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault())))
                    }
                    if (type == "reminder") {
                        TextButton(onClick = { showTimePicker = true }, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Outlined.Schedule, contentDescription = null)
                            Spacer(Modifier.size(8.dp))
                            Text(time.format(DateTimeFormatter.ofPattern("HH:mm")))
                        }
                    }
                    Text(if (Locale.getDefault().language == "id") "Prioritas" else "Priority", style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        (1..3).forEach { level ->
                            val label = priorityLabel(level)
                            val isSelected = priority == level
                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(50))
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
                                shape = RoundedCornerShape(50)
                            ) {
                                Text(
                                    text = if (isSelected) "✓ $label" else label,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 9.dp),
                                    textAlign = TextAlign.Center,
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
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

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        )
        AlertDialog(
            onDismissRequest = { showDatePicker = false },
            title = { Text(if (Locale.getDefault().language == "id") "Pilih tanggal" else "Select date") },
            text = { DatePicker(state = datePickerState) },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        date = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                    }
                    showDatePicker = false
                }) { Text(if (Locale.getDefault().language == "id") "Pilih" else "Select") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text(if (Locale.getDefault().language == "id") "Batal" else "Cancel")
                }
            }
        )
    }

    if (showTimePicker) {
        val timePickerState = rememberTimePickerState(
            initialHour = time.hour,
            initialMinute = time.minute,
            is24Hour = true
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            title = { Text(if (Locale.getDefault().language == "id") "Pilih waktu" else "Select time") },
            text = { TimePicker(state = timePickerState) },
            confirmButton = {
                TextButton(onClick = {
                    time = LocalTime.of(timePickerState.hour, timePickerState.minute)
                    showTimePicker = false
                }) { Text(if (Locale.getDefault().language == "id") "Pilih" else "Select") }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) {
                    Text(if (Locale.getDefault().language == "id") "Batal" else "Cancel")
                }
            }
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
