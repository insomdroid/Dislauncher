package com.insomdroid.dislauncher

import android.Manifest
import android.content.Context
import android.content.ContentUris
import android.provider.CalendarContract
import android.content.Intent
import android.net.Uri
import android.widget.ImageView
import android.widget.TextView
import android.content.pm.PackageManager
import android.os.Build
import android.util.TypedValue
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.text.ClickableText
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ArrowForward
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FileOpen
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.NoteAdd
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Title
import androidx.compose.material.icons.outlined.FormatBold
import androidx.compose.material.icons.outlined.FormatItalic
import androidx.compose.material.icons.outlined.FormatStrikethrough
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.FormatListNumbered
import androidx.compose.material.icons.outlined.FormatQuote
import androidx.compose.material.icons.outlined.HorizontalRule
import androidx.compose.material.icons.outlined.FormatListBulleted
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.InsertLink
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import io.noties.markwon.Markwon
import io.noties.markwon.image.ImagesPlugin
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.ext.tasklist.TaskListPlugin
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.Instant
import java.time.ZoneOffset
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.WeekFields
import java.util.Locale
import java.util.Date
import java.text.SimpleDateFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
    val updatedAt: Long,
    val pinned: Boolean = false
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
        LocalNote(o.optString("id"), o.optString("title"), o.optString("content"), o.optLong("updatedAt"), o.optBoolean("pinned", false))
    }.filter { it.id.isNotBlank() }
}.getOrDefault(emptyList()).sortedWith(compareByDescending<LocalNote> { it.pinned }.thenByDescending { it.updatedAt })

private fun saveNotes(context: Context, notes: List<LocalNote>) {
    val array = JSONArray()
    notes.forEach { note ->
        array.put(JSONObject().put("id", note.id).put("title", note.title)
            .put("content", note.content).put("updatedAt", note.updatedAt).put("pinned", note.pinned))
    }
    notesStore(context).edit().putString("notes_json", array.toString()).apply()
}

/** Release only image grants referenced by the deleted note and by no remaining note. */
private fun releaseUnusedNoteImagePermissions(
    context: Context,
    removedNote: LocalNote,
    remainingNotes: List<LocalNote>
) {
    runCatching {
        context.contentResolver.persistedUriPermissions
            .filter { permission ->
                permission.isReadPermission &&
                    removedNote.content.contains(permission.uri.toString()) &&
                    remainingNotes.none { note -> note.content.contains(permission.uri.toString()) }
            }
            .forEach { permission ->
                runCatching {
                    context.contentResolver.releasePersistableUriPermission(
                        permission.uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                }
            }
    }
}

private const val MAX_MARKDOWN_IMPORT_BYTES = 1_048_576

/** Read user-selected Markdown with a byte limit before decoding it into a String. */
private fun readMarkdownBounded(input: java.io.InputStream, maxBytes: Int): String {
    input.use { stream ->
        val result = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val count = stream.read(buffer)
            if (count < 0) break
            if (result.size() + count > maxBytes) {
                error("Markdown file exceeds the 1 MiB import limit")
            }
            result.write(buffer, 0, count)
        }
        return result.toByteArray().toString(Charsets.UTF_8)
    }
}

/** Remote image embeds are hidden in preview to avoid contacting third-party hosts from a note. */
private fun markdownForSafePreview(markdown: String): String =
    Regex("!\\[([^]]*)]\\(<?https?://[^)\\s>]+>?\\)", RegexOption.IGNORE_CASE)
        .replace(markdown) { match ->
            val description = match.groupValues[1].ifBlank { "External image" }
            "[$description — external image hidden for privacy]"
        }

/** Match the Markdown preview's Android TextView to Dislauncher's selected font family. */
private fun markdownPreviewTypeface(context: Context): android.graphics.Typeface {
    val preferences = LauncherPreferences.load(context)
    return when (preferences.fontMode) {
        "dislauncher" -> androidx.core.content.ContextCompat.getFont(context, R.font.inter_variable)
            ?: android.graphics.Typeface.DEFAULT
        "custom" -> listOf("ttf", "otf")
            .map { java.io.File(context.filesDir, "fonts/custom-font.$it") }
            .firstOrNull { it.isFile && it.length() in 12L..MAX_CUSTOM_FONT_BYTES.toLong() }
            ?.let { file -> runCatching { android.graphics.Typeface.createFromFile(file) }.getOrNull() }
            ?: android.graphics.Typeface.DEFAULT
        else -> android.graphics.Typeface.DEFAULT
    }
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
        BuiltInTool.NOTES -> "Notes"
        BuiltInTool.CALCULATOR -> if (calculatorShowingHistory) "History" else "Calculator"
        BuiltInTool.CALENDAR -> "Calendar"
        BuiltInTool.UNIT_CONVERTER -> "Unit Converter"
    }
    Scaffold(
                containerColor = MaterialTheme.colorScheme.background,
                topBar = {
                    if (tool != BuiltInTool.CALENDAR && !(tool == BuiltInTool.NOTES && notesEditing)) TopAppBar(
                        title = { Text(title, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        navigationIcon = {
                            IconButton(onClick = {
                                when {
                                    tool == BuiltInTool.NOTES && notesEditing -> notesBackSignal++
                                    tool == BuiltInTool.CALCULATOR && calculatorShowingHistory -> calculatorShowingHistory = false
                                    else -> requestClose()
                                }
                            }) {
                                Icon(Icons.Outlined.ArrowBack, contentDescription = builtInToolText("Back", "Kembali"))
                            }
                        },
                        actions = {
                            if (tool == BuiltInTool.CALCULATOR) {
                                if (calculatorShowingHistory) {
                                    IconButton(
                                        onClick = { calculatorClearHistorySignal++ },
                                        enabled = calculatorHistoryHasItems
                                    ) {
                                        Icon(Icons.Outlined.DeleteOutline, contentDescription = builtInToolText("Clear calculator history", "Hapus riwayat kalkulator"))
                                    }
                                } else {
                                    IconButton(onClick = { calculatorShowingHistory = true }) {
                                        Icon(Icons.Outlined.History, contentDescription = builtInToolText("Open calculator history", "Buka riwayat kalkulator"))
                                    }
                                }
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
                    )
                }
            ) { padding ->
                BoxWithConstraints(Modifier.fillMaxSize()) {
                    Box(
                        Modifier
                            .widthIn(max = 840.dp)
                            .fillMaxSize()
                            .align(Alignment.TopCenter)
                            .padding(padding)
                            .padding(horizontal = if (maxWidth >= 600.dp) 24.dp else 16.dp, vertical = 8.dp)
                    ) {
                    when (tool) {
                        BuiltInTool.NOTES -> NotesTool(
                            context = context,
                            backSignal = notesBackSignal,
                            onEditingChanged = { notesEditing = it },
                            onBackRequested = { notesBackSignal++ },
                            modifier = Modifier.fillMaxSize()
                        )
                        BuiltInTool.CALCULATOR -> CalculatorTool(
                            modifier = Modifier.fillMaxSize(),
                            showHistory = calculatorShowingHistory,
                            onShowHistoryChange = { calculatorShowingHistory = it },
                            clearHistorySignal = calculatorClearHistorySignal,
                            onHistoryAvailabilityChanged = { calculatorHistoryHasItems = it }
                        )
                        BuiltInTool.CALENDAR -> CalendarTool(context, onBackRequested = onBack, modifier = Modifier.fillMaxSize())
                        BuiltInTool.UNIT_CONVERTER -> UnitConverterTool(Modifier.fillMaxSize())
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
    onBackRequested: () -> Unit,
    modifier: Modifier = Modifier
) {
    var notes by remember(context) { mutableStateOf(loadNotes(context)) }
    var selectedId by remember { mutableStateOf<String?>(null) }
    var title by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    var bodyField by remember { mutableStateOf(TextFieldValue("")) }
    var searchQuery by remember { mutableStateOf("") }
    var previewMode by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<String?>(null) }
    var noteMenuTarget by remember { mutableStateOf<String?>(null) }
    var exportContent by remember { mutableStateOf("") }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) runCatching {
            val input = context.contentResolver.openInputStream(uri)
                ?: error("Unable to read this file")
            val imported = readMarkdownBounded(input, MAX_MARKDOWN_IMPORT_BYTES)
            val firstLine = imported.lineSequence().firstOrNull().orEmpty()
            val importedTitle = if (firstLine.startsWith("# ")) firstLine.removePrefix("# ").trim() else ""
            val importedBody = if (importedTitle.isNotEmpty()) imported.substringAfter('\n', "") else imported
            val note = LocalNote(UUID.randomUUID().toString(), importedTitle, importedBody, System.currentTimeMillis())
            notes = (notes + note).sortedWith(compareByDescending<LocalNote> { it.pinned }.thenByDescending { it.updatedAt })
            saveNotes(context, notes)
            selectedId = note.id
            title = note.title
            body = note.content
            bodyField = TextFieldValue(note.content)
            previewMode = false
            onEditingChanged(true)
        }.onFailure { Toast.makeText(context, "Could not import Markdown file", Toast.LENGTH_LONG).show() }
    }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/markdown")) { uri ->
        if (uri != null) runCatching {
            context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(exportContent) }
                ?: error("Unable to write this file")
            Toast.makeText(context, "Markdown exported", Toast.LENGTH_SHORT).show()
        }.onFailure { Toast.makeText(context, "Could not export Markdown file", Toast.LENGTH_LONG).show() }
    }
    fun saveCurrent() {
        val id = selectedId ?: return
        if (title.isBlank() && body.isBlank()) {
            val removedNote = notes.firstOrNull { it.id == id }
            notes = notes.filterNot { it.id == id }
            saveNotes(context, notes)
            removedNote?.let { releaseUnusedNoteImagePermissions(context, it, notes) }
            return
        }
        val pinned = notes.firstOrNull { it.id == id }?.pinned ?: false
        val current = LocalNote(id, title.trim(), body, System.currentTimeMillis(), pinned)
        notes = (notes.filterNot { it.id == id } + current)
            .sortedWith(compareByDescending<LocalNote> { it.pinned }.thenByDescending { it.updatedAt })
        saveNotes(context, notes)
    }

    // Coalesce rapid keystrokes into one local preference write instead of serializing the
    // complete note on every character. Explicit actions still save immediately.
    LaunchedEffect(selectedId, title, body) {
        if (selectedId != null) {
            kotlinx.coroutines.delay(350)
            saveCurrent()
        }
    }

    val imageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null && selectedId != null) runCatching {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            val markdownImage = "![](<" + uri.toString() + ">)"
            body = if (body.isBlank()) markdownImage else body + "\n\n" + markdownImage
            bodyField = TextFieldValue(body, TextRange(body.length))
            saveCurrent()
        }.onFailure {
            Toast.makeText(context, "Could not add image", Toast.LENGTH_LONG).show()
        }
    }

    fun openNote(note: LocalNote) {
        selectedId = note.id
        title = note.title
        body = note.content
        bodyField = TextFieldValue(note.content)
        previewMode = false
        onEditingChanged(true)
    }
    fun closeEditor() {
        saveCurrent()
        selectedId = null
        previewMode = false
        onEditingChanged(false)
    }
    fun exportNote(noteTitle: String, noteBody: String) {
        exportContent = if (noteTitle.isBlank()) noteBody else "# $noteTitle\n\n$noteBody"
        val safeName = noteTitle.ifBlank { "note" }.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim().ifBlank { "note" }
        exportLauncher.launch("$safeName.md")
    }

    LaunchedEffect(backSignal) { if (backSignal > 0 && selectedId != null) closeEditor() }
    BackHandler(enabled = selectedId != null) { closeEditor() }

    Box(modifier = modifier) {
        if (selectedId == null) {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text(builtInToolText("Search notes", "Cari catatan")) },
                        leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp)
                    )
                    IconButton(
                        onClick = { importLauncher.launch(arrayOf("text/markdown", "text/plain", "application/octet-stream")) },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(Icons.Outlined.FileOpen, contentDescription = builtInToolText("Import Markdown file", "Impor berkas Markdown"))
                    }
                }
                val visibleNotes = notes.filter {
                    searchQuery.isBlank() || it.title.contains(searchQuery, true) || it.content.contains(searchQuery, true)
                }
                if (visibleNotes.isEmpty()) {
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(horizontal = 24.dp)
                        ) {
                            Icon(
                                Icons.Outlined.NoteAdd,
                                contentDescription = null,
                                modifier = Modifier.size(36.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                if (searchQuery.isBlank()) "No notes yet" else "No matching notes",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                if (searchQuery.isBlank()) "Create a note to keep your thoughts in one place." else "Try a different search term.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else {
                    LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        items(visibleNotes, key = { it.id }) { note ->
                            Column(Modifier.fillMaxWidth()) {
                                Row(Modifier.fillMaxWidth().heightIn(min = 88.dp).clip(RoundedCornerShape(8.dp))
                                    .clickable { openNote(note) }.padding(horizontal = 8.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f).padding(end = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            if (note.pinned) Text("PINNED", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                            Text(note.title.ifBlank { "Untitled note" }, style = MaterialTheme.typography.titleMedium,
                                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        }
                                        Text(note.content.lineSequence().map { it.trimStart('#', ' ', '-', '*') }
                                            .firstOrNull { it.isNotBlank() }.orEmpty().ifBlank { "No content" },
                                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 2, overflow = TextOverflow.Ellipsis)
                                        Text(Instant.ofEpochMilli(note.updatedAt).atZone(ZoneId.systemDefault())
                                            .format(DateTimeFormatter.ofPattern("d MMM, HH:mm", Locale.getDefault())),
                                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Box {
                                        IconButton(onClick = { noteMenuTarget = note.id }) {
                                            Icon(Icons.Outlined.MoreVert, contentDescription = "Note actions")
                                        }
                                        DropdownMenu(
                                            expanded = noteMenuTarget == note.id,
                                            onDismissRequest = { noteMenuTarget = null }
                                        ) {
                                            DropdownMenuItem(
                                                text = { Text(if (note.pinned) "Unpin note" else "Pin note") },
                                                leadingIcon = { Icon(Icons.Outlined.PushPin, contentDescription = null) },
                                                onClick = {
                                                    notes = notes.map { if (it.id == note.id) it.copy(pinned = !it.pinned) else it }
                                                        .sortedWith(compareByDescending<LocalNote> { it.pinned }.thenByDescending { it.updatedAt })
                                                    saveNotes(context, notes)
                                                    noteMenuTarget = null
                                                }
                                            )
                                            DropdownMenuItem(
                                                text = { Text(builtInToolText("Delete", "Hapus")) },
                                                leadingIcon = { Icon(Icons.Outlined.DeleteOutline, contentDescription = null) },
                                                onClick = { deleteTarget = note.id; noteMenuTarget = null }
                                            )
                                        }
                                    }
                                }
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
                            }
                        }
                    }
                }
            }
        } else {
            Column(Modifier.fillMaxSize().imePadding(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBackRequested) {
                        Icon(Icons.Outlined.ArrowBack, contentDescription = "Back")
                    }
                    Spacer(Modifier.weight(1f))
                    val selectedNotePinned = notes.firstOrNull { it.id == selectedId }?.pinned == true
                    IconButton(onClick = {
                        notes = notes.map { if (it.id == selectedId) it.copy(pinned = !selectedNotePinned, updatedAt = System.currentTimeMillis()) else it }
                            .sortedWith(compareByDescending<LocalNote> { it.pinned }.thenByDescending { it.updatedAt })
                        saveNotes(context, notes)
                    }) {
                        Icon(Icons.Outlined.PushPin, contentDescription = if (selectedNotePinned) "Unpin note" else "Pin note",
                            tint = if (selectedNotePinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = { previewMode = !previewMode }) {
                        Icon(
                            if (previewMode) Icons.Outlined.Edit else Icons.Outlined.Visibility,
                            contentDescription = if (previewMode) "Edit note" else "Preview note"
                        )
                    }
                    Box {
                        var editorMenuExpanded by remember { mutableStateOf(false) }
                        IconButton(onClick = { editorMenuExpanded = true }) {
                            Icon(Icons.Outlined.MoreVert, contentDescription = "More note actions")
                        }
                        DropdownMenu(expanded = editorMenuExpanded, onDismissRequest = { editorMenuExpanded = false }) {
                            DropdownMenuItem(text = { Text(builtInToolText("Export Markdown", "Ekspor Markdown")) }, leadingIcon = { Icon(Icons.Outlined.FileDownload, contentDescription = null) }, onClick = {
                                editorMenuExpanded = false
                                exportNote(title, body)
                            })
                            DropdownMenuItem(text = { Text(builtInToolText("Import Markdown", "Impor Markdown")) }, leadingIcon = { Icon(Icons.Outlined.FileOpen, contentDescription = null) }, onClick = {
                                editorMenuExpanded = false
                                importLauncher.launch(arrayOf("text/markdown", "text/plain", "application/octet-stream"))
                            })
                            DropdownMenuItem(
                                text = { Text(builtInToolText("Delete note", "Hapus catatan")) },
                                leadingIcon = { Icon(Icons.Outlined.DeleteOutline, contentDescription = null) },
                                onClick = { editorMenuExpanded = false; deleteTarget = selectedId }
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = title, onValueChange = { title = it }, modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(builtInToolText("Title (optional)", "Judul (opsional)")) }, singleLine = true,
                    textStyle = MaterialTheme.typography.titleLarge, readOnly = previewMode
                )
                if (previewMode) {
                    val markdownRenderer = remember(context) { Markwon.builder(context)
                        .usePlugin(ImagesPlugin.create())
                        .usePlugin(TablePlugin.create(context))
                        .usePlugin(StrikethroughPlugin.create())
                        .usePlugin(TaskListPlugin.create(context))
                        .build() }
                    val previewTextColor = MaterialTheme.colorScheme.onSurface
                    val previewTextSizePx = with(LocalDensity.current) {
                        MaterialTheme.typography.bodyMedium.fontSize.toPx()
                    }
                    val previewTypeface = remember(context) { markdownPreviewTypeface(context) }
                    AndroidView(
                        factory = { viewContext ->
                            TextView(viewContext).apply {
                                setPadding(8, 8, 8, 8)
                                setTextSize(TypedValue.COMPLEX_UNIT_PX, previewTextSizePx)
                                typeface = previewTypeface
                            }
                        },
                        update = { textView ->
                            textView.setTextSize(TypedValue.COMPLEX_UNIT_PX, previewTextSizePx)
                            textView.typeface = previewTypeface
                            textView.setTextColor(android.graphics.Color.rgb(
                                (previewTextColor.red * 255).toInt(),
                                (previewTextColor.green * 255).toInt(),
                                (previewTextColor.blue * 255).toInt()
                            ))
                            markdownRenderer.setMarkdown(textView, markdownForSafePreview(body))
                        },
                        modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp)
                    )
                } else {
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        OutlinedTextField(
                            value = bodyField,
                            onValueChange = { bodyField = it; body = it.text },
                            modifier = Modifier.fillMaxSize(),
                            placeholder = { Text(builtInToolText("Write Markdown here…", "Tulis Markdown di sini…")) },
                            minLines = 8, maxLines = Int.MAX_VALUE
                        )
                        Surface(
                            modifier = Modifier.align(Alignment.BottomCenter).padding(horizontal = 8.dp, vertical = 12.dp)
                                .shadow(4.dp, RoundedCornerShape(12.dp)),
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            tonalElevation = 3.dp
                        ) {
                            Row(
                                Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 6.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                IconButton(onClick = { bodyField = markdownEdit(bodyField, body, "# ", ""); body = bodyField.text; saveCurrent() }) {
                                    Icon(Icons.Outlined.Title, contentDescription = "Heading")
                                }
                                IconButton(onClick = { bodyField = markdownEdit(bodyField, body, "**", "**"); body = bodyField.text; saveCurrent() }) {
                                    Icon(Icons.Outlined.FormatBold, contentDescription = "Bold")
                                }
                                IconButton(onClick = { bodyField = markdownEdit(bodyField, body, "*", "*"); body = bodyField.text; saveCurrent() }) {
                                    Icon(Icons.Outlined.FormatItalic, contentDescription = "Italic")
                                }
                                IconButton(onClick = { bodyField = markdownEdit(bodyField, body, "~~", "~~"); body = bodyField.text; saveCurrent() }) {
                                    Icon(Icons.Outlined.FormatStrikethrough, contentDescription = "Strikethrough")
                                }
                                IconButton(onClick = { bodyField = markdownEdit(bodyField, body, "`", "`"); body = bodyField.text; saveCurrent() }) {
                                    Icon(Icons.Outlined.Code, contentDescription = "Inline code")
                                }
                                IconButton(onClick = { bodyField = markdownEdit(bodyField, body, "- ", ""); body = bodyField.text; saveCurrent() }) {
                                    Icon(Icons.Outlined.FormatListBulleted, contentDescription = "List")
                                }
                                IconButton(onClick = { bodyField = markdownEdit(bodyField, body, "1. ", ""); body = bodyField.text; saveCurrent() }) {
                                    Icon(Icons.Outlined.FormatListNumbered, contentDescription = "Numbered list")
                                }
                                IconButton(onClick = { bodyField = markdownEdit(bodyField, body, "> ", ""); body = bodyField.text; saveCurrent() }) {
                                    Icon(Icons.Outlined.FormatQuote, contentDescription = "Block quote")
                                }
                                IconButton(onClick = { bodyField = markdownEdit(bodyField, body, "---", ""); body = bodyField.text; saveCurrent() }) {
                                    Icon(Icons.Outlined.HorizontalRule, contentDescription = "Horizontal divider")
                                }
                                IconButton(onClick = { bodyField = markdownEdit(bodyField, body, "- [ ] ", ""); body = bodyField.text; saveCurrent() }) {
                                    Icon(Icons.Outlined.Checklist, contentDescription = "Checklist")
                                }
                                IconButton(onClick = { bodyField = markdownEdit(bodyField, body, "[", "](https://)"); body = bodyField.text; saveCurrent() }) {
                                    Icon(Icons.Outlined.InsertLink, contentDescription = "Insert link")
                                }
                                IconButton(onClick = { imageLauncher.launch(arrayOf("image/*")) }) {
                                    Icon(Icons.Outlined.Image, contentDescription = "Insert image")
                                }
                            }
                        }
                    }
                }
            }
        }
        if (selectedId == null) {
            FloatingActionButton(onClick = {
                selectedId = UUID.randomUUID().toString()
                title = ""
                body = ""
                bodyField = TextFieldValue("")
                previewMode = false
                onEditingChanged(true)
            }, modifier = Modifier.align(Alignment.BottomEnd).padding(end = 8.dp, bottom = 24.dp),
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer) {
                Icon(Icons.Outlined.NoteAdd, contentDescription = "New note")
            }
        }
    }

    if (deleteTarget != null) {
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(builtInToolText("Delete note?", "Hapus catatan?")) },
            text = { Text(builtInToolText("This note will be permanently deleted from this device.", "Catatan ini akan dihapus permanen dari perangkat.")) },
            confirmButton = {
                TextButton(onClick = {
                    val removedNote = notes.firstOrNull { it.id == deleteTarget }
                    notes = notes.filterNot { it.id == deleteTarget }
                    saveNotes(context, notes)
                    removedNote?.let { releaseUnusedNoteImagePermissions(context, it, notes) }
                    deleteTarget = null
                    selectedId = null
                    title = ""
                    body = ""
                    bodyField = TextFieldValue("")
                    previewMode = false
                    onEditingChanged(false)
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text(builtInToolText("Cancel", "Batal")) } }
        )
    }
}


private fun markdownEdit(field: TextFieldValue, body: String, prefix: String, suffix: String): TextFieldValue {
    val start = field.selection.min.coerceIn(0, body.length)
    val end = field.selection.max.coerceIn(start, body.length)
    if (suffix.isEmpty()) {
        val lineStart = body.lastIndexOf('\n', (start - 1).coerceAtLeast(0)).let { if (it < 0) 0 else it + 1 }
        val insertion = if (lineStart == 0) prefix else "\n" + prefix
        val updated = body.substring(0, lineStart) + insertion + body.substring(lineStart)
        return TextFieldValue(updated, TextRange(lineStart + insertion.length))
    }
    val selected = body.substring(start, end)
    val inner = selected.ifEmpty { if (prefix == "[") "link" else "text" }
    val replacement = prefix + inner + suffix
    val updated = body.substring(0, start) + replacement + body.substring(end)
    val selection = if (selected.isEmpty()) TextRange(start + prefix.length + inner.length)
        else TextRange(start + prefix.length, start + prefix.length + selected.length)
    return TextFieldValue(updated, selection)
}

@Composable
private fun MarkdownPreviewLine(line: String) {
    val trimmed = line.trimStart()
    val imageMatch = Regex("^!\\[[^]]*]\\((?:<)?([^)>]+)(?:>)?\\)$").matchEntire(trimmed)
    if (imageMatch != null) {
        val imageUri = runCatching { Uri.parse(imageMatch.groupValues[1]) }.getOrNull()
        if (imageUri != null) {
            AndroidView(
                factory = { context -> ImageView(context).apply { adjustViewBounds = true; scaleType = ImageView.ScaleType.FIT_CENTER } },
                update = { view -> runCatching { view.setImageURI(imageUri) } },
                modifier = Modifier.fillMaxWidth().height(220.dp)
            )
        }
        return
    }
    val headingLevel = trimmed.takeWhile { it == '#' }.length.coerceIn(0, 6)
        .takeIf { trimmed.startsWith("#") && trimmed.getOrNull(it) == ' ' } ?: 0
    val displayText = when {
        headingLevel > 0 -> trimmed.drop(headingLevel).trimStart()
        trimmed.startsWith("- [ ] ") -> "☐ " + trimmed.removePrefix("- [ ] ")
        trimmed.startsWith("- [x] ", true) -> "☑ " + trimmed.substring(6)
        trimmed.startsWith("- ") || trimmed.startsWith("* ") -> "• " + trimmed.drop(2)
        trimmed.startsWith("> ") -> "│ " + trimmed.drop(2)
        trimmed.startsWith("---") -> "────────"
        else -> trimmed
    }
    val visibleText = displayText
        .replace(Regex("\\*\\*([^*]+)\\*\\*")) { it.groupValues[1] }
        .replace(Regex("\\*([^*]+)\\*")) { it.groupValues[1] }
    val styled = buildAnnotatedString {
        append(visibleText)
        if (headingLevel > 0) addStyle(SpanStyle(fontWeight = FontWeight.SemiBold), 0, length)
        if (trimmed.startsWith(">")) addStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurfaceVariant, fontStyle = FontStyle.Italic), 0, length)
    }
    val context = androidx.compose.ui.platform.LocalContext.current
    val linkRegex = Regex("\\[([^]]+)]\\((https?://[^)]+)\\)")
    val linkedText = buildAnnotatedString {
        var cursor = 0
        linkRegex.findAll(visibleText).forEach { match ->
            append(visibleText.substring(cursor, match.range.first))
            pushStringAnnotation(tag = "URL", annotation = match.groupValues[2])
            pushStyle(SpanStyle(color = MaterialTheme.colorScheme.primary, textDecoration = TextDecoration.Underline))
            append(match.groupValues[1])
            pop()
            pop()
            cursor = match.range.last + 1
        }
        append(visibleText.substring(cursor))
    }
    ClickableText(
        text = linkedText,
        style = when {
            headingLevel == 1 -> MaterialTheme.typography.headlineSmall
            headingLevel == 2 -> MaterialTheme.typography.titleLarge
            headingLevel in 3..6 -> MaterialTheme.typography.titleMedium
            else -> MaterialTheme.typography.bodyLarge
        }.copy(color = MaterialTheme.colorScheme.onSurface),
        modifier = Modifier.fillMaxWidth(),
        onClick = { offset ->
            linkedText.getStringAnnotations("URL", offset, offset).firstOrNull()?.let { annotation ->
                runCatching {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(annotation.item)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    if (intent.resolveActivity(context.packageManager) != null) context.startActivity(intent)
                    else Toast.makeText(context, "No app can open this link", Toast.LENGTH_SHORT).show()
                }.onFailure { Toast.makeText(context, "Could not open link", Toast.LENGTH_SHORT).show() }
            }
        }
    )
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
            title = { Text(builtInToolText("Clear calculator history?", "Hapus riwayat kalkulator?")) },
            text = { Text(builtInToolText("Saved calculations will be removed from this device.", "Perhitungan tersimpan akan dihapus dari perangkat ini.")) },
            confirmButton = {
                TextButton(onClick = {
                    history = emptyList()
                    saveCalculatorHistory(context, history)
                    showClearHistoryDialog = false
                }) { Text(builtInToolText("Clear", "Bersihkan")) }
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

private data class DeviceCalendarEvent(
    val id: Long, val title: String, val begin: Long, val end: Long, val allDay: Boolean
)

private fun readDeviceCalendarEvents(context: Context, start: LocalDate, endExclusive: LocalDate): List<DeviceCalendarEvent> {
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

private fun openDeviceCalendarEvent(context: Context, event: DeviceCalendarEvent) {
    val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, event.id).buildUpon()
        .appendQueryParameter("beginTime", event.begin.toString()).appendQueryParameter("endTime", event.end.toString()).build()
    val intent = Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching {
        if (intent.resolveActivity(context.packageManager) != null) context.startActivity(intent)
        else Toast.makeText(context, "No calendar app available", Toast.LENGTH_SHORT).show()
    }.onFailure { Toast.makeText(context, "Could not open calendar event", Toast.LENGTH_SHORT).show() }
}

@OptIn(ExperimentalMaterial3Api::class)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CalendarTool(context: Context, onBackRequested: () -> Unit, modifier: Modifier = Modifier) {
    var tasks by remember(context) { mutableStateOf(loadTasks(context)) }
    var month by remember { mutableStateOf(YearMonth.now()) }
    var calendarPermission by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED) }
    var deviceEvents by remember { mutableStateOf<List<DeviceCalendarEvent>>(emptyList()) }
    val calendarPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        calendarPermission = granted || ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED
    }
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

    LaunchedEffect(month, calendarPermission) {
        deviceEvents = if (calendarPermission) withContext(Dispatchers.IO) {
            readDeviceCalendarEvents(context, month.atDay(1), month.plusMonths(1).atDay(1))
        } else emptyList()
    }

    val dateFormatter = DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", Locale.getDefault())
    val monthFormatter = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault())
    val firstDayOfWeek = WeekFields.of(Locale.getDefault()).firstDayOfWeek
    val weekDays = (0..6).map { firstDayOfWeek.plus(it.toLong()) }
    val taskDate = selectedDate.toString()
    val dayEvents = deviceEvents.filter { Instant.ofEpochMilli(it.begin).atZone(ZoneId.systemDefault()).toLocalDate() == selectedDate }.sortedBy { it.begin }
    val dayTasks = tasks.filter { it.date == taskDate }
        .sortedWith(compareBy<LocalTask> { it.completed }.thenByDescending { it.priority }
            .thenBy { if (it.time.isBlank()) "99:99" else it.time })

    Box(modifier = modifier) {
    Column(modifier = Modifier.fillMaxSize().padding(bottom = 64.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(0.dp), modifier = Modifier.fillMaxWidth()) {
            TopAppBar(
                title = {
                    Text(
                        month.format(monthFormatter),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackRequested) {
                        Icon(Icons.Outlined.ArrowBack, contentDescription = builtInToolText("Back", "Kembali"))
                    }
                },
                actions = {
                    IconButton(onClick = { month = month.minusMonths(1); selectedDate = month.atDay(1) }) {
                        Icon(Icons.Outlined.ArrowBack, contentDescription = builtInToolText("Previous month", "Bulan sebelumnya"))
                    }
                    IconButton(onClick = { month = month.plusMonths(1); selectedDate = month.atDay(1) }) {
                        Icon(Icons.Outlined.ArrowForward, contentDescription = builtInToolText("Next month", "Bulan berikutnya"))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    selectedDate.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.getDefault())),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                TextButton(onClick = { month = YearMonth.now(); selectedDate = LocalDate.now() }) {
                    Text(if (Locale.getDefault().language == "id") "Hari ini" else "Today")
                }
            }
        }
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow
        ) {
            Column(Modifier.padding(horizontal = 8.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Row(Modifier.fillMaxWidth()) {
                    weekDays.forEach { weekDay ->
                        Text(
                            weekDay.getDisplayName(java.time.format.TextStyle.NARROW, Locale.getDefault()),
                            Modifier.weight(1f).padding(vertical = 4.dp), textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
                val firstOffset = (month.atDay(1).dayOfWeek.value - firstDayOfWeek.value + 7) % 7
                val cells = List(firstOffset) { 0 } + (1..month.lengthOfMonth()).toList()
                cells.chunked(7).forEach { week ->
                    Row(Modifier.fillMaxWidth()) {
                        week.forEach { day ->
                            Box(Modifier.weight(1f).height(38.dp).padding(1.dp), contentAlignment = Alignment.Center) {
                                if (day > 0) {
                                    val dateValue = month.atDay(day)
                                    val selected = dateValue == selectedDate
                                    val today = dateValue == LocalDate.now()
                                    val hasTasks = tasks.any { it.date == dateValue.toString() } ||
                                        deviceEvents.any { Instant.ofEpochMilli(it.begin).atZone(ZoneId.systemDefault()).toLocalDate() == dateValue }
                                    Column(
                                        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(14.dp))
                                            .background(when {
                                                selected -> MaterialTheme.colorScheme.primary
                                                today -> MaterialTheme.colorScheme.secondaryContainer
                                                else -> Color.Transparent
                                            })
                                            .clickable { selectedDate = dateValue },
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Text(day.toString(), color = when {
                                            selected -> MaterialTheme.colorScheme.onPrimary
                                            today -> MaterialTheme.colorScheme.onSecondaryContainer
                                            else -> MaterialTheme.colorScheme.onSurface
                                        }, style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = if (selected || today) FontWeight.SemiBold else FontWeight.Normal)
                                        Box(Modifier.padding(top = 2.dp).size(4.dp).clip(CircleShape)
                                            .background(if (hasTasks) {
                                                if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary
                                            } else Color.Transparent))
                                    }
                                }
                            }
                        }
                        repeat(7 - week.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(if (selectedDate == LocalDate.now()) (if (Locale.getDefault().language == "id") "Hari ini" else "Today") else selectedDate.format(dateFormatter),
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
                Text("Nothing planned", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(dayEvents, key = { "device-${it.id}-${it.begin}" }) { event ->
                    Row(Modifier.fillMaxWidth().clickable { openDeviceCalendarEvent(context, event) }
                        .padding(horizontal = 4.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(if (event.allDay) "All day" else SimpleDateFormat(
                            if (android.text.format.DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm a",
                            Locale.getDefault()).format(Date(event.begin)),
                            modifier = Modifier.width(72.dp), style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary)
                        Box(Modifier.width(3.dp).height(34.dp).clip(RoundedCornerShape(2.dp))
                            .background(MaterialTheme.colorScheme.tertiary))
                        Text(event.title, modifier = Modifier.weight(1f).padding(start = 12.dp), style = MaterialTheme.typography.bodyLarge,
                            maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
                }
                items(dayTasks, key = { "task-${it.id}" }) { task ->
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 2.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (task.type == "todo") {
                            Checkbox(checked = task.completed, onCheckedChange = { checked ->
                                tasks = tasks.map { if (it.id == task.id) it.copy(completed = checked) else it }
                                saveTasks(context, tasks)
                            })
                        } else {
                            Icon(Icons.Outlined.Notifications, contentDescription = builtInToolText("Reminder", "Pengingat"),
                                tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(horizontal = 10.dp))
                        }
                        Column(Modifier.weight(1f).padding(vertical = 2.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
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
                            Icon(Icons.Outlined.DeleteOutline, contentDescription = builtInToolText("Delete task", "Hapus tugas"),
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
        ) { Icon(Icons.Outlined.Add, contentDescription = builtInToolText("Add task", "Tambah tugas")) }
    }

    if (showEditor) {
        AlertDialog(
            onDismissRequest = { showEditor = false },
            title = { Text(builtInToolText("New task", "Tugas baru")) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(value = title, onValueChange = { title = it },
                        modifier = Modifier.fillMaxWidth(), label = { Text(builtInToolText("Title", "Judul")) }, singleLine = true)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (type == "todo") {
                            Button(
                                onClick = { type = "todo" },
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp)
                            ) { Text("To-do", maxLines = 1, softWrap = false, style = MaterialTheme.typography.labelLarge) }
                        } else {
                            FilledTonalButton(
                                onClick = { type = "todo" },
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp)
                            ) { Text("To-do", maxLines = 1, softWrap = false, style = MaterialTheme.typography.labelLarge) }
                        }
                        if (type == "reminder") {
                            Button(
                                onClick = { type = "reminder" },
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp)
                            ) { Text("Reminder", maxLines = 1, softWrap = false, style = MaterialTheme.typography.labelLarge) }
                        } else {
                            FilledTonalButton(
                                onClick = { type = "reminder" },
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp)
                            ) { Text("Reminder", maxLines = 1, softWrap = false, style = MaterialTheme.typography.labelLarge) }
                        }
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
                                    text = label,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 9.dp),
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
                }) { Text(builtInToolText("Save", "Simpan")) }
            },
            dismissButton = { TextButton(onClick = { showEditor = false }) { Text("Cancel") } }
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
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh
            ) {
                Column {
                    DatePicker(
                        state = datePickerState,
                        title = null,
                        headline = {
                            Text(
                                if (Locale.getDefault().language == "id") "Pilih tanggal" else "Select date",
                                style = MaterialTheme.typography.titleLarge,
                                modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
                            )
                        }
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = { showDatePicker = false }) {
                            Text(if (Locale.getDefault().language == "id") "Batal" else "Cancel")
                        }
                        TextButton(onClick = {
                            datePickerState.selectedDateMillis?.let { millis ->
                                date = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                            }
                            showDatePicker = false
                        }) {
                            Text(if (Locale.getDefault().language == "id") "Pilih" else "Select")
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
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("Cancel") } }
        )
    }
}
