package com.insomdroid.dislauncher

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

internal data class WidgetPageItem(
    val id: String,
    val kind: String,
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int
)

private const val WIDGET_PAGE_STORE = "dislauncher_widget_page"
private const val WIDGET_PAGE_LAYOUT_KEY = "layout_json"
private const val WIDGET_PAGE_INITIALIZED_KEY = "layout_initialized"
internal const val WIDGET_PAGE_COLUMNS = 5
internal const val WIDGET_PAGE_ROWS = 7
internal const val QUICK_NOTES_WIDGET_ID = "builtin_quick_notes"
internal const val MONTH_CALENDAR_WIDGET_ID = "builtin_month_calendar"

private fun defaultWidgetPageItems(): List<WidgetPageItem> = listOf(
    WidgetPageItem(
        id = QUICK_NOTES_WIDGET_ID,
        kind = "quick_notes",
        x = 0,
        y = 0,
        width = WIDGET_PAGE_COLUMNS,
        height = 2
    ),
    WidgetPageItem(
        id = MONTH_CALENDAR_WIDGET_ID,
        kind = "month_calendar",
        x = 0,
        y = 2,
        width = WIDGET_PAGE_COLUMNS,
        height = 5
    )
)

internal fun loadWidgetPageItems(context: Context): List<WidgetPageItem> {
    val store = context.getSharedPreferences(WIDGET_PAGE_STORE, Context.MODE_PRIVATE)
    if (!store.getBoolean(WIDGET_PAGE_INITIALIZED_KEY, false)) {
        val defaults = defaultWidgetPageItems()
        saveWidgetPageItems(context, defaults)
        store.edit().putBoolean(WIDGET_PAGE_INITIALIZED_KEY, true).apply()
        return defaults
    }

    val raw = store.getString(WIDGET_PAGE_LAYOUT_KEY, "[]") ?: "[]"
    return runCatching {
        val array = JSONArray(raw)
        (0 until array.length()).mapNotNull { index ->
            val value = array.optJSONObject(index) ?: return@mapNotNull null
            val item = WidgetPageItem(
                id = value.optString("id"),
                kind = value.optString("kind"),
                x = value.optInt("x", 0),
                y = value.optInt("y", 0),
                width = value.optInt("width", WIDGET_PAGE_COLUMNS),
                height = value.optInt("height", 1)
            )
            item.takeIf {
                it.id.isNotBlank() &&
                    it.kind in setOf("quick_notes", "month_calendar") &&
                    it.width in 1..WIDGET_PAGE_COLUMNS &&
                    it.height in 1..WIDGET_PAGE_ROWS &&
                    it.x >= 0 && it.y >= 0 &&
                    it.x + it.width <= WIDGET_PAGE_COLUMNS &&
                    it.y + it.height <= WIDGET_PAGE_ROWS
            }
        }.let { items ->
            items.filterIndexed { index, item ->
                items.take(index).none { other -> widgetItemsOverlap(item, other) }
            }
        }
    }.getOrElse {
        if (!store.contains("layout_recovery_backup")) {
            store.edit().putString("layout_recovery_backup", raw).apply()
        }
        emptyList()
    }
}

internal fun saveWidgetPageItems(context: Context, items: List<WidgetPageItem>) {
    val array = JSONArray()
    items.forEach { item ->
        array.put(
            JSONObject()
                .put("id", item.id)
                .put("kind", item.kind)
                .put("x", item.x)
                .put("y", item.y)
                .put("width", item.width)
                .put("height", item.height)
        )
    }
    context.getSharedPreferences(WIDGET_PAGE_STORE, Context.MODE_PRIVATE)
        .edit()
        .putString(WIDGET_PAGE_LAYOUT_KEY, array.toString())
        .putBoolean(WIDGET_PAGE_INITIALIZED_KEY, true)
        .apply()
}

internal fun widgetItemsOverlap(first: WidgetPageItem, second: WidgetPageItem): Boolean =
    first.x < second.x + second.width &&
        first.x + first.width > second.x &&
        first.y < second.y + second.height &&
        first.y + first.height > second.y

internal fun canPlaceWidgetItem(
    items: List<WidgetPageItem>,
    candidate: WidgetPageItem,
    columns: Int = WIDGET_PAGE_COLUMNS,
    rows: Int = WIDGET_PAGE_ROWS
): Boolean {
    if (candidate.x < 0 || candidate.y < 0 ||
        candidate.width <= 0 || candidate.height <= 0 ||
        candidate.x + candidate.width > columns ||
        candidate.y + candidate.height > rows
    ) return false

    return items.filterNot { it.id == candidate.id }
        .none { widgetItemsOverlap(candidate, it) }
}

internal fun moveWidgetItem(
    items: List<WidgetPageItem>,
    itemId: String,
    newX: Int,
    newY: Int
): List<WidgetPageItem>? {
    val moving = items.firstOrNull { it.id == itemId } ?: return null
    val candidate = moving.copy(x = newX, y = newY)
    if (canPlaceWidgetItem(items, candidate)) {
        return items.map { if (it.id == itemId) candidate else it }
    }

    val target = items.firstOrNull {
        it.id != itemId &&
            newX < it.x + it.width && newX + moving.width > it.x &&
            newY < it.y + it.height && newY + moving.height > it.y
    } ?: return null

    val swappedMoving = moving.copy(x = target.x, y = target.y)
    val swappedTarget = target.copy(x = moving.x, y = moving.y)
    val swapped = items.map {
        when (it.id) {
            moving.id -> swappedMoving
            target.id -> swappedTarget
            else -> it
        }
    }
    return swapped.takeIf {
        canPlaceWidgetItem(it, swappedMoving) && canPlaceWidgetItem(it, swappedTarget)
    }
}

internal fun resizeWidgetItem(
    items: List<WidgetPageItem>,
    itemId: String,
    newHeight: Int
): List<WidgetPageItem>? {
    val item = items.firstOrNull { it.id == itemId } ?: return null
    val candidate = item.copy(height = newHeight)
    if (!canPlaceWidgetItem(items, candidate)) return null
    return items.map { if (it.id == itemId) candidate else it }
}
