package com.insomdroid.dislauncher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NoteDocumentCodecTest {
    @Test
    fun parsesSupportedBlockTypesAndTheirMetadata() {
        val document = NoteDocumentCodec.fromLegacyContent(
            "# Heading\n- Bullet\n3. Numbered\n- [x] Completed\n> Quoted\n---\n```kotlin\nval answer = 42\n```"
        )

        assertEquals(
            listOf(
                NoteBlockType.HEADING,
                NoteBlockType.BULLET,
                NoteBlockType.NUMBERED,
                NoteBlockType.CHECKLIST,
                NoteBlockType.QUOTE,
                NoteBlockType.DIVIDER,
                NoteBlockType.CODE
            ),
            document.blocks.map { it.type }
        )
        assertEquals(1, document.blocks[0].level)
        assertEquals(3, document.blocks[2].orderedIndex)
        assertTrue(document.blocks[3].checked)
        assertEquals("Quoted", document.blocks[4].spans.single().text)
        assertEquals("val answer = 42", document.blocks[6].spans.single().text)
    }

    @Test
    fun parsesInlineFormattingLinksAndEscapedCharacters() {
        val document = NoteDocumentCodec.fromLegacyContent(
            "Plain **bold** *italic* ~~strike~~ `code` <u>underlined</u> [website](https://example.com) and \\*literal\\*"
        )
        val spans = document.blocks.single().spans

        assertTrue(spans.any { it.text == "bold" && NoteMark.BOLD in it.marks })
        assertTrue(spans.any { it.text == "italic" && NoteMark.ITALIC in it.marks })
        assertTrue(spans.any { it.text == "strike" && NoteMark.STRIKETHROUGH in it.marks })
        assertTrue(spans.any { it.text == "code" && NoteMark.CODE in it.marks })
        assertTrue(spans.any { it.text == "underlined" && NoteMark.UNDERLINE in it.marks })
        assertTrue(spans.any { it.text == "website" && it.link == "https://example.com" })
        assertTrue(spans.any { it.text == "*" })
    }

    @Test
    fun encodesAndDecodesEveryStructuredField() {
        val original = NoteDocument(
            blocks = listOf(
                NoteBlock(
                    id = "heading-1",
                    type = NoteBlockType.HEADING,
                    spans = listOf(NoteInline("Title", setOf(NoteMark.BOLD, NoteMark.ITALIC))),
                    level = 3,
                    checked = true,
                    orderedIndex = 4,
                    indentLevel = 2
                ),
                NoteBlock(
                    id = "image-1",
                    type = NoteBlockType.PARAGRAPH,
                    spans = listOf(NoteInline("Alt", imageUri = "content://images/1", imageAlt = "Alt"))
                )
            )
        )

        val decoded = NoteDocumentCodec.decode(NoteDocumentCodec.encode(original))

        assertEquals(original, decoded)
    }

    @Test
    fun rejectsMalformedJsonAndUnsupportedVersions() {
        assertNull(NoteDocumentCodec.decode("not-json"))
        assertNull(NoteDocumentCodec.decode("""{"version":0,"blocks":[]}"""))
        assertNull(NoteDocumentCodec.decode("""{"version":999,"blocks":[]}"""))
    }

    @Test
    fun safelyNormalizesOutOfRangeBlockMetadata() {
        val decoded = NoteDocumentCodec.decode(
            """{"version":1,"blocks":[{"id":"","type":"HEADING","level":99,"orderedIndex":0,"indentLevel":99,"spans":[]}]}"""
        )

        assertEquals(1, decoded?.blocks?.single()?.level)
        assertEquals(1, decoded?.blocks?.single()?.orderedIndex)
        assertEquals(5, decoded?.blocks?.single()?.indentLevel)
        assertEquals("b1", decoded?.blocks?.single()?.id)
    }

    @Test
    fun plainTextUsesReadableRepresentationsForSpecialBlocks() {
        val document = NoteDocument(
            blocks = listOf(
                NoteBlock("1", NoteBlockType.BULLET, listOf(NoteInline("Task"))),
                NoteBlock("2", NoteBlockType.NUMBERED, listOf(NoteInline("Step")), orderedIndex = 2),
                NoteBlock("3", NoteBlockType.CHECKLIST, listOf(NoteInline("Done")), checked = true),
                NoteBlock("4", NoteBlockType.QUOTE, listOf(NoteInline("Quote"))),
                NoteBlock("5", NoteBlockType.DIVIDER)
            )
        )

        assertEquals("• Task\n2. Step\n☑ Done\n▏ Quote\n────────", NoteDocumentCodec.plainText(document))
    }

    @Test
    fun emptyLegacyContentProducesEmptyDocument() {
        assertTrue(NoteDocumentCodec.fromLegacyContent(" \n\t ").blocks.isEmpty())
        assertFalse(NoteDocumentCodec.fromLegacyContent("Content").blocks.isEmpty())
    }
}
