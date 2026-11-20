package com.insomdroid.dislauncher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetPageStateTest {
    @Test
    fun droppingOneWidgetOnAnotherReordersWithoutOverlap() {
        val first = WidgetPageItem(
            id = "android_widget_1",
            kind = "android_widget",
            x = 0,
            y = 0,
            width = 5,
            height = 2,
            appWidgetId = 1
        )
        val second = WidgetPageItem(
            id = "android_widget_2",
            kind = "android_widget",
            x = 0,
            y = 2,
            width = 5,
            height = 5,
            appWidgetId = 2
        )

        val moved = moveWidgetItem(
            items = listOf(first, second),
            itemId = first.id,
            newX = 0,
            newY = 2
        )

        assertNotNull(moved)
        assertEquals(second.id, moved!![0].id)
        assertEquals(0, moved[0].y)
        assertEquals(first.id, moved[1].id)
        assertEquals(5, moved[1].y)
        assertFalse(widgetItemsOverlap(moved[0], moved[1]))
    }

    @Test
    fun resizeRespectsProviderMinimumSizeAndGridBounds() {
        val widget = WidgetPageItem(
            id = "android_widget_1",
            kind = "android_widget",
            x = 0,
            y = 0,
            width = 2,
            height = 2,
            appWidgetId = 1,
            minWidthCells = 2,
            minHeightCells = 2
        )

        assertNotNull(resizeWidgetBounds(listOf(widget), widget.id, 0, 0, 3, 3))
        assertEquals(null, resizeWidgetBounds(listOf(widget), widget.id, 0, 0, 1, 2))
        assertEquals(null, resizeWidgetBounds(listOf(widget), widget.id, 0, 0, 2, 8))
    }

    @Test
    fun placementFindsFirstFreeCellAndRejectsOverflow() {
        val widget = WidgetPageItem(
            id = "android_widget_1",
            kind = "android_widget",
            x = 0,
            y = 0,
            width = 5,
            height = 2,
            appWidgetId = 1
        )

        assertEquals(0 to 2, findWidgetPlacement(listOf(widget), 5, 5))
        assertEquals(null, findWidgetPlacement(listOf(widget), 5, 6))
    }

    @Test
    fun widgetsCannotOverlapAfterPlacement() {
        val first = WidgetPageItem(
            id = "android_widget_1",
            kind = "android_widget",
            x = 0,
            y = 0,
            width = 2,
            height = 2,
            appWidgetId = 1
        )
        val second = first.copy(id = "android_widget_2", appWidgetId = 2, x = 1, y = 1)

        assertTrue(widgetItemsOverlap(first, second))
        assertFalse(canPlaceWidgetItem(listOf(first), second))
    }
}
