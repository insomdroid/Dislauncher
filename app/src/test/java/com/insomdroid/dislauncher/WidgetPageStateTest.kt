package com.insomdroid.dislauncher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetPageStateTest {
    @Test
    fun droppingOneWidgetOnAnotherReordersWithoutOverlap() {
        val quickNotes = WidgetPageItem(
            id = QUICK_NOTES_WIDGET_ID,
            kind = "quick_notes",
            x = 0,
            y = 0,
            width = 5,
            height = 2
        )
        val calendar = WidgetPageItem(
            id = MONTH_CALENDAR_WIDGET_ID,
            kind = "month_calendar",
            x = 0,
            y = 2,
            width = 5,
            height = 5
        )

        val moved = moveWidgetItem(
            items = listOf(quickNotes, calendar),
            itemId = QUICK_NOTES_WIDGET_ID,
            newX = 0,
            newY = 2
        )

        assertNotNull(moved)
        assertEquals(MONTH_CALENDAR_WIDGET_ID, moved!![0].id)
        assertEquals(0, moved[0].y)
        assertEquals(QUICK_NOTES_WIDGET_ID, moved[1].id)
        assertEquals(5, moved[1].y)
        assertFalse(widgetItemsOverlap(moved[0], moved[1]))
    }

    @Test
    fun growingQuickNotesMovesFollowingWidgetOnlyWhenSpaceAllows() {
        val quickNotes = WidgetPageItem(
            id = QUICK_NOTES_WIDGET_ID,
            kind = "quick_notes",
            x = 0,
            y = 0,
            width = 5,
            height = 2
        )
        val calendar = WidgetPageItem(
            id = MONTH_CALENDAR_WIDGET_ID,
            kind = "month_calendar",
            x = 0,
            y = 2,
            width = 5,
            height = 4
        )

        val resized = resizeWidgetItem(
            items = listOf(quickNotes, calendar),
            itemId = QUICK_NOTES_WIDGET_ID,
            newHeight = 3
        )

        assertNotNull(resized)
        assertEquals(3, resized!!.first { it.id == QUICK_NOTES_WIDGET_ID }.height)
        assertEquals(3, resized.first { it.id == MONTH_CALENDAR_WIDGET_ID }.y)
        assertTrue(resized.all { it.y + it.height <= WIDGET_PAGE_ROWS })
    }

    @Test
    fun placementFindsFirstFreeCellAndRejectsOverflow() {
        val quickNotes = WidgetPageItem(
            id = QUICK_NOTES_WIDGET_ID,
            kind = "quick_notes",
            x = 0,
            y = 0,
            width = 5,
            height = 2
        )

        assertEquals(0 to 2, findWidgetPlacement(listOf(quickNotes), 5, 5))
        assertEquals(null, findWidgetPlacement(listOf(quickNotes), 5, 6))
    }
}
