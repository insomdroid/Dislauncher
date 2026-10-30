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

    @Test
    fun diskBackedReaderPagesCanBeReadInOrderAndRevisited() {
        val file = java.io.File.createTempFile("reader-pages", ".bin")
        try {
            val writer = DiskBackedBookPages.Writer(file, pageLimit = 32)
            writer.append("First page text.")
            writer.finishSection()
            writer.append("Second page text.")
            val pages = writer.finish()

            assertEquals(2, pages.size)
            assertEquals("First page text.", pages[0])
            assertEquals("Second page text.", pages[1])
            assertEquals("First page text.", pages[0])
        } finally {
            file.delete()
        }
    }

    @Test
    fun diskBackedReaderProvidesPlaceholderForEmptyInput() {
        val file = java.io.File.createTempFile("reader-pages", ".bin")
        try {
            val pages = DiskBackedBookPages.Writer(file).finish()
            assertEquals(1, pages.size)
            assertEquals("This book contains no readable text.", pages[0])
        } finally {
            file.delete()
        }
    }

    @Test
    fun markdownEditHistoryIsBoundedToFiftySteps() {
        var history = MarkdownEditHistory()
        repeat(60) { index -> history = history.record("state-$index") }
        assertEquals(MAX_MARKDOWN_HISTORY_STEPS, history.undo.size)
        assertEquals("state-59", history.undo.last())
        repeat(MAX_MARKDOWN_HISTORY_STEPS) { index ->
            history = history.undo("current-$index")!!.history
        }
        assertTrue(history.undo.isEmpty())
        assertEquals(MAX_MARKDOWN_HISTORY_STEPS, history.redo.size)
    }

    @Test
    fun markdownEditHistoryCanUndoAndRedo() {
        val initial = MarkdownEditHistory()
        val first = initial.record("one")
        val second = first.record("two")
        val undone = second.undo("three")!!
        assertEquals("two", undone.text)
        val redone = undone.history.redo("two")!!
        assertEquals("three", redone.text)
        assertTrue(redone.history.redo.isEmpty())
    }
}
