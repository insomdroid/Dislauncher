package com.insomdroid.dislauncher

import android.content.Context
import android.net.Uri
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Portable, structured note archive. The document JSON is the interchange format;
 * images are copied into the archive and references are rewritten to relative asset paths.
 */
internal data class ImportedNoteArchive(val title: String, val document: NoteDocument)

private const val MAX_NOTE_ARCHIVE_ENTRIES = 256
private const val MAX_NOTE_ARCHIVE_IMAGE_BYTES = 20 * 1024 * 1024
private const val MAX_NOTE_ARCHIVE_TOTAL_BYTES = 100L * 1024 * 1024
private const val MAX_NOTE_DOCUMENT_BYTES = 2 * 1024 * 1024

internal fun writeStructuredNoteArchive(
    context: Context,
    destination: Uri,
    title: String,
    document: NoteDocument
) {
    val output = context.contentResolver.openOutputStream(destination)
        ?: error("Unable to open export destination")
    ZipOutputStream(output.buffered()).use { zip ->
        val imageEntries = linkedMapOf<String, ByteArray>()
        val sourceEntries = linkedMapOf<String, String>()
        var totalBytes = 0L
        val portableBlocks = document.blocks.map { block ->
            block.copy(spans = block.spans.map spanLoop@ { span ->
                val source = span.imageUri ?: return@spanLoop span
                val sourceUri = Uri.parse(source)
                if (sourceUri.scheme !in setOf("content", "file")) return@spanLoop span
                if (sourceUri.scheme == "file") {
                    val allowedRoot = File(context.filesDir, "notes-import").canonicalFile
                    val candidate = File(sourceUri.path.orEmpty()).canonicalFile
                    require(candidate.path.startsWith(allowedRoot.path + File.separator)) {
                        "An image reference points outside Dislauncher storage"
                    }
                }
                val entry = sourceEntries[source] ?: run {
                    val bytes = context.contentResolver.openInputStream(sourceUri)?.use { readBounded(it, MAX_NOTE_ARCHIVE_IMAGE_BYTES) }
                        ?: error("Could not read a local image referenced by this note")
                    totalBytes += bytes.size
                    require(totalBytes <= MAX_NOTE_ARCHIVE_TOTAL_BYTES) { "Images exceed the 100 MiB export limit" }
                    val extension = sourceUri.lastPathSegment?.substringAfterLast('.', "")?.lowercase()
                        ?.takeIf { it.matches(Regex("[a-z0-9]{1,8}")) } ?: "bin"
                    val name = "images/image_${imageEntries.size + 1}.$extension"
                    imageEntries[name] = bytes
                    sourceEntries[source] = name
                    name
                }
                span.copy(imageUri = entry)
            })
        }
        val portableDocument = document.copy(blocks = portableBlocks)
        val root = JSONObject()
            .put("format", "dislauncher-note")
            .put("formatVersion", 1)
            .put("title", title)
            .put("document", JSONObject(NoteDocumentCodec.encode(portableDocument)))
        val documentBytes = root.toString().toByteArray(Charsets.UTF_8)
        require(documentBytes.size <= MAX_NOTE_DOCUMENT_BYTES) { "Note document exceeds the 2 MiB export limit" }
        zip.putNextEntry(ZipEntry("note.json"))
        zip.write(documentBytes)
        zip.closeEntry()
        imageEntries.forEach { (name, bytes) ->
            zip.putNextEntry(ZipEntry(name))
            zip.write(bytes)
            zip.closeEntry()
        }
    }
}

internal fun readStructuredNoteArchive(context: Context, input: InputStream): ImportedNoteArchive {
    val archiveRoot = File(context.filesDir, "notes-import/${UUID.randomUUID()}").canonicalFile
    val assets = linkedMapOf<String, File>()
    var documentBytes: ByteArray? = null
    var entryCount = 0
    var totalBytes = 0L
    ZipInputStream(input.buffered()).use { zip ->
        while (true) {
            val entry = zip.nextEntry ?: break
            entryCount++
            require(entryCount <= MAX_NOTE_ARCHIVE_ENTRIES) { "Archive contains too many entries" }
            val name = entry.name.replace('\\', '/')
            require(!name.startsWith("/") && !name.contains(':') && !name.split('/').any { it == ".." }) {
                "Archive contains an unsafe path"
            }
            val isDocument = name == "note.json"
            val isImage = name.startsWith("images/") && name.count { it == '/' } == 1 &&
                name.substringAfter("images/").isNotBlank() && !entry.isDirectory
            require(isDocument || isImage || name == "images" || name == "images/") {
                "Archive contains an unsupported file: $name"
            }
            if (!entry.isDirectory && (isDocument || isImage)) {
                val limit = if (isDocument) MAX_NOTE_DOCUMENT_BYTES else MAX_NOTE_ARCHIVE_IMAGE_BYTES
                val bytes = readBounded(zip, limit)
                totalBytes += bytes.size
                require(totalBytes <= MAX_NOTE_ARCHIVE_TOTAL_BYTES) { "Archive expands beyond the 100 MiB limit" }
                if (isDocument) {
                    require(documentBytes == null) { "Archive contains duplicate note.json files" }
                    documentBytes = bytes
                } else {
                    val file = File(archiveRoot, name).canonicalFile
                    require(file.path.startsWith(archiveRoot.path + File.separator)) { "Archive contains an unsafe path" }
                    file.parentFile?.mkdirs()
                    file.writeBytes(bytes)
                    assets[name] = file
                }
            }
            zip.closeEntry()
        }
    }
    val bytes = documentBytes ?: error("Archive does not contain note.json")
    val root = JSONObject(bytes.toString(Charsets.UTF_8))
    require(root.optString("format") == "dislauncher-note" && root.optInt("formatVersion") == 1) {
        "Unsupported note archive format"
    }
    val title = root.optString("title")
    val encoded = root.optJSONObject("document")?.toString() ?: error("Archive has no structured document")
    val decoded = NoteDocumentCodec.decode(encoded) ?: error("Note document is invalid")
    val localDocument = decoded.copy(blocks = decoded.blocks.map { block ->
        block.copy(spans = block.spans.map { span ->
            val path = span.imageUri
            val file = path?.let { assets[it] }
            if (file != null) span.copy(imageUri = Uri.fromFile(file).toString()) else span
        })
    })
    return ImportedNoteArchive(title, localDocument)
}

private fun readBounded(input: InputStream, maxBytes: Int): ByteArray {
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    while (true) {
        val count = input.read(buffer)
        if (count < 0) break
        require(output.size() + count <= maxBytes) { "An archive entry exceeds its size limit" }
        output.write(buffer, 0, count)
    }
    return output.toByteArray()
}
