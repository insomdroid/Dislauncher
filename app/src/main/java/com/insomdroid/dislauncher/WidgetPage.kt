package com.insomdroid.dislauncher

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Intent
import android.content.Context
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.roundToInt
import kotlinx.coroutines.withTimeoutOrNull

@Composable
internal fun WidgetPage(
    onOpenSettings: () -> Unit
) {
    val context = LocalContext.current
    val appWidgetManager = remember(context) { AppWidgetManager.getInstance(context) }
    val appWidgetHost = rememberDislauncherAppWidgetHost()
    var items by remember(context) {
        mutableStateOf(loadWidgetPageItems(context))
    }
    var pageMenuExpanded by remember { mutableStateOf(false) }
    var pageMenuOffset by remember { mutableStateOf(DpOffset.Zero) }
    var addWidgetDialog by remember { mutableStateOf(false) }
    var editingWidgetId by remember { mutableStateOf<String?>(null) }
    var draggingWidgetId by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
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
        val removeZoneHeightPx = with(LocalDensity.current) { 76.dp.toPx() }

        fun finishWidgetDrag(item: WidgetPageItem) {
            val finalOffset = dragOffset
            val draggedTopPx = item.y * cellHeightPx + finalOffset.y
            val dropOnRemove = draggedTopPx <= removeZoneHeightPx
            if (dropOnRemove) {
                if (item.appWidgetId >= 0) appWidgetHost.deleteAppWidgetId(item.appWidgetId)
                updateItems(items.filterNot { it.id == item.id })
                editingWidgetId = null
            } else {
                val targetX = (item.x + finalOffset.x / cellWidthPx).roundToInt()
                val targetY = (item.y + finalOffset.y / cellHeightPx).roundToInt()
                val moved = moveWidgetItem(items, item.id, targetX, targetY)
                if (moved != null) {
                    updateItems(moved)
                    editingWidgetId = item.id
                } else if (targetX != item.x || targetY != item.y) {
                    Toast.makeText(
                        context,
                        "That position is outside the available widget space.",
                        Toast.LENGTH_SHORT
                    ).show()
                    editingWidgetId = item.id
                }
            }
            draggingWidgetId = null
            dragOffset = Offset.Zero
        }
        SideEffect {
            gridCellWidthDp = cellWidth.value
            gridCellHeightDp = cellHeight.value
        }

        items.forEach { item ->
            key(item.id) {
                val itemModifier = Modifier
                    .offset(x = cellWidth * item.x, y = cellHeight * item.y)
                    .width(cellWidth * item.width)
                    .height(cellHeight * item.height)
                    .graphicsLayer {
                        if (draggingWidgetId == item.id) {
                            translationX = dragOffset.x
                            translationY = dragOffset.y
                            alpha = 0.90f
                            scaleX = 1.02f
                            scaleY = 1.02f
                        } else {
                            translationX = 0f
                            translationY = 0f
                            alpha = 1f
                            scaleX = 1f
                            scaleY = 1f
                        }
                    }
                    .pointerInput(item.id, items, cellWidthPx, cellHeightPx) {
                        awaitEachGesture {
                            val down = awaitFirstDown(
                                requireUnconsumed = false,
                                pass = PointerEventPass.Initial
                            )
                            val releasedOrMoved = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                                while (true) {
                                    val event = awaitPointerEvent(PointerEventPass.Initial)
                                    val change = event.changes.firstOrNull { it.id == down.id }
                                        ?: return@withTimeoutOrNull true
                                    if (!change.pressed || change.isConsumed ||
                                        (change.position - down.position).getDistance() > viewConfiguration.touchSlop
                                    ) {
                                        return@withTimeoutOrNull true
                                    }
                                }
                            }
                            if (releasedOrMoved != null) return@awaitEachGesture

                            var moving = false
                            var lastPosition = down.position
                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                val change = event.changes.firstOrNull { it.id == down.id }
                                    ?: break
                                if (!change.pressed && change.isConsumed) break

                                val delta = change.position - lastPosition
                                lastPosition = change.position
                                if (!moving &&
                                    (change.position - down.position).getDistance() > viewConfiguration.touchSlop
                                ) {
                                    moving = true
                                    editingWidgetId = null
                                    draggingWidgetId = item.id
                                    dragOffset = Offset.Zero
                                }

                                if (moving) {
                                    dragOffset += delta
                                    event.changes.forEach { it.consume() }
                                }

                                if (!change.pressed) {
                                    if (moving) {
                                        finishWidgetDrag(item)
                                    } else {
                                        editingWidgetId = item.id
                                    }
                                    break
                                }
                            }
                        }
                    }

                if (item.kind == "android_widget") {
                    AndroidWidgetCard(
                        modifier = itemModifier,
                        context = context,
                        host = appWidgetHost,
                        manager = appWidgetManager,
                        item = item,
                        cellWidthDp = gridCellWidthDp,
                        cellHeightDp = gridCellHeightDp
                    )
                }
            }
        }

        val draggedItem = items.firstOrNull { it.id == draggingWidgetId }
        if (draggedItem != null) {
            val overRemove = draggedItem.y * cellHeightPx + dragOffset.y <= removeZoneHeightPx
            Surface(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 16.dp),
                shape = CircleShape,
                color = if (overRemove) MaterialTheme.colorScheme.error else Color.Black.copy(alpha = 0.58f),
                shadowElevation = 6.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("×", color = Color.White, fontSize = 20.sp)
                    Text(
                        "Remove",
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
        }

        val editingItem = items.firstOrNull { it.id == editingWidgetId }
        if (editingItem != null && draggingWidgetId == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.24f))
                    .pointerInput(editingItem.id) {
                        detectTapGestures { editingWidgetId = null }
                    }
            )

            Box(
                modifier = Modifier
                    .offset(
                        x = cellWidth * editingItem.x,
                        y = cellHeight * editingItem.y
                    )
                    .size(
                        width = cellWidth * editingItem.width,
                        height = cellHeight * editingItem.height
                    )
                    .border(
                        width = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(24.dp)
                    )
                    .pointerInput(editingItem.id, items, cellWidthPx, cellHeightPx) {
                        detectDragGestures(
                            onDragStart = {
                                editingWidgetId = null
                                draggingWidgetId = editingItem.id
                                dragOffset = Offset.Zero
                            },
                            onDragEnd = { finishWidgetDrag(editingItem) },
                            onDragCancel = {
                                draggingWidgetId = null
                                dragOffset = Offset.Zero
                                editingWidgetId = editingItem.id
                            }
                        ) { change, amount ->
                            change.consume()
                            dragOffset += amount
                        }
                    }
            ) {
                WidgetResizeHandles(
                    itemX = editingItem.x,
                    itemY = editingItem.y,
                    itemWidth = editingItem.width,
                    itemHeight = editingItem.height,
                    minimumWidth = editingItem.minWidthCells,
                    minimumHeight = editingItem.minHeightCells,
                    maximumWidth = WIDGET_PAGE_COLUMNS,
                    maximumHeight = WIDGET_PAGE_ROWS,
                    cellWidthDp = gridCellWidthDp,
                    cellHeightDp = gridCellHeightDp,
                    resizeMode = editingItem.resizeMode,
                    onResize = { x, y, width, height ->
                        resizeWidgetBounds(items, editingItem.id, x, y, width, height)?.let(::updateItems)
                            ?: Toast.makeText(
                                context,
                                "The widget cannot fit at that size.",
                                Toast.LENGTH_SHORT
                            ).show()
                    }
                )
            }

            val configureComponent = remember(editingItem.appWidgetId) {
                runCatching {
                    appWidgetManager.getAppWidgetInfo(editingItem.appWidgetId)?.configure
                }.getOrNull()
            }
            if (configureComponent != null) {
                Box(
                    modifier = Modifier
                        .offset(
                            x = cellWidth * (editingItem.x + editingItem.width) - 48.dp,
                            y = cellHeight * editingItem.y + 8.dp
                        )
                        .size(40.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape)
                        .clickable {
                            val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE)
                                .setComponent(configureComponent)
                                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, editingItem.appWidgetId)
                            editingWidgetId = null
                            configureWidgetLauncher.launch(intent)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text("⚙", color = MaterialTheme.colorScheme.onPrimary, fontSize = 19.sp)
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
        val installedProviders = remember(context) {
            appWidgetManager.installedProviders
                .sortedBy { it.provider.packageName.lowercase(Locale.ROOT) }
        }
        val providerGroups = remember(installedProviders) {
            installedProviders.groupBy { it.provider.packageName }.toSortedMap()
        }
        var expandedPackages by remember {
            mutableStateOf(setOf("builtin_dislauncher"))
        }
        val ownAppIcon = remember(context) {
            runCatching {
                context.packageManager.getApplicationIcon(context.packageName).toBitmap(96, 96)
            }.getOrNull()
        }

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
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 440.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        providerGroups.forEach { (packageName, providers) ->
                            item(key = "app_group_$packageName") {
                                val appLabel = remember(packageName) {
                                    runCatching {
                                        context.packageManager.getApplicationLabel(
                                            context.packageManager.getApplicationInfo(packageName, 0)
                                        ).toString()
                                    }.getOrDefault(packageName)
                                }
                                Card(
                                    modifier = Modifier.fillMaxWidth().clickable {
                                        expandedPackages = if (packageName in expandedPackages) {
                                            expandedPackages - packageName
                                        } else {
                                            expandedPackages + packageName
                                        }
                                    },
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                                    ),
                                    shape = RoundedCornerShape(16.dp)
                                ) {
                                    Row(
                                        Modifier.fillMaxWidth().padding(14.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        val appIcon = remember(packageName) {
                                            runCatching {
                                                context.packageManager.getApplicationIcon(packageName).toBitmap(96, 96)
                                            }.getOrNull()
                                        }
                                        if (appIcon != null) {
                                            Image(
                                                bitmap = appIcon.asImageBitmap(),
                                                contentDescription = null,
                                                modifier = Modifier.size(38.dp).clip(RoundedCornerShape(10.dp))
                                            )
                                        }
                                        Column(Modifier.weight(1f).padding(start = 12.dp)) {
                                            Text(
                                                appLabel,
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            Text(
                                                "${providers.size} widget${if (providers.size == 1) "" else "s"}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Text(
                                            if (packageName in expandedPackages) "⌃" else "⌄",
                                            style = MaterialTheme.typography.titleMedium
                                        )
                                    }
                                }
                            }

                            if (packageName in expandedPackages) {
                                lazyItems(providers, key = { it.provider.flattenToString() }) { provider ->
                                    val preview = remember(provider) {
                                        runCatching {
                                            val drawable = provider.loadPreviewImage(
                                                context,
                                                context.resources.displayMetrics.densityDpi
                                            ) ?: provider.loadIcon(
                                                context,
                                                context.resources.displayMetrics.densityDpi
                                            )
                                            if (drawable == null) {
                                                null
                                            } else {
                                                val width = drawable.intrinsicWidth.coerceAtLeast(1)
                                                val height = drawable.intrinsicHeight.coerceAtLeast(1)
                                                val scale = minOf(1f, 640f / maxOf(width, height))
                                                drawable.toBitmap(
                                                    (width * scale).toInt().coerceAtLeast(1),
                                                    (height * scale).toInt().coerceAtLeast(1)
                                                )
                                            }
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
                                                Column(Modifier.weight(1f)) {
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
                                                Surface(
                                                    modifier = Modifier.fillMaxWidth().height(116.dp),
                                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                                    shape = RoundedCornerShape(12.dp)
                                                ) {
                                                    Image(
                                                        bitmap = preview.asImageBitmap(),
                                                        contentDescription = "Preview of ${provider.loadLabel(context.packageManager)} widget",
                                                        modifier = Modifier.fillMaxSize().padding(8.dp),
                                                        contentScale = ContentScale.Fit
                                                    )
                                                }
                                            } else {
                                                Surface(
                                                    modifier = Modifier.fillMaxWidth().height(76.dp),
                                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                                    shape = RoundedCornerShape(12.dp)
                                                ) {
                                                    Box(contentAlignment = Alignment.Center) {
                                                        Text(
                                                            "Widget preview unavailable",
                                                            style = MaterialTheme.typography.bodySmall
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
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
