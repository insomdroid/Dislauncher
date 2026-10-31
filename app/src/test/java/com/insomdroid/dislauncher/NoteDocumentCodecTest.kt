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

            val reopened = DiskBackedBookPages.open(file)!!
            assertEquals(2, reopened.size)
            assertEquals("First page text.", reopened[0])
            assertEquals("Second page text.", reopened[1])
        } finally {
            file.delete()
            java.io.File(file.parentFile, file.name + ".idx").delete()
        }
    }

    @Test
    fun diskBackedReaderPreservesTextLineAndParagraphBreaks() {
        val file = java.io.File.createTempFile("reader-paragraphs", ".bin")
        try {
            val source = "First line\nSecond line\n\nThird paragraph"
            val pages = DiskBackedBookPages.Writer(file).apply { append(source) }.finish()
            assertEquals(source, pages.joinToString(""))
        } finally {
            file.delete()
            java.io.File(file.parentFile, file.name + ".idx").delete()
        }
    }

    @Test
    fun markdownEditHistoryCoalescesTypingAndRestoresCursor() {
        val first = MarkdownEditHistory().record("start", cursor = 2, nowMillis = 1_000L)
        val coalesced = first.record("started", cursor = 5, nowMillis = 1_500L)
        val separated = coalesced.record("started!", cursor = 8, nowMillis = 2_201L)

        assertEquals(listOf("start", "started"), separated.undo.map { it.text })
        val undone = separated.undo("started!!", cursor = 9)!!
        assertEquals("started", undone.text)
        assertEquals(5, undone.cursor)
        val redone = undone.history.redo("started", cursor = 5)!!
        assertEquals("started!!", redone.text)
        assertEquals(9, redone.cursor)
    }

    @Test
    fun markdownEditHistoryRespectsCombinedByteLimit() {
        val largeText = "x".repeat(MAX_MARKDOWN_HISTORY_BYTES / 2)
        var history = MarkdownEditHistory()
        repeat(4) { index ->
            history = history.record("$largeText$index", nowMillis = index * 1_000L)
        }
        val retainedBytes = history.undo.sumOf { it.byteCount.toLong() } +
            history.redo.sumOf { it.byteCount.toLong() }
        assertTrue(retainedBytes <= MAX_MARKDOWN_HISTORY_BYTES)
        assertTrue(history.undo.size + history.redo.size <= MAX_MARKDOWN_HISTORY_STEPS)
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
            java.io.File(file.parentFile, file.name + ".idx").delete()
        }
    }

    @Test
    fun markdownEditHistoryIsBoundedToFiftySteps() {
        var history = MarkdownEditHistory()
        repeat(60) { index -> history = history.record("state-$index", nowMillis = index * 1_000L) }
        assertEquals(MAX_MARKDOWN_HISTORY_STEPS, history.undo.size)
        assertEquals("state-59", history.undo.last().text)
        repeat(MAX_MARKDOWN_HISTORY_STEPS) { index ->
            history = history.undo("current-$index")!!.history
        }
        assertTrue(history.undo.isEmpty())
        assertEquals(MAX_MARKDOWN_HISTORY_STEPS, history.redo.size)
    }

    @Test
    fun markdownEditHistoryCanUndoAndRedo() {
        val initial = MarkdownEditHistory()
        val first = initial.record("one", nowMillis = 1_000L)
        val second = first.record("two", nowMillis = 2_000L)
        val undone = second.undo("three")!!
        assertEquals("two", undone.text)
        val redone = undone.history.redo("two")!!
        assertEquals("three", redone.text)
        assertTrue(redone.history.redo.isEmpty())
    }
}
