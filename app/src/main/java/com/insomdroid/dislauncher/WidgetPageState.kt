package com.insomdroid.dislauncher

import android.content.Context
import android.appwidget.AppWidgetProviderInfo
import kotlin.math.ceil
import org.json.JSONArray
import org.json.JSONObject

internal data class WidgetPageItem(
    val id: String,
    val kind: String,
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
    val appWidgetId: Int = -1,
    val providerComponent: String? = null,
    val minWidthCells: Int = 1,
    val minHeightCells: Int = 1,
    val resizeMode: Int = 3
)

private const val WIDGET_PAGE_STORE = "dislauncher_widget_page"
private const val WIDGET_PAGE_LAYOUT_KEY = "layout_json"
private const val WIDGET_PAGE_INITIALIZED_KEY = "layout_initialized"
internal const val WIDGET_PAGE_COLUMNS = 5
internal const val WIDGET_PAGE_ROWS = 7

internal fun loadWidgetPageItems(context: Context): List<WidgetPageItem> {
    val store = context.getSharedPreferences(WIDGET_PAGE_STORE, Context.MODE_PRIVATE)
    val raw = store.getString(WIDGET_PAGE_LAYOUT_KEY, "[]") ?: "[]"
    val parsedItems = runCatching {
        val array = JSONArray(raw)
        (0 until array.length()).mapNotNull { index ->
            val value = array.optJSONObject(index) ?: return@mapNotNull null
            val item = WidgetPageItem(
                id = value.optString("id"),
                kind = value.optString("kind"),
                x = value.optInt("x", 0),
                y = value.optInt("y", 0),
                width = value.optInt("width", WIDGET_PAGE_COLUMNS),
                height = value.optInt("height", 1),
                appWidgetId = value.optInt("appWidgetId", -1),
                providerComponent = value.optString("providerComponent").takeIf { it.isNotBlank() && it != "null" },
                minWidthCells = value.optInt("minWidthCells", 1).coerceIn(1, WIDGET_PAGE_COLUMNS),
                minHeightCells = value.optInt("minHeightCells", 1).coerceIn(1, WIDGET_PAGE_ROWS),
                resizeMode = value.optInt("resizeMode", 3)
            )
            item.takeIf {
                it.id.isNotBlank() &&
                    it.kind == "android_widget" &&
                    it.appWidgetId >= 0 &&
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

    // Discard unsupported legacy built-in entries while retaining Android widgets.
    if (parsedItems.size != runCatching { JSONArray(raw).length() }.getOrDefault(0) ||
        !store.getBoolean(WIDGET_PAGE_INITIALIZED_KEY, false)
    ) {
        saveWidgetPageItems(context, parsedItems)
    }
    return parsedItems
}
internal fun resetWidgetPageLayout(context: Context) {
    val host = android.appwidget.AppWidgetHost(context, DISLAUNCHER_APP_WIDGET_HOST_ID)
    loadWidgetPageItems(context)
        .filter { it.kind == "android_widget" && it.appWidgetId >= 0 }
        .forEach { item -> runCatching { host.deleteAppWidgetId(item.appWidgetId) } }
    saveWidgetPageItems(context, emptyList())
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
                .put("appWidgetId", item.appWidgetId)
                .put("providerComponent", item.providerComponent)
                .put("minWidthCells", item.minWidthCells)
                .put("minHeightCells", item.minHeightCells)
                .put("resizeMode", item.resizeMode)
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

    // Dropping onto another widget changes their order. Re-pack the page from the
    // top-left so different widget sizes can exchange positions without overlap.
    val ordered = items.sortedWith(compareBy<WidgetPageItem> { it.y }.thenBy { it.x })
        .toMutableList()
    val sourceIndex = ordered.indexOfFirst { it.id == itemId }
    val targetIndex = ordered.indexOfFirst { it.id == target.id }
    if (sourceIndex < 0 || targetIndex < 0) return null
    ordered.removeAt(sourceIndex)
    ordered.add(targetIndex.coerceAtMost(ordered.size), moving)

    val placed = mutableListOf<WidgetPageItem>()
    ordered.forEach { item ->
        val position = findWidgetPlacement(placed, item.width, item.height) ?: return null
        placed += item.copy(x = position.first, y = position.second)
    }
    return placed
}

internal fun findWidgetPlacement(
    items: List<WidgetPageItem>,
    width: Int,
    height: Int
): Pair<Int, Int>? {
    if (width !in 1..WIDGET_PAGE_COLUMNS || height !in 1..WIDGET_PAGE_ROWS) return null
    for (y in 0..(WIDGET_PAGE_ROWS - height)) {
        for (x in 0..(WIDGET_PAGE_COLUMNS - width)) {
            val candidate = WidgetPageItem("placement-check", "android_widget", x, y, width, height, appWidgetId = Int.MAX_VALUE)
            if (canPlaceWidgetItem(items, candidate)) return x to y
        }
    }
    return null
}

internal fun resizeWidgetBounds(
    items: List<WidgetPageItem>,
    itemId: String,
    newX: Int,
    newY: Int,
    newWidth: Int,
    newHeight: Int
): List<WidgetPageItem>? {
    val item = items.firstOrNull { it.id == itemId } ?: return null
    val minWidth = item.minWidthCells.coerceAtLeast(1)
    val minHeight = item.minHeightCells.coerceAtLeast(1)
    if (newWidth !in minWidth..WIDGET_PAGE_COLUMNS || newHeight !in minHeight..WIDGET_PAGE_ROWS) return null
    val candidate = item.copy(x = newX, y = newY, width = newWidth, height = newHeight)
    if (!canPlaceWidgetItem(items, candidate)) return null
    return items.map { if (it.id == itemId) candidate else it }
}


internal fun createPinnedWidgetPageItem(
    context: Context,
    appWidgetId: Int,
    providerInfo: AppWidgetProviderInfo
): WidgetPageItem? {
    val metrics = context.resources.displayMetrics
    val density = metrics.density.coerceAtLeast(1f)
    val screenWidthDp = metrics.widthPixels / density
    val screenHeightDp = metrics.heightPixels / density

    fun systemDimensionDp(name: String, fallbackDp: Float): Float {
        val resourceId = context.resources.getIdentifier(name, "dimen", "android")
        return if (resourceId != 0) {
            context.resources.getDimension(resourceId) / density
        } else {
            fallbackDp
        }
    }

    val statusBarDp = systemDimensionDp("status_bar_height", 24f)
    val navigationBarDp = systemDimensionDp("navigation_bar_height", 24f)
    val cellWidthDp = (screenWidthDp / WIDGET_PAGE_COLUMNS).coerceAtLeast(1f)
    val cellHeightDp = (
        (screenHeightDp - statusBarDp - navigationBarDp) / WIDGET_PAGE_ROWS
    ).coerceAtLeast(1f)

    val canResizeHorizontally =
        providerInfo.resizeMode and AppWidgetProviderInfo.RESIZE_HORIZONTAL != 0
    val canResizeVertically =
        providerInfo.resizeMode and AppWidgetProviderInfo.RESIZE_VERTICAL != 0
    val requestedMinWidth = if (canResizeHorizontally && providerInfo.minResizeWidth > 0) {
        providerInfo.minResizeWidth
    } else {
        providerInfo.minWidth
    }
    val requestedMinHeight = if (canResizeVertically && providerInfo.minResizeHeight > 0) {
        providerInfo.minResizeHeight
    } else {
        providerInfo.minHeight
    }

    // Match WidgetPage's provider-pixel to dp conversion and its preferred initial span.
    val minWidthCells = ceil((requestedMinWidth / density) / cellWidthDp)
        .toInt().coerceAtLeast(1)
    val minHeightCells = ceil((requestedMinHeight / density) / cellHeightDp)
        .toInt().coerceAtLeast(1)
    if (minWidthCells > WIDGET_PAGE_COLUMNS || minHeightCells > WIDGET_PAGE_ROWS) {
        return null
    }

    val width = ceil((providerInfo.minWidth / density) / cellWidthDp)
        .toInt().coerceAtLeast(minWidthCells).coerceAtMost(WIDGET_PAGE_COLUMNS)
    val height = ceil((providerInfo.minHeight / density) / cellHeightDp)
        .toInt().coerceAtLeast(minHeightCells).coerceAtMost(WIDGET_PAGE_ROWS)
    val existingItems = loadWidgetPageItems(context)
    val position = findWidgetPlacement(existingItems, width, height) ?: return null

    return WidgetPageItem(
        id = "android_widget_$appWidgetId",
        kind = "android_widget",
        x = position.first,
        y = position.second,
        width = width,
        height = height,
        appWidgetId = appWidgetId,
        providerComponent = providerInfo.provider.flattenToString(),
        minWidthCells = minWidthCells,
        minHeightCells = minHeightCells,
        resizeMode = providerInfo.resizeMode
    )
}

internal fun savePinnedWidgetPageItem(
    context: Context,
    item: WidgetPageItem
): Boolean {
    val existingItems = loadWidgetPageItems(context)
    if (!canPlaceWidgetItem(existingItems, item)) return false
    saveWidgetPageItems(context, existingItems + item)
    return true
}
