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
    @Test
    fun markdownRoundTripPreservesEscapesInsideFormatting() {
        val document = NoteDocument(
            blocks = listOf(
                NoteBlock(
                    id = "p1",
                    type = NoteBlockType.PARAGRAPH,
                    spans = listOf(
                        NoteInline("bold * star", setOf(NoteMark.BOLD)),
                        NoteInline(" "),
                        NoteInline("code ` tick", setOf(NoteMark.CODE))
                    )
                )
            )
        )

        val parsed = NoteDocumentCodec.fromLegacyContent(NoteDocumentCodec.toMarkdown(document))
        val spans = parsed.blocks.single().spans

        assertEquals("bold * star", spans[0].text)
        assertEquals(setOf(NoteMark.BOLD), spans[0].marks)
        assertEquals(" ", spans[1].text)
        assertEquals("code ` tick", spans[2].text)
        assertEquals(setOf(NoteMark.CODE), spans[2].marks)
    }

    @Test
    fun noteMarkdownLinkParserAcceptsOnlyHttpAndHttpsDestinations() {
        val document = NoteDocumentCodec.fromLegacyContent(
            "[safe](https://example.com) [also-safe](http://example.com) [unsafe](javascript:alert(1))"
        )
        val links = document.blocks.flatMap { it.spans }.mapNotNull { it.link }
        assertEquals(listOf("https://example.com", "http://example.com"), links)
    }

}
