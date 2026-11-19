package com.insomdroid.dislauncher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotePersistenceTest {
    @Test
    fun snapshotPreservesMetadataAndCreatesStructuredDocument() {
        val existing = LocalNote(
            id = "note-1",
            title = "Old title",
            content = "Old content",
            updatedAt = 10L,
            pinned = true
        )

        val result = buildLocalNoteSnapshot(
            id = "note-1",
            title = "Updated title",
            markdown = "# Heading\n\n- First item\n- Second item",
            existing = existing,
            labels = listOf("work", "ideas"),
            color = "blue",
            updatedAt = 20L
        )

        assertEquals("note-1", result.id)
        assertEquals("Updated title", result.title)
        assertEquals("Heading\n• First item\n• Second item", result.content)
        assertEquals(20L, result.updatedAt)
        assertTrue(result.pinned)
        assertEquals(listOf("work", "ideas"), result.labels)
        assertEquals("blue", result.color)
        assertEquals("# Heading\n\n- First item\n\n- Second item", result.markdown)
        assertEquals(result.documentJson, NoteDocumentCodec.encode(
            NoteDocumentCodec.decode(result.documentJson)!!
        ))
    }

    @Test
    fun newSnapshotDefaultsPinnedToFalse() {
        val result = buildLocalNoteSnapshot(
            id = "new-note",
            title = "New",
            markdown = "Body",
            existing = null,
            labels = emptyList(),
            color = "default",
            updatedAt = 30L
        )

        assertFalse(result.pinned)
        assertEquals("Body", result.content)
        assertEquals("New", result.title)
    }

    @Test
    fun emptyMarkdownProducesAnEmptyDocumentAndContent() {
        val result = buildLocalNoteSnapshot(
            id = "empty-note",
            title = "",
            markdown = " \n ",
            existing = null,
            labels = emptyList(),
            color = "default",
            updatedAt = 40L
        )

        assertTrue(result.content.isEmpty())
        assertTrue(NoteDocumentCodec.decode(result.documentJson)?.blocks.isNullOrEmpty())
    }
}
