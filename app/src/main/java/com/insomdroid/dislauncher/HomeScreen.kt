package com.insomdroid.dislauncher

import android.Manifest
import android.content.Intent
import android.content.IntentFilter
import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Process
import android.provider.Settings
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun HomeScreen(
    apps: List<LauncherApp>,
    favorites: Set<String>,
    favoriteOrder: List<String>,
    preferences: LauncherPreferences,
    cachedWeather: WeatherInfo?,
    isActive: Boolean,
    isDark: Boolean,
    fontFamily: FontFamily,
    fontScale: Float,
    onLaunch: (LauncherApp) -> Unit,
    onMoveFavorite: (String, String) -> Unit,
    onOpenTasks: () -> Unit,
    onOpenWeather: (WeatherInfo?) -> Unit,
    onOpenSettings: () -> Unit,
    onDoubleTapToLock: () -> Unit,
    homeItemOrder: List<String>,
    onMoveHomeItem: (String, String) -> Unit,
    onSolarInfoChange: (WeatherInfo?) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val haptic = LocalHapticFeedback.current
    val currentOpenSettings by rememberUpdatedState(onOpenSettings)
    val currentLockScreen by rememberUpdatedState(onDoubleTapToLock)
    val currentSolarInfoChange by rememberUpdatedState(onSolarInfoChange)
    val coroutineScope = rememberCoroutineScope()
    var hostResumed by remember(lifecycleOwner) {
        mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
    }
    var now by remember { mutableStateOf(LocalTime.now()) }
    var batteryPercent by remember { mutableStateOf(readBatteryPercent(context)) }
    var batteryCharging by remember { mutableStateOf(readBatteryCharging(context)) }
    var screenTimeMinutes by remember { mutableStateOf<Long?>(null) }
    var weatherInfo by remember { mutableStateOf(cachedWeather) }
    var weatherStatus by remember { mutableStateOf("Tap to allow location") }
    val today = LocalDate.now()
    val systemTimeFormat = android.text.format.DateFormat.is24HourFormat(context)
    val timePattern = when (preferences.timeFormat) {
        "12" -> "h:mm"
        "24" -> "HH:mm"
        else -> if (systemTimeFormat) "HH:mm" else "h:mm"
    }
    val timeText = now.format(DateTimeFormatter.ofPattern(timePattern, Locale.getDefault()))
    val dateText = formatHomeDate(today)
    val background = MaterialTheme.colorScheme.background
    val primaryText = MaterialTheme.colorScheme.onBackground
    val secondaryText = MaterialTheme.colorScheme.onSurfaceVariant
    val visibleFavorites = remember(apps, favorites) {
        apps.filter { it.packageName in favorites }
    }
    val favoriteAppsByPackage = remember(visibleFavorites) { visibleFavorites.associateBy { it.packageName } }
    var draggingMainItem by remember { mutableStateOf<String?>(null) }
    var dragDistance by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(isActive, hostResumed, preferences.showScreenTime) {
        if (!isActive || !hostResumed) {
            if (!preferences.showScreenTime) screenTimeMinutes = null
            return@LaunchedEffect
        }

        now = LocalTime.now()
        batteryPercent = readBatteryPercent(context)
        batteryCharging = readBatteryCharging(context)
        screenTimeMinutes = if (preferences.showScreenTime) {
            withContext(Dispatchers.IO) { readScreenTimeMinutes(context) }
        } else {
            null
        }

        while (true) {
            delay(60_000L)
            now = LocalTime.now()
            batteryPercent = readBatteryPercent(context)
            batteryCharging = readBatteryCharging(context)
            screenTimeMinutes = if (preferences.showScreenTime) {
                withContext(Dispatchers.IO) { readScreenTimeMinutes(context) }
            } else {
                null
            }
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> hostResumed = true
                Lifecycle.Event.ON_PAUSE -> hostResumed = false
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(preferences.showWeather, preferences.weatherUnit, cachedWeather, isActive) {
        if (!isActive) return@LaunchedEffect
        if (!preferences.showWeather) {
            weatherInfo = null
            currentSolarInfoChange(null)
            return@LaunchedEffect
        }
        val usableCache = cachedWeather?.let { convertWeatherUnit(it, preferences.weatherUnit) }
        val hasLocation = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasLocation) {
            weatherInfo = usableCache
            weatherStatus = if (usableCache == null && preferences.showWeather) "Tap to allow location" else ""
            currentSolarInfoChange(usableCache)
        } else {
            val matchingCache = usableCache?.takeIf {
                it.temperature.endsWith("°${preferences.weatherUnit}")
            }
            if (matchingCache != null) {
                weatherInfo = matchingCache
                weatherStatus = ""
                currentSolarInfoChange(matchingCache)
                return@LaunchedEffect
            }
            weatherInfo = null
            weatherStatus = if (preferences.showWeather) "Loading weather…" else ""
            val fetched = withContext(Dispatchers.IO) { fetchWeather(context, preferences.weatherUnit) }
            weatherInfo = fetched
            currentSolarInfoChange(fetched)
            weatherStatus = if (fetched == null && preferences.showWeather) "Weather unavailable · tap to retry" else ""
        }
    }

    Column(
        modifier = Modifier.fillMaxSize()
            .background(background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState())
            .combinedClickable(
                onClick = {},
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    currentOpenSettings()
                },
                onDoubleClick = {
                    if (preferences.doubleTapToLock) currentLockScreen()
                }
            )
            .padding(horizontal = 26.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.Top
    ) {
        if (preferences.showClock) {
            Text(
                timeText,
                color = primaryText,
                fontSize = 30.sp,
                lineHeight = 36.sp,
                letterSpacing = (-0.6).sp,
                fontWeight = FontWeight.Light,
                fontFamily = fontFamily,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.clip(RoundedCornerShape(5.dp))
                    .clickable { openClock(context) }
            )
        }
        if (preferences.showDate) {
            Spacer(Modifier.height(8.dp))
            Text(
                dateText,
                color = secondaryText,
                fontSize = 14.sp,
                fontFamily = fontFamily,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.clip(RoundedCornerShape(5.dp)).clickable { openCalendar(context) }
            )
        }
        if (preferences.showYearProgress || preferences.showDayProgress) {
            Spacer(Modifier.height(12.dp))
            if (preferences.showYearProgress) {
                ProgressLine(
                    label = "Year in progress",
                    percent = (today.dayOfYear * 100f / today.lengthOfYear()).toInt(),
                    primaryText = primaryText,
                    secondaryText = secondaryText,
                    fill = primaryText,
                    track = if (isDark) Color(0xFF555555) else Color(0xFFD5D5D5),
                    fontScale = fontScale
                )
            }
            if (preferences.showDayProgress) {
                if (preferences.showYearProgress) Spacer(Modifier.height(6.dp))
                ProgressLine(
                    label = "Day in progress",
                    percent = (now.toSecondOfDay() * 100f / 86400f).toInt(),
                    primaryText = primaryText,
                    secondaryText = secondaryText,
                    fill = primaryText,
                    track = if (isDark) Color(0xFF555555) else Color(0xFFD5D5D5),
                    fontScale = fontScale
                )
            }
        }


        if (preferences.showWeather || preferences.showBattery || preferences.showScreenTime || preferences.showCalendarShortcut) {
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (preferences.showBattery) {
                    Text(
                        (if (batteryCharging) "+" else "") + (batteryPercent?.let { "$it%" } ?: "--%"),
                        color = primaryText,
                        fontSize = 12.sp,
                        maxLines = 1
                    )
                }
                if (preferences.showScreenTime) {
                    val screenText = screenTimeMinutes?.let { formatMinutes(it) } ?: "SCREEN TIME"
                    Text(
                        screenText,
                        color = primaryText,
                        fontSize = 12.sp,
                        maxLines = 1,
                        modifier = Modifier.clickable {
                            if (!hasUsageStatsAccess(context)) {
                                runCatching {
                                    context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                                }
                            } else {
                                coroutineScope.launch {
                                    val refreshed = withContext(Dispatchers.IO) {
                                        readScreenTimeMinutes(context)
                                    }
                                    screenTimeMinutes = refreshed
                                    if (refreshed == null) {
                                        runCatching {
                                            context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                                        }
                                    }
                                }
                            }
                        }
                    )
                }
                if (preferences.showWeather || preferences.showCalendarShortcut) {
                    Row(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (preferences.showWeather) {
                            val hasLocation = ContextCompat.checkSelfPermission(
                                context, Manifest.permission.ACCESS_COARSE_LOCATION
                            ) == PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(
                                context, Manifest.permission.ACCESS_FINE_LOCATION
                            ) == PackageManager.PERMISSION_GRANTED
                            val weatherText = weatherInfo?.let {
                                "${it.temperature} ${it.condition.lowercase(Locale.getDefault())}"
                            } ?: weatherStatus.ifBlank {
                                if (!hasLocation) "Tap to allow location" else ""
                            }
                            Text(
                                weatherText,
                                color = primaryText,
                                fontSize = 12.sp,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.widthIn(max = if (preferences.showCalendarShortcut) 116.dp else 210.dp)
                                    .clickable { onOpenWeather(weatherInfo ?: cachedWeather) }
                            )
                        }
                        if (preferences.showCalendarShortcut) {
                            CalendarAgenda(
                                preferences.timeFormat,
                                modifier = Modifier.widthIn(max = 148.dp)
                            )
                        }
                    }
                }
            }
        }

        if (preferences.showMusic) {
            Spacer(Modifier.height(12.dp))
            MusicPlayerWidget(
                modifier = Modifier.fillMaxWidth(),
                onOpenMusicApp = { openMusicPlayer(context, apps) }
            )
        }

        val activeMainItemIds = buildList {
            if (preferences.showTasks) add(HOME_ITEM_TASKS)
            if (preferences.showCall) add(HOME_ITEM_CALLS)
            addAll(visibleFavorites.map { it.packageName })
        }
        val orderedMainItemIds = remember(activeMainItemIds, homeItemOrder) {
            val existing = homeItemOrder.filter { it in activeMainItemIds }
            (existing + activeMainItemIds.filterNot { it in existing }).distinct()
        }
        val latestOrderedMainItemIds = rememberUpdatedState(orderedMainItemIds)
        val latestMoveHomeItem = rememberUpdatedState(onMoveHomeItem)
        Spacer(Modifier.height(140.dp))
        orderedMainItemIds.forEach { itemId ->
            val app = favoriteAppsByPackage[itemId]
            val label = when (itemId) {
                HOME_ITEM_TASKS -> "Tasks"
                HOME_ITEM_CALLS -> "Calls"
                else -> app?.label.orEmpty()
            }
            key(itemId) {
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .alpha(if (draggingMainItem == itemId) 0.62f else 1f)
                        .pointerInput(itemId) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = {
                                    draggingMainItem = itemId
                                    dragDistance = 0f
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                },
                                onDragEnd = {
                                    draggingMainItem = null
                                    dragDistance = 0f
                                },
                                onDragCancel = {
                                    draggingMainItem = null
                                    dragDistance = 0f
                                },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    dragDistance += dragAmount.y
                                    val threshold = 28.dp.toPx()
                                    if (kotlin.math.abs(dragDistance) >= threshold) {
                                        val order = latestOrderedMainItemIds.value
                                        val current = order.indexOf(itemId)
                                        val direction = if (dragDistance > 0f) 1 else -1
                                        val target = order.getOrNull(current + direction)
                                        if (target != null) {
                                            latestMoveHomeItem.value(itemId, target)
                                            dragDistance -= direction * threshold
                                        } else {
                                            dragDistance = 0f
                                        }
                                    }
                                }
                            )
                        }
                        .then(
                            if (itemId == HOME_ITEM_MUSIC) Modifier else Modifier.clickable {
                                when {
                                    app != null -> onLaunch(app)
                                    itemId == HOME_ITEM_TASKS -> onOpenTasks()
                                    itemId == HOME_ITEM_CALLS -> runCatching {
                                        context.startActivity(Intent(Intent.ACTION_DIAL))
                                    }
                                }
                            }
                        )
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (app != null && preferences.showAppIcons) {
                        AppIcon(app, preferences.iconPack, size = 19.dp)
                    }
                    Text(
                        text = label,
                        color = primaryText,
                        fontSize = 26.sp,
                        lineHeight = 34.sp,
                        fontFamily = fontFamily,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                            .padding(start = if (app != null && preferences.showAppIcons) 8.dp else 0.dp)
                    )
                }
            }
        }

        if (preferences.showSettingsButton) {
            Text(
                "⋯",
                color = secondaryText,
                fontSize = 25.sp,
                lineHeight = 28.sp,
                fontWeight = FontWeight.Light,
                modifier = Modifier.clip(RoundedCornerShape(5.dp)).clickable(onClick = onOpenSettings)
                    .padding(vertical = 1.dp, horizontal = 1.dp)
            )
        }
        Spacer(Modifier.height(14.dp))
    }


}

@Composable
private fun ProgressLine(
    label: String,
    percent: Int,
    primaryText: Color,
    secondaryText: Color,
    fill: Color,
    track: Color,
    fontScale: Float
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Text(
            "$label $percent%",
            color = primaryText,
            fontSize = 14.sp,
            lineHeight = 18.sp,
            fontWeight = FontWeight.Normal
        )
        Canvas(
            modifier = Modifier.fillMaxWidth(0.36f).height(13.dp)
        ) {
            val stepX = 4.dp.toPx()
            val stepY = 4.dp.toPx()
            val radius = 1.1.dp.toPx()
            val columns = (size.width / stepX).toInt().coerceAtLeast(1)
            val rows = (size.height / stepY).toInt().coerceAtLeast(1)
            for (row in 0..rows) {
                for (column in 0..columns) {
                    val x = column * stepX + radius
                    val y = row * stepY + radius
                    if (x <= size.width && y <= size.height) {
                        val color = if (x / size.width * 100f <= percent.coerceIn(0, 100)) {
                            fill
                        } else {
                            track.copy(alpha = 0.85f)
                        }
                        drawCircle(color = color, radius = radius, center = Offset(x, y))
                    }
                }
            }
        }
    }
}

private fun readBatteryCharging(context: Context): Boolean {
    val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return false
    return intent.getIntExtra("plugged", 0) != 0
}

private fun formatHomeDate(date: LocalDate): String {
    val locale = Locale.getDefault()
    if (locale.language != "en") {
        return date.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", locale))
    }
    val day = date.dayOfMonth
    val suffix = when {
        day % 100 in 11..13 -> "th"
        day % 10 == 1 -> "st"
        day % 10 == 2 -> "nd"
        day % 10 == 3 -> "rd"
        else -> "th"
    }
    return date.format(DateTimeFormatter.ofPattern("EEEE, ", locale)) +
        day + suffix + date.format(DateTimeFormatter.ofPattern(" MMMM", locale))
}

private fun hasUsageStatsAccess(context: Context): Boolean {
    val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager ?: return false
    val mode = runCatching {
        appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName
        )
    }.getOrDefault(AppOpsManager.MODE_DEFAULT)
    return mode == AppOpsManager.MODE_ALLOWED
}

private fun readScreenTimeMinutes(context: Context): Long? {
    if (!hasUsageStatsAccess(context)) return null
    val manager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager ?: return null
    val start = LocalDate.now().atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
    val end = System.currentTimeMillis()
    val usage = runCatching { manager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, start, end) }
        .getOrNull() ?: return null
    return usage.sumOf { it.totalTimeInForeground } / 60_000L
}

private fun formatMinutes(minutes: Long): String {
    val hours = minutes / 60
    val remaining = minutes % 60
    return if (hours > 0) "${hours}h ${remaining}m" else "${remaining}m"
}
