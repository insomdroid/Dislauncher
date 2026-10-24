package com.insomdroid.dislauncher

import org.junit.Assert.assertEquals
import org.junit.Test

class NotesRichTextTest {
    @Test
    fun markdownInlineMarksRenderAsStyledTextAndRoundTrip() {
        val field = richFieldFromMarkdown("Keep **this** bold and *this* italic.")
        assertEquals("Keep this bold and this italic.", field.text)
        assertEquals("Keep **this** bold and *this* italic.", richFieldToMarkdown(field.annotatedString))
    }

    @Test
    fun selectedTextCanReceiveRichFormatting() {
        val field = richFieldFromMarkdown("hello world")
            .copy(selection = androidx.compose.ui.text.input.TextRange(6, 11))
        val formatted = applyRichMark(field, "bold")
        assertEquals("hello **world**", richFieldToMarkdown(formatted.annotatedString))
    }

    @Test
    fun markdownBlockPrefixesPreserveLineStructure() {
        val field = richFieldFromMarkdown("first\nsecond")
            .copy(selection = androidx.compose.ui.text.input.TextRange(8))
        assertEquals("first\n- second", applyBlockPrefix("first\nsecond", field, "- "))
    }
}
