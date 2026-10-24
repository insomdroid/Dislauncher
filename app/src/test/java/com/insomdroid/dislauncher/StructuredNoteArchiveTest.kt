package com.insomdroid.dislauncher

import android.content.Context
import android.net.Uri
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
class StructuredNoteArchiveTest {
    private val context: Context
        get() = RuntimeEnvironment.getApplication()

    @Test
    fun exportsAndImportsStructuredNoteMetadataAndFormatting() {
        val file = File(context.cacheDir, "note-archive-${UUID.randomUUID()}.zip")
        val document = NoteDocument(
            blocks = listOf(
                NoteBlock(
                    id = "heading",
                    type = NoteBlockType.HEADING,
                    level = 2,
                    spans = listOf(NoteInline("Trip plan", setOf(NoteMark.BOLD)))
                ),
                NoteBlock(
                    id = "check",
                    type = NoteBlockType.CHECKLIST,
                    checked = true,
                    spans = listOf(NoteInline("Book hotel"))
                )
            )
        )
        try {
            writeStructuredNoteArchive(
                context = context,
                destination = Uri.fromFile(file),
                title = "Travel",
                document = document,
                labels = listOf("personal", "travel"),
                color = "blue"
            )

            val imported = FileInputStream(file).use { readStructuredNoteArchive(context, it) }

            assertEquals("Travel", imported.title)
            assertEquals(document, imported.document)
            assertEquals(listOf("personal", "travel"), imported.labels)
            assertEquals("blue", imported.color)
        } finally {
            file.delete()
        }
    }

    @Test
    fun rejectsArchiveWithUnsupportedFormat() {
        val bytes = zipWithEntry(
            "note.json",
            JSONObject()
                .put("format", "unknown-format")
                .put("formatVersion", 1)
                .toString()
        )
        assertThrows(IllegalArgumentException::class.java) {
            readStructuredNoteArchive(context, ByteArrayInputStream(bytes))
        }
    }

    @Test
    fun rejectsArchivePathTraversal() {
        val bytes = zipWithEntry("../outside.txt", "unexpected")
        assertThrows(IllegalArgumentException::class.java) {
            readStructuredNoteArchive(context, ByteArrayInputStream(bytes))
        }
    }

    private fun zipWithEntry(name: String, content: String): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            zip.putNextEntry(ZipEntry(name))
            zip.write(content.toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        }
        return output.toByteArray()
    }
}
