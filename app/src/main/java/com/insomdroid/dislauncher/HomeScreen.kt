package com.insomdroid.dislauncher

import android.Manifest
import android.content.Intent
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
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private val inspirationQuotes = listOf(
    "Focus on what matters.",
    "Do less, with more intention.",
    "Make room for what matters.",
    "Attention is your most precious resource.",
    "Small steps still move you forward.",
    "Be where your feet are.",
    "Choose presence over noise.",
    "Progress, not pressure."
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun HomeScreen(
    apps: List<LauncherApp>,
    favorites: Set<String>,
    favoriteOrder: List<String>,
    preferences: LauncherPreferences,
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
    val haptic = LocalHapticFeedback.current
    val currentOpenSettings by rememberUpdatedState(onOpenSettings)
    val currentLockScreen by rememberUpdatedState(onDoubleTapToLock)
    val currentSolarInfoChange by rememberUpdatedState(onSolarInfoChange)
    var now by remember { mutableStateOf(LocalTime.now()) }
    var quoteIndex by remember { mutableIntStateOf(LocalDate.now().dayOfYear % inspirationQuotes.size) }
    var batteryPercent by remember { mutableStateOf(readBatteryPercent(context)) }
    var batteryCharging by remember { mutableStateOf(readBatteryCharging(context)) }
    var screenTimeMinutes by remember { mutableStateOf(readScreenTimeMinutes(context)) }
    var weatherInfo by remember { mutableStateOf<WeatherInfo?>(null) }
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

    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000L)
            now = LocalTime.now()
            batteryPercent = readBatteryPercent(context)
            batteryCharging = readBatteryCharging(context)
            screenTimeMinutes = readScreenTimeMinutes(context)
        }
    }

    LaunchedEffect(preferences.showWeather, preferences.themeMode, preferences.weatherUnit) {
        if (!preferences.showWeather && preferences.themeMode != "sun") {
            weatherInfo = null
            currentSolarInfoChange(null)
            return@LaunchedEffect
        }
        val hasLocation = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasLocation) {
            weatherInfo = null
            weatherStatus = if (preferences.showWeather) "Tap to allow location" else ""
            currentSolarInfoChange(null)
        } else {
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
            .windowInsetsPadding(WindowInsets.systemBars)
            .verticalScroll(rememberScrollState())
            .pointerInput(preferences.doubleTapToLock) {
                detectTapGestures(
                    onLongPress = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        currentOpenSettings()
                    },
                    onDoubleTap = {
                        if (preferences.doubleTapToLock) currentLockScreen()
                    }
                )
            }
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
        if (preferences.showYearProgress && preferences.pinYearProgress) {
            Spacer(Modifier.height(6.dp))
            ProgressLine(
                label = "Year in progress",
                percent = (today.dayOfYear * 100f / today.lengthOfYear()).toInt(),
                primaryText = primaryText,
                secondaryText = secondaryText,
                fill = primaryText,
                track = secondaryText.copy(alpha = 0.22f),
                fontScale = fontScale
            )
        }

        if (preferences.showYearProgress && !preferences.pinYearProgress || preferences.showDayProgress) {
            Spacer(Modifier.height(6.dp))
            if (preferences.showYearProgress && !preferences.pinYearProgress) {
                ProgressLine(
                    label = "Year in progress",
                    percent = (today.dayOfYear * 100f / today.lengthOfYear()).toInt(),
                    primaryText = primaryText, secondaryText = secondaryText,
                    fill = primaryText,
                    track = secondaryText.copy(alpha = 0.22f),
                    fontScale = fontScale
                )
            }
            if (preferences.showDayProgress) {
                if (preferences.showYearProgress && !preferences.pinYearProgress) Spacer(Modifier.height(4.dp))
                ProgressLine(
                    label = "Day in progress",
                    percent = (now.toSecondOfDay() * 100f / 86400f).toInt(),
                    primaryText = primaryText, secondaryText = secondaryText,
                    fill = primaryText,
                    track = secondaryText.copy(alpha = 0.22f),
                    fontScale = fontScale
                )
            }
        }


        if (preferences.showQuote) {
            Spacer(Modifier.height(10.dp))
            Text(
                text = "“${inspirationQuotes[quoteIndex]}”",
                color = secondaryText,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                fontFamily = fontFamily,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                    .clickable { quoteIndex = (quoteIndex + 1) % inspirationQuotes.size }
                    .padding(vertical = 6.dp)
            )
        }

        if (preferences.showWeather || preferences.showBattery || preferences.showScreenTime) {
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
                        fontSize = 14.sp,
                        maxLines = 1
                    )
                }
                if (preferences.showScreenTime) {
                    val screenText = screenTimeMinutes?.let { formatMinutes(it) } ?: "SCREEN TIME"
                    Text(
                        screenText,
                        color = primaryText,
                        fontSize = 14.sp,
                        maxLines = 1,
                        modifier = Modifier.clickable {
                            if (screenTimeMinutes == null) {
                                runCatching {
                                    context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                                }
                            } else {
                                screenTimeMinutes = readScreenTimeMinutes(context)
                            }
                        }
                    )
                }
                if (preferences.showWeather) {
                    val hasLocation = ContextCompat.checkSelfPermission(
                        context, Manifest.permission.ACCESS_COARSE_LOCATION
                    ) == PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(
                        context, Manifest.permission.ACCESS_FINE_LOCATION
                    ) == PackageManager.PERMISSION_GRANTED
                    val weatherText = weatherInfo?.let {
                        "${it.temperature} ${it.condition.lowercase(Locale.getDefault())}"
                    } ?: if (!hasLocation) "Tap to allow location" else weatherStatus
                    Text(
                        weatherText,
                        color = primaryText,
                        fontSize = 14.sp,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                            .clickable { onOpenWeather(weatherInfo) }
                    )
                }
            }
        }

        if (preferences.showCalendarShortcut) {
            Spacer(Modifier.height(6.dp))
            CalendarAgenda(preferences.timeFormat)
        }

        val activeMainItemIds = buildList {
            if (preferences.showTasks) add(HOME_ITEM_TASKS)
            if (preferences.showCall) add(HOME_ITEM_CALLS)
            if (preferences.showMusic) add(HOME_ITEM_MUSIC)
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
                HOME_ITEM_MUSIC -> "Music"
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
                    if (itemId == HOME_ITEM_MUSIC) {
                        MusicPlayerWidget(
                            modifier = Modifier.fillMaxWidth(),
                            onOpenMusicApp = { openMusicPlayer(context, apps) }
                        )
                    } else {
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

private fun readScreenTimeMinutes(context: Context): Long? {
    val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager ?: return null
    val mode = runCatching {
        appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
    }.getOrDefault(AppOpsManager.MODE_DEFAULT)
    if (mode != AppOpsManager.MODE_ALLOWED) return null
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
