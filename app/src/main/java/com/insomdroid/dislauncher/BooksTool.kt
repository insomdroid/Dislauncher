package com.insomdroid.dislauncher

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.BookmarkAdded
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.ViewList
import androidx.compose.material.icons.outlined.ViewModule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID
import java.util.zip.ZipFile

private data class BookEntry(
    val id: String,
    val uri: String,
    val title: String,
    val type: String,
    val position: Int = 0,
    val bookmarks: List<Int> = emptyList()
)

private data class BookContent(
    val pages: List<String>,
    val chapters: List<Pair<String, Int>> = emptyList(),
    val pdfFile: File? = null
)

private fun readBookLibrary(context: Context): List<BookEntry> {
    val raw = context.getSharedPreferences("dislauncher_books", Context.MODE_PRIVATE)
        .getString("library", "[]") ?: "[]"
    return runCatching {
        val array = JSONArray(raw)
        (0 until array.length()).mapNotNull { i ->
            val o = array.optJSONObject(i) ?: return@mapNotNull null
            val uri = o.optString("uri").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            BookEntry(
                id = o.optString("id", uri),
                uri = uri,
                title = o.optString("title", "Untitled"),
                type = o.optString("type", "txt"),
                position = o.optInt("position", 0).coerceAtLeast(0),
                bookmarks = (o.optJSONArray("bookmarks") ?: JSONArray()).let { a ->
                    (0 until a.length()).map { a.optInt(it) }.filter { it >= 0 }
                }
            )
        }
    }.getOrDefault(emptyList())
}

private fun writeBookLibrary(context: Context, books: List<BookEntry>) {
    val array = JSONArray()
    books.forEach { book ->
        array.put(JSONObject().apply {
            put("id", book.id); put("uri", book.uri); put("title", book.title)
            put("type", book.type); put("position", book.position)
            put("bookmarks", JSONArray(book.bookmarks))
        })
    }
    context.getSharedPreferences("dislauncher_books", Context.MODE_PRIVATE)
        .edit().putString("library", array.toString()).apply()
}

private fun displayName(context: Context, uri: Uri): String {
    return runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
    }.getOrNull()?.takeIf { it.isNotBlank() } ?: (uri.lastPathSegment ?: "Book")
}

private fun bookType(name: String, uri: Uri): String {
    val value = name.substringAfterLast('.', "").lowercase()
    return when {
        value == "pdf" -> "pdf"
        value == "epub" -> "epub"
        value == "txt" || value == "text" -> "txt"
        uri.toString().contains("pdf", true) -> "pdf"
        else -> value
    }
}

private fun copyBookToCache(context: Context, uri: Uri, suffix: String): File {
    val target = File(context.cacheDir, "book_${uri.toString().hashCode()}.$suffix")
    if (!target.exists() || target.length() == 0L) {
        context.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "Unable to open document" }
            target.outputStream().use { output -> input.copyTo(output) }
        }
    }
    return target
}

private fun splitPages(text: String, limit: Int = 2600): List<String> {
    val normalized = text.replace("\r\n", "\n").replace('\r', '\n')
        .replace(Regex("[\\t ]+"), " ").trim()
    if (normalized.isBlank()) return listOf("This book contains no readable text.")
    val result = mutableListOf<String>()
    var start = 0
    while (start < normalized.length) {
        var end = (start + limit).coerceAtMost(normalized.length)
        if (end < normalized.length) {
            val breakAt = normalized.lastIndexOfAny(charArrayOf('\n', ' ', '.', '!', '?'), end)
            if (breakAt > start + limit / 2) end = breakAt + 1
        }
        result += normalized.substring(start, end).trim()
        start = end
    }
    return result.ifEmpty { listOf(normalized) }
}

private fun decodeEntities(text: String): String = text
    .replace("&nbsp;", " ").replace("&amp;", "&").replace("&lt;", "<")
    .replace("&gt;", ">").replace("&quot;", "\"").replace("&#39;", "'")
    .replace(Regex("&#(\\d+);")) { match -> match.groupValues[1].toIntOrNull()?.toChar()?.toString() ?: "" }

private fun htmlToText(html: String): String = decodeEntities(
    html.replace(Regex("(?is)<(script|style|head|svg)[^>]*>.*?</\\1>"), " ")
        .replace(Regex("(?i)<br\\s*/?>|</(p|div|h[1-6]|li|tr|section|article)>"), "\n\n")
        .replace(Regex("(?s)<[^>]+>"), " ")
).replace(Regex("[ \\t]+"), " ").replace(Regex(" *\\n *"), "\n")
    .replace(Regex("\\n{3,}"), "\n\n").trim()

private fun loadBookContent(context: Context, book: BookEntry): BookContent {
    val uri = Uri.parse(book.uri)
    return when (book.type) {
        "pdf" -> {
            val file = copyBookToCache(context, uri, "pdf")
            val count = PdfRenderer(android.os.ParcelFileDescriptor.open(file, android.os.ParcelFileDescriptor.MODE_READ_ONLY)).use { it.pageCount }
            BookContent((0 until count).map { "PDF page ${it + 1}" }, pdfFile = file)
        }
        "epub" -> {
            val file = copyBookToCache(context, uri, "epub")
            ZipFile(file).use { zip ->
                val opfPath = zip.getEntry("META-INF/container.xml")?.let { entry ->
                    zip.getInputStream(entry).bufferedReader().use { it.readText() }
                        .let { Regex("full-path=[\"']([^\"']+)[\"']").find(it)?.groupValues?.get(1) }
                }
                val opf = opfPath?.let { zip.getEntry(it) }?.let { entry ->
                    zip.getInputStream(entry).bufferedReader().use { it.readText() }
                }
                val base = opfPath?.substringBeforeLast('/', "")?.let { if (it.isBlank()) "" else "$it/" } ?: ""
                val manifest = opf?.let { source ->
                    Regex("(?is)<item\\b([^>]*?)>").findAll(source).associate { match ->
                        val attrs = match.groupValues[1]
                        val id = Regex("id=[\"']([^\"']+)[\"']").find(attrs)?.groupValues?.get(1).orEmpty()
                        val href = Regex("href=[\"']([^\"']+)[\"']").find(attrs)?.groupValues?.get(1).orEmpty()
                        id to href
                    }
                }.orEmpty()
                val spineIds = opf?.let { source ->
                    Regex("(?is)<itemref\\b[^>]*?idref=[\"']([^\"']+)[\"'][^>]*/?>").findAll(source)
                        .map { it.groupValues[1] }.toList()
                }.orEmpty()
                val paths = spineIds.mapNotNull { manifest[it] }.ifEmpty {
                    zip.entries().asSequence().map { it.name }
                        .filter { it.endsWith(".xhtml", true) || it.endsWith(".html", true) || it.endsWith(".htm", true) }
                        .toList()
                }
                val sections = paths.mapNotNull { relative ->
                    val normalized = java.net.URI(base + relative).normalize().path.removePrefix("/")
                    val entry = zip.getEntry(normalized) ?: zip.getEntry(base + relative) ?: return@mapNotNull null
                    val text = zip.getInputStream(entry).bufferedReader().use { htmlToText(it.readText()) }
                    text.takeIf { it.isNotBlank() }?.let { relative.substringAfterLast('/').substringBeforeLast('.') to it }
                }
                val combined = sections.joinToString("\n\n") { it.second }
                val pages = splitPages(combined)
                val chapters = sections.mapNotNull { (title, text) ->
                    val index = combined.indexOf(text).takeIf { it >= 0 } ?: return@mapNotNull null
                    val position = pages.indexOfFirst { page -> combined.indexOf(page).let { it >= 0 && it <= index && index < it + page.length } }
                    title.replace(Regex("[-_]"), " ").takeIf { it.isNotBlank() }?.let { it to position.coerceAtLeast(0) }
                }
                BookContent(pages, chapters)
            }
        }
        else -> {
            val text = context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                ?: throw IllegalStateException("Unable to read this text file")
            BookContent(splitPages(text))
        }
    }
}

private fun renderPdfPage(file: File, pageIndex: Int): Bitmap {
    val descriptor = android.os.ParcelFileDescriptor.open(file, android.os.ParcelFileDescriptor.MODE_READ_ONLY)
    PdfRenderer(descriptor).use { renderer ->
        renderer.openPage(pageIndex.coerceIn(0, renderer.pageCount - 1)).use { page ->
            val width = 1200
            val height = (width.toFloat() * page.height / page.width).toInt().coerceAtLeast(1)
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(android.graphics.Color.WHITE)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            return bitmap
        }
    }
}

@Composable
internal fun BooksTool(onBack: () -> Unit) {
    val context = LocalContext.current
    var books by remember { mutableStateOf(readBookLibrary(context)) }
    var selectedBook by remember { mutableStateOf<BookEntry?>(null) }
    var bookContent by remember { mutableStateOf<BookContent?>(null) }
    var currentPage by remember { mutableStateOf(0) }
    var pageDirection by remember { mutableStateOf(1) }
    val pageScrollState = rememberScrollState()
    var fontSize by remember { mutableStateOf(20f) }
    var showMenu by remember { mutableStateOf(false) }
    var menuSection by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var pdfBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var pendingDelete by remember { mutableStateOf<BookEntry?>(null) }
    var gridView by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching { context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            val name = displayName(context, uri)
            val type = bookType(name, uri)
            if (type !in listOf("pdf", "epub", "txt", "text")) {
                error = "Choose an EPUB, PDF, or TXT file."
            } else {
                val existing = books.firstOrNull { it.uri == uri.toString() }
                val book = existing ?: BookEntry(UUID.randomUUID().toString(), uri.toString(), name.substringBeforeLast('.'), type)
                books = if (existing == null) books + book else books
                writeBookLibrary(context, books)
                selectedBook = book
            }
        }
    }

    fun saveProgress(position: Int) {
        val selected = selectedBook ?: return
        books = books.map { if (it.id == selected.id) it.copy(position = position.coerceAtLeast(0)) else it }
        selectedBook = books.firstOrNull { it.id == selected.id }
        writeBookLibrary(context, books)
    }

    LaunchedEffect(selectedBook?.id) {
        val book = selectedBook ?: return@LaunchedEffect
        loading = true
        error = null
        bookContent = null
        pdfBitmap = null
        try {
            val loaded = withContext(Dispatchers.IO) { loadBookContent(context, book) }
            bookContent = loaded
            currentPage = book.position.coerceIn(0, loaded.pages.lastIndex.coerceAtLeast(0))
            if (book.type == "pdf" && loaded.pdfFile != null) {
                pdfBitmap = withContext(Dispatchers.IO) { renderPdfPage(loaded.pdfFile, currentPage) }
            }
        } catch (e: Exception) {
            error = e.message ?: "Unable to open this book."
        } finally {
            loading = false
        }
    }

    LaunchedEffect(currentPage, selectedBook?.id) {
        if (selectedBook != null && bookContent?.pdfFile == null) {
            if (pageDirection > 0) pageScrollState.scrollTo(0) else pageScrollState.scrollTo(pageScrollState.maxValue)
        }
    }

    LaunchedEffect(selectedBook?.id, currentPage, bookContent?.pdfFile) {
        val file = bookContent?.pdfFile ?: return@LaunchedEffect
        pdfBitmap = withContext(Dispatchers.IO) { runCatching { renderPdfPage(file, currentPage) }.getOrNull() }
    }

    BackHandler(enabled = selectedBook != null || showMenu || menuSection.isNotEmpty()) {
        when {
            menuSection.isNotEmpty() -> menuSection = ""
            showMenu -> showMenu = false
            else -> { selectedBook = null; bookContent = null; pdfBitmap = null }
        }
    }

    val bg = MaterialTheme.colorScheme.background
    val fg = MaterialTheme.colorScheme.onBackground

    if (selectedBook == null) {
        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                IconButton(onClick = onBack) { Icon(Icons.Outlined.ArrowBack, contentDescription = "Back") }
                Text("Reader", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                if (books.isNotEmpty()) IconButton(onClick = { gridView = !gridView }) {
                    Icon(if (gridView) Icons.Outlined.ViewList else Icons.Outlined.ViewModule, contentDescription = if (gridView) "List view" else "Grid view")
                }
                IconButton(onClick = { picker.launch(arrayOf("application/epub+zip", "application/pdf", "text/plain", "application/octet-stream")) }) {
                    Icon(Icons.Outlined.Add, contentDescription = "Add book")
                }
            }
            Text("Your library", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 10.dp))
            if (books.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("A quiet place for your books", style = MaterialTheme.typography.titleMedium)
                        Text("Add an EPUB, PDF, or TXT file to begin.", color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                        TextButton(onClick = { picker.launch(arrayOf("application/epub+zip", "application/pdf", "text/plain", "application/octet-stream")) }) { Text("Add a book") }
                    }
                }
            } else {
                if (gridView) {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(books, key = { it.id }) { book ->
                            Surface(
                                modifier = Modifier.fillMaxWidth().clickable { selectedBook = book },
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceContainer
                            ) {
                                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(book.title, style = MaterialTheme.typography.titleMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
                                    Text("${book.type.uppercase()} · ${if (book.position == 0) "Not started" else "Continue reading"}",
                                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                        IconButton(onClick = { pendingDelete = book }) { Icon(Icons.Outlined.DeleteOutline, contentDescription = "Remove book") }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        books.forEach { book ->
                            Row(
                                Modifier.fillMaxWidth().clickable { selectedBook = book }.padding(vertical = 14.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(book.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    Text("${book.type.uppercase()} · ${if (book.position == 0) "Not started" else "Continue reading"}",
                                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                IconButton(onClick = { pendingDelete = book }) { Icon(Icons.Outlined.DeleteOutline, contentDescription = "Remove book") }
                            }
                        }
                    }
                }
            }
        }
    } else {
        val book = selectedBook!!
        Box(Modifier.fillMaxSize().background(bg).statusBarsPadding()) {
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { selectedBook = null; bookContent = null; pdfBitmap = null }) { Icon(Icons.Outlined.ArrowBack, contentDescription = "Library", tint = fg) }
                    Text(book.title, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, color = fg, style = MaterialTheme.typography.titleSmall)
                    IconButton(onClick = {
                        val saved = books.firstOrNull { it.id == book.id }?.bookmarks.orEmpty()
                        val next = if (currentPage in saved) saved - currentPage else saved + currentPage
                        books = books.map { if (it.id == book.id) it.copy(bookmarks = next.sorted()) else it }
                        selectedBook = books.first { it.id == book.id }
                        writeBookLibrary(context, books)
                    }) {
                        Icon(if (currentPage in book.bookmarks) Icons.Outlined.BookmarkAdded else Icons.Outlined.Bookmark, contentDescription = "Bookmark", tint = fg)
                    }
                }
                Box(
                    Modifier.weight(1f).fillMaxWidth().pointerInput(selectedBook?.id, currentPage) {
                        detectTapGestures { offset ->
                            val x = offset.x / size.width
                            when {
                                x < 0.22f -> if (currentPage > 0) { pageDirection = -1; currentPage--; saveProgress(currentPage) }
                                x > 0.78f -> if (bookContent != null && currentPage < bookContent!!.pages.lastIndex) { pageDirection = 1; currentPage++; saveProgress(currentPage) }
                                else -> showMenu = !showMenu
                            }
                        }
                    },
                    contentAlignment = Alignment.Center
                ) {
                    when {
                        loading -> Text("Opening book…", color = fg)
                        error != null -> Text(error ?: "", color = fg, modifier = Modifier.padding(24.dp), textAlign = TextAlign.Center)
                        bookContent == null -> Text("No content", color = fg)
                        book.type == "pdf" && pdfBitmap != null -> Image(
                            bitmap = pdfBitmap!!.asImageBitmap(), contentDescription = "PDF page ${currentPage + 1}",
                            modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 12.dp)
                        )
                        else -> Text(
                            bookContent!!.pages.getOrElse(currentPage) { "" },
                            modifier = Modifier.fillMaxSize().verticalScroll(pageScrollState).padding(horizontal = 28.dp, vertical = 28.dp),
                            color = fg, fontSize = fontSize.sp, lineHeight = (fontSize * 1.55f).sp,
                            fontFamily = FontFamily.Serif
                        )
                    }
                }
                Text(
                    "${currentPage + 1} / ${bookContent?.pages?.size ?: "—"}",
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    color = fg.copy(alpha = 0.55f), style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center
                )
            }

            if (showMenu) {
                Surface(
                    modifier = Modifier.align(Alignment.Center).fillMaxWidth().padding(horizontal = 28.dp),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shadowElevation = 4.dp
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(book.title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            IconButton(onClick = { showMenu = false }) { Icon(Icons.Outlined.Close, contentDescription = "Close menu") }
                        }
                        TextButton(onClick = { menuSection = "toc"; showMenu = false }, modifier = Modifier.fillMaxWidth()) { Text("Table of contents", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Start) }
                        TextButton(onClick = { menuSection = "bookmarks"; showMenu = false }, modifier = Modifier.fillMaxWidth()) { Text("Bookmarks", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Start) }
                        Text("Text size", style = MaterialTheme.typography.labelLarge)
                        Slider(value = fontSize, onValueChange = { fontSize = it }, valueRange = 15f..30f)
                        Text("Tap left or right edge to turn pages.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            if (menuSection.isNotEmpty()) {
                Surface(
                    modifier = Modifier.fillMaxSize().padding(12.dp),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh
                ) {
                    Column(Modifier.fillMaxSize().padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { menuSection = "" }) { Icon(Icons.Outlined.ArrowBack, contentDescription = "Back") }
                            Text(if (menuSection == "toc") "Table of contents" else "Bookmarks", style = MaterialTheme.typography.titleLarge)
                        }
                        val entries = if (menuSection == "toc") bookContent?.chapters.orEmpty() else
                            book.bookmarks.map { "Page ${it + 1}" to it }
                        if (entries.isEmpty()) {
                            Text(if (menuSection == "toc") "No table of contents available." else "No bookmarks yet.",
                                modifier = Modifier.padding(12.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            Column(Modifier.verticalScroll(rememberScrollState())) {
                                entries.forEach { (label, position) ->
                                    Text(label, modifier = Modifier.fillMaxWidth().clickable {
                                        pageDirection = if (position >= currentPage) 1 else -1
                                        currentPage = position.coerceIn(0, bookContent?.pages?.lastIndex ?: 0)
                                        saveProgress(currentPage)
                                        menuSection = ""
                                    }.padding(vertical = 12.dp, horizontal = 8.dp), maxLines = 2, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (pendingDelete != null) {
        val target = pendingDelete!!
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Remove book?") },
            text = { Text("Remove \"${target.title}\" from your library? The original file will not be deleted.") },
            confirmButton = {
                TextButton(onClick = {
                    books = books.filterNot { it.id == target.id }
                    writeBookLibrary(context, books)
                    pendingDelete = null
                }) { Text("Remove") }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Cancel") } }
        )
    }
    if (error != null && selectedBook == null) {
        AlertDialog(onDismissRequest = { error = null }, title = { Text("Unable to add book") },
            text = { Text(error ?: "") }, confirmButton = { TextButton(onClick = { error = null }) { Text("OK") } })
    }
}
