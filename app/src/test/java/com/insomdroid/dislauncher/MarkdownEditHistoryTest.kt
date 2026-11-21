package com.insomdroid.dislauncher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Test

class MarkdownEditHistoryTest {
    @Test
    fun restoresPreviousTextAndCursorOnUndoAndRedo() {
        val history = MarkdownEditHistory()
            .record("first", cursor = 3, nowMillis = 1_000)
            .record("second", cursor = 5, nowMillis = 2_000)

        val undone = history.undo("third", cursor = 2)
        assertNotNull(undone)
        assertEquals("second", undone!!.text)
        assertEquals(5, undone.cursor)

        val redone = undone.history.redo(undone.text, undone.cursor)
        assertNotNull(redone)
        assertEquals("third", redone!!.text)
        assertEquals(2, redone.cursor)
    }

    @Test
    fun clampsRecordedAndRestoredCursorPositions() {
        val history = MarkdownEditHistory().record("hello", cursor = 99, nowMillis = 1_000)
        val undone = history.undo("changed", cursor = -5)
        assertNotNull(undone)
        assertEquals(5, undone!!.cursor)
    }

    @Test
    fun returnsNullWhenUndoOrRedoIsUnavailable() {
        val history = MarkdownEditHistory()
        assertNull(history.undo("text"))
        assertNull(history.redo("text"))
    }

    @Test
    fun coalescesEditsWithinTheConfiguredWindow() {
        val history = MarkdownEditHistory()
            .record("first", nowMillis = 1_000)
            .record("second", nowMillis = 1_500)

        assertEquals(1, history.undo.size)
        assertEquals("first", history.undo.single().text)
    }

    @Test
    fun clearsRedoHistoryWhenANewEditIsRecorded() {
        val initial = MarkdownEditHistory()
            .record("first", nowMillis = 1_000)
            .record("second", nowMillis = 2_000)
        val undone = initial.undo("third")!!
        val edited = undone.history.record("new edit", nowMillis = 4_000)

        assertEquals(0, edited.redo.size)
    }

    @Test
    fun enforcesMaximumHistoryStepCount() {
        var history = MarkdownEditHistory()
        repeat(MAX_MARKDOWN_HISTORY_STEPS + 10) { index ->
            history = history.record("entry-$index", nowMillis = index * 1_000L)
        }

        assertEquals(MAX_MARKDOWN_HISTORY_STEPS, history.undo.size)
    }
}
