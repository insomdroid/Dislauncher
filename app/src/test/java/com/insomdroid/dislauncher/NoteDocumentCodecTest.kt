package com.insomdroid.dislauncher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NoteDocumentCodecTest {
    @Test
    fun markdownRoundTripPreservesEscapedPunctuation() {
        val original = "literal * star, _ underscore, ~ tilde, \\ slash, and [brackets]"
        val parsed = NoteDocumentCodec.fromLegacyContent(
            NoteDocumentCodec.toMarkdown(
                NoteDocument(
                    blocks = listOf(
                        NoteBlock(id = "p1", type = NoteBlockType.PARAGRAPH, spans = listOf(NoteInline(original)))
                    )
                )
            )
        )

        assertEquals(original, parsed.blocks.single().spans.joinToString("") { it.text })
    }

    @Test
    fun markdownRoundTripPreservesUnderline() {
        val document = NoteDocument(
            blocks = listOf(
                NoteBlock(
                    id = "p1",
                    type = NoteBlockType.PARAGRAPH,
                    spans = listOf(NoteInline("underlined", setOf(NoteMark.UNDERLINE)))
                )
            )
        )

        val parsed = NoteDocumentCodec.fromLegacyContent(NoteDocumentCodec.toMarkdown(document))

        assertEquals(setOf(NoteMark.UNDERLINE), parsed.blocks.single().spans.single().marks)
        assertEquals("underlined", parsed.blocks.single().spans.single().text)
    }

    @Test
    fun markdownCodeFenceKeepsMarkdownCharactersLiteral() {
        val code = "**not bold** and `ticks`\n*not italic*"
        val document = NoteDocument(
            blocks = listOf(
                NoteBlock(id = "code1", type = NoteBlockType.CODE, spans = listOf(NoteInline(code)))
            )
        )

        val parsed = NoteDocumentCodec.fromLegacyContent(NoteDocumentCodec.toMarkdown(document))

        assertEquals(NoteBlockType.CODE, parsed.blocks.single().type)
        assertEquals(code, parsed.blocks.single().spans.joinToString("") { it.text })
        assertTrue(parsed.blocks.single().spans.none { it.marks.isNotEmpty() })
    }
}
