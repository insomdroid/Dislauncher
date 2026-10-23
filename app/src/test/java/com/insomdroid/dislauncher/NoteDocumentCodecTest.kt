package com.insomdroid.dislauncher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NoteDocumentCodecTest {
    @Test
    fun roundTripsCommonMarkdownBlocksAndInlineMarks() {
        val markdown = """# Heading

A **bold**, *italic*, ***both***, ~~removed~~, `code`, and [site](https://example.com).

- [x] finished
- [ ] next

1. first
2. second

> quoted
> continued

---

```
val answer = 42
```"""

        val parsed = NoteDocumentCodec.fromMarkdown(markdown)

        assertEquals(markdown, NoteDocumentCodec.toMarkdown(parsed))
        assertEquals(markdown, NoteDocumentCodec.toMarkdown(
            NoteDocumentCodec.decode(NoteDocumentCodec.encode(parsed))!!
        ))
        assertTrue(parsed.blocks.any { it.type == NoteBlockType.CHECKLIST && it.checked })
        assertTrue(parsed.blocks.any {
            it.spans.any { span ->
                NoteMark.BOLD in span.marks && NoteMark.ITALIC in span.marks && span.text == "both"
            }
        })
    }

    @Test
    fun emptyMarkdownProducesAnEmptyDocument() {
        assertTrue(NoteDocumentCodec.fromMarkdown("").blocks.isEmpty())
        assertEquals("", NoteDocumentCodec.toMarkdown(NoteDocument()))
    }

    @Test
    fun rejectsUnknownDocumentVersions() {
        assertNull(NoteDocumentCodec.decode("""{"version":99,"blocks":[]}"""))
    }
}
