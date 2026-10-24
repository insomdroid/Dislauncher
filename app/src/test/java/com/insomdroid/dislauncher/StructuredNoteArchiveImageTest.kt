package com.insomdroid.dislauncher

import android.content.Context
import android.net.Uri
import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
class StructuredNoteArchiveImageTest {
    private val context: Context
        get() = RuntimeEnvironment.getApplication()

    @Test
    fun imageBytesSurviveArchiveExportAndImport() {
        val sourceDirectory = File(context.filesDir, "notes-import/test-" + UUID.randomUUID())
        assertTrue(sourceDirectory.mkdirs())
        val sourceImage = File(sourceDirectory, "image.png")
        val imageBytes = byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a)
        sourceImage.writeBytes(imageBytes)
        val archive = File(context.cacheDir, "note-image-" + UUID.randomUUID() + ".zip")
        try {
            val document = NoteDocument(
                blocks = listOf(
                    NoteBlock(
                        id = "image",
                        type = NoteBlockType.PARAGRAPH,
                        spans = listOf(
                            NoteInline(text = "sample", imageUri = Uri.fromFile(sourceImage).toString(), imageAlt = "sample image")
                        )
                    )
                )
            )
            writeStructuredNoteArchive(context, Uri.fromFile(archive), "Image note", document)

            val imported = FileInputStream(archive).use { readStructuredNoteArchive(context, it) }
            val importedUri = Uri.parse(imported.document.blocks.single().spans.single().imageUri)
            assertEquals("file", importedUri.scheme)
            val importedImage = File(importedUri.path!!)
            assertTrue(importedImage.isFile)
            assertArrayEquals(imageBytes, importedImage.readBytes())
        } finally {
            archive.delete()
            sourceDirectory.deleteRecursively()
        }
    }

    @Test
    fun rejectsDocumentThatReferencesMissingImageAndCleansExtractedFiles() {
        val root = JSONObject()
            .put("format", "dislauncher-note")
            .put("formatVersion", 1)
            .put("title", "Broken image")
            .put("document", JSONObject(NoteDocumentCodec.encode(
                NoteDocument(blocks = listOf(
                    NoteBlock(
                        id = "image",
                        type = NoteBlockType.PARAGRAPH,
                        spans = listOf(NoteInline("missing", imageUri = "images/missing.png"))
                    )
                ))
            )))
        val bytes = zipWithEntries("note.json" to root.toString())
        val before = File(context.filesDir, "notes-import").listFiles()?.map { it.name }?.toSet().orEmpty()

        assertThrows(IllegalStateException::class.java) {
            readStructuredNoteArchive(context, ByteArrayInputStream(bytes))
        }

        val after = File(context.filesDir, "notes-import").listFiles()?.map { it.name }?.toSet().orEmpty()
        assertEquals("Rejected import should not leave a new extraction directory", before, after)
    }

    @Test
    fun rejectsArchiveWithDuplicateDocumentEntries() {
        val doc = JSONObject()
            .put("format", "dislauncher-note")
            .put("formatVersion", 1)
            .put("title", "Duplicate")
            .put("document", JSONObject(NoteDocumentCodec.encode(NoteDocument())))
            .toString()
        val bytes = zipWithEntries("note.json" to doc, "note.json" to doc)

        assertThrows(IllegalArgumentException::class.java) {
            readStructuredNoteArchive(context, ByteArrayInputStream(bytes))
        }
    }

    @Test
    fun rejectsArchiveImageEntryWithPathTraversal() {
        val doc = JSONObject()
            .put("format", "dislauncher-note")
            .put("formatVersion", 1)
            .put("title", "Traversal")
            .put("document", JSONObject(NoteDocumentCodec.encode(NoteDocument())))
            .toString()
        val bytes = zipWithEntries("note.json" to doc, "images/../outside.png" to "bad")

        assertThrows(IllegalArgumentException::class.java) {
            readStructuredNoteArchive(context, ByteArrayInputStream(bytes))
        }
    }

    private fun zipWithEntries(vararg entries: Pair<String, String>): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            entries.forEach { (name, content) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
        }
        return output.toByteArray()
    }
}
