package com.insomdroid.dislauncher

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.BookmarkAdded
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.ViewList
import androidx.compose.material.icons.outlined.ViewModule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
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
import android.util.LruCache
import java.util.UUID
import java.util.zip.ZipFile

private data class BookEntry(
    val id: String,
    val uri: String,
    val title: String,
    val type: String,
    val position: Int = 0,
    val bookmarks: List<Int> = emptyList(),
    val pinned: Boolean = false
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
                },
                pinned = o.optBoolean("pinned", false)
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
            put("bookmarks", JSONArray(book.bookmarks)); put("pinned", book.pinned)
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

private fun bookType(name: String): String {
    val value = name.substringAfterLast('.', "").lowercase()
    return when (value) {
        "pdf" -> "pdf"
        "epub" -> "epub"
        "txt", "text" -> "txt"
        else -> value
    }
}

private const val MAX_BOOK_BYTES = 100L * 1024L * 1024L
private const val MAX_TEXT_BYTES = MAX_BOOK_BYTES
private const val MAX_EPUB_TOTAL_BYTES = MAX_BOOK_BYTES

private fun bookCacheDigest(uri: Uri): String =
    java.security.MessageDigest.getInstance("SHA-256")
        .digest(uri.toString().toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

private fun cachedBookFile(context: Context, uri: Uri, suffix: String): File? =
    File(context.cacheDir, "book_${bookCacheDigest(uri)}.$suffix")
        .takeIf { it.isFile && it.length() in 1..MAX_BOOK_BYTES }

private fun copyBookToCache(context: Context, uri: Uri, suffix: String): File {
    val target = File(context.cacheDir, "book_${bookCacheDigest(uri)}.$suffix")
    if (target.exists()) {
        if (target.length() in 1..MAX_BOOK_BYTES) return target
        target.delete()
    }
    val temporary = File(context.cacheDir, "${target.name}.tmp")
    temporary.delete()
    try {
        context.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "Unable to open document" }
            temporary.outputStream().buffered().use { output ->
                val buffer = ByteArray(16 * 1024)
                var total = 0L
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    total += count
                    require(total <= MAX_BOOK_BYTES) { "Book is too large. Maximum supported size is 100 MB." }
                    output.write(buffer, 0, count)
                }
            }
        }
        if (!temporary.renameTo(target)) {
            // Keep the completed temporary file if the atomic rename is unavailable.
            // A later read can use it only after it has been moved to the final name.
            throw java.io.IOException("Unable to finalize the cached book file.")
        }
    } catch (e: Exception) {
        temporary.delete()
        throw e
    }
    return target
}

private fun readZipEntryText(zip: ZipFile, entry: java.util.zip.ZipEntry, limit: Long): String {
    require(entry.size < 0L || entry.size <= limit) { "This EPUB contains an oversized section." }
    val output = java.io.ByteArrayOutputStream()
    zip.getInputStream(entry).buffered().use { input ->
        val buffer = ByteArray(8 * 1024)
        var total = 0L
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            total += count
            require(total <= limit) { "This EPUB contains an oversized section." }
            output.write(buffer, 0, count)
        }
    }
    return output.toString(Charsets.UTF_8.name())
}

private fun readTextPages(file: File, pageFile: File): List<String> {
    val writer = DiskBackedBookPages.Writer(pageFile)
    return try {
        file.bufferedReader(Charsets.UTF_8).use { reader ->
            val buffer = CharArray(8 * 1024)
            while (true) {
                val count = reader.read(buffer)
                if (count < 0) break
                writer.append(String(buffer, 0, count))
            }
        }
        writer.finish()
    } catch (failure: Exception) {
        writer.abort()
        throw failure
    }
}

private class BoundedEpubInputStream(
    input: java.io.InputStream,
    private val limit: Long,
    private val onBytesRead: (Long) -> Unit
) : java.io.FilterInputStream(input) {
    private var consumed = 0L

    override fun read(): Int {
        val value = super.read()
        if (value >= 0) record(1)
        return value
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        val count = super.read(buffer, offset, length)
        if (count > 0) record(count.toLong())
        return count
    }

    private fun record(count: Long) {
        consumed += count
        require(consumed <= limit) { "This EPUB exceeds the 100 MB processing limit." }
        onBytesRead(count)
    }
}

private fun appendEpubSection(
    input: java.io.InputStream,
    remainingBytes: Long,
    writer: DiskBackedBookPages.Writer,
    onBytesRead: (Long) -> Unit
) {
    val parser = android.util.Xml.newPullParser()
    parser.setFeature(org.xmlpull.v1.XmlPullParser.FEATURE_PROCESS_DOCDECL, false)
    BoundedEpubInputStream(input, remainingBytes, onBytesRead).use { stream ->
        parser.setInput(stream, "UTF-8")
        val ignoredTags = setOf("head", "script", "style", "svg")
        val blockTags = setOf("p", "div", "h1", "h2", "h3", "h4", "h5", "h6", "li", "tr", "section", "article", "br")
        var ignoredDepth = 0
        var event = parser.eventType
        while (event != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
            when (event) {
                org.xmlpull.v1.XmlPullParser.START_TAG -> {
                    val tag = parser.name?.lowercase().orEmpty()
                    if (ignoredDepth > 0) {
                        ignoredDepth++
                    } else if (tag in ignoredTags) {
                        ignoredDepth = 1
                    } else if (tag in blockTags) {
                        writer.append(" ")
                    }
                }
                org.xmlpull.v1.XmlPullParser.TEXT,
                org.xmlpull.v1.XmlPullParser.CDSECT,
                org.xmlpull.v1.XmlPullParser.ENTITY_REF -> {
                    if (ignoredDepth == 0) parser.text?.let(writer::append)
                }
                org.xmlpull.v1.XmlPullParser.END_TAG -> {
                    if (ignoredDepth > 0) {
                        ignoredDepth--
                    } else if (parser.name?.lowercase() in blockTags) {
                        writer.append(" ")
                    }
                }
            }
            event = parser.next()
        }
    }
    writer.finishSection()
}

private val bookContentCache = object : LruCache<String, BookContent>(
    (Runtime.getRuntime().maxMemory() / 32L).coerceIn(2L * 1024L * 1024L, 8L * 1024L * 1024L).toInt()
) {
    override fun sizeOf(key: String, value: BookContent): Int =
        (value.pages as? DiskBackedBookPages)?.metadataBytes
            ?: (value.pages.sumOf { it.length.toLong() } * 2L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
}
private val pdfPageCache = object : LruCache<String, Bitmap>(
    (Runtime.getRuntime().maxMemory() / 16L).coerceIn(2L * 1024L * 1024L, 12L * 1024L * 1024L).toInt()
) {
    override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
}

private fun loadBookContent(context: Context, book: BookEntry): BookContent {
    bookContentCache.get("${book.id}:${book.uri}")?.let { return it }
    val uri = Uri.parse(book.uri)
    val content = when (book.type) {
        "pdf" -> {
            val file = copyBookToCache(context, uri, "pdf")
            val count = android.os.ParcelFileDescriptor.open(
                file, android.os.ParcelFileDescriptor.MODE_READ_ONLY
            ).use { descriptor -> PdfRenderer(descriptor).use { it.pageCount } }
            require(count > 0) { "This PDF contains no pages." }
            BookContent(object : AbstractList<String>() {
                override val size: Int = count
                override fun get(index: Int): String {
                    if (index !in 0 until size) throw IndexOutOfBoundsException("Index $index is outside 0 until $size")
                    return "PDF page ${index + 1}"
                }
            }, pdfFile = file)
        }
        "epub" -> {
            val file = copyBookToCache(context, uri, "epub")
            val pageFile = File(context.cacheDir, "book_${bookCacheDigest(uri)}.pages")
            val writer = DiskBackedBookPages.Writer(pageFile)
            try {
                val chapters = mutableListOf<Pair<String, Int>>()
                ZipFile(file).use { zip ->
                    val opfPath = zip.getEntry("META-INF/container.xml")?.let { entry ->
                        readZipEntryText(zip, entry, 256L * 1024L)
                            .let { Regex("full-path=[\"']([^\"']+)[\"']").find(it)?.groupValues?.get(1) }
                    }
                    val opf = opfPath?.let { zip.getEntry(it) }?.let { entry ->
                        readZipEntryText(zip, entry, 1024L * 1024L)
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
                    val baseUri = java.net.URI(base.replace(" ", "%20"))
                    var totalSectionBytes = 0L
                    paths.forEach { relative ->
                        val relativeUri = java.net.URI(relative.replace(" ", "%20"))
                        require(!relativeUri.isAbsolute && relativeUri.rawAuthority == null &&
                            !relativeUri.path.startsWith("/")) {
                            "Invalid EPUB section reference."
                        }
                        val normalized = baseUri.resolve(relativeUri).normalize().path.removePrefix("/")
                        require(normalized.isNotBlank() && normalized != ".." &&
                            !normalized.startsWith("../") && !normalized.split('/').contains("..")) {
                            "Invalid EPUB section path."
                        }
                        val entry = zip.getEntry(normalized) ?: return@forEach
                        val remainingBytes = (MAX_EPUB_TOTAL_BYTES - totalSectionBytes).coerceAtLeast(0L)
                        require(entry.size < 0L || entry.size <= remainingBytes) {
                            "This EPUB exceeds the 100 MB processing limit."
                        }
                        val chapterStart = writer.pageCount
                        appendEpubSection(zip.getInputStream(entry), remainingBytes, writer) { count ->
                            totalSectionBytes += count
                        }
                        if (writer.pageCount > chapterStart) {
                            val chapterTitle = normalized.substringAfterLast('/').substringBeforeLast('.')
                                .replace(Regex("[-_]"), " ").trim()
                            if (chapterTitle.isNotBlank()) chapters += chapterTitle to chapterStart
                        }
                    }
                }
                BookContent(writer.finish(), chapters)
            } catch (failure: Exception) {
                writer.abort()
                throw failure
            }
        }
        else -> {
            val file = copyBookToCache(context, uri, "txt")
            require(file.length() in 1..MAX_TEXT_BYTES) {
                "Books larger than 100 MB are not supported."
            }
            val pageFile = File(context.cacheDir, "book_${bookCacheDigest(uri)}.pages")
            BookContent(readTextPages(file, pageFile))
        }
    }
    bookContentCache.put("${book.id}:${book.uri}", content)
    return content
}

private val bookCoverCache = object : LruCache<String, Bitmap>(
    (Runtime.getRuntime().maxMemory() / 32L).coerceIn(1L * 1024L * 1024L, 6L * 1024L * 1024L).toInt()
) {
    override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
}

private fun removeBookResources(context: Context, book: BookEntry, remainingBooks: List<BookEntry>) {
    val contentKey = "${book.id}:${book.uri}"
    bookContentCache.remove(contentKey)
    File(context.cacheDir, "book_${bookCacheDigest(Uri.parse(book.uri))}.pages").delete()
    bookCoverCache.remove(contentKey)
    if (remainingBooks.any { it.uri == book.uri }) return

    val digest = bookCacheDigest(Uri.parse(book.uri))
    val pdfPath = File(context.cacheDir, "book_$digest.pdf").absolutePath
    pdfPageCache.snapshot().keys
        .filter { it.startsWith("$pdfPath:") }
        .forEach(pdfPageCache::remove)

    context.cacheDir.listFiles().orEmpty()
        .filter { it.name.startsWith("book_$digest.") }
        .forEach { it.delete() }

    runCatching {
        context.contentResolver.releasePersistableUriPermission(
            Uri.parse(book.uri), android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
        )
    }
}

private fun loadBookCover(context: Context, book: BookEntry): Bitmap? {
    bookCoverCache.get("${book.id}:${book.uri}")?.let { return it }
    val result = runCatching {
        when (book.type) {
            "pdf" -> cachedBookFile(context, Uri.parse(book.uri), "pdf")?.let { renderPdfPage(it, 0) }
            "epub" -> {
                val file = cachedBookFile(context, Uri.parse(book.uri), "epub") ?: return@runCatching null
                ZipFile(file).use { zip ->
                    val imageEntry = zip.entries().asSequence().firstOrNull {
                        !it.isDirectory && it.size in 1..(8L * 1024L * 1024L) &&
                            it.name.substringAfterLast('.', "").lowercase() in setOf("jpg", "jpeg", "png", "webp")
                    } ?: return@use null
                    zip.getInputStream(imageEntry).use { input ->
                        val bytes = input.use { stream ->
                            val output = java.io.ByteArrayOutputStream()
                            val buffer = ByteArray(8 * 1024)
                            var total = 0L
                            while (true) {
                                val count = stream.read(buffer)
                                if (count < 0) break
                                total += count
                                require(total <= 8L * 1024L * 1024L) { "Cover image is too large." }
                                output.write(buffer, 0, count)
                            }
                            output.toByteArray()
                        }
                        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                        if (bounds.outWidth !in 1..12_000 || bounds.outHeight !in 1..12_000) null
                        else {
                            var sample = 1
                            while (sample < 64 &&
                                (bounds.outWidth / sample > 1200 || bounds.outHeight / sample > 1600)) sample *= 2
                            val options = BitmapFactory.Options().apply { inSampleSize = sample }
                            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
                        }
                    }
                }
            }
            else -> null
        }
    }.getOrNull()
    if (result != null) bookCoverCache.put("${book.id}:${book.uri}", result)
    return result
}

@Composable
private fun BookCover(book: BookEntry, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val cover by produceState<Bitmap?>(initialValue = null, book.id, book.uri, book.type) {
        value = withContext(Dispatchers.IO) { loadBookCover(context, book) }
    }
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
    ) {
        if (cover != null) {
            Image(bitmap = cover!!.asImageBitmap(), contentDescription = "Cover of ${book.title}", modifier = Modifier.fillMaxSize())
        } else {
            Box(Modifier.fillMaxSize().padding(8.dp), contentAlignment = Alignment.Center) {
                Text(book.type.uppercase(), style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private fun renderPdfPage(file: File, pageIndex: Int): Bitmap {
    val cacheKey = "${file.absolutePath}:$pageIndex"
    pdfPageCache.get(cacheKey)?.let { return it }
    val descriptor = android.os.ParcelFileDescriptor.open(file, android.os.ParcelFileDescriptor.MODE_READ_ONLY)
    PdfRenderer(descriptor).use { renderer ->
        renderer.openPage(pageIndex.coerceIn(0, renderer.pageCount - 1)).use { page ->
            val maxPixels = 2_000_000L
            val scale = minOf(900f / page.width, kotlin.math.sqrt(maxPixels.toDouble() / (page.width.toLong() * page.height)).toFloat())
            val width = (page.width * scale).toInt().coerceAtLeast(1)
            val height = (page.height * scale).toInt().coerceAtLeast(1)
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(android.graphics.Color.WHITE)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            pdfPageCache.put(cacheKey, bitmap)
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
    var currentPageText by remember { mutableStateOf("") }
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
    var searchExpanded by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var showLibraryMenu by remember { mutableStateOf(false) }
    var sortByTitle by remember { mutableStateOf(false) }
    var pinnedOnly by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val name = displayName(context, uri)
            val type = bookType(name)
            if (type !in listOf("pdf", "epub", "txt", "text")) {
                error = "Choose an EPUB, PDF, or TXT file."
            } else {
                if (uri.scheme == "content") {
                    runCatching {
                        context.contentResolver.takePersistableUriPermission(
                            uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                        )
                    }
                }
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
        } catch (e: OutOfMemoryError) {
            bookContent = null
            pdfBitmap = null
            error = "This book could not be opened because the device ran out of memory. Close other apps and try again."
            System.gc()
        } catch (e: Exception) {
            error = e.message ?: "Unable to open this book."
        } finally {
            loading = false
        }
    }

    LaunchedEffect(currentPage, selectedBook?.id, bookContent) {
        val content = bookContent ?: return@LaunchedEffect
        currentPageText = withContext(Dispatchers.IO) {
            runCatching { content.pages.getOrElse(currentPage) { "" } }.getOrDefault("")
        }
    }

    LaunchedEffect(currentPage, selectedBook?.id) {
        if (selectedBook != null && bookContent?.pdfFile == null) {
            withFrameNanos { }
            if (pageDirection > 0) {
                pageScrollState.scrollTo(0)
            } else {
                pageScrollState.scrollTo(pageScrollState.maxValue)
            }
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

    val bg = MaterialTheme.colorScheme.surface
    val fg = MaterialTheme.colorScheme.onSurface

    if (selectedBook == null) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding()) {
          Column(Modifier.fillMaxSize().padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                IconButton(onClick = onBack) { Icon(Icons.Outlined.ArrowBack, contentDescription = "Back") }
                if (searchExpanded) {
                    OutlinedTextField(value = searchQuery, onValueChange = { searchQuery = it },
                        modifier = Modifier.weight(1f), placeholder = { Text("Search books") }, singleLine = true,
                        trailingIcon = { IconButton(onClick = { searchQuery = ""; searchExpanded = false }) {
                            Icon(Icons.Outlined.Close, contentDescription = "Close search")
                        } })
                } else {
                    Text("Library", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f), maxLines = 1)
                    IconButton(onClick = { searchExpanded = true }) { Icon(Icons.Outlined.Search, contentDescription = "Search books") }
                }
                if (books.isNotEmpty()) IconButton(onClick = { gridView = !gridView }) {
                    Icon(if (gridView) Icons.Outlined.ViewList else Icons.Outlined.ViewModule,
                        contentDescription = if (gridView) "Switch to list view" else "Switch to grid view")
                }
                Box {
                    IconButton(onClick = { showLibraryMenu = true }) { Icon(Icons.Outlined.MoreVert, contentDescription = "Library options") }
                    DropdownMenu(expanded = showLibraryMenu, onDismissRequest = { showLibraryMenu = false }) {
                        DropdownMenuItem(text = { Text(if (sortByTitle) "Sort by recently opened" else "Sort by title") },
                            onClick = { sortByTitle = !sortByTitle; showLibraryMenu = false })
                        DropdownMenuItem(text = { Text(if (pinnedOnly) "Show all books" else "Pinned books") },
                            leadingIcon = { Icon(Icons.Outlined.PushPin, contentDescription = null, modifier = Modifier.size(20.dp)) },
                            onClick = { pinnedOnly = !pinnedOnly; showLibraryMenu = false })
                    }
                }
            }
            val visibleBooks = books.filter { it.title.contains(searchQuery, ignoreCase = true) && (!pinnedOnly || it.pinned) }
                .let { list -> if (sortByTitle) list.sortedWith(compareByDescending<BookEntry> { it.pinned }.thenBy { it.title.lowercase() })
                    else list.sortedWith(compareByDescending<BookEntry> { it.pinned }) }
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = !pinnedOnly, onClick = { pinnedOnly = false }, label = { Text("All books") })
                FilterChip(selected = pinnedOnly, onClick = { pinnedOnly = true }, label = { Text("Pinned") })
            }
            if (visibleBooks.isEmpty()) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(if (searchQuery.isBlank()) "A quiet place for your books" else "No matching books", style = MaterialTheme.typography.titleMedium)
                        Text(if (searchQuery.isBlank()) "Add an EPUB, PDF, or TXT file to begin." else "Try another search.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                    }
                }
            } else {
                if (gridView) {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        contentPadding = PaddingValues(start = 4.dp, end = 4.dp, bottom = 96.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(visibleBooks, key = { it.id }) { book ->
                            Surface(
                                modifier = Modifier.fillMaxWidth().clickable { selectedBook = book },
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
                            ) {
                                Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    BookCover(book, Modifier.fillMaxWidth().height(148.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        if (book.pinned) Icon(Icons.Outlined.PushPin, contentDescription = "Pinned", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
                                        Text(book.title, style = MaterialTheme.typography.titleMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
                                    }
                                    Text("${book.type.uppercase()} · ${if (book.position == 0) "Not started" else "Continue reading"}",
                                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                        IconButton(onClick = { books = books.map { if (it.id == book.id) it.copy(pinned = !it.pinned) else it }; writeBookLibrary(context, books) }) {
                                            Icon(Icons.Outlined.PushPin, contentDescription = if (book.pinned) "Unpin book" else "Pin book",
                                                tint = if (book.pinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        IconButton(onClick = { pendingDelete = book }) { Icon(Icons.Outlined.DeleteOutline, contentDescription = "Remove book") }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        visibleBooks.forEach { book ->
                            Row(
                                Modifier.fillMaxWidth().clickable { selectedBook = book }.padding(vertical = 14.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                BookCover(book, Modifier.size(width = 48.dp, height = 64.dp))
                                Column(Modifier.weight(1f).padding(start = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        if (book.pinned) Icon(Icons.Outlined.PushPin, contentDescription = "Pinned", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
                                        Text(book.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    }
                                    Text("${book.type.uppercase()} · ${if (book.position == 0) "Not started" else "Continue reading"}",
                                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                IconButton(onClick = { books = books.map { if (it.id == book.id) it.copy(pinned = !it.pinned) else it }; writeBookLibrary(context, books) }) {
                                    Icon(Icons.Outlined.PushPin, contentDescription = if (book.pinned) "Unpin book" else "Pin book",
                                        tint = if (book.pinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                IconButton(onClick = { pendingDelete = book }) { Icon(Icons.Outlined.DeleteOutline, contentDescription = "Remove book") }
                            }
                        }
                    }
                }
            }
          }
          FloatingActionButton(onClick = {
              picker.launch(arrayOf("application/epub+zip", "application/pdf", "text/plain", "application/octet-stream"))
          }, modifier = Modifier.align(Alignment.BottomEnd).padding(end = 8.dp, bottom = 24.dp),
              containerColor = MaterialTheme.colorScheme.primaryContainer,
              contentColor = MaterialTheme.colorScheme.onPrimaryContainer) {
              Icon(Icons.Outlined.Add, contentDescription = "Add book")
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
                        Icon(if (currentPage in book.bookmarks) Icons.Outlined.Bookmark else Icons.Outlined.BookmarkAdded,
                            contentDescription = if (currentPage in book.bookmarks) "Remove bookmark" else "Add bookmark", tint = fg)
                    }
                }
                Box(
                    Modifier.weight(1f).fillMaxWidth().pointerInput(selectedBook?.id, currentPage) {
                        detectTapGestures(
                            onLongPress = { showMenu = true },
                            onTap = { offset ->
                            val x = offset.x / size.width
                            when {
                                x < 0.22f -> if (currentPage > 0) { pageDirection = -1; currentPage--; saveProgress(currentPage) }
                                x > 0.78f -> if (bookContent != null && currentPage < bookContent!!.pages.lastIndex) { pageDirection = 1; currentPage++; saveProgress(currentPage) }
                                else -> showMenu = !showMenu
                            }
                        })
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
                            currentPageText,
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
                AlertDialog(
                    onDismissRequest = { showMenu = false },
                    title = { Text("Reader options") },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(book.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            TextButton(onClick = { menuSection = "toc"; showMenu = false }, modifier = Modifier.fillMaxWidth()) {
                                Text("Table of contents", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Start)
                            }
                            TextButton(onClick = { menuSection = "bookmarks"; showMenu = false }, modifier = Modifier.fillMaxWidth()) {
                                Text("Bookmarks", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Start)
                            }
                            Text("Text size", style = MaterialTheme.typography.labelLarge)
                            Slider(value = fontSize, onValueChange = { fontSize = it }, valueRange = 15f..30f)
                            Text("Tap left or right edge to turn pages.", style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    },
                    confirmButton = { TextButton(onClick = { showMenu = false }) { Text("Done") } }
                )
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
                    val remaining = books.filterNot { it.id == target.id }
                    books = remaining
                    writeBookLibrary(context, books)
                    removeBookResources(context, target, remaining)
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
