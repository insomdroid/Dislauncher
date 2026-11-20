package com.insomdroid.dislauncher

import android.Manifest
import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.content.Context
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
    var items by remember(context) { mutableStateOf(loadWidgetPageItems(context)) }
    var pageMenuExpanded by remember { mutableStateOf(false) }
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
        val minWidthCells = ceil(providerInfo.minWidth / gridCellWidthDp).toInt().coerceAtLeast(1)
        val minHeightCells = ceil(providerInfo.minHeight / gridCellHeightDp).toInt().coerceAtLeast(1)
        if (minWidthCells > WIDGET_PAGE_COLUMNS || minHeightCells > WIDGET_PAGE_ROWS) {
            appWidgetHost.deleteAppWidgetId(appWidgetId)
            Toast.makeText(context, "This widget is larger than the available page.", Toast.LENGTH_LONG).show()
            return
        }
        val width = minWidthCells
        val height = minHeightCells
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
                detectTapGestures(onLongPress = { pageMenuExpanded = true })
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
                    .pointerInput(item.id, items, cellWidthPx, cellHeightPx) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = {
                                dragDelta = Offset.Zero
                                selectedWidgetId = item.id
                                draggingWidgetId = item.id
                            },
                            onDragEnd = {
                                if (dragDelta.y < -cellHeightPx * 0.72f) {
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
                        onRemove = { updateItems(items.filterNot { it.id == item.id }) },
                        onResize = { height ->
                            resizeWidgetItem(items, item.id, height)?.let(::updateItems)
                                ?: Toast.makeText(context, "Not enough space to resize this widget.", Toast.LENGTH_SHORT).show()
                        }
                    )
                    "month_calendar" -> MonthCalendarWidget(
                        modifier = itemModifier,
                        context = context,
                        isActive = isActive,
                        selected = selectedWidgetId == item.id,
                        onRemove = { updateItems(items.filterNot { it.id == item.id }) },
                        onResize = { height ->
                            resizeWidgetItem(items, item.id, height)?.let(::updateItems)
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
                        onResize = { width, height ->
                            resizeWidgetDimensions(items, item.id, width, height)?.let(::updateItems)
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
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 10.dp),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.errorContainer,
                shadowElevation = 6.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("⌫", color = MaterialTheme.colorScheme.onErrorContainer)
                    Text(
                        "Release here to remove widget",
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }

        DropdownMenu(
            expanded = pageMenuExpanded,
            onDismissRequest = { pageMenuExpanded = false }
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
                    Card(
                        modifier = Modifier.fillMaxWidth().clickable {
                            addWidgetDialog = false
                            launchAndroidWidgetPicker()
                        },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Android app widgets", style = MaterialTheme.typography.titleSmall)
                                Text("Choose widgets installed on this device", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text("Add", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
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
    onRemove: () -> Unit,
    onResize: (Int) -> Unit
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

    WidgetCard(modifier = modifier) {
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
            if (selected) {
                var actionsExpanded by remember { mutableStateOf(false) }
                Box {
                    IconButton(onClick = { actionsExpanded = true }) {
                        Text("⋮", fontSize = 20.sp)
                    }
                    DropdownMenu(
                        expanded = actionsExpanded,
                        onDismissRequest = { actionsExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Increase height") },
                            onClick = { actionsExpanded = false; onResize(3) }
                        )
                        DropdownMenuItem(
                            text = { Text("Decrease height") },
                            onClick = { actionsExpanded = false; onResize(2) }
                        )
                        DropdownMenuItem(
                            text = { Text("Remove widget") },
                            onClick = { actionsExpanded = false; onRemove() }
                        )
                    }
                }
            }
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
    onRemove: () -> Unit,
    onResize: (Int) -> Unit
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

    WidgetCard(modifier = modifier) {
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
            if (selected) {
                var actionsExpanded by remember { mutableStateOf(false) }
                Box {
                    IconButton(onClick = { actionsExpanded = true }) {
                        Text("⋮", fontSize = 20.sp)
                    }
                    DropdownMenu(
                        expanded = actionsExpanded,
                        onDismissRequest = { actionsExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Increase height") },
                            onClick = { actionsExpanded = false; onResize(5) }
                        )
                        DropdownMenuItem(
                            text = { Text("Decrease height") },
                            onClick = { actionsExpanded = false; onResize(4) }
                        )
                        DropdownMenuItem(
                            text = { Text("Remove widget") },
                            onClick = { actionsExpanded = false; onRemove() }
                        )
                    }
                }
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
    onResize: (Int, Int) -> Unit,
    onRemove: () -> Unit
) {
    val providerInfo = remember(item.appWidgetId) {
        runCatching { manager.getAppWidgetInfo(item.appWidgetId) }.getOrNull()
    }
    var actionsExpanded by remember(item.id) { mutableStateOf(false) }

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
                    top = if (selected) 42.dp else 4.dp,
                    start = 4.dp,
                    end = 4.dp,
                    bottom = 4.dp
                )
        )

        if (selected) {
            Box(modifier = Modifier.align(Alignment.TopEnd)) {
                IconButton(onClick = { actionsExpanded = true }) {
                    Text("⋮", fontSize = 22.sp)
                }
                DropdownMenu(
                    expanded = actionsExpanded,
                    onDismissRequest = { actionsExpanded = false }
                ) {
                    if (providerInfo?.supportsHorizontalResize() == true) {
                        DropdownMenuItem(
                            text = { Text("Increase width") },
                            onClick = {
                                actionsExpanded = false
                                onResize(item.width + 1, item.height)
                            },
                            enabled = item.width < WIDGET_PAGE_COLUMNS
                        )
                        DropdownMenuItem(
                            text = { Text("Decrease width") },
                            onClick = {
                                actionsExpanded = false
                                onResize(item.width - 1, item.height)
                            },
                            enabled = item.width > item.minWidthCells
                        )
                    }
                    if (providerInfo?.supportsVerticalResize() == true) {
                        DropdownMenuItem(
                            text = { Text("Increase height") },
                            onClick = {
                                actionsExpanded = false
                                onResize(item.width, item.height + 1)
                            },
                            enabled = item.height < WIDGET_PAGE_ROWS
                        )
                        DropdownMenuItem(
                            text = { Text("Decrease height") },
                            onClick = {
                                actionsExpanded = false
                                onResize(item.width, item.height - 1)
                            },
                            enabled = item.height > item.minHeightCells
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("Remove widget") },
                        onClick = {
                            actionsExpanded = false
                            onRemove()
                        }
                    )
                }
            }
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
