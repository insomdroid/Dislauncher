package com.insomdroid.dislauncher

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

internal data class LocalNote(
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


internal data class PendingNoteExport(val title: String, val document: NoteDocument, val labels: List<String>, val color: String)


internal fun notesStore(context: Context) =
    context.getSharedPreferences("dislauncher_notes", Context.MODE_PRIVATE)


internal fun showNotesWarning(context: Context, message: String) {
    android.os.Handler(android.os.Looper.getMainLooper()).post {
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
    }
}


internal fun preserveCorruptNotes(context: Context, rawJson: String) {
    val store = notesStore(context)
    if (!store.contains("notes_json_recovery_backup")) {
        store.edit()
            .putString("notes_json_recovery_backup", rawJson)
            .putLong("notes_json_recovery_backup_at", System.currentTimeMillis())
            .apply()
    }
    showNotesWarning(
        context,
        "Notes data could not be read safely. A recovery copy was preserved; the original data was not overwritten."
    )
}


internal fun loadNotes(context: Context): List<LocalNote> {
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
    } catch (_: JSONException) {
        // Preserve the original value and keep a separate recovery copy before returning an empty view.
        preserveCorruptNotes(context, rawJson)
        return emptyList()
    } catch (_: IllegalArgumentException) {
        // NoteDocumentCodec rejects unsupported document versions with require(); treat them as corrupt data.
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


internal fun saveNotes(context: Context, notes: List<LocalNote>) {
    // Do not replace recoverable user data if the current store contains malformed entries.
    val existingRaw = notesStore(context).getString("notes_json", "[]") ?: "[]"
    val existingStoreIsSafe = runCatching {
        val existing = JSONArray(existingRaw)
        (0 until existing.length()).all { index ->
            val item = existing.optJSONObject(index)
            item != null && item.optString("id").isNotBlank()
        }
    }.getOrDefault(false)
    if (!existingStoreIsSafe) {
        preserveCorruptNotes(context, existingRaw)
        return
    }

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
internal fun releaseUnusedNoteImageResources(
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


@Composable
internal fun NotesTool(
    context: Context,
    backSignal: Int,
    onEditingChanged: (Boolean) -> Unit,
    onBackRequested: () -> Unit,
    modifier: Modifier = Modifier
) {
    var notes by remember(context) { mutableStateOf(emptyList<LocalNote>()) }
    var notesLoaded by remember(context) { mutableStateOf(false) }
    var selectedId by remember { mutableStateOf<String?>(null) }
    var title by remember { mutableStateOf("") }
    var markdownText by remember { mutableStateOf("") }
    var markdownCursor by remember { mutableStateOf(0) }
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

    LaunchedEffect(context) {
        notes = withContext(Dispatchers.IO) { loadNotes(context) }
        notesLoaded = true
    }

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
        markdownCursor = markdownCursor.coerceIn(0, markdownText.length)
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
        markdownCursor = markdownText.length
        currentLabels = note.labels.toSet()
        currentColor = note.color
        editHistory = MarkdownEditHistory()
        onEditingChanged(true)
    }
    fun createNote() {
        selectedId = UUID.randomUUID().toString()
        title = ""
        markdownText = ""
        markdownCursor = 0
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
                        "Could not import note archive: " + it.message.orEmpty(),
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
                    .onFailure { Toast.makeText(context, "Could not export note archive: " + it.message.orEmpty(), Toast.LENGTH_LONG).show() }
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
            editHistory = editHistory.record(markdownText, markdownCursor)
            markdownText = markdownText.trimEnd() + "\n\n![Image](" + uri.toString() + ")"
            markdownCursor = markdownText.length
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

    val visibleNotes = remember(notes, pinnedOnly, activeLabel, searchQuery, sortByTitle) {
        notes.filter { note ->
            (!pinnedOnly || note.pinned) &&
                (activeLabel == null || activeLabel in note.labels) &&
                (searchQuery.isBlank() || note.title.contains(searchQuery, true) ||
                    note.content.contains(searchQuery, true))
        }.let { list ->
            if (sortByTitle) list.sortedWith(compareByDescending<LocalNote> { it.pinned }.thenBy { it.title.lowercase() })
            else list.sortedWith(compareByDescending<LocalNote> { it.pinned }.thenByDescending { it.updatedAt })
        }
    }

    BuiltInToolScaffold(
        modifier = modifier,
        header = {
            if (!notesLoaded || selectedId == null) {
                    BuiltInToolHeader(
                        title = {
                            if (searchExpanded) {
                                BuiltInToolSearchField(
                                    value = searchQuery,
                                    onValueChange = { searchQuery = it },
                                    modifier = Modifier.fillMaxWidth(),
                                    placeholder = "Search notes",
                                    trailingIcon = {
                                        IconButton(onClick = { searchQuery = ""; searchExpanded = false }) {
                                            Icon(painterResource(R.drawable.ic_symbol_close), contentDescription = "Close search", modifier = Modifier.size(LauncherIconTokens.standard))
                                        }
                                    }
                                )
                            } else {
                                Text(
                                    "Notes",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1
                                )
                            }
                        },
                        navigationIcon = {
                            IconButton(onClick = onBackRequested) {
                                Icon(painterResource(R.drawable.ic_symbol_arrow_back), contentDescription = "Back", modifier = Modifier.size(LauncherIconTokens.standard))
                            }
                        },
                        actions = {
                            if (!searchExpanded) {
                                IconButton(onClick = { searchExpanded = true }) {
                                    Icon(painterResource(R.drawable.ic_symbol_search), contentDescription = "Search notes", modifier = Modifier.size(LauncherIconTokens.standard))
                                }
                            }
                            IconButton(onClick = {
                                gridView = !gridView
                                context.getSharedPreferences("dislauncher_notes", Context.MODE_PRIVATE)
                                    .edit().putBoolean("grid_view", gridView).apply()
                            }) {
                                Icon(painterResource(if (gridView) R.drawable.ic_symbol_view_list else R.drawable.ic_symbol_view_module),
                                    contentDescription = if (gridView) "Switch to list view" else "Switch to grid view", modifier = Modifier.size(LauncherIconTokens.standard))
                            }
                            Box {
                                IconButton(onClick = { menuTarget = "home" }) {
                                    Icon(painterResource(R.drawable.ic_symbol_more_vert), contentDescription = "Notes options", modifier = Modifier.size(LauncherIconTokens.standard))
                                }
                                DropdownMenu(expanded = menuTarget == "home", onDismissRequest = { menuTarget = null }) {
                                    DropdownMenuItem(text = { Text(if (pinnedOnly) "Show all notes" else "Pinned notes") },
                                        leadingIcon = { Icon(painterResource(R.drawable.ic_symbol_push_pin), contentDescription = null, modifier = Modifier.size(LauncherIconTokens.compact)) },
                                        onClick = { pinnedOnly = !pinnedOnly; menuTarget = null })
                                    DropdownMenuItem(text = { Text(if (sortByTitle) "Sort by modified" else "Sort by title") },
                                        onClick = { sortByTitle = !sortByTitle; menuTarget = null })
                                    DropdownMenuItem(text = { Text("Manage labels") },
                                        onClick = { menuTarget = null; showLabelManager = true })
                                    DropdownMenuItem(text = { Text("Import note archive") },
                                        leadingIcon = { Icon(painterResource(R.drawable.ic_symbol_file_open), contentDescription = null, modifier = Modifier.size(LauncherIconTokens.compact)) },
                                        onClick = { menuTarget = null; importLauncher.launch(arrayOf("application/zip", "application/octet-stream")) })
                                }
                            }
                        }
                    )
            } else {
                BuiltInToolHeader(
                    title = {
                        Text(
                            title.ifBlank { "Untitled note" },
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBackRequested) {
                            Icon(painterResource(R.drawable.ic_symbol_arrow_back), contentDescription = "Back", modifier = Modifier.size(LauncherIconTokens.standard))
                        }
                    }
                )
            }
        }
    ) {
        if (!notesLoaded) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Loading notes…", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else if (selectedId == null) {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(BuiltInToolSpacing.section)) {
                Column(
                    Modifier.weight(1f).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(BuiltInToolSpacing.section)
                ) {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = BuiltInToolDesign.filterHorizontalInset),
                    horizontalArrangement = Arrangement.spacedBy(BuiltInToolSpacing.space8),
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
                        val notesLibraryIsEmpty = notes.isEmpty() &&
                            searchQuery.isBlank() && !pinnedOnly && activeLabel == null
                        BuiltInEmptyState(
                            title = if (notesLibraryIsEmpty) "No notes yet" else "No matching notes",
                            icon = {
                                Icon(painterResource(R.drawable.ic_symbol_note_add), contentDescription = null, modifier = Modifier.size(LauncherIconTokens.illustration),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        )
                    }
                } else if (gridView) {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 156.dp),
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        contentPadding = PaddingValues(top = BuiltInToolSpacing.space8, bottom = 96.dp),
                        horizontalArrangement = Arrangement.spacedBy(BuiltInToolSpacing.space10),
                        verticalArrangement = Arrangement.spacedBy(BuiltInToolSpacing.space10)
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
                                shape = RoundedCornerShape(BuiltInToolDesign.cardCornerRadius),
                                color = when (note.color) {
                                    "red" -> MaterialTheme.colorScheme.errorContainer
                                    "yellow" -> MaterialTheme.colorScheme.secondaryContainer
                                    "green" -> MaterialTheme.colorScheme.tertiaryContainer
                                    "blue" -> MaterialTheme.colorScheme.primaryContainer
                                    "purple" -> MaterialTheme.colorScheme.tertiaryContainer
                                    else -> MaterialTheme.colorScheme.surfaceContainerLow
                                },
                                border = androidx.compose.foundation.BorderStroke(BuiltInToolSpacing.space1, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
                                tonalElevation = 0.dp
                            ) {
                                Column(Modifier.padding(BuiltInToolSpacing.card), verticalArrangement = Arrangement.spacedBy(BuiltInToolSpacing.section)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (note.pinned) Icon(painterResource(R.drawable.ic_symbol_push_pin), contentDescription = "Pinned",
                                            tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(LauncherIconTokens.status))
                                        Text(note.title.ifBlank { "Untitled note" }, modifier = Modifier.weight(1f),
                                            style = MaterialTheme.typography.titleMedium, color = noteCardContentColor, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                        Box {
                                            IconButton(onClick = { menuTarget = note.id }, modifier = Modifier.size(LauncherIconTokens.touchTarget)) {
                                                Icon(painterResource(R.drawable.ic_symbol_more_vert), contentDescription = "Note actions", modifier = Modifier.size(LauncherIconTokens.standard))
                                            }
                                            DropdownMenu(expanded = menuTarget == note.id, onDismissRequest = { menuTarget = null }) {
                                                DropdownMenuItem(text = { Text(if (note.pinned) "Unpin" else "Pin") },
                                                    leadingIcon = { Icon(painterResource(R.drawable.ic_symbol_push_pin), contentDescription = null, modifier = Modifier.size(LauncherIconTokens.compact)) },
                                                    onClick = {
                                                        notes = notes.map { if (it.id == note.id) it.copy(pinned = !it.pinned) else it }
                                                        saveNotes(context, notes); menuTarget = null
                                                    })
                                                DropdownMenuItem(text = { Text("Delete") },
                                                    leadingIcon = { Icon(painterResource(R.drawable.ic_symbol_delete), contentDescription = null, modifier = Modifier.size(LauncherIconTokens.compact)) },
                                                    onClick = { deleteTarget = note.id; menuTarget = null })
                                            }
                                        }
                                    }
                                    Text(note.content.ifBlank { "No content" }, style = MaterialTheme.typography.bodyMedium,
                                        color = noteCardContentColor.copy(alpha = 0.82f), maxLines = 5, overflow = TextOverflow.Ellipsis)
                                    if (note.labels.isNotEmpty()) {
                                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(BuiltInToolSpacing.space4)) {
                                            note.labels.forEach { label ->
                                                Surface(shape = RoundedCornerShape(LauncherShapeTokens.small), color = MaterialTheme.colorScheme.secondaryContainer) {
                                                    Text(label, Modifier.padding(horizontal = BuiltInToolSpacing.space6, vertical = BuiltInToolSpacing.space3), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
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
                    LazyColumn(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(BuiltInToolSpacing.space2)) {
                        items(visibleNotes, key = { it.id }) { note ->
                            Row(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(BuiltInToolDesign.cardCornerRadius)).clickable { openNote(note) }
                                    .padding(horizontal = BuiltInToolSpacing.listRowHorizontal, vertical = BuiltInToolSpacing.listRowVertical),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f).padding(end = BuiltInToolSpacing.space8), verticalArrangement = Arrangement.spacedBy(BuiltInToolSpacing.space4)) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(BuiltInToolSpacing.space6)) {
                                        if (note.pinned) Icon(painterResource(R.drawable.ic_symbol_push_pin), contentDescription = "Pinned",
                                            tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(LauncherIconTokens.status))
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
                                        Icon(painterResource(R.drawable.ic_symbol_more_vert), contentDescription = "Note actions", modifier = Modifier.size(LauncherIconTokens.standard))
                                    }
                                    DropdownMenu(expanded = menuTarget == note.id, onDismissRequest = { menuTarget = null }) {
                                        DropdownMenuItem(text = { Text(if (note.pinned) "Unpin" else "Pin") },
                                            leadingIcon = { Icon(painterResource(R.drawable.ic_symbol_push_pin), contentDescription = null, modifier = Modifier.size(LauncherIconTokens.compact)) },
                                            onClick = {
                                                notes = notes.map { if (it.id == note.id) it.copy(pinned = !it.pinned) else it }
                                                saveNotes(context, notes); menuTarget = null
                                            })
                                        DropdownMenuItem(text = { Text("Delete") },
                                            leadingIcon = { Icon(painterResource(R.drawable.ic_symbol_delete), contentDescription = null, modifier = Modifier.size(LauncherIconTokens.compact)) },
                                            onClick = { deleteTarget = note.id; menuTarget = null })
                                    }
                                }
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
                        }
                    }
                }
                }
            }
            FloatingActionButton(
                onClick = { createNote() },
                modifier = Modifier.align(Alignment.BottomEnd).padding(bottom = BuiltInToolDesign.fabBottomPadding).size(BuiltInToolDesign.fabSize),
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            ) { Icon(painterResource(R.drawable.ic_symbol_note_add), contentDescription = "New note", modifier = Modifier.size(LauncherIconTokens.standard)) }
        } else {
            Column(Modifier.fillMaxSize().imePadding(), verticalArrangement = Arrangement.spacedBy(BuiltInToolSpacing.section)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = {
                            editHistory.undo(markdownText, markdownCursor)?.let { transition ->
                                editHistory = transition.history
                                markdownText = transition.text
                                markdownCursor = transition.cursor
                            }
                        },
                        enabled = editHistory.undo.isNotEmpty()
                    ) { Icon(painterResource(R.drawable.ic_symbol_undo), contentDescription = "Undo", modifier = Modifier.size(LauncherIconTokens.standard)) }
                    IconButton(
                        onClick = {
                            editHistory.redo(markdownText, markdownCursor)?.let { transition ->
                                editHistory = transition.history
                                markdownText = transition.text
                                markdownCursor = transition.cursor
                            }
                        },
                        enabled = editHistory.redo.isNotEmpty()
                    ) { Icon(painterResource(R.drawable.ic_symbol_redo), contentDescription = "Redo", modifier = Modifier.size(LauncherIconTokens.standard)) }
                    Spacer(Modifier.weight(1f))
                    val pinned = notes.firstOrNull { it.id == selectedId }?.pinned == true
                    IconButton(onClick = {
                        notes = notes.map { if (it.id == selectedId) it.copy(pinned = !pinned) else it }
                            .sortedWith(compareByDescending<LocalNote> { it.pinned }.thenByDescending { it.updatedAt })
                        saveNotes(context, notes)
                    }) {
                        Icon(painterResource(R.drawable.ic_symbol_push_pin), contentDescription = if (pinned) "Unpin note" else "Pin note",
                            tint = if (pinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(LauncherIconTokens.standard))
                    }
                    Box {
                        IconButton(onClick = { menuTarget = "editor" }) {
                            Icon(painterResource(R.drawable.ic_symbol_more_vert), contentDescription = "More note actions", modifier = Modifier.size(LauncherIconTokens.standard))
                        }
                        DropdownMenu(expanded = menuTarget == "editor", onDismissRequest = { menuTarget = null }) {
                            DropdownMenuItem(text = { Text("Export Markdown (.md)") },
                                leadingIcon = { Icon(painterResource(R.drawable.ic_symbol_file_download), contentDescription = null, modifier = Modifier.size(LauncherIconTokens.compact)) },
                                onClick = {
                                    saveCurrent()
                                    val filename = title.ifBlank { "note" }.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim().ifBlank { "note" }
                                    menuTarget = null
                                    markdownExportLauncher.launch("$filename.md")
                                })
                            DropdownMenuItem(text = { Text("Export note archive") },
                                leadingIcon = { Icon(painterResource(R.drawable.ic_symbol_file_download), contentDescription = null, modifier = Modifier.size(LauncherIconTokens.compact)) },
                                onClick = {
                                    saveCurrent()
                                    pendingExport = PendingNoteExport(
                                        title,
                                        NoteDocumentCodec.fromLegacyContent(markdownText),
                                        currentLabels.toList(),
                                        currentColor
                                    )
                                    val filename = title.ifBlank { "note" }.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim().ifBlank { "note" }
                                    menuTarget = null
                                    exportLauncher.launch("$filename.zip")
                                })
                            DropdownMenuItem(text = { Text("Labels") },
                                onClick = { menuTarget = null; showNoteLabels = true })
                            DropdownMenuItem(text = { Text("Note color") },
                                onClick = { menuTarget = null; showColorPicker = true })
                            DropdownMenuItem(text = { Text("Insert image") },
                                leadingIcon = { Icon(painterResource(R.drawable.ic_symbol_image), contentDescription = null, modifier = Modifier.size(LauncherIconTokens.compact)) },
                                onClick = { menuTarget = null; imageLauncher.launch(arrayOf("image/*")) })
                            DropdownMenuItem(text = { Text("Delete note") },
                                leadingIcon = { Icon(painterResource(R.drawable.ic_symbol_delete), contentDescription = null, modifier = Modifier.size(LauncherIconTokens.compact)) },
                                onClick = { deleteTarget = selectedId; menuTarget = null })
                        }
                    }
                }
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = BuiltInToolSpacing.space12, vertical = BuiltInToolSpacing.space4),
                    shape = RoundedCornerShape(BuiltInToolDesign.cardCornerRadius),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    border = BorderStroke(BuiltInToolSpacing.space1, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = BuiltInToolSpacing.space10),
                        verticalArrangement = Arrangement.spacedBy(BuiltInToolSpacing.space4)
                    ) {
                        Text(
                            text = "NOTE TITLE",
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
                                        "Add a note title…",
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
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = BuiltInToolSpacing.space12), horizontalArrangement = Arrangement.spacedBy(BuiltInToolSpacing.space6)) {
                        currentLabels.sorted().forEach { label ->
                            Surface(shape = RoundedCornerShape(LauncherShapeTokens.medium), color = MaterialTheme.colorScheme.secondaryContainer) {
                                Text(label, Modifier.padding(horizontal = BuiltInToolSpacing.space8, vertical = BuiltInToolSpacing.space5), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
                            }
                        }
                    }
                }
                Column(
                    modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = BuiltInToolSpacing.space12, vertical = BuiltInToolSpacing.space4),
                    verticalArrangement = Arrangement.spacedBy(BuiltInToolSpacing.space6)
                ) {
                    Surface(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        shape = RoundedCornerShape(BuiltInToolDesign.cardCornerRadius),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        border = BorderStroke(BuiltInToolSpacing.space1, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                    MarkdownNoteEditor(
                        value = markdownText,
                        selection = markdownCursor,
                        onValueChange = { updated ->
                            if (updated != markdownText) {
                                editHistory = editHistory.record(markdownText, markdownCursor)
                                markdownText = updated
                            }
                        },
                        onSelectionChange = { markdownCursor = it },
                        modifier = Modifier.fillMaxSize().padding(vertical = BuiltInToolSpacing.space2),
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
                Column(verticalArrangement = Arrangement.spacedBy(BuiltInToolSpacing.space8)) {
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
                Column(verticalArrangement = Arrangement.spacedBy(BuiltInToolSpacing.space8)) {
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
                Column(verticalArrangement = Arrangement.spacedBy(BuiltInToolSpacing.space2)) {
                    if (allLabels.isEmpty()) Text("Create labels from the Notes menu first.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    allLabels.sorted().forEach { label ->
                        Row(Modifier.fillMaxWidth().clickable {
                            currentLabels = if (label in currentLabels) currentLabels - label else currentLabels + label
                        }.padding(vertical = BuiltInToolSpacing.space6), verticalAlignment = Alignment.CenterVertically) {
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
