package com.insomdroid.dislauncher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NoteDocumentCodecTest {
    @Test
    fun structuredDocumentRoundTripsRichBlocksAndInlineMarks() {
        val document = NoteDocument(
            blocks = listOf(
                NoteBlock("heading", NoteBlockType.HEADING, spans = listOf(NoteInline("Heading", setOf(NoteMark.BOLD))), level = 2),
                NoteBlock("paragraph", NoteBlockType.PARAGRAPH, spans = listOf(
                    NoteInline("bold", setOf(NoteMark.BOLD)),
                    NoteInline(" italic", setOf(NoteMark.ITALIC)),
                    NoteInline(" link", link = "https://example.com")
                )),
                NoteBlock("checked", NoteBlockType.CHECKLIST, spans = listOf(NoteInline("finished")), checked = true),
                NoteBlock("unchecked", NoteBlockType.CHECKLIST, spans = listOf(NoteInline("next")), checked = false),
                NoteBlock("numbered", NoteBlockType.NUMBERED, spans = listOf(NoteInline("first")), orderedIndex = 2, indentLevel = 1),
                NoteBlock("quote", NoteBlockType.QUOTE, spans = listOf(NoteInline("quoted"))),
                NoteBlock("code", NoteBlockType.CODE, spans = listOf(NoteInline("val answer = 42", setOf(NoteMark.CODE)))),
                NoteBlock("divider", NoteBlockType.DIVIDER)
            )
        )

        assertEquals(document, NoteDocumentCodec.decode(NoteDocumentCodec.encode(document)))
        assertTrue(document.blocks.any { it.type == NoteBlockType.CHECKLIST && it.checked })
        assertTrue(document.blocks.any { it.indentLevel == 1 })
    }

    @Test
    fun emptyLegacyContentProducesAnEmptyDocument() {
        assertTrue(NoteDocumentCodec.fromLegacyContent("").blocks.isEmpty())
        assertEquals("", NoteDocumentCodec.plainText(NoteDocument()))
    }

    @Test
    fun migratesLegacyParagraphsAndImageReferencesIntoTypedBlocks() {
        val legacy = "First line\nsecond line\n\n![photo](<content://media/external/images/media/42>)"
        val document = NoteDocumentCodec.fromLegacyContent(legacy)

        assertTrue(document.blocks.any { it.type == NoteBlockType.PARAGRAPH && it.spans.any { span -> span.text.contains("First line") } })
        val image = document.blocks.flatMap { it.spans }.single { it.imageUri != null }
        assertEquals("content://media/external/images/media/42", image.imageUri)
        assertEquals("photo", image.imageAlt)
    }

    @Test
    fun malformedDocumentJsonIsRejectedSafely() {
        assertNull(NoteDocumentCodec.decode("not-json"))
        assertTrue(NoteDocumentCodec.decode("""{"version":1,"blocks":[{"type":"NOT_A_BLOCK"}]}""")!!.blocks.isEmpty())
    }

    @Test
    fun rejectsUnknownDocumentVersions() {
        assertNull(NoteDocumentCodec.decode("""{"version":99,"blocks":[]}"""))
    }
}
