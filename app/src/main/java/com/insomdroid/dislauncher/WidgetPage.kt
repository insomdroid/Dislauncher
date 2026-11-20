package com.insomdroid.dislauncher

import android.Manifest
import android.app.Activity
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Intent
import android.content.Context
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items as lazyItems
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.ceil
import kotlin.math.roundToInt

private const val QUICK_NOTE_SAVE_DELAY_MS = 550L

@Composable
internal fun WidgetPage(
    isActive: Boolean,
    onOpenSettings: () -> Unit
) {
    val context = LocalContext.current
    val appWidgetManager = remember(context) { AppWidgetManager.getInstance(context) }
    val appWidgetHost = rememberDislauncherAppWidgetHost()
    var items by remember(context) {
        val loaded = loadWidgetPageItems(context)
        val oldQuickNotes = loaded.firstOrNull { it.id == QUICK_NOTES_WIDGET_ID }
        val oldCalendar = loaded.firstOrNull { it.id == MONTH_CALENDAR_WIDGET_ID }
        val isLegacyFullWidthLayout = loaded.size == 2 &&
            oldQuickNotes?.let { it.x == 0 && it.y == 0 && it.width == WIDGET_PAGE_COLUMNS && it.height == 2 } == true &&
            oldCalendar?.let { it.x == 0 && it.y == 2 && it.width == WIDGET_PAGE_COLUMNS && it.height == 5 } == true
        val initialItems = if (isLegacyFullWidthLayout) {
            loaded.map { item ->
                when (item.id) {
                    QUICK_NOTES_WIDGET_ID -> item.copy(x = 0, y = 0, width = 2, height = 2)
                    MONTH_CALENDAR_WIDGET_ID -> item.copy(x = 2, y = 0, width = 3, height = 4)
                    else -> item
                }
            }.also { saveWidgetPageItems(context, it) }
        } else loaded
        mutableStateOf(initialItems)
    }
    var pageMenuExpanded by remember { mutableStateOf(false) }
    var pageMenuOffset by remember { mutableStateOf(DpOffset.Zero) }
    var addWidgetDialog by remember { mutableStateOf(false) }
    var selectedWidgetId by remember { mutableStateOf<String?>(null) }
    var draggingWidgetId by remember { mutableStateOf<String?>(null) }
    var pendingAppWidgetId by remember { mutableStateOf(AppWidgetManager.INVALID_APPWIDGET_ID) }
    var gridCellWidthDp by remember { mutableStateOf(0f) }
    var gridCellHeightDp by remember { mutableStateOf(0f) }

    fun updateItems(next: List<WidgetPageItem>) {
        items = next
        saveWidgetPageItems(context, next)
    }

    fun addAndroidWidget(appWidgetId: Int) {
        val providerInfo = runCatching {
            appWidgetManager.getAppWidgetInfo(appWidgetId)
        }.getOrNull()
        if (providerInfo == null || gridCellWidthDp <= 0f || gridCellHeightDp <= 0f) {
            appWidgetHost.deleteAppWidgetId(appWidgetId)
            Toast.makeText(context, "The selected widget could not be loaded.", Toast.LENGTH_LONG).show()
            return
        }
        // Prefer the provider's resize minimum when it supports resizing. The declared
        // minWidth/minHeight describe its preferred initial size, not its smallest usable size.
        val canResizeHorizontally =
            providerInfo.resizeMode and AppWidgetProviderInfo.RESIZE_HORIZONTAL != 0
        val canResizeVertically =
            providerInfo.resizeMode and AppWidgetProviderInfo.RESIZE_VERTICAL != 0
        val requestedMinWidth = if (canResizeHorizontally && providerInfo.minResizeWidth > 0) {
            providerInfo.minResizeWidth
        } else providerInfo.minWidth
        val requestedMinHeight = if (canResizeVertically && providerInfo.minResizeHeight > 0) {
            providerInfo.minResizeHeight
        } else providerInfo.minHeight
        // AppWidgetProviderInfo dimensions are physical pixels; launcher grid dimensions are dp.
        // Convert before calculating cells, otherwise high-density devices overestimate widget size.
        val density = context.resources.displayMetrics.density.coerceAtLeast(1f)
        val minWidthDp = requestedMinWidth / density
        val minHeightDp = requestedMinHeight / density
        val preferredWidthDp = providerInfo.minWidth / density
        val preferredHeightDp = providerInfo.minHeight / density
        val minWidthCells = ceil(minWidthDp / gridCellWidthDp).toInt().coerceAtLeast(1)
        val minHeightCells = ceil(minHeightDp / gridCellHeightDp).toInt().coerceAtLeast(1)
        if (minWidthCells > WIDGET_PAGE_COLUMNS || minHeightCells > WIDGET_PAGE_ROWS) {
            appWidgetHost.deleteAppWidgetId(appWidgetId)
            Toast.makeText(
                context,
                "This widget's declared minimum size is larger than the widget page.",
                Toast.LENGTH_LONG
            ).show()
            return
        }
        val preferredWidth = ceil(preferredWidthDp / gridCellWidthDp).toInt()
            .coerceAtLeast(minWidthCells).coerceAtMost(WIDGET_PAGE_COLUMNS)
        val preferredHeight = ceil(preferredHeightDp / gridCellHeightDp).toInt()
            .coerceAtLeast(minHeightCells).coerceAtMost(WIDGET_PAGE_ROWS)
        val width = preferredWidth
        val height = preferredHeight
        val position = findWidgetPlacement(items, width, height)
        if (position == null) {
            appWidgetHost.deleteAppWidgetId(appWidgetId)
            Toast.makeText(context, "There is not enough free space for this widget.", Toast.LENGTH_LONG).show()
            return
        }
        val item = WidgetPageItem(
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
        updateItems(items + item)
    }

    val configureWidgetLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val id = pendingAppWidgetId
        if (result.resultCode == Activity.RESULT_OK && id != AppWidgetManager.INVALID_APPWIDGET_ID) {
            addAndroidWidget(id)
        } else if (id != AppWidgetManager.INVALID_APPWIDGET_ID) {
            appWidgetHost.deleteAppWidgetId(id)
        }
        pendingAppWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    }

    val widgetPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val id = result.data?.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            pendingAppWidgetId
        ) ?: pendingAppWidgetId
        if (result.resultCode == Activity.RESULT_OK && id != AppWidgetManager.INVALID_APPWIDGET_ID) {
            pendingAppWidgetId = id
            val providerInfo = runCatching { appWidgetManager.getAppWidgetInfo(id) }.getOrNull()
            if (providerInfo?.configure != null) {
                val configureIntent = Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE)
                    .setComponent(providerInfo.configure)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                configureWidgetLauncher.launch(configureIntent)
            } else {
                addAndroidWidget(id)
                pendingAppWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
            }
        } else {
            if (id != AppWidgetManager.INVALID_APPWIDGET_ID) appWidgetHost.deleteAppWidgetId(id)
            pendingAppWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
        }
    }

    fun launchAndroidWidgetPicker() {
        val id = appWidgetHost.allocateAppWidgetId()
        pendingAppWidgetId = id
        val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_PICK)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
        if (intent.resolveActivity(context.packageManager) != null) {
            widgetPickerLauncher.launch(intent)
        } else {
            appWidgetHost.deleteAppWidgetId(id)
            pendingAppWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
            Toast.makeText(context, "No Android widget picker is available on this device.", Toast.LENGTH_LONG).show()
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .padding(WindowInsets.safeDrawing.asPaddingValues())
            .clip(RoundedCornerShape(0.dp))
            .pointerInput(items) {
                detectTapGestures(onLongPress = { position ->
                    val cellWidthPx = size.width.toFloat() / WIDGET_PAGE_COLUMNS
                    val cellHeightPx = size.height.toFloat() / WIDGET_PAGE_ROWS
                    val pressedWidget = items.any { item ->
                        position.x >= item.x * cellWidthPx &&
                            position.x < (item.x + item.width) * cellWidthPx &&
                            position.y >= item.y * cellHeightPx &&
                            position.y < (item.y + item.height) * cellHeightPx
                    }
                    if (!pressedWidget) {
                        val density = context.resources.displayMetrics.density.coerceAtLeast(1f)
                        pageMenuOffset = DpOffset((position.x / density).dp, (position.y / density).dp)
                        pageMenuExpanded = true
                    }
                })
            }
    ) {
        val cellWidth = maxWidth / WIDGET_PAGE_COLUMNS
        val cellHeight = maxHeight / WIDGET_PAGE_ROWS
        val cellWidthPx = with(LocalDensity.current) { cellWidth.toPx() }
        val cellHeightPx = with(LocalDensity.current) { cellHeight.toPx() }
        SideEffect {
            gridCellWidthDp = cellWidth.value
            gridCellHeightDp = cellHeight.value
        }

        items.forEach { item ->
            key(item.id) {
                var dragDelta by remember(item.id) { mutableStateOf(Offset.Zero) }
                val itemModifier = Modifier
                    .offset(x = cellWidth * item.x, y = cellHeight * item.y)
                    .width(cellWidth * item.width)
                    .height(cellHeight * item.height)
                    .graphicsLayer {
                        translationX = dragDelta.x
                        translationY = dragDelta.y
                    }
                    .pointerInput(item.id) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val longPress = awaitLongPressOrCancellation(down.id)
                            if (longPress != null) {
                                selectedWidgetId = item.id
                            }
                        }
                    }
                    .pointerInput(item.id, items, cellWidthPx, cellHeightPx) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = {
                                dragDelta = Offset.Zero
                                selectedWidgetId = item.id
                                draggingWidgetId = item.id
                            },
                            onDragEnd = {
                                val draggedTopPx = item.y * cellHeightPx + dragDelta.y
                                val droppedInDeleteTarget =
                                    dragDelta.y < -cellHeightPx * 0.45f &&
                                        draggedTopPx < cellHeightPx * 0.75f
                                if (droppedInDeleteTarget) {
                                    if (item.kind == "android_widget" && item.appWidgetId >= 0) {
                                        appWidgetHost.deleteAppWidgetId(item.appWidgetId)
                                    }
                                    updateItems(items.filterNot { it.id == item.id })
                                    selectedWidgetId = null
                                } else {
                                    val targetX = (item.x + dragDelta.x / cellWidthPx).roundToInt()
                                    val targetY = (item.y + dragDelta.y / cellHeightPx).roundToInt()
                                    val moved = moveWidgetItem(items, item.id, targetX, targetY)
                                    if (moved != null) {
                                        updateItems(moved)
                                        selectedWidgetId = item.id
                                    } else if (targetX != item.x || targetY != item.y) {
                                        Toast.makeText(
                                            context,
                                            "That position is outside the available widget space.",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                }
                                draggingWidgetId = null
                                dragDelta = Offset.Zero
                            },
                            onDragCancel = {
                                draggingWidgetId = null
                                dragDelta = Offset.Zero
                            },
                            onDrag = { change, amount ->
                                change.consume()
                                dragDelta += amount
                            }
                        )
                    }

                when (item.kind) {
                    "quick_notes" -> QuickNotesWidget(
                        modifier = itemModifier,
                        isActive = isActive,
                        selected = selectedWidgetId == item.id,
                        itemX = item.x,
                        itemY = item.y,
                        currentWidth = item.width,
                        currentHeight = item.height,
                        cellWidthDp = gridCellWidthDp,
                        cellHeightDp = gridCellHeightDp,
                        onRemove = { updateItems(items.filterNot { it.id == item.id }) },
                        onResize = { x, y, width, height ->
                            resizeWidgetBounds(items, item.id, x, y, width, height)?.let(::updateItems)
                                ?: Toast.makeText(context, "Not enough space to resize this widget.", Toast.LENGTH_SHORT).show()
                        }
                    )
                    "month_calendar" -> MonthCalendarWidget(
                        modifier = itemModifier,
                        context = context,
                        isActive = isActive,
                        selected = selectedWidgetId == item.id,
                        itemX = item.x,
                        itemY = item.y,
                        currentWidth = item.width,
                        currentHeight = item.height,
                        cellWidthDp = gridCellWidthDp,
                        cellHeightDp = gridCellHeightDp,
                        onRemove = { updateItems(items.filterNot { it.id == item.id }) },
                        onResize = { x, y, width, height ->
                            resizeWidgetBounds(items, item.id, x, y, width, height)?.let(::updateItems)
                                ?: Toast.makeText(context, "Not enough space to resize this widget.", Toast.LENGTH_SHORT).show()
                        }
                    )
                    "android_widget" -> AndroidWidgetCard(
                        modifier = itemModifier,
                        context = context,
                        host = appWidgetHost,
                        manager = appWidgetManager,
                        item = item,
                        cellWidthDp = gridCellWidthDp,
                        cellHeightDp = gridCellHeightDp,
                        selected = selectedWidgetId == item.id,
                        onResize = { x, y, width, height ->
                            resizeWidgetBounds(items, item.id, x, y, width, height)?.let(::updateItems)
                                ?: Toast.makeText(context, "The widget cannot fit at that size.", Toast.LENGTH_SHORT).show()
                        },
                        onRemove = {
                            appWidgetHost.deleteAppWidgetId(item.appWidgetId)
                            updateItems(items.filterNot { it.id == item.id })
                            selectedWidgetId = null
                        }
                    )
                }
            }
        }

        if (draggingWidgetId != null) {
            Surface(
                modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth().height(58.dp),
                shape = RoundedCornerShape(bottomStart = 18.dp, bottomEnd = 18.dp),
                color = MaterialTheme.colorScheme.errorContainer
            ) {
                Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                    Text("⌫", color = MaterialTheme.colorScheme.onErrorContainer, fontSize = 20.sp)
                    Text("Drop here to remove", modifier = Modifier.padding(start = 10.dp), color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.titleSmall)
                }
            }
        } else if (selectedWidgetId != null) {
            val selectedItem = items.firstOrNull { it.id == selectedWidgetId }
            if (selectedItem != null) {
                Surface(
                    modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth().height(54.dp),
                    shape = RoundedCornerShape(bottomStart = 18.dp, bottomEnd = 18.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh
                ) {
                    Row(Modifier.fillMaxSize().padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Widget selected", modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
                        TextButton(onClick = {
                            if (selectedItem.kind == "android_widget" && selectedItem.appWidgetId >= 0) appWidgetHost.deleteAppWidgetId(selectedItem.appWidgetId)
                            updateItems(items.filterNot { it.id == selectedItem.id })
                            selectedWidgetId = null
                        }) { Text("Remove", color = MaterialTheme.colorScheme.error) }
                        TextButton(onClick = { selectedWidgetId = null }) { Text("Done") }
                    }
                }
            }
        }

        DropdownMenu(
            expanded = pageMenuExpanded,
            onDismissRequest = { pageMenuExpanded = false },
            offset = pageMenuOffset
        ) {
            DropdownMenuItem(
                text = { Text("Add widget") },
                onClick = {
                    pageMenuExpanded = false
                    addWidgetDialog = true
                }
            )
            DropdownMenuItem(
                text = { Text("Dislauncher Settings") },
                onClick = {
                    pageMenuExpanded = false
                    onOpenSettings()
                }
            )
        }
    }

    if (addWidgetDialog) {
        val hasQuickNotes = items.any { it.id == QUICK_NOTES_WIDGET_ID }
        val hasCalendar = items.any { it.id == MONTH_CALENDAR_WIDGET_ID }
        AlertDialog(
            onDismissRequest = { addWidgetDialog = false },
            title = { Text("Add widget") },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        "Choose a widget to place in an available grid area.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (!hasQuickNotes) {
                        Card(
                            modifier = Modifier.fillMaxWidth().clickable {
                                val position = findWidgetPlacement(items, 2, 2)
                                if (position != null) {
                                    updateItems(items + WidgetPageItem(
                                        QUICK_NOTES_WIDGET_ID, "quick_notes",
                                        position.first, position.second, 2, 2
                                    ))
                                    addWidgetDialog = false
                                } else {
                                    Toast.makeText(context, "There is not enough free space for Quick Notes.", Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Quick Notes", style = MaterialTheme.typography.titleSmall)
                                    Text("Compact note input · 2 × 2", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Text("Add", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                    if (!hasCalendar) {
                        Card(
                            modifier = Modifier.fillMaxWidth().clickable {
                                val position = findWidgetPlacement(items, 3, 4)
                                if (position != null) {
                                    updateItems(items + WidgetPageItem(
                                        MONTH_CALENDAR_WIDGET_ID, "month_calendar",
                                        position.first, position.second, 3, 4
                                    ))
                                    addWidgetDialog = false
                                } else {
                                    Toast.makeText(context, "There is not enough free space for Calendar.", Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Calendar", style = MaterialTheme.typography.titleSmall)
                                    Text("Monthly view · 3 × 4", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Text("Add", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                    Text(
                        "Installed app widgets",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    val installedProviders = remember(context) {
                        appWidgetManager.installedProviders.sortedBy { it.provider.packageName.lowercase(Locale.ROOT) }
                    }
                    val providerGroups = remember(installedProviders) { installedProviders.groupBy { it.provider.packageName }.toSortedMap() }
                    var expandedPackages by remember { mutableStateOf(emptySet<String>()) }
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        providerGroups.forEach { (packageName, providers) ->
                            val appLabel = remember(packageName) {
                                runCatching { context.packageManager.getApplicationLabel(context.packageManager.getApplicationInfo(packageName, 0)).toString() }.getOrDefault(packageName)
                            }
                            item(key = "app_group_$packageName") {
                                Card(
                                    modifier = Modifier.fillMaxWidth().clickable {
                                        expandedPackages = if (packageName in expandedPackages) expandedPackages - packageName else expandedPackages + packageName
                                    },
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                                    shape = RoundedCornerShape(16.dp)
                                ) {
                                    Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                        val appIcon = remember(packageName) {
                                            runCatching { context.packageManager.getApplicationIcon(packageName).toBitmap(96, 96) }.getOrNull()
                                        }
                                        if (appIcon != null) Image(bitmap = appIcon.asImageBitmap(), contentDescription = null, modifier = Modifier.size(38.dp).clip(RoundedCornerShape(10.dp)))
                                        Column(Modifier.weight(1f).padding(start = 12.dp)) {
                                            Text(appLabel, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                            Text("${providers.size} widget${if (providers.size == 1) "" else "s"}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        Text(if (packageName in expandedPackages) "⌃" else "⌄", style = MaterialTheme.typography.titleMedium)
                                    }
                                }
                            }
                            if (packageName in expandedPackages) lazyItems(providers, key = { it.provider.flattenToString() }) { provider ->
                            val preview = remember(provider) {
                                runCatching {
                                    (provider.loadPreviewImage(context, context.resources.displayMetrics.densityDpi)
                                        ?: provider.loadIcon(context, context.resources.displayMetrics.densityDpi))
                                        ?.toBitmap(width = 420, height = 260)
                                }.getOrNull()
                            }
                            Card(
                                modifier = Modifier.fillMaxWidth().clickable {
                                    val id = appWidgetHost.allocateAppWidgetId()
                                    pendingAppWidgetId = id
                                    val bound = runCatching {
                                        appWidgetManager.bindAppWidgetIdIfAllowed(id, provider.provider)
                                    }.getOrDefault(false)
                                    if (bound) {
                                        val configure = provider.configure
                                        if (configure != null) {
                                            val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE)
                                                .setComponent(configure)
                                                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                                            configureWidgetLauncher.launch(intent)
                                        } else {
                                            addAndroidWidget(id)
                                            pendingAppWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
                                        }
                                    } else {
                                        val bindIntent = Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
                                            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                                            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, provider.provider)
                                        widgetPickerLauncher.launch(bindIntent)
                                    }
                                    addWidgetDialog = false
                                },
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                                )
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                provider.loadLabel(context.packageManager).toString(),
                                                style = MaterialTheme.typography.titleSmall
                                            )
                                            Text(
                                                provider.provider.packageName,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        Text(
                                            "${ceil(provider.minWidth / gridCellWidthDp.coerceAtLeast(1f)).toInt()} × ${ceil(provider.minHeight / gridCellHeightDp.coerceAtLeast(1f)).toInt()}",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    if (preview != null) {
                                        Image(
                                            bitmap = preview.asImageBitmap(),
                                            contentDescription = "Preview of ${provider.loadLabel(context.packageManager)} widget",
                                            modifier = Modifier.fillMaxWidth().height(116.dp),
                                            contentScale = ContentScale.Fit
                                        )
                                    } else {
                                        Surface(
                                            modifier = Modifier.fillMaxWidth().height(76.dp),
                                            color = MaterialTheme.colorScheme.surfaceVariant,
                                            shape = RoundedCornerShape(12.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Text("Widget preview unavailable", style = MaterialTheme.typography.bodySmall)
                                            }
                                        }
                                    }
                                }
                            }
                            }
                        }
                    }
                    if (hasQuickNotes && hasCalendar) {
                        Text(
                            "Both built-in widgets are already on this page.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { addWidgetDialog = false }) { Text("Close") }
            }
        )
    }
}

@Composable
private fun QuickNotesWidget(
    modifier: Modifier,
    isActive: Boolean,
    selected: Boolean,
    itemX: Int,
    itemY: Int,
    currentWidth: Int,
    currentHeight: Int,
    cellWidthDp: Float,
    cellHeightDp: Float,
    onRemove: () -> Unit,
    onResize: (Int, Int, Int, Int) -> Unit
) {
    val context = LocalContext.current
    var noteText by remember(context) { mutableStateOf("") }
    var hasLoaded by remember(context) { mutableStateOf(false) }

    LaunchedEffect(isActive) {
        if (isActive && !hasLoaded) {
            noteText = withContext(Dispatchers.IO) { loadQuickWidgetNote(context) }
            hasLoaded = true
        } else if (isActive && hasLoaded) {
            noteText = withContext(Dispatchers.IO) { loadQuickWidgetNote(context) }
        }
    }

    LaunchedEffect(noteText, hasLoaded) {
        if (!hasLoaded) return@LaunchedEffect
        delay(QUICK_NOTE_SAVE_DELAY_MS)
        withContext(Dispatchers.IO) { saveQuickWidgetNote(context, noteText) }
    }

    Box(modifier = modifier) {
        WidgetCard(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Quick Notes",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f).clickable {
                    launchBuiltInTool(context, BuiltInTool.NOTES)
                }
            )
            
        }
        OutlinedTextField(
            value = noteText,
            onValueChange = { noteText = it },
            modifier = Modifier.fillMaxSize(),
            placeholder = { Text("Write something…", fontSize = 13.sp) },
            textStyle = MaterialTheme.typography.bodyMedium,
            minLines = 2,
            maxLines = 4,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            shape = RoundedCornerShape(12.dp)
        )
        }
        if (selected) {
            WidgetResizeHandles(
                itemX = itemX, itemY = itemY, itemWidth = currentWidth, itemHeight = currentHeight,
                minimumWidth = 2, minimumHeight = 2,
                maximumWidth = WIDGET_PAGE_COLUMNS, maximumHeight = WIDGET_PAGE_ROWS,
                cellWidthDp = cellWidthDp, cellHeightDp = cellHeightDp,
                resizeMode = AppWidgetProviderInfo.RESIZE_HORIZONTAL or AppWidgetProviderInfo.RESIZE_VERTICAL,
                onResize = onResize
            )
        }
    }
}

private fun loadQuickWidgetNote(context: Context): String =
    loadNotes(context).firstOrNull { it.id == QUICK_NOTES_WIDGET_ID }
        ?.let { it.markdown.ifBlank { it.content } }
        .orEmpty()

private val quickWidgetNoteLock = Any()

private fun saveQuickWidgetNote(context: Context, text: String) {
    synchronized(quickWidgetNoteLock) {
        val notes = loadNotes(context)
        val existing = notes.firstOrNull { it.id == QUICK_NOTES_WIDGET_ID }
        if (text.isBlank()) {
            if (existing != null) {
                saveNotes(context, notes.filterNot { it.id == QUICK_NOTES_WIDGET_ID })
            }
            return
        }
        if (existing?.markdown == text) return
        val updated = buildLocalNoteSnapshot(
            id = QUICK_NOTES_WIDGET_ID,
            title = "Quick Note",
            markdown = text,
            existing = existing,
            labels = existing?.labels.orEmpty(),
            color = existing?.color ?: "default",
            updatedAt = System.currentTimeMillis()
        )
        val next = (notes.filterNot { it.id == QUICK_NOTES_WIDGET_ID } + updated)
            .sortedWith(compareByDescending<LocalNote> { it.pinned }.thenByDescending { it.updatedAt })
        saveNotes(context, next)
    }
}

@Composable
private fun MonthCalendarWidget(
    modifier: Modifier,
    context: Context,
    isActive: Boolean,
    selected: Boolean,
    itemX: Int,
    itemY: Int,
    currentWidth: Int,
    currentHeight: Int,
    cellWidthDp: Float,
    cellHeightDp: Float,
    onRemove: () -> Unit,
    onResize: (Int, Int, Int, Int) -> Unit
) {
    val locale = remember { Locale.getDefault() }
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    var month by remember { mutableStateOf(YearMonth.now()) }
    var events by remember { mutableStateOf<List<DeviceCalendarEvent>>(emptyList()) }
    var tasks by remember { mutableStateOf(loadTasks(context)) }
    var refreshKey by remember { mutableStateOf(0) }
    var hasCalendarPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCalendarPermission = granted
        refreshKey++
    }

    androidx.compose.runtime.DisposableEffect(lifecycleOwner, isActive) {
        if (!isActive) return@DisposableEffect onDispose { }
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refreshKey++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(month, hasCalendarPermission, refreshKey, isActive) {
        if (!isActive) return@LaunchedEffect
        events = if (hasCalendarPermission) {
            withContext(Dispatchers.IO) {
                readDeviceCalendarEvents(context, month.atDay(1), month.plusMonths(1).atDay(1))
            }
        } else emptyList()
        tasks = withContext(Dispatchers.IO) { loadTasks(context) }
    }

    val startOffset = (month.atDay(1).dayOfWeek.value - DayOfWeek.MONDAY.value + 7) % 7
    val firstVisibleDate = month.atDay(1).minusDays(startOffset.toLong())
    val dates = (0 until 42).map { firstVisibleDate.plusDays(it.toLong()) }
    val eventDates = events.flatMap { event ->
        val start = java.time.Instant.ofEpochMilli(event.begin).atZone(ZoneId.systemDefault()).toLocalDate()
        val end = java.time.Instant.ofEpochMilli((event.end - 1).coerceAtLeast(event.begin))
            .atZone(ZoneId.systemDefault()).toLocalDate()
        generateSequence(start) { previous -> if (previous.isBefore(end)) previous.plusDays(1) else null }
            .toList()
    }.toSet()
    val taskDates = tasks.mapNotNull { runCatching { LocalDate.parse(it.date) }.getOrNull() }.toSet()
    val markedDates = eventDates + taskDates

    Box(modifier = modifier) {
        WidgetCard(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                month.format(DateTimeFormatter.ofPattern("MMMM yyyy", locale)),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).clickable {
                    launchBuiltInTool(context, BuiltInTool.CALENDAR)
                }
            )
            TextButton(onClick = { month = month.minusMonths(1) }, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 3.dp)) {
                Text("‹")
            }
            TextButton(onClick = { month = month.plusMonths(1) }, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 3.dp)) {
                Text("›")
            }
            
        }

        Row(modifier = Modifier.fillMaxWidth()) {
            (0..6).forEach { index ->
                val day = DayOfWeek.of(index + 1)
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        day.getDisplayName(TextStyle.SHORT, locale).take(2),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 10.sp
                    )
                }
            }
        }

        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceEvenly
        ) {
            dates.chunked(7).forEach { week ->
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    week.forEach { date ->
                        val isCurrentMonth = YearMonth.from(date) == month
                        val isToday = date == LocalDate.now()
                        val hasMarker = date in markedDates
                        Box(
                            modifier = Modifier.weight(1f).height(26.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Surface(
                                modifier = Modifier
                                    .size(25.dp)
                                    .clickable { launchBuiltInTool(context, BuiltInTool.CALENDAR) },
                                shape = CircleShape,
                                color = if (isToday) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.surface
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        date.dayOfMonth.toString(),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = when {
                                            isToday -> MaterialTheme.colorScheme.onPrimary
                                            isCurrentMonth -> MaterialTheme.colorScheme.onSurface
                                            else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.42f)
                                        }
                                    )
                                }
                            }
                            if (hasMarker && !isToday) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomCenter)
                                        .padding(bottom = 0.dp)
                                        .size(3.dp)
                                        .background(MaterialTheme.colorScheme.primary, CircleShape)
                                )
                            }
                        }
                    }
                }
            }
        }
        if (!hasCalendarPermission) {
            TextButton(
                onClick = { permissionLauncher.launch(Manifest.permission.READ_CALENDAR) },
                contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
            ) {
                Text("Allow calendar access", fontSize = 11.sp)
            }
        }
        }
        if (selected) {
            WidgetResizeHandles(
                itemX = itemX, itemY = itemY, itemWidth = currentWidth, itemHeight = currentHeight,
                minimumWidth = 3, minimumHeight = 3,
                maximumWidth = WIDGET_PAGE_COLUMNS, maximumHeight = WIDGET_PAGE_ROWS,
                cellWidthDp = cellWidthDp, cellHeightDp = cellHeightDp,
                resizeMode = AppWidgetProviderInfo.RESIZE_HORIZONTAL or AppWidgetProviderInfo.RESIZE_VERTICAL,
                onResize = onResize
            )
        }
    }
}

@Composable
private fun BoxScope.VerticalWidgetResizeHandle(
    currentHeight: Int,
    cellHeightDp: Float,
    minimum: Int,
    maximum: Int,
    onResize: (Int) -> Unit
) {
    val density = LocalDensity.current
    Surface(
        modifier = Modifier
            .align(Alignment.BottomEnd)
            .padding(8.dp)
            .size(30.dp)
            .pointerInput(currentHeight, cellHeightDp, minimum, maximum) {
                var accumulated = 0f
                detectDragGestures(
                    onDragEnd = {
                        val cellPx = with(density) { cellHeightDp.dp.toPx() }.coerceAtLeast(1f)
                        val delta = (accumulated / cellPx).roundToInt()
                        if (delta != 0) onResize((currentHeight + delta).coerceIn(minimum, maximum))
                    },
                    onDrag = { change, amount ->
                        accumulated += amount.y
                        change.consume()
                    }
                )
            },
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        shadowElevation = 3.dp
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text("↕", color = MaterialTheme.colorScheme.onPrimaryContainer, fontSize = 18.sp)
        }
    }
}

@Composable
private fun BoxScope.WidgetResizeHandle(
    currentWidth: Int,
    currentHeight: Int,
    minimumWidth: Int,
    minimumHeight: Int,
    maximumWidth: Int,
    maximumHeight: Int,
    cellWidthDp: Float,
    cellHeightDp: Float,
    resizeMode: Int,
    onResize: (Int, Int) -> Unit
) {
    val density = LocalDensity.current
    Surface(
        modifier = Modifier
            .align(Alignment.BottomEnd)
            .padding(8.dp)
            .size(32.dp)
            .pointerInput(
                currentWidth, currentHeight, minimumWidth, minimumHeight,
                cellWidthDp, cellHeightDp, resizeMode
            ) {
                var accumulated = Offset.Zero
                detectDragGestures(
                    onDragEnd = {
                        val widthPx = with(density) { cellWidthDp.dp.toPx() }.coerceAtLeast(1f)
                        val heightPx = with(density) { cellHeightDp.dp.toPx() }.coerceAtLeast(1f)
                        val horizontal = resizeMode and AppWidgetProviderInfo.RESIZE_HORIZONTAL != 0
                        val vertical = resizeMode and AppWidgetProviderInfo.RESIZE_VERTICAL != 0
                        val widthDelta = if (horizontal) (accumulated.x / widthPx).roundToInt() else 0
                        val heightDelta = if (vertical) (accumulated.y / heightPx).roundToInt() else 0
                        if (widthDelta != 0 || heightDelta != 0) {
                            onResize(
                                (currentWidth + widthDelta).coerceIn(minimumWidth, maximumWidth),
                                (currentHeight + heightDelta).coerceIn(minimumHeight, maximumHeight)
                            )
                        }
                    },
                    onDrag = { change, amount ->
                        accumulated += amount
                        change.consume()
                    }
                )
            },
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        shadowElevation = 3.dp
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text("↘", color = MaterialTheme.colorScheme.onPrimaryContainer, fontSize = 18.sp)
        }
    }
}

@Composable
private fun BoxScope.WidgetResizeHandles(
    itemX: Int, itemY: Int, itemWidth: Int, itemHeight: Int, minimumWidth: Int, minimumHeight: Int,
    maximumWidth: Int, maximumHeight: Int, cellWidthDp: Float, cellHeightDp: Float,
    resizeMode: Int, onResize: (Int, Int, Int, Int) -> Unit
) {
    val density = LocalDensity.current
    val hEnabled = resizeMode and AppWidgetProviderInfo.RESIZE_HORIZONTAL != 0
    val vEnabled = resizeMode and AppWidgetProviderInfo.RESIZE_VERTICAL != 0
    @Composable
    fun grip(edgeX: Int, edgeY: Int, alignment: Alignment, width: androidx.compose.ui.unit.Dp, height: androidx.compose.ui.unit.Dp) {
        if ((edgeX != 0 && !hEnabled) || (edgeY != 0 && !vEnabled)) return
        Surface(
            modifier = Modifier.align(alignment).size(width, height).pointerInput(
                itemX, itemY, itemWidth, itemHeight, minimumWidth, minimumHeight, cellWidthDp, cellHeightDp, edgeX, edgeY
            ) {
                var delta = Offset.Zero
                detectDragGestures(
                    onDrag = { change, amount -> delta += amount; change.consume() },
                    onDragEnd = {
                        val cw = with(density) { cellWidthDp.dp.toPx() }.coerceAtLeast(1f)
                        val ch = with(density) { cellHeightDp.dp.toPx() }.coerceAtLeast(1f)
                        val dx = (delta.x / cw).roundToInt()
                        val dy = (delta.y / ch).roundToInt()
                        val nx = itemX + if (edgeX < 0) dx else 0
                        val ny = itemY + if (edgeY < 0) dy else 0
                        val nw = (itemWidth + if (edgeX < 0) -dx else if (edgeX > 0) dx else 0).coerceIn(minimumWidth, maximumWidth)
                        val nh = (itemHeight + if (edgeY < 0) -dy else if (edgeY > 0) dy else 0).coerceIn(minimumHeight, maximumHeight)
                        if (nw != itemWidth || nh != itemHeight) onResize(nx, ny, nw, nh)
                    }
                )
            },
            shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.primaryContainer, shadowElevation = 1.dp
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(when {
                    edgeX < 0 && edgeY < 0 -> "↖"
                    edgeX > 0 && edgeY < 0 -> "↗"
                    edgeX < 0 && edgeY > 0 -> "↙"
                    edgeX > 0 && edgeY > 0 -> "↘"
                    edgeX != 0 -> "↔"
                    else -> "↕"
                }, color = MaterialTheme.colorScheme.onPrimaryContainer, fontSize = 13.sp)
            }
        }
    }
    if (hEnabled) { grip(-1,0,Alignment.CenterStart,28.dp,42.dp); grip(1,0,Alignment.CenterEnd,28.dp,42.dp) }
    if (vEnabled) { grip(0,-1,Alignment.TopCenter,42.dp,28.dp); grip(0,1,Alignment.BottomCenter,42.dp,28.dp) }
    if (hEnabled && vEnabled) {
        grip(-1,-1,Alignment.TopStart,28.dp,28.dp); grip(1,-1,Alignment.TopEnd,28.dp,28.dp)
        grip(-1,1,Alignment.BottomStart,28.dp,28.dp); grip(1,1,Alignment.BottomEnd,28.dp,28.dp)
    }
}

@Composable
private fun AndroidWidgetCard(
    modifier: Modifier,
    context: Context,
    host: android.appwidget.AppWidgetHost,
    manager: AppWidgetManager,
    item: WidgetPageItem,
    cellWidthDp: Float,
    cellHeightDp: Float,
    selected: Boolean,
    onResize: (Int, Int, Int, Int) -> Unit,
    onRemove: () -> Unit
) {
    Box(
        modifier = modifier
            .padding(4.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
    ) {
        AndroidAppWidgetContent(
            context = context,
            host = host,
            manager = manager,
            item = item,
            cellWidthDp = cellWidthDp,
            cellHeightDp = cellHeightDp,
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    top = 4.dp,
                    start = 4.dp,
                    end = 4.dp,
                    bottom = 4.dp
                )
        )

        if (selected && item.resizeMode != AppWidgetProviderInfo.RESIZE_NONE) {
            WidgetResizeHandles(
                itemX = item.x, itemY = item.y, itemWidth = item.width, itemHeight = item.height,
                minimumWidth = item.minWidthCells, minimumHeight = item.minHeightCells,
                maximumWidth = WIDGET_PAGE_COLUMNS, maximumHeight = WIDGET_PAGE_ROWS,
                cellWidthDp = cellWidthDp, cellHeightDp = cellHeightDp,
                resizeMode = item.resizeMode, onResize = onResize
            )
            
        }
    }
}

@Composable
private fun WidgetCard(
    modifier: Modifier,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier.padding(4.dp),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            content = content
        )
    }
}
