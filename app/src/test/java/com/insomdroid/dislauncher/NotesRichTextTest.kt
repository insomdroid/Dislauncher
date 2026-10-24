package com.insomdroid.dislauncher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class NoteDocumentTest {
    @Test
    fun structuredDocumentRoundTripsAllBlockAndInlineData() {
        val original = NoteDocument(
            blocks = listOf(
                NoteBlock(
                    id = "heading-1",
                    type = NoteBlockType.HEADING,
                    level = 2,
                    spans = listOf(NoteInline("Planning", setOf(NoteMark.BOLD)))
                ),
                NoteBlock(
                    id = "task-1",
                    type = NoteBlockType.CHECKLIST,
                    checked = true,
                    spans = listOf(NoteInline("Ship release"))
                ),
                NoteBlock(
                    id = "link-1",
                    type = NoteBlockType.PARAGRAPH,
                    spans = listOf(NoteInline("Project", link = "https://example.com"))
                ),
                NoteBlock(
                    id = "image-1",
                    type = NoteBlockType.PARAGRAPH,
                    spans = listOf(NoteInline("", imageUri = "file:///tmp/image.png", imageAlt = "Reference"))
                )
            )
        )

        val decoded = NoteDocumentCodec.decode(NoteDocumentCodec.encode(original))

        assertEquals(original, decoded)
    }

    @Test
    fun plainTextProjectionDoesNotExposeFormattingSyntax() {
        val document = NoteDocument(
            blocks = listOf(
                NoteBlock("heading", NoteBlockType.HEADING, spans = listOf(NoteInline("Ideas", setOf(NoteMark.BOLD)))),
                NoteBlock("task", NoteBlockType.CHECKLIST, checked = false, spans = listOf(NoteInline("Write tests")))
            )
        )

        assertEquals("Ideas\n☐ Write tests", NoteDocumentCodec.plainText(document))
    }

    @Test
    fun legacyNotesMigrateIntoTypedBlocks() {
        val document = NoteDocumentCodec.fromLegacyContent("# Heading\n- [x] Done\n- Next")

        assertEquals(NoteBlockType.HEADING, document.blocks[0].type)
        assertEquals(NoteBlockType.CHECKLIST, document.blocks[1].type)
        assertEquals(true, document.blocks[1].checked)
        assertEquals(NoteBlockType.BULLET, document.blocks[2].type)
        assertNotNull(NoteDocumentCodec.decode(NoteDocumentCodec.encode(document)))
    }
}
