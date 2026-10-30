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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.Redo
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FileOpen
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.NoteAdd
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Title
import androidx.compose.material.icons.outlined.ViewList
import androidx.compose.material.icons.outlined.ViewModule
import androidx.compose.material.icons.outlined.History
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
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
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
import java.time.temporal.WeekFields
import java.util.Locale
import java.util.Date
import java.text.SimpleDateFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import com.kizitonwose.calendar.compose.HorizontalCalendar
import com.kizitonwose.calendar.compose.rememberCalendarState
import com.kizitonwose.calendar.core.DayPosition
import java.util.UUID

internal enum class BuiltInTool { NOTES, CALCULATOR, CALENDAR, UNIT_CONVERTER, BOOKS }

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
    val pinned: Boolean = false,
    /** Versioned structured document JSON; content is a searchable plain-text projection. */
    val documentJson: String = "",
    val labels: List<String> = emptyList(),
    val color: String = "default",
    /** Canonical Markdown source for editing and direct .md export. */
    val markdown: String = ""
)

private data class PendingNoteExport(val title: String, val document: NoteDocument, val labels: List<String>, val color: String)

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

/** A single agenda row, sorted all-day first, timed items chronologically, then untimed tasks. */
private data class CalendarAgendaItem(
    val key: String,
    val group: Int,
    val timestamp: Long,
    val event: DeviceCalendarEvent? = null,
    val task: LocalTask? = null
)

private fun notesStore(context: Context) =
    context.getSharedPreferences("dislauncher_notes", Context.MODE_PRIVATE)

private fun preserveCorruptNotes(context: Context, rawJson: String) {
    val store = notesStore(context)
    if (!store.contains("notes_json_recovery_backup")) {
        store.edit()
            .putString("notes_json_recovery_backup", rawJson)
            .putLong("notes_json_recovery_backup_at", System.currentTimeMillis())
            .apply()
    }
    Toast.makeText(
        context,
        "Notes data could not be read safely. A recovery copy was preserved; the original data was not overwritten.",
        Toast.LENGTH_LONG
    ).show()
}

private fun loadNotes(context: Context): List<LocalNote> {
    val store = notesStore(context)
    val rawJson = store.getString("notes_json", "[]") ?: "[]"
    var needsMigration = false
    var safeToPersistMigration = true

    // Never replace the stored notes with an empty list if the JSON itself is malformed.
    val loaded = try {
        val array = JSONArray(rawJson)
        (0 until array.length()).mapNotNull { i ->
            val o = array.optJSONObject(i) ?: run {
                safeToPersistMigration = false
                return@mapNotNull null
            }
            val id = o.optString("id")
            if (id.isBlank()) {
                safeToPersistMigration = false
                return@mapNotNull null
            }

            val oldContent = o.optString("content")
            val savedDocument = o.optString("document")
            val decodedDocument = savedDocument.takeIf { it.isNotBlank() }
                ?.let { NoteDocumentCodec.decode(it) }
            val hasMarkdown = o.has("markdown")
            val markdown = if (hasMarkdown) o.optString("markdown")
                else NoteDocumentCodec.toMarkdown(decodedDocument ?: NoteDocumentCodec.fromLegacyContent(oldContent))
            val document = if (hasMarkdown) NoteDocumentCodec.fromLegacyContent(markdown)
                else decodedDocument ?: NoteDocumentCodec.fromLegacyContent(oldContent)
            val plainText = NoteDocumentCodec.plainText(document)
            if (!hasMarkdown || decodedDocument == null || oldContent != plainText) {
                needsMigration = true
            }

            LocalNote(
                id = id,
                title = o.optString("title"),
                content = plainText,
                updatedAt = o.optLong("updatedAt"),
                pinned = o.optBoolean("pinned", false),
                documentJson = NoteDocumentCodec.encode(document),
                labels = o.optJSONArray("labels")?.let { labels ->
                    (0 until labels.length()).map { labels.optString(it) }
                }.orEmpty(),
                color = o.optString("color", "default"),
                markdown = markdown
            )
        }.sortedWith(
            compareByDescending<LocalNote> { it.pinned }.thenByDescending { it.updatedAt }
        )
    } catch (_: Exception) {
        // Preserve the original value and keep a separate recovery copy before returning an empty view.
        preserveCorruptNotes(context, rawJson)
        return emptyList()
    }

    if (!safeToPersistMigration) {
        preserveCorruptNotes(context, rawJson)
    }

    // Migrate legacy notes once, and only when every stored entry was parsed safely.
    if (needsMigration && safeToPersistMigration) {
        saveNotes(context, loaded)
    }
    return loaded
}

private fun saveNotes(context: Context, notes: List<LocalNote>) {
    // Do not replace recoverable user data if the current store contains malformed entries.
    val existingRaw = notesStore(context).getString("notes_json", "[]") ?: "[]"
    val existingStoreIsSafe = runCatching {
        val existing = JSONArray(existingRaw)
        (0 until existing.length()).all { index ->
            val item = existing.optJSONObject(index)
            item != null && item.optString("id").isNotBlank()
        }
    }.getOrDefault(false)
    if (!existingStoreIsSafe) return

    val array = JSONArray()
    notes.forEach { note ->
        array.put(JSONObject().put("id", note.id).put("title", note.title)
            .put("content", note.content)
            .put("markdown", note.markdown)
            .put("document", note.documentJson.takeIf { it.isNotBlank() }
                ?: NoteDocumentCodec.encode(NoteDocumentCodec.fromLegacyContent(note.content)))
            .put("updatedAt", note.updatedAt).put("pinned", note.pinned)
            .put("labels", JSONArray(note.labels)).put("color", note.color))
    }
    notesStore(context).edit().putString("notes_json", array.toString()).apply()
}

/** Release image grants and remove imported archive assets no longer referenced by any note. */
private fun releaseUnusedNoteImageResources(
    context: Context,
    removedNote: LocalNote,
    remainingNotes: List<LocalNote>
) {
    val removedUris = NoteDocumentCodec.decode(removedNote.documentJson)
        ?.blocks.orEmpty().flatMap { it.spans }.mapNotNull { it.imageUri }.toSet()
    val remainingUris = remainingNotes.flatMap { note ->
        NoteDocumentCodec.decode(note.documentJson)?.blocks.orEmpty().flatMap { it.spans }.mapNotNull { it.imageUri }
    }.toSet()

    context.contentResolver.persistedUriPermissions
        .filter { permission ->
            permission.isReadPermission &&
                permission.uri.toString() in removedUris &&
                permission.uri.toString() !in remainingUris
        }
        .forEach { permission ->
            runCatching {
                context.contentResolver.releasePersistableUriPermission(
                    permission.uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
        }

    val importRoot = java.io.File(context.filesDir, "notes-import").canonicalFile
    val remainingFiles = remainingUris.mapNotNull { rawUri ->
        val uri = android.net.Uri.parse(rawUri)
        if (uri.scheme != "file") null
        else uri.path?.let { java.io.File(it) }?.canonicalFile
    }
    removedUris.forEach { rawUri ->
        val uri = android.net.Uri.parse(rawUri)
        if (uri.scheme != "file") return@forEach
        val imageFile = uri.path?.let { java.io.File(it) }?.canonicalFile ?: return@forEach
        if (!imageFile.path.startsWith(importRoot.path + java.io.File.separator)) return@forEach
        val archiveDirectory = imageFile.parentFile?.parentFile?.canonicalFile ?: return@forEach
        if (!archiveDirectory.path.startsWith(importRoot.path + java.io.File.separator)) return@forEach
        val stillReferenced = remainingFiles.any {
            it.path.startsWith(archiveDirectory.path + java.io.File.separator)
        }
        if (!stillReferenced) archiveDirectory.deleteRecursively()
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

private fun priorityLabel(priority: Int): String = when (priority) {
    1 -> "Low"
    3 -> "High"
    else -> "Medium"
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
    if (tool == BuiltInTool.BOOKS) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            Box(
                Modifier
                    .widthIn(max = 840.dp)
                    .fillMaxSize()
                    .align(Alignment.TopCenter)
                    .padding(
                        horizontal = if (maxWidth >= 600.dp) 24.dp else 16.dp,
                        vertical = 10.dp
                    )
            ) {
                BooksTool(onBack = onBack)
            }
        }
        return
    }
    val context = androidx.compose.ui.platform.LocalContext.current
    var notesEditing by remember { mutableStateOf(false) }
    var notesBackSignal by remember { mutableStateOf(0) }
    var calculatorShowingHistory by remember { mutableStateOf(false) }
    var calculatorScientificMode by remember { mutableStateOf(false) }
    var calculatorHistoryHasItems by remember { mutableStateOf(false) }
    var calculatorClearHistorySignal by remember { mutableStateOf(0) }
    var calendarMonth by remember { mutableStateOf(YearMonth.now()) }
    var converterSearchExpanded by remember { mutableStateOf(false) }
    var converterSearchQuery by remember { mutableStateOf("") }
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
                    if (!(tool == BuiltInTool.NOTES)) TopAppBar(
                        title = {
                            if (tool == BuiltInTool.UNIT_CONVERTER && converterSearchExpanded) {
                                OutlinedTextField(
                                    value = converterSearchQuery,
                                    onValueChange = { converterSearchQuery = it },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    placeholder = { Text(builtInToolText("Search units and categories", "Cari satuan dan kategori")) }
                                )
                            } else {
                                Text(
                                    title,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        },
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
                            if (tool == BuiltInTool.UNIT_CONVERTER) {
                                if (converterSearchExpanded) {
                                    IconButton(onClick = {
                                        converterSearchQuery = ""
                                        converterSearchExpanded = false
                                    }) {
                                        Icon(Icons.Outlined.Close, contentDescription = builtInToolText("Close search", "Tutup pencarian"))
                                    }
                                } else {
                                    IconButton(onClick = { converterSearchExpanded = true }) {
                                        Icon(Icons.Outlined.Search, contentDescription = builtInToolText("Search converter", "Cari konverter"))
                                    }
                                }
                            }
                            if (tool == BuiltInTool.CALENDAR) {
                                IconButton(onClick = { calendarMonth = calendarMonth.minusMonths(1) }) {
                                    Icon(Icons.Outlined.ArrowBack, contentDescription = builtInToolText("Previous month", "Bulan sebelumnya"))
                                }
                                IconButton(onClick = { calendarMonth = calendarMonth.plusMonths(1) }) {
                                    Icon(Icons.Outlined.ArrowForward, contentDescription = builtInToolText("Next month", "Bulan berikutnya"))
                                }
                            }
                            if (tool == BuiltInTool.CALCULATOR && !calculatorShowingHistory) {
                                TextButton(onClick = { calculatorScientificMode = !calculatorScientificMode }) {
                                    Text(if (calculatorScientificMode) builtInToolText("Basic", "Dasar") else builtInToolText("Scientific", "Ilmiah"))
                                }
                            }
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
                            .padding(
                                horizontal = if (maxWidth >= 600.dp) 24.dp else 16.dp,
                                vertical = 10.dp
                            )
                    ) {
                    when (tool) {
                        BuiltInTool.NOTES -> NotesTool(
                            context = context,
                            backSignal = notesBackSignal,
                            onEditingChanged = { notesEditing = it },
                            onBackRequested = { if (notesEditing) notesBackSignal++ else requestClose() },
                            modifier = Modifier.fillMaxSize()
                        )
                        BuiltInTool.CALCULATOR -> CalculatorTool(
                            modifier = Modifier.fillMaxSize(),
                            showHistory = calculatorShowingHistory,
                            onShowHistoryChange = { calculatorShowingHistory = it },
                            clearHistorySignal = calculatorClearHistorySignal,
                            onHistoryAvailabilityChanged = { calculatorHistoryHasItems = it },
                            scientificMode = calculatorScientificMode,
                            onScientificModeChange = { calculatorScientificMode = it }
                        )
                        BuiltInTool.CALENDAR -> CalendarTool(
                            context = context,
                            month = calendarMonth,
                            onMonthChange = { calendarMonth = it },
                            modifier = Modifier.fillMaxSize()
                        )
                        BuiltInTool.UNIT_CONVERTER -> UnitConverterTool(Modifier.fillMaxSize(), converterSearchQuery)
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
    var document by remember { mutableStateOf(NoteDocument()) }
    var markdownText by remember { mutableStateOf("") }
    var searchQuery by remember { mutableStateOf("") }
    var searchExpanded by remember { mutableStateOf(false) }
    var currentLabels by remember { mutableStateOf(setOf<String>()) }
    var currentColor by remember { mutableStateOf("default") }
    var showColorPicker by remember { mutableStateOf(false) }
    var activeLabel by remember { mutableStateOf<String?>(null) }
    var allLabels by remember(context) { mutableStateOf(notesStore(context).getString("note_labels", "").orEmpty().split(",").map { it.trim() }.filter { it.isNotBlank() }.toSet()) }
    var newLabel by remember { mutableStateOf("") }
    var showLabelManager by remember { mutableStateOf(false) }
    var showNoteLabels by remember { mutableStateOf(false) }
    var gridView by remember(context) {
        mutableStateOf(context.getSharedPreferences("dislauncher_notes", Context.MODE_PRIVATE).getBoolean("grid_view", true))
    }
    var pinnedOnly by remember { mutableStateOf(false) }
    var sortByTitle by remember { mutableStateOf(false) }
    var menuTarget by remember { mutableStateOf<String?>(null) }
    var deleteTarget by remember { mutableStateOf<String?>(null) }
    var editHistory by remember { mutableStateOf(MarkdownEditHistory()) }
    var pendingExport by remember { mutableStateOf<PendingNoteExport?>(null) }
    val scope = rememberCoroutineScope()

    fun plainText(doc: NoteDocument): String = NoteDocumentCodec.plainText(doc)
    fun isEmptyMarkdown(markdown: String): Boolean = markdown.isBlank()
    fun saveCurrent() {
        val id = selectedId ?: return
        if (title.isBlank() && isEmptyMarkdown(markdownText)) {
            if (notes.any { it.id == id }) {
                notes = notes.filterNot { it.id == id }
                saveNotes(context, notes)
            }
            return
        }
        val existing = notes.firstOrNull { it.id == id }
        val normalizedTitle = title.trim()
        if (existing != null &&
            existing.title == normalizedTitle &&
            existing.markdown == markdownText &&
            existing.labels.toSet() == currentLabels &&
            existing.color == currentColor
        ) {
            return
        }
        val parsedDocument = NoteDocumentCodec.fromLegacyContent(markdownText)
        document = parsedDocument
        val pinned = existing?.pinned ?: false
        val current = LocalNote(
            id = id,
            title = normalizedTitle,
            content = plainText(parsedDocument),
            updatedAt = System.currentTimeMillis(),
            pinned = pinned,
            documentJson = NoteDocumentCodec.encode(parsedDocument),
            labels = currentLabels.toList(),
            color = currentColor,
            markdown = markdownText
        )
        notes = (notes.filterNot { it.id == id } + current)
            .sortedWith(compareByDescending<LocalNote> { it.pinned }.thenByDescending { it.updatedAt })
        saveNotes(context, notes)
    }
    fun openNote(note: LocalNote) {
        selectedId = note.id
        title = note.title
        val loadedDocument = NoteDocumentCodec.decode(note.documentJson)
            ?: NoteDocumentCodec.fromLegacyContent(note.content)
        markdownText = note.markdown.ifEmpty { NoteDocumentCodec.toMarkdown(loadedDocument) }
        document = NoteDocumentCodec.fromLegacyContent(markdownText)
        currentLabels = note.labels.toSet()
        currentColor = note.color
        editHistory = MarkdownEditHistory()
        onEditingChanged(true)
    }
    fun createNote() {
        selectedId = UUID.randomUUID().toString()
        title = ""
        document = NoteDocument()
        markdownText = ""
        currentLabels = emptySet()
        currentColor = "default"
        editHistory = MarkdownEditHistory()
        onEditingChanged(true)
    }
    fun closeEditor() {
        saveCurrent()
        selectedId = null
        onEditingChanged(false)
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                val importResult = withContext(Dispatchers.IO) {
                    runCatching {
                        val input = context.contentResolver.openInputStream(uri)
                            ?: error("Unable to read archive")
                        input.use { readStructuredNoteArchive(context, it) }
                    }
                }
                importResult.onSuccess { imported ->
                    val importedNote = LocalNote(
                        id = UUID.randomUUID().toString(),
                        title = imported.title,
                        content = plainText(imported.document),
                        updatedAt = System.currentTimeMillis(),
                        documentJson = NoteDocumentCodec.encode(imported.document),
                        labels = imported.labels,
                        color = imported.color,
                        markdown = NoteDocumentCodec.toMarkdown(imported.document)
                    )
                    allLabels = allLabels + imported.labels
                    notesStore(context).edit()
                        .putString("note_labels", allLabels.sorted().joinToString(","))
                        .apply()
                    notes = (notes + importedNote).sortedWith(
                        compareByDescending<LocalNote> { it.pinned }.thenByDescending { it.updatedAt }
                    )
                    saveNotes(context, notes)
                    openNote(importedNote)
                }.onFailure {
                    Toast.makeText(
                        context,
                        "Could not import note archive: ${it.message.orEmpty()}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        val request = pendingExport
        if (uri != null && request != null) {
            scope.launch {
                val result = withContext(Dispatchers.IO) {
                    runCatching { writeStructuredNoteArchive(context, uri, request.title, request.document, request.labels, request.color) }
                }
                result.onSuccess { Toast.makeText(context, "Note archive exported", Toast.LENGTH_SHORT).show() }
                    .onFailure { Toast.makeText(context, "Could not export note archive: ${it.message.orEmpty()}", Toast.LENGTH_LONG).show() }
            }
        }
        pendingExport = null
    }

    val markdownExportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/markdown")) { uri ->
        if (uri != null) {
            val markdownToExport = markdownText
            scope.launch {
                val result = withContext(Dispatchers.IO) {
                    runCatching {
                        context.contentResolver.openOutputStream(uri, "wt")?.bufferedWriter(Charsets.UTF_8)?.use {
                            it.write(markdownToExport)
                        } ?: error("Unable to write Markdown file")
                    }
                }
                result.onSuccess { Toast.makeText(context, "Markdown exported", Toast.LENGTH_SHORT).show() }
                    .onFailure { Toast.makeText(context, "Could not export Markdown: " + it.message.orEmpty(), Toast.LENGTH_LONG).show() }
            }
        }
    }
    val imageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null && selectedId != null) runCatching {
            if (uri.scheme == "content") {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            editHistory = editHistory.record(markdownText)
            markdownText = markdownText.trimEnd() + "\n\n![Image](" + uri.toString() + ")"
            document = NoteDocumentCodec.fromLegacyContent(markdownText)
        }.onFailure {
            Toast.makeText(context, "Could not add image", Toast.LENGTH_LONG).show()
        }
    }

    LaunchedEffect(selectedId, title, markdownText) {
        if (selectedId != null) {
            kotlinx.coroutines.delay(350)
            saveCurrent()
        }
    }
    LaunchedEffect(backSignal) {
        if (backSignal > 0 && selectedId != null) closeEditor()
    }
    BackHandler(enabled = selectedId != null) { closeEditor() }

    val visibleNotes = notes.filter { note ->
        (!pinnedOnly || note.pinned) &&
            (activeLabel == null || activeLabel in note.labels) &&
            (searchQuery.isBlank() || note.title.contains(searchQuery, true) ||
                note.content.contains(searchQuery, true))
    }.let { list ->
        if (sortByTitle) list.sortedWith(compareByDescending<LocalNote> { it.pinned }.thenBy { it.title.lowercase() })
        else list.sortedWith(compareByDescending<LocalNote> { it.pinned }.thenByDescending { it.updatedAt })
    }

    Box(modifier.fillMaxSize()) {
        if (selectedId == null) {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    IconButton(onClick = onBackRequested) {
                        Icon(Icons.Outlined.ArrowBack, contentDescription = builtInToolText("Back", "Kembali"))
                    }
                    if (searchExpanded) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            modifier = Modifier.weight(1f),
                            placeholder = { Text("Search notes") },
                            singleLine = true,
                            shape = RoundedCornerShape(BuiltInToolDesign.controlCornerRadius),
                            trailingIcon = {
                                IconButton(onClick = { searchQuery = ""; searchExpanded = false }) {
                                    Icon(Icons.Outlined.Close, contentDescription = "Close search")
                                }
                            }
                        )
                    } else {
                        Text("Notes", modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold, maxLines = 1)
                        IconButton(onClick = { searchExpanded = true }) {
                            Icon(Icons.Outlined.Search, contentDescription = "Search notes")
                        }
                    }
                    IconButton(onClick = {
                        gridView = !gridView
                        context.getSharedPreferences("dislauncher_notes", Context.MODE_PRIVATE)
                            .edit().putBoolean("grid_view", gridView).apply()
                    }) {
                        Icon(if (gridView) Icons.Outlined.ViewList else Icons.Outlined.ViewModule,
                            contentDescription = if (gridView) "Switch to list view" else "Switch to grid view")
                    }
                    Box {
                        IconButton(onClick = { menuTarget = "home" }) {
                            Icon(Icons.Outlined.MoreVert, contentDescription = "Notes options")
                        }
                        DropdownMenu(expanded = menuTarget == "home", onDismissRequest = { menuTarget = null }) {
                            DropdownMenuItem(text = { Text(if (pinnedOnly) "Show all notes" else "Pinned notes") },
                                leadingIcon = { Icon(Icons.Outlined.PushPin, contentDescription = null, modifier = Modifier.size(20.dp)) },
                                onClick = { pinnedOnly = !pinnedOnly; menuTarget = null })
                            DropdownMenuItem(text = { Text(if (sortByTitle) "Sort by modified" else "Sort by title") },
                                onClick = { sortByTitle = !sortByTitle; menuTarget = null })
                            DropdownMenuItem(text = { Text("Manage labels") },
                                onClick = { menuTarget = null; showLabelManager = true })
                            DropdownMenuItem(text = { Text("Import note archive") },
                                leadingIcon = { Icon(Icons.Outlined.FileOpen, contentDescription = null, modifier = Modifier.size(20.dp)) },
                                onClick = { menuTarget = null; importLauncher.launch(arrayOf("application/zip", "application/octet-stream")) })
                        }
                    }
                }
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilterChip(selected = activeLabel == null && !pinnedOnly, onClick = { activeLabel = null; pinnedOnly = false }, label = { Text("All notes") })
                    FilterChip(selected = pinnedOnly, onClick = { pinnedOnly = !pinnedOnly; activeLabel = null }, label = { Text("Pinned") })
                    allLabels.sorted().forEach { label ->
                        FilterChip(selected = activeLabel == label, onClick = { activeLabel = if (activeLabel == label) null else label; pinnedOnly = false }, label = { Text(label) })
                    }
                }
                if (visibleNotes.isEmpty()) {
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(horizontal = 24.dp)) {
                            Icon(Icons.Outlined.NoteAdd, contentDescription = null, modifier = Modifier.size(38.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            val notesLibraryIsEmpty = notes.isEmpty() &&
                                searchQuery.isBlank() && !pinnedOnly && activeLabel == null
                            Text(
                                if (notesLibraryIsEmpty) "No notes yet" else "No matching notes",
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                    }
                } else if (gridView) {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 156.dp),
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        contentPadding = PaddingValues(start = 4.dp, top = 8.dp, end = 4.dp, bottom = 96.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        gridItems(visibleNotes, key = { it.id }) { note ->
                            val noteCardContentColor = when (note.color) {
                                "red" -> MaterialTheme.colorScheme.onErrorContainer
                                "yellow" -> MaterialTheme.colorScheme.onSecondaryContainer
                                "green", "purple" -> MaterialTheme.colorScheme.onTertiaryContainer
                                "blue" -> MaterialTheme.colorScheme.onPrimaryContainer
                                else -> MaterialTheme.colorScheme.onSurface
                            }
                            Surface(
                                modifier = Modifier.fillMaxWidth().heightIn(min = 144.dp).clickable { openNote(note) },
                                shape = RoundedCornerShape(12.dp),
                                color = when (note.color) {
                                    "red" -> MaterialTheme.colorScheme.errorContainer
                                    "yellow" -> MaterialTheme.colorScheme.secondaryContainer
                                    "green" -> MaterialTheme.colorScheme.tertiaryContainer
                                    "blue" -> MaterialTheme.colorScheme.primaryContainer
                                    "purple" -> MaterialTheme.colorScheme.tertiaryContainer
                                    else -> MaterialTheme.colorScheme.surfaceContainerLow
                                },
                                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
                                tonalElevation = 0.dp
                            ) {
                                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (note.pinned) Icon(Icons.Outlined.PushPin, contentDescription = "Pinned",
                                            tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(15.dp))
                                        Text(note.title.ifBlank { "Untitled note" }, modifier = Modifier.weight(1f),
                                            style = MaterialTheme.typography.titleMedium, color = noteCardContentColor, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                        Box {
                                            IconButton(onClick = { menuTarget = note.id }, modifier = Modifier.size(28.dp)) {
                                                Icon(Icons.Outlined.MoreVert, contentDescription = "Note actions")
                                            }
                                            DropdownMenu(expanded = menuTarget == note.id, onDismissRequest = { menuTarget = null }) {
                                                DropdownMenuItem(text = { Text(if (note.pinned) "Unpin" else "Pin") },
                                                    leadingIcon = { Icon(Icons.Outlined.PushPin, contentDescription = null, modifier = Modifier.size(20.dp)) },
                                                    onClick = {
                                                        notes = notes.map { if (it.id == note.id) it.copy(pinned = !it.pinned) else it }
                                                        saveNotes(context, notes); menuTarget = null
                                                    })
                                                DropdownMenuItem(text = { Text("Delete") },
                                                    leadingIcon = { Icon(Icons.Outlined.DeleteOutline, contentDescription = null, modifier = Modifier.size(20.dp)) },
                                                    onClick = { deleteTarget = note.id; menuTarget = null })
                                            }
                                        }
                                    }
                                    Text(note.content.ifBlank { "No content" }, style = MaterialTheme.typography.bodyMedium,
                                        color = noteCardContentColor.copy(alpha = 0.82f), maxLines = 5, overflow = TextOverflow.Ellipsis)
                                    if (note.labels.isNotEmpty()) {
                                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            note.labels.forEach { label ->
                                                Surface(shape = RoundedCornerShape(6.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
                                                    Text(label, Modifier.padding(horizontal = 6.dp, vertical = 3.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
                                                }
                                            }
                                        }
                                    }
                                    Spacer(Modifier.weight(1f))
                                    Text(Instant.ofEpochMilli(note.updatedAt).atZone(ZoneId.systemDefault())
                                        .format(DateTimeFormatter.ofPattern("d MMM, HH:mm", Locale.getDefault())),
                                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                } else {
                    LazyColumn(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        items(visibleNotes, key = { it.id }) { note ->
                            Row(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { openNote(note) }
                                    .padding(horizontal = 8.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f).padding(end = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        if (note.pinned) Icon(Icons.Outlined.PushPin, contentDescription = "Pinned",
                                            tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
                                        Text(note.title.ifBlank { "Untitled note" }, style = MaterialTheme.typography.titleMedium,
                                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                    Text(note.content.ifBlank { "No content" }, style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    if (note.labels.isNotEmpty()) {
                                        Text(note.labels.joinToString(" · "), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                    Text(Instant.ofEpochMilli(note.updatedAt).atZone(ZoneId.systemDefault())
                                        .format(DateTimeFormatter.ofPattern("d MMM, HH:mm", Locale.getDefault())),
                                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Box {
                                    IconButton(onClick = { menuTarget = note.id }) {
                                        Icon(Icons.Outlined.MoreVert, contentDescription = "Note actions")
                                    }
                                    DropdownMenu(expanded = menuTarget == note.id, onDismissRequest = { menuTarget = null }) {
                                        DropdownMenuItem(text = { Text(if (note.pinned) "Unpin" else "Pin") },
                                            leadingIcon = { Icon(Icons.Outlined.PushPin, contentDescription = null, modifier = Modifier.size(20.dp)) },
                                            onClick = {
                                                notes = notes.map { if (it.id == note.id) it.copy(pinned = !it.pinned) else it }
                                                saveNotes(context, notes); menuTarget = null
                                            })
                                        DropdownMenuItem(text = { Text("Delete") },
                                            leadingIcon = { Icon(Icons.Outlined.DeleteOutline, contentDescription = null, modifier = Modifier.size(20.dp)) },
                                            onClick = { deleteTarget = note.id; menuTarget = null })
                                    }
                                }
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
                        }
                    }
                }
            }
            FloatingActionButton(
                onClick = { createNote() },
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = BuiltInToolDesign.fabEndPadding, bottom = BuiltInToolDesign.fabBottomPadding),
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            ) { Icon(Icons.Outlined.NoteAdd, contentDescription = "New note") }
        } else {
            Column(Modifier.fillMaxSize().imePadding(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBackRequested) { Icon(Icons.Outlined.ArrowBack, contentDescription = "Back") }
                    IconButton(
                        onClick = {
                            editHistory.undo(markdownText)?.let { transition ->
                                editHistory = transition.history
                                markdownText = transition.text
                                document = NoteDocumentCodec.fromLegacyContent(markdownText)
                            }
                        },
                        enabled = editHistory.undo.isNotEmpty()
                    ) { Icon(Icons.AutoMirrored.Outlined.Undo, contentDescription = "Undo") }
                    IconButton(
                        onClick = {
                            editHistory.redo(markdownText)?.let { transition ->
                                editHistory = transition.history
                                markdownText = transition.text
                                document = NoteDocumentCodec.fromLegacyContent(markdownText)
                            }
                        },
                        enabled = editHistory.redo.isNotEmpty()
                    ) { Icon(Icons.AutoMirrored.Outlined.Redo, contentDescription = "Redo") }
                    Spacer(Modifier.weight(1f))
                    val pinned = notes.firstOrNull { it.id == selectedId }?.pinned == true
                    IconButton(onClick = {
                        notes = notes.map { if (it.id == selectedId) it.copy(pinned = !pinned) else it }
                            .sortedWith(compareByDescending<LocalNote> { it.pinned }.thenByDescending { it.updatedAt })
                        saveNotes(context, notes)
                    }) {
                        Icon(Icons.Outlined.PushPin, contentDescription = if (pinned) "Unpin note" else "Pin note",
                            tint = if (pinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Box {
                        IconButton(onClick = { menuTarget = "editor" }) {
                            Icon(Icons.Outlined.MoreVert, contentDescription = "More note actions")
                        }
                        DropdownMenu(expanded = menuTarget == "editor", onDismissRequest = { menuTarget = null }) {
                            DropdownMenuItem(text = { Text("Export Markdown (.md)") },
                                leadingIcon = { Icon(Icons.Outlined.FileDownload, contentDescription = null, modifier = Modifier.size(20.dp)) },
                                onClick = {
                                    saveCurrent()
                                    val filename = title.ifBlank { "note" }.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim().ifBlank { "note" }
                                    menuTarget = null
                                    markdownExportLauncher.launch("$filename.md")
                                })
                            DropdownMenuItem(text = { Text("Export note archive") },
                                leadingIcon = { Icon(Icons.Outlined.FileDownload, contentDescription = null, modifier = Modifier.size(20.dp)) },
                                onClick = {
                                    saveCurrent()
                                    pendingExport = PendingNoteExport(title, document, currentLabels.toList(), currentColor)
                                    val filename = title.ifBlank { "note" }.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim().ifBlank { "note" }
                                    menuTarget = null
                                    exportLauncher.launch("$filename.zip")
                                })
                            DropdownMenuItem(text = { Text("Labels") },
                                onClick = { menuTarget = null; showNoteLabels = true })
                            DropdownMenuItem(text = { Text("Note color") },
                                onClick = { menuTarget = null; showColorPicker = true })
                            DropdownMenuItem(text = { Text("Insert image") },
                                leadingIcon = { Icon(Icons.Outlined.Image, contentDescription = null, modifier = Modifier.size(20.dp)) },
                                onClick = { menuTarget = null; imageLauncher.launch(arrayOf("image/*")) })
                            DropdownMenuItem(text = { Text("Delete note") },
                                leadingIcon = { Icon(Icons.Outlined.DeleteOutline, contentDescription = null, modifier = Modifier.size(20.dp)) },
                                onClick = { deleteTarget = selectedId; menuTarget = null })
                        }
                    }
                }
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = builtInToolText("NOTE TITLE", "JUDUL CATATAN"),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            letterSpacing = 1.sp
                        )
                        BasicTextField(
                            value = title,
                            onValueChange = { title = it },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            textStyle = MaterialTheme.typography.headlineSmall.copy(
                                color = MaterialTheme.colorScheme.onSurface
                            ),
                            decorationBox = { inner ->
                                if (title.isBlank()) {
                                    Text(
                                        builtInToolText("Add a note title…", "Tambahkan judul catatan…"),
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f)
                                    )
                                }
                                inner()
                            }
                        )
                    }
                }
                if (currentLabels.isNotEmpty()) {
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        currentLabels.sorted().forEach { label ->
                            Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
                                Text(label, Modifier.padding(horizontal = 8.dp, vertical = 5.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
                            }
                        }
                    }
                }
                Column(
                    modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                    MarkdownNoteEditor(
                        value = markdownText,
                        onValueChange = { updated ->
                            if (updated != markdownText) {
                                editHistory = editHistory.record(markdownText)
                                markdownText = updated
                                document = NoteDocumentCodec.fromLegacyContent(updated)
                            }
                        },
                        modifier = Modifier.fillMaxSize().padding(vertical = 2.dp),
                        onInsertImage = { imageLauncher.launch(arrayOf("image/*")) }
                    )
                    }
                }
            }
        }
    }

    if (showLabelManager) {
        AlertDialog(
            onDismissRequest = { showLabelManager = false; newLabel = "" },
            title = { Text("Manage labels") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = newLabel, onValueChange = { newLabel = it }, singleLine = true, label = { Text("New label") })
                    allLabels.sorted().forEach { label ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(label, Modifier.weight(1f))
                            TextButton(onClick = {
                                allLabels = allLabels - label
                                notes = notes.map { it.copy(labels = it.labels - label) }
                                saveNotes(context, notes)
                                notesStore(context).edit().putString("note_labels", allLabels.sorted().joinToString(",")).apply()
                                if (activeLabel == label) activeLabel = null
                                currentLabels = currentLabels - label
                            }) { Text("Remove") }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val label = newLabel.trim()
                    if (label.isNotBlank()) allLabels = allLabels + label
                    notesStore(context).edit().putString("note_labels", allLabels.sorted().joinToString(",")).apply()
                    newLabel = ""
                    showLabelManager = false
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showLabelManager = false; newLabel = "" }) { Text("Cancel") } }
        )
    }
    if (showColorPicker) {
        AlertDialog(
            onDismissRequest = { showColorPicker = false },
            title = { Text("Note color") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("default", "red", "yellow", "green", "blue", "purple").forEach { color ->
                        val label = when (color) {
                            "default" -> "Default"
                            "red" -> "Red"
                            "yellow" -> "Yellow"
                            "green" -> "Green"
                            "blue" -> "Blue"
                            else -> "Purple"
                        }
                        FilterChip(
                            selected = currentColor == color,
                            onClick = { currentColor = color },
                            label = { Text(label) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    notes = notes.map { if (it.id == selectedId) it.copy(color = currentColor) else it }
                    saveNotes(context, notes)
                    showColorPicker = false
                }) { Text("Done") }
            }
        )
    }
    if (showNoteLabels) {
        AlertDialog(
            onDismissRequest = { showNoteLabels = false },
            title = { Text("Labels") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    if (allLabels.isEmpty()) Text("Create labels from the Notes menu first.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    allLabels.sorted().forEach { label ->
                        Row(Modifier.fillMaxWidth().clickable {
                            currentLabels = if (label in currentLabels) currentLabels - label else currentLabels + label
                        }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = label in currentLabels, onCheckedChange = { checked ->
                                currentLabels = if (checked) currentLabels + label else currentLabels - label
                            })
                            Text(label)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    notes = notes.map { if (it.id == selectedId) it.copy(labels = currentLabels.toList()) else it }
                    saveNotes(context, notes)
                    showNoteLabels = false
                }) { Text("Done") }
            }
        )
    }

    if (deleteTarget != null) {
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete note?") },
            text = { Text("This note will be permanently deleted from this device.") },
            confirmButton = {
                TextButton(onClick = {
                    val target = deleteTarget
                    val removed = notes.firstOrNull { it.id == target }
                    notes = notes.filterNot { it.id == target }
                    saveNotes(context, notes)
                    removed?.let { releaseUnusedNoteImageResources(context, it, notes) }
                    if (selectedId == target) {
                        selectedId = null
                        title = ""
                        document = NoteDocument()
                        markdownText = ""
                        onEditingChanged(false)
                    }
                    deleteTarget = null
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
    onHistoryAvailabilityChanged: (Boolean) -> Unit,
    scientificMode: Boolean,
    onScientificModeChange: (Boolean) -> Unit
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
        listOf("±", "0", ".", "=")
    )

    BoxWithConstraints(modifier = modifier) {
        val keySize = minOf(72.dp, (maxWidth - 24.dp) / 4, (maxHeight - if (scientificMode) 240.dp else 170.dp) / 5)
            .coerceAtLeast(36.dp)

        if (showHistory) {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (history.isEmpty()) {
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text("No calculations yet", style = MaterialTheme.typography.titleMedium)
                        }
                    }
                } else {
                    LazyColumn(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(history, key = { it.id }) { entry ->
                            Surface(
                                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(BuiltInToolDesign.cardCornerRadius)).clickable {
                                    expression = entry.expression
                                    result = entry.result
                                    onShowHistoryChange(false)
                                },
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                                shape = RoundedCornerShape(BuiltInToolDesign.cardCornerRadius)
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
                if (scientificMode) {
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        listOf(
                            "sin" to "sin(", "cos" to "cos(", "tan" to "tan(",
                            "√" to "sqrt(", "ln" to "ln(", "log" to "log(",
                            "xʸ" to "^", "π" to "π", "e" to "e"
                        ).forEach { (label, token) ->
                            TextButton(onClick = { expression += token; result = "" }) {
                                Text(label, maxLines = 1, softWrap = false)
                            }
                        }
                    }
                }
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
                                                result = builtInToolText("Invalid expression", "Ekspresi tidak valid")
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
                                    Button(onClick = action, modifier = Modifier.size(keySize), shape = RoundedCornerShape(BuiltInToolDesign.calculatorKeyCornerRadius),
                                        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                                        Text(key, fontSize = labelSize, lineHeight = labelSize)
                                    }
                                } else {
                                    FilledTonalButton(onClick = action, modifier = Modifier.size(keySize), shape = RoundedCornerShape(BuiltInToolDesign.calculatorKeyCornerRadius),
                                        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                                        Text(key, fontSize = labelSize, lineHeight = labelSize)
                                    }
                                }
                        }
                    }
                }
            }
        }    }

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
            dismissButton = { TextButton(onClick = { showClearHistoryDialog = false }) { Text(builtInToolText("Cancel", "Batal")) } }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CalendarTool(
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
        selectedDate = LocalDate.parse(task.date)
        onMonthChange(YearMonth.from(selectedDate))
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
        date = runCatching { LocalDate.parse(task.date) }.getOrDefault(selectedDate)
        time = runCatching { LocalTime.parse(task.time) }.getOrDefault(LocalTime.of(9, 0))
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
            val timestamp = if (timed) runCatching {
                LocalDate.parse(task.date).atTime(LocalTime.parse(task.time))
                    .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            }.getOrDefault(Long.MAX_VALUE) else Long.MAX_VALUE
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
    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
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
                    Text("Today")
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
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)) {
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
                                .padding(vertical = 10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(5.dp)
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
                            Box(Modifier.size(4.dp).clip(CircleShape).background(if (hasItems) {
                                if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary
                            } else Color.Transparent))
                        }
                    }
                }
            }
        } else {
            Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(BuiltInToolDesign.cardCornerRadius),
            color = MaterialTheme.colorScheme.surfaceContainerLow) {
            Column(Modifier.padding(horizontal = 8.dp, vertical = 8.dp)) {
                Row(Modifier.fillMaxWidth()) {
                    (0..6).map { calendarFirstDay.plus(it.toLong()) }.forEach { weekDay ->
                        Text(weekDay.getDisplayName(java.time.format.TextStyle.NARROW, Locale.getDefault()),
                            Modifier.weight(1f).padding(vertical = 4.dp), textAlign = TextAlign.Center,
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
                        Column(modifier = Modifier.fillMaxWidth().height(38.dp).padding(1.dp)
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
                            Box(Modifier.padding(top = 2.dp).size(4.dp).clip(CircleShape).background(if (hasTasks) {
                                if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary
                            } else Color.Transparent))
                        }
                    } else Box(Modifier.fillMaxWidth().height(38.dp))
                })
            }
        }
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(if (selectedDate == LocalDate.now()) "Today" else selectedDate.format(dateFormatter),
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
                Text(
                    if (!calendarPermission) "Calendar access is off" else "Nothing scheduled",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 24.dp)
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = 72.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(agendaItems, key = { it.key }) { item ->
                    val event = item.event
                    val task = item.task
                    if (event != null) {
                        Row(Modifier.fillMaxWidth().clickable { openDeviceCalendarEvent(context, event) }
                            .padding(horizontal = 4.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(if (event.allDay) builtInToolText("All day", "Sepanjang hari") else SimpleDateFormat(
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
                    } else if (task != null) {
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
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 10.dp).size(20.dp))
                        }
                        Column(Modifier.weight(1f).padding(vertical = 6.dp, horizontal = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(task.title, style = MaterialTheme.typography.bodyLarge,
                                textDecoration = if (task.completed) TextDecoration.LineThrough else TextDecoration.None,
                                color = if (task.completed) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Surface(color = priorityColor(task.priority), shape = RoundedCornerShape(50)) {                                    Text(priorityLabel(task.priority), modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface)
                                }
                                if (task.time.isNotBlank()) {
                                    Text(task.time, style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                } else {
                                    Text("No time", style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                if (task.type == "reminder") Text("Reminder", style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary)
                            }
                        }
                        IconButton(onClick = { beginEditTask(task) }) {
                            Icon(Icons.Outlined.Edit, contentDescription = builtInToolText("Edit task", "Edit tugas"),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                        }
                        IconButton(onClick = { deleteTarget = task }) {
                            Icon(Icons.Outlined.DeleteOutline, contentDescription = builtInToolText("Delete task", "Hapus tugas"),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }
        }
    }
    }
        FloatingActionButton(
            onClick = ::beginNewTask,
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = BuiltInToolDesign.fabEndPadding, bottom = BuiltInToolDesign.fabBottomPadding),
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        ) { Icon(Icons.Outlined.Add, contentDescription = builtInToolText("Add task", "Tambah tugas")) }
    }

    if (showEditor) {
        AlertDialog(
            onDismissRequest = { showEditor = false },
            title = { Text(if (editingTaskId == null) builtInToolText("New task", "Tugas baru") else builtInToolText("Edit task", "Edit tugas")) },
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
                        Icon(Icons.Outlined.Event, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.size(8.dp))
                        Text(date.format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault())))
                    }
                    if (type == "reminder") {
                        TextButton(onClick = { showTimePicker = true }, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Outlined.Schedule, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.size(8.dp))
                            Text(time.format(DateTimeFormatter.ofPattern("HH:mm")))
                        }
                    }
                    Text("Priority", style = MaterialTheme.typography.labelLarge)
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
                                "Select date",
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
                            Text("Cancel")
                        }
                        TextButton(onClick = {
                            datePickerState.selectedDateMillis?.let { millis ->
                                date = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                            }
                            showDatePicker = false
                        }) {
                            Text("Select")
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
            title = { Text("Select time") },
            text = { TimePicker(state = timePickerState) },
            confirmButton = {
                TextButton(onClick = {
                    time = LocalTime.of(timePickerState.hour, timePickerState.minute)
                    showTimePicker = false
                }) { Text("Select") }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) {
                    Text("Cancel")
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