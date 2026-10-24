package com.insomdroid.dislauncher

import android.content.Context
import android.net.Uri
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.style.TextDecoration
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.util.UUID
import java.util.zip.ZipInputStream

/**
 * Converts inline Markdown marks into styled editable text. The editor edits the styled text,
 * while persistence and interchange remain Markdown-compatible.
 */
internal fun richFieldFromMarkdown(markdown: String): TextFieldValue {
    val pattern = Regex("""(\*\*\*(.+?)\*\*\*|\*\*(.+?)\*\*|~~(.+?)~~|(?<!\*)\*(?!\*)(.+?)(?<!\*)\*(?!\*)|`([^`]+)`)""")
    val annotated = buildAnnotatedString {
        var cursor = 0
        pattern.findAll(markdown).forEach { match ->
            if (match.range.first > cursor) append(markdown.substring(cursor, match.range.first))
            val g = match.groupValues
            val (text, style) = when {
                g[2].isNotEmpty() -> g[2] to SpanStyle(fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic)
                g[3].isNotEmpty() -> g[3] to SpanStyle(fontWeight = FontWeight.Bold)
                g[4].isNotEmpty() -> g[4] to SpanStyle(textDecoration = TextDecoration.LineThrough)
                g[5].isNotEmpty() -> g[5] to SpanStyle(fontStyle = FontStyle.Italic)
                else -> g[6] to SpanStyle(fontFamily = FontFamily.Monospace)
            }
            val start = length
            append(text)
            addStyle(style, start, length)
            cursor = match.range.last + 1
        }
        if (cursor < markdown.length) append(markdown.substring(cursor))
    }
    return TextFieldValue(annotated, TextRange(annotated.length))
}

/** Serialize styled text back to Markdown without changing the visible text or block newlines. */
internal fun richFieldToMarkdown(text: AnnotatedString): String {
    data class Mark(val start: Int, val end: Int, val open: String, val close: String)
    val marks = mutableListOf<Mark>()
    text.spanStyles.forEach { range ->
        val style = range.item
        val openClose = when {
            style.fontWeight == FontWeight.Bold && style.fontStyle == FontStyle.Italic -> "***" to "***"
            style.fontWeight == FontWeight.Bold -> "**" to "**"
            style.fontStyle == FontStyle.Italic -> "*" to "*"
            style.textDecoration?.contains(TextDecoration.LineThrough) == true -> "~~" to "~~"
            style.fontFamily == FontFamily.Monospace -> "`" to "`"
            else -> null
        }
        if (openClose != null && range.start >= 0 && range.end <= text.length && range.start < range.end) {
            marks += Mark(range.start, range.end, openClose.first, openClose.second)
        }
    }
    val result = StringBuilder(text.text)
    // Insert at the end first so original selection offsets remain valid.
    marks.sortedWith(compareByDescending<Mark> { it.start }.thenByDescending { it.end }).forEach { mark ->
        result.insert(mark.end, mark.close)
        result.insert(mark.start, mark.open)
    }
    return result.toString()
}

internal fun applyRichMark(field: TextFieldValue, mark: String): TextFieldValue {
    val start = field.selection.min.coerceIn(0, field.text.length)
    val end = field.selection.max.coerceIn(start, field.text.length)
    if (start == end) return field
    val style = when (mark) {
        "bold" -> SpanStyle(fontWeight = FontWeight.Bold)
        "italic" -> SpanStyle(fontStyle = FontStyle.Italic)
        "strike" -> SpanStyle(textDecoration = TextDecoration.LineThrough)
        "code" -> SpanStyle(fontFamily = FontFamily.Monospace)
        else -> return field
    }
    val styled = androidx.compose.ui.text.buildAnnotatedString {
        append(field.annotatedString)
        addStyle(style, start, end)
    }
    return field.copy(annotatedString = styled)
}

internal fun applyBlockPrefix(markdown: String, field: TextFieldValue, prefix: String): String {
    val visibleStart = field.text.lastIndexOf('\n', (field.selection.min - 1).coerceAtLeast(0))
        .let { if (it < 0) 0 else it + 1 }
    val lineIndex = field.text.substring(0, visibleStart).count { it == '\n' }
    val markdownLines = markdown.split('\n').toMutableList()
    while (markdownLines.size <= lineIndex) markdownLines += ""
    markdownLines[lineIndex] = prefix + markdownLines[lineIndex]
    return markdownLines.joinToString("\n")
}

/**
 * Imports only the archive format produced by Dislauncher. Unknown files are ignored; unsafe
 * paths, excessive entry counts, and oversized expanded content are rejected.
 */
internal fun readNoteArchive(context: Context, input: InputStream): String {
    val archiveId = UUID.randomUUID().toString()
    val imageRoot = File(context.filesDir, "notes-import/$archiveId/images")
    val markdownBytes = ByteArrayOutputStream()
    val extracted = mutableListOf<Pair<String, File>>()
    var entryCount = 0
    var expandedTotal = 0L
    ZipInputStream(input.buffered()).use { zip ->
        while (true) {
            val entry = zip.nextEntry ?: break
            entryCount++
            require(entryCount <= 256) { "Archive contains too many entries" }
            val name = entry.name.replace('\\', '/')
            val allowedEntry = name == "note.md" || name == "README.txt" || name == "images" || name == "images/" ||
                (name.startsWith("images/") && name.count { it == '/' } == 1)
            require(allowedEntry) { "Archive contains an unsupported file: $name" }
            require(!name.startsWith("/") && !name.split('/').any { it == ".." } && !name.contains(':')) {
                "Archive contains an unsafe path"
            }
            if (!entry.isDirectory && (name == "note.md" || name == "README.txt" || (name.startsWith("images/") && name.count { it == '/' } == 1))) {
                val out = ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    val count = zip.read(buffer)
                    if (count < 0) break
                    expandedTotal += count
                    require(expandedTotal <= 100L * 1024 * 1024) { "Archive expands beyond the 100 MiB limit" }
                    val entryLimit = when (name) { "note.md" -> 1_048_576; "README.txt" -> 4096; else -> 20 * 1024 * 1024 }
                    require(out.size() + count <= entryLimit) {
                        if (name == "note.md") "Markdown exceeds the 1 MiB limit" else "An image exceeds the 20 MiB limit"
                    }
                    out.write(buffer, 0, count)
                }
                if (name == "note.md") {
                    markdownBytes.reset()
                    markdownBytes.write(out.toByteArray())
                } else if (name != "README.txt") {
                    val baseName = name.substringAfterLast('/').replace(Regex("[^A-Za-z0-9._-]"), "_")
                    require(baseName.isNotBlank() && baseName != "." && baseName != "..") { "Invalid image name" }
                    val target = File(imageRoot, "${UUID.randomUUID()}_${baseName}")
                    extracted += name to target
                    target.parentFile?.mkdirs()
                    target.writeBytes(out.toByteArray())
                }
            }
            zip.closeEntry()
        }
    }
    require(markdownBytes.size() > 0) { "Archive does not contain note.md" }
    var markdown = markdownBytes.toByteArray().toString(Charsets.UTF_8)
    extracted.forEach { (entryName, file) ->
        val relative = Regex.escape(entryName)
        markdown = markdown.replace(Regex("""(!\[[^]]*]\()<?$relative>?\)""")) { match ->
            "${match.groupValues[1]}<${Uri.fromFile(file)}>)"
        }
    }
    return markdown
}
