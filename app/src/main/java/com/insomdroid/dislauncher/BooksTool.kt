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
import androidx.compose.foundation.layout.navigationBarsPadding
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
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
    val pdfFile: File? = null,
    val pdfUri: String? = null
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
private const val MAX_BOOK_CACHE_BYTES = 500L * 1024L * 1024L

private fun bookCacheDigest(uri: Uri): String =
    java.security.MessageDigest.getInstance("SHA-256")
        .digest(uri.toString().toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

private fun cachedBookFile(context: Context, uri: Uri, suffix: String): File? =
    File(context.cacheDir, "book_${bookCacheDigest(uri)}.$suffix")
        .takeIf { it.isFile && it.length() in 1..MAX_BOOK_BYTES }
        ?.also { it.setLastModified(System.currentTimeMillis()) }

private suspend fun copyBookToCache(context: Context, uri: Uri, suffix: String): File {
    val coroutineContext = currentCoroutineContext()
    val target = File(context.cacheDir, "book_${bookCacheDigest(uri)}.$suffix")
    if (target.exists()) {
        if (target.length() in 1..MAX_BOOK_BYTES) {
            target.setLastModified(System.currentTimeMillis())
            return target
        }
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
                    coroutineContext.ensureActive()
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
    target.setLastModified(System.currentTimeMillis())
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

private suspend fun readTextPages(file: File, pageFile: File): List<String> {
    DiskBackedBookPages.open(pageFile)?.let { cached ->
        cached.touch()
        return cached
    }
    val writer = DiskBackedBookPages.Writer(pageFile)
    val coroutineContext = currentCoroutineContext()
    return try {
        file.bufferedReader(Charsets.UTF_8).use { reader ->
            val buffer = CharArray(8 * 1024)
            while (true) {
                coroutineContext.ensureActive()
                val count = reader.read(buffer)
                if (count < 0) break
                writer.append(String(buffer, 0, count)) { coroutineContext.ensureActive() }
            }
        }
        writer.finish().also { pageFile.setLastModified(System.currentTimeMillis()) }
    } catch (failure: Exception) {
        writer.abort()
        throw failure
    }
}

private fun readBookChapters(file: File): List<Pair<String, Int>> = runCatching {
    val array = JSONArray(file.readText(Charsets.UTF_8))
    (0 until array.length()).mapNotNull { index ->
        val item = array.optJSONObject(index) ?: return@mapNotNull null
        val title = item.optString("title").takeIf { it.isNotBlank() } ?: return@mapNotNull null
        val page = item.optInt("page", -1)
        if (page < 0) null else title to page
    }
}.getOrDefault(emptyList())

private fun writeBookChapters(file: File, chapters: List<Pair<String, Int>>) {
    val array = JSONArray()
    chapters.forEach { (title, page) ->
        array.put(JSONObject().put("title", title).put("page", page))
    }
    file.writeText(array.toString(), Charsets.UTF_8)
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

private suspend fun appendEpubSection(
    input: java.io.InputStream,
    remainingBytes: Long,
    writer: DiskBackedBookPages.Writer,
    onBytesRead: (Long) -> Unit
) {
    val parser = android.util.Xml.newPullParser()
    parser.setFeature(org.xmlpull.v1.XmlPullParser.FEATURE_PROCESS_DOCDECL, false)
    runCatching { parser.setFeature(org.xmlpull.v1.XmlPullParser.FEATURE_RELAXED, true) }
    val entities = mapOf(
        "nbsp" to "\u00A0", "mdash" to "—", "ndash" to "–", "hellip" to "…",
        "rsquo" to "’", "lsquo" to "‘", "rdquo" to "”", "ldquo" to "“",
        "copy" to "©", "reg" to "®", "trade" to "™", "bull" to "•",
        "euro" to "€", "pound" to "£", "mdash" to "—", "apos" to "'"
    )
    entities.forEach { (name, value) -> runCatching { parser.defineEntityReplacementText(name, value) } }
    val coroutineContext = currentCoroutineContext()
    BoundedEpubInputStream(input, remainingBytes, onBytesRead).use { stream ->
        parser.setInput(stream, "UTF-8")
        val ignoredTags = setOf("head", "script", "style", "svg")
        val blockTags = setOf("p", "div", "h1", "h2", "h3", "h4", "h5", "h6", "li", "tr", "section", "article")
        var ignoredDepth = 0
        var event = parser.eventType
        while (event != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
            coroutineContext.ensureActive()
            when (event) {
                org.xmlpull.v1.XmlPullParser.START_TAG -> {
                    val tag = parser.name?.lowercase().orEmpty()
                    if (ignoredDepth > 0) {
                        ignoredDepth++
                    } else if (tag in ignoredTags) {
                        ignoredDepth = 1
                    } else if (tag == "br") {
                        writer.append("\n") { coroutineContext.ensureActive() }
                    }
                }
                org.xmlpull.v1.XmlPullParser.TEXT,
                org.xmlpull.v1.XmlPullParser.CDSECT,
                org.xmlpull.v1.XmlPullParser.ENTITY_REF -> {
                    if (ignoredDepth == 0) parser.text?.let {
                        writer.append(it) { coroutineContext.ensureActive() }
                    }
                }
                org.xmlpull.v1.XmlPullParser.END_TAG -> {
                    if (ignoredDepth > 0) {
                        ignoredDepth--
                    } else if (parser.name?.lowercase() in blockTags) {
                        writer.append("\n\n") { coroutineContext.ensureActive() }
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

private suspend fun loadBookContent(context: Context, book: BookEntry): BookContent {
    val cacheKey = "${book.id}:${book.uri}"
    bookContentCache.get(cacheKey)?.let { cached ->
        val pages = cached.pages as? DiskBackedBookPages
        val pagesAvailable = pages?.isAvailable != false
        if (pagesAvailable && (cached.pdfFile == null || cached.pdfFile.isFile)) {
            pages?.touch()
            cached.pdfFile?.setLastModified(System.currentTimeMillis())
            return cached
        }
        bookContentCache.remove(cacheKey)
    }
    val uri = Uri.parse(book.uri)
    val content = when (book.type) {
        "pdf" -> {
            val directPageCount = runCatching {
                val descriptor = context.contentResolver.openFileDescriptor(uri, "r")
                    ?: error("Unable to open PDF document")
                PdfRenderer(descriptor).use { it.pageCount }
            }.getOrNull()?.takeIf { it > 0 }
            val file = if (directPageCount == null) copyBookToCache(context, uri, "pdf") else null
            val count = directPageCount ?: run {
                val descriptor = android.os.ParcelFileDescriptor.open(
                    requireNotNull(file), android.os.ParcelFileDescriptor.MODE_READ_ONLY
                )
                PdfRenderer(descriptor).use { it.pageCount }
            }
            require(count > 0) { "This PDF contains no pages." }
            BookContent(
                pages = object : AbstractList<String>() {
                    override val size: Int = count
                    override fun get(index: Int): String {
                        if (index !in 0 until size) throw IndexOutOfBoundsException("Index $index is outside 0 until $size")
                        return "PDF page ${index + 1}"
                    }
                },
                pdfFile = file,
                pdfUri = uri.toString().takeIf { file == null }
            )
        }
        "epub" -> {
            val file = copyBookToCache(context, uri, "epub")
            val digest = bookCacheDigest(uri)
            val pageFile = File(context.cacheDir, "book_$digest.pages")
            val chapterFile = File(context.cacheDir, "book_$digest.chapters")
            val cachedPages = DiskBackedBookPages.open(pageFile)
            if (cachedPages != null) {
                cachedPages.touch()
                chapterFile.setLastModified(System.currentTimeMillis())
                BookContent(cachedPages, readBookChapters(chapterFile)).also {
                    pruneBookCache(context, digest)
                }
            } else {
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
                    var hasProcessedSection = false
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
                        if (hasProcessedSection) writer.append("\n\n")
                        hasProcessedSection = true
                        val chapterStart = writer.pageCount
                        try {
                            appendEpubSection(zip.getInputStream(entry), remainingBytes, writer) { count ->
                                totalSectionBytes += count
                            }
                        } catch (_: org.xmlpull.v1.XmlPullParserException) {
                            // A malformed chapter should not prevent the remaining spine items from opening.
                            writer.finishSection()
                        }
                        if (writer.pageCount > chapterStart) {
                            val chapterTitle = normalized.substringAfterLast('/').substringBeforeLast('.')
                                .replace(Regex("[-_]"), " ").trim()
                            if (chapterTitle.isNotBlank()) chapters += chapterTitle to chapterStart
                        }
                    }
                }
                    BookContent(writer.finish(), chapters).also {
                        writeBookChapters(chapterFile, chapters)
                        pageFile.setLastModified(System.currentTimeMillis())
                        chapterFile.setLastModified(System.currentTimeMillis())
                        pruneBookCache(context, digest)
                    }
                } catch (failure: Exception) {
                    writer.abort()
                    throw failure
                }
            }
        }
        else -> {
            val file = copyBookToCache(context, uri, "txt")
            require(file.length() in 1..MAX_TEXT_BYTES) {
                "Books larger than 100 MB are not supported."
            }
            val pageFile = File(context.cacheDir, "book_${bookCacheDigest(uri)}.pages")
            BookContent(readTextPages(file, pageFile)).also {
                pruneBookCache(context, bookCacheDigest(uri))
            }
        }
    }
    if (book.type == "pdf" && content.pdfFile != null) {
        pruneBookCache(context, bookCacheDigest(uri))
    }
    bookContentCache.put(cacheKey, content)
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
    bookCoverCache.remove(contentKey)
    if (remainingBooks.any { it.uri == book.uri }) return
    File(context.cacheDir, "book_${bookCacheDigest(Uri.parse(book.uri))}.pages").delete()

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

private fun pruneBookCache(context: Context, protectedDigest: String) {
    val files = context.cacheDir.listFiles().orEmpty().filter { it.name.startsWith("book_") }
    files.filter { it.name.endsWith(".tmp") }.forEach { it.delete() }
    val groups = files.filter { it.isFile && !it.name.endsWith(".tmp") }
        .groupBy { it.name.removePrefix("book_").substringBefore('.') }
    var totalBytes = groups.values.sumOf { group -> group.sumOf { it.length() } }
    groups.entries
        .filter { it.key != protectedDigest }
        .sortedBy { entry -> entry.value.minOfOrNull { it.lastModified() } ?: 0L }
        .forEach { entry ->
            if (totalBytes <= MAX_BOOK_CACHE_BYTES) return@forEach
            entry.value.forEach { file ->
                totalBytes -= file.length()
                file.delete()
            }
        }
    bookContentCache.snapshot().forEach { (key, value) ->
        val pageFileMissing = (value.pages as? DiskBackedBookPages)?.isAvailable == false
        val pdfFileMissing = value.pdfFile != null && !value.pdfFile.isFile
        if (pageFileMissing || pdfFileMissing) bookContentCache.remove(key)
    }
}

private fun loadBookCover(context: Context, book: BookEntry): Bitmap? {
    bookCoverCache.get("${book.id}:${book.uri}")?.let { return it }
    val result = runCatching {
        when (book.type) {
            "pdf" -> runCatching {
                renderPdfThumbnail(context, Uri.parse(book.uri), 0)
            }.getOrElse {
                cachedBookFile(context, Uri.parse(book.uri), "pdf")?.let { renderPdfThumbnail(it, 0) }
            }
            "epub" -> {
                val file = cachedBookFile(context, Uri.parse(book.uri), "epub") ?: return@runCatching null
                ZipFile(file).use { zip ->
                    val container = zip.getEntry("META-INF/container.xml")
                        ?.let { readZipEntryText(zip, it, 256L * 1024L) }
                    val opfPath = container?.let {
                        Regex("full-path=[\\\"']([^\\\"']+)[\\\"']").find(it)?.groupValues?.get(1)
                    } ?: return@use null
                    val opfEntry = zip.getEntry(opfPath) ?: return@use null
                    val opf = readZipEntryText(zip, opfEntry, 1024L * 1024L)
                    val items = Regex("(?is)<item\\b([^>]*?)/?>").findAll(opf).map { match ->
                        val attrs = match.groupValues[1]
                        fun attr(name: String) = Regex("(?is)\\b$name=[\\\"']([^\\\"']+)[\\\"']")
                            .find(attrs)?.groupValues?.get(1).orEmpty()
                        Triple(attr("id"), attr("href"), attr("properties"))
                    }.toList()
                    val legacyCoverId = Regex("(?is)<meta\\b[^>]*?name=[\\\"']cover[\\\"'][^>]*?content=[\\\"']([^\\\"']+)[\\\"']")
                        .find(opf)?.groupValues?.get(1)
                    val coverHref = items.firstOrNull { "cover-image" in it.third.split(Regex("\\s+")) }?.second
                        ?: items.firstOrNull { it.first == legacyCoverId }?.second
                        ?: return@use null
                    val base = opfPath.substringBeforeLast('/', "").let { if (it.isBlank()) "" else "$it/" }
                    val coverPath = java.net.URI(base.replace(" ", "%20"))
                        .resolve(java.net.URI(coverHref.replace(" ", "%20"))).normalize().path.removePrefix("/")
                    val imageEntry = zip.getEntry(coverPath) ?: return@use null
                    if (imageEntry.isDirectory || imageEntry.size !in 1..(8L * 1024L * 1024L) ||
                        imageEntry.name.substringAfterLast('.', "").lowercase() !in setOf("jpg", "jpeg", "png", "webp")
                    ) return@use null
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
                                (bounds.outWidth / sample > 300 || bounds.outHeight / sample > 450)) sample *= 2
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
    return renderPdfPage(descriptor, cacheKey, pageIndex, 900f, cache = true)
}

private fun renderPdfPage(context: Context, uri: Uri, pageIndex: Int): Bitmap {
    val cacheKey = "${uri}:$pageIndex"
    pdfPageCache.get(cacheKey)?.let { return it }
    val descriptor = context.contentResolver.openFileDescriptor(uri, "r")
        ?: error("Unable to open PDF document")
    return renderPdfPage(descriptor, cacheKey, pageIndex, 900f, cache = true)
}

private fun renderPdfThumbnail(context: Context, uri: Uri, pageIndex: Int): Bitmap {
    val descriptor = context.contentResolver.openFileDescriptor(uri, "r")
        ?: error("Unable to open PDF document")
    return renderPdfPage(descriptor, "cover:${uri}", pageIndex, 300f, cache = false)
}

private fun renderPdfThumbnail(file: File, pageIndex: Int): Bitmap {
    val descriptor = android.os.ParcelFileDescriptor.open(file, android.os.ParcelFileDescriptor.MODE_READ_ONLY)
    return renderPdfPage(descriptor, "cover:${file.absolutePath}", pageIndex, 300f, cache = false)
}

private fun renderPdfPage(
    descriptor: android.os.ParcelFileDescriptor,
    cacheKey: String,
    pageIndex: Int,
    maxDimension: Float,
    cache: Boolean
): Bitmap {
    PdfRenderer(descriptor).use { renderer ->
        renderer.openPage(pageIndex.coerceIn(0, renderer.pageCount - 1)).use { page ->
            val maxPixels = if (maxDimension <= 300f) 160_000L else 2_000_000L
            val dimensionScale = if (maxDimension <= 300f) {
                minOf(maxDimension / page.width, maxDimension / page.height)
            } else {
                maxDimension / page.width
            }
            val scale = minOf(
                dimensionScale,
                kotlin.math.sqrt(maxPixels.toDouble() / (page.width.toLong() * page.height)).toFloat()
            )
            val width = (page.width * scale).toInt().coerceAtLeast(1)
            val height = (page.height * scale).toInt().coerceAtLeast(1)
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(android.graphics.Color.WHITE)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            if (cache) pdfPageCache.put(cacheKey, bitmap)
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
    var gridView by remember {
        mutableStateOf(
            context.getSharedPreferences("dislauncher_books", Context.MODE_PRIVATE)
                .getBoolean("grid_view", false)
        )
    }
    var searchExpanded by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var showLibraryMenu by remember { mutableStateOf(false) }
    var bookMenuTarget by remember { mutableStateOf<String?>(null) }
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
        val book = selectedBook
        if (book == null) {
            loading = false
            error = null
            bookContent = null
            pdfBitmap = null
            return@LaunchedEffect
        }
        loading = true
        error = null
        bookContent = null
        pdfBitmap = null
        try {
            val loaded = withContext(Dispatchers.IO) { loadBookContent(context, book) }
            bookContent = loaded
            currentPage = book.position.coerceIn(0, loaded.pages.lastIndex.coerceAtLeast(0))
            if (book.type == "pdf" && (loaded.pdfFile != null || loaded.pdfUri != null)) {
                pdfBitmap = withContext(Dispatchers.IO) {
                    loaded.pdfUri?.let { renderPdfPage(context, Uri.parse(it), currentPage) }
                        ?: renderPdfPage(requireNotNull(loaded.pdfFile), currentPage)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: OutOfMemoryError) {
            bookContent = null
            pdfBitmap = null
            error = "This book could not be opened because the device ran out of memory. Close other apps and try again."
            System.gc()
        } catch (e: Exception) {
            val detail = e.message.orEmpty()
            error = when {
                detail.contains("too large", ignoreCase = true) ->
                    "$detail Choose a smaller file; the maximum supported size is 100 MB."
                detail.contains("oversized section", ignoreCase = true) ->
                    "This EPUB contains a section that is too large to load. Try another copy of the book."
                detail.contains("permission", ignoreCase = true) ||
                    detail.contains("access", ignoreCase = true) ->
                    "The app can no longer access this file. Add the book again and select the file from your device."
                detail.isNotBlank() ->
                    "Could not open this ${book.type.uppercase()} file. It may be damaged or use unsupported content. Select another file and try again. Details: $detail"
                else ->
                    "Could not open this ${book.type.uppercase()} file. Check that the file is available, then add it again."
            }
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
        if (selectedBook != null && bookContent?.pdfFile == null && bookContent?.pdfUri == null) {
            withFrameNanos { }
            if (pageDirection > 0) {
                pageScrollState.scrollTo(0)
            } else {
                pageScrollState.scrollTo(pageScrollState.maxValue)
            }
        }
    }

    LaunchedEffect(selectedBook?.id, currentPage, bookContent?.pdfFile, bookContent?.pdfUri) {
        val content = bookContent ?: return@LaunchedEffect
        if (content.pdfFile == null && content.pdfUri == null) return@LaunchedEffect
        pdfBitmap = withContext(Dispatchers.IO) {
            runCatching {
                content.pdfUri?.let { renderPdfPage(context, Uri.parse(it), currentPage) }
                    ?: renderPdfPage(requireNotNull(content.pdfFile), currentPage)
            }.getOrNull()
        }
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
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding().navigationBarsPadding()) {
          Column(Modifier.fillMaxSize().padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                IconButton(onClick = onBack) { Icon(Icons.Outlined.ArrowBack, contentDescription = "Back", tint = fg) }
                if (searchExpanded) {
                    OutlinedTextField(value = searchQuery, onValueChange = { searchQuery = it },
                        modifier = Modifier.weight(1f), placeholder = { Text("Search books") }, singleLine = true,
                        trailingIcon = { IconButton(onClick = { searchQuery = ""; searchExpanded = false }) {
                            Icon(Icons.Outlined.Close, contentDescription = "Close search", tint = fg)
                        } })
                } else {
                    Text("Library", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold,
                        color = fg, modifier = Modifier.weight(1f), maxLines = 1)
                    IconButton(onClick = { searchExpanded = true }) { Icon(Icons.Outlined.Search, contentDescription = "Search books", tint = fg) }
                }
                if (books.isNotEmpty()) IconButton(onClick = {
                    gridView = !gridView
                    context.getSharedPreferences("dislauncher_books", Context.MODE_PRIVATE)
                        .edit()
                        .putBoolean("grid_view", gridView)
                        .apply()
                }) {
                    Icon(if (gridView) Icons.Outlined.ViewList else Icons.Outlined.ViewModule,
                        contentDescription = if (gridView) "Switch to list view" else "Switch to grid view", tint = fg)
                }
                Box {
                    IconButton(onClick = { showLibraryMenu = true }) { Icon(Icons.Outlined.MoreVert, contentDescription = "Library options", tint = fg) }
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
                        Text(
                            if (books.isEmpty() && searchQuery.isBlank() && !pinnedOnly) "No books yet" else "No matching books",
                            style = MaterialTheme.typography.titleMedium,
                            color = fg
                        )
                        if (books.isEmpty() && searchQuery.isBlank() && !pinnedOnly) {
                            Text(
                                "Supports EPUB, PDF, and TXT",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
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
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
                            ) {
                                Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    BookCover(book, Modifier.fillMaxWidth().height(148.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        if (book.pinned) Icon(Icons.Outlined.PushPin, contentDescription = "Pinned",
                                            tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
                                        Text(book.title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium,
                                            color = fg, maxLines = 3, overflow = TextOverflow.Ellipsis)
                                        Box {
                                            IconButton(onClick = { bookMenuTarget = book.id }, modifier = Modifier.size(28.dp)) {
                                                Icon(Icons.Outlined.MoreVert, contentDescription = "Book actions", tint = fg)
                                            }
                                            DropdownMenu(expanded = bookMenuTarget == book.id, onDismissRequest = { bookMenuTarget = null }) {
                                                DropdownMenuItem(text = { Text(if (book.pinned) "Unpin" else "Pin") },
                                                    leadingIcon = { Icon(Icons.Outlined.PushPin, contentDescription = null, modifier = Modifier.size(20.dp)) },
                                                    onClick = {
                                                        books = books.map { if (it.id == book.id) it.copy(pinned = !it.pinned) else it }
                                                        writeBookLibrary(context, books)
                                                        bookMenuTarget = null
                                                    })
                                                DropdownMenuItem(text = { Text("Remove") },
                                                    leadingIcon = { Icon(Icons.Outlined.DeleteOutline, contentDescription = null, modifier = Modifier.size(20.dp)) },
                                                    onClick = { pendingDelete = book; bookMenuTarget = null })
                                            }
                                        }
                                    }
                                    Text("${book.type.uppercase()} · ${if (book.position == 0) "Not started" else "Continue reading"}",
                                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                                        Text(book.title, style = MaterialTheme.typography.titleMedium, color = fg, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    }
                                    Text("${book.type.uppercase()} · ${if (book.position == 0) "Not started" else "Continue reading"}",
                                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Box {
                                    IconButton(onClick = { bookMenuTarget = book.id }) {
                                        Icon(Icons.Outlined.MoreVert, contentDescription = "Book actions", tint = fg)
                                    }
                                    DropdownMenu(expanded = bookMenuTarget == book.id, onDismissRequest = { bookMenuTarget = null }) {
                                        DropdownMenuItem(text = { Text(if (book.pinned) "Unpin" else "Pin") },
                                            leadingIcon = { Icon(Icons.Outlined.PushPin, contentDescription = null, modifier = Modifier.size(20.dp)) },
                                            onClick = {
                                                books = books.map { if (it.id == book.id) it.copy(pinned = !it.pinned) else it }
                                                writeBookLibrary(context, books)
                                                bookMenuTarget = null
                                            })
                                        DropdownMenuItem(text = { Text("Remove") },
                                            leadingIcon = { Icon(Icons.Outlined.DeleteOutline, contentDescription = null, modifier = Modifier.size(20.dp)) },
                                            onClick = { pendingDelete = book; bookMenuTarget = null })
                                    }
                                }
                            }
                        }
                    }
                }
            }
          }
          FloatingActionButton(
              onClick = {
                  picker.launch(arrayOf("application/epub+zip", "application/pdf", "text/plain", "application/octet-stream"))
              },
              modifier = Modifier.align(Alignment.BottomEnd)
                  .padding(end = BuiltInToolDesign.fabEndPadding, bottom = BuiltInToolDesign.fabBottomPadding)
                  .size(56.dp),
              containerColor = MaterialTheme.colorScheme.primaryContainer,
              contentColor = MaterialTheme.colorScheme.onPrimaryContainer
          ) {
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
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Outlined.MoreVert, contentDescription = "Reader settings", tint = fg)
                    }
                }
                Box(
                    Modifier.weight(1f).fillMaxWidth().pointerInput(selectedBook?.id, currentPage) {
                        detectTapGestures(
                            onTap = { offset ->
                                val x = offset.x / size.width
                                when {
                                    x < 0.22f -> if (currentPage > 0) { pageDirection = -1; currentPage--; saveProgress(currentPage) }
                                    x > 0.78f -> if (bookContent != null && currentPage < bookContent!!.pages.lastIndex) { pageDirection = 1; currentPage++; saveProgress(currentPage) }
                                }
                            }
                        )
                    },
                    contentAlignment = Alignment.Center
                ) {
                    when {
                        loading -> Text("Opening book…", color = fg)
                        error != null -> Text(error ?: "", color = fg, modifier = Modifier.padding(24.dp), textAlign = TextAlign.Center)
                        bookContent == null -> Text(
                            "This file has no readable content. Try another EPUB, PDF, or TXT file.",
                            color = fg,
                            modifier = Modifier.padding(24.dp),
                            textAlign = TextAlign.Center
                        )
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
