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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
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
    onOpenWeather: () -> Unit,
    onOpenSettings: () -> Unit,
    onDoubleTapToLock: () -> Unit
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val currentOpenSettings by rememberUpdatedState(onOpenSettings)
    val currentLockScreen by rememberUpdatedState(onDoubleTapToLock)
    var now by remember { mutableStateOf(LocalTime.now()) }
    var quoteIndex by remember { mutableIntStateOf(LocalDate.now().dayOfYear % inspirationQuotes.size) }
    var batteryPercent by remember { mutableStateOf(readBatteryPercent(context)) }
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
    val dateText = today.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.getDefault()))
    val background = MaterialTheme.colorScheme.background
    val primaryText = MaterialTheme.colorScheme.onBackground
    val secondaryText = MaterialTheme.colorScheme.onSurfaceVariant
    val visibleFavorites = remember(apps, favorites, favoriteOrder) {
        apps.filter { it.packageName in favorites }
            .sortedBy { app ->
                val position = favoriteOrder.indexOf(app.packageName)
                if (position < 0) Int.MAX_VALUE else position
            }
    }
    var draggingFavorite by remember { mutableStateOf<String?>(null) }
    var dragDistance by remember { mutableFloatStateOf(0f) }
    val latestVisibleFavorites = rememberUpdatedState(visibleFavorites)
    val latestMoveFavorite = rememberUpdatedState(onMoveFavorite)

    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000L)
            now = LocalTime.now()
            batteryPercent = readBatteryPercent(context)
            screenTimeMinutes = readScreenTimeMinutes(context)
        }
    }

    LaunchedEffect(preferences.showWeather, preferences.weatherUnit) {
        if (!preferences.showWeather) return@LaunchedEffect
        val hasLocation = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasLocation) {
            weatherInfo = null
            weatherStatus = "Tap to allow location"
        } else {
            weatherStatus = "Loading weather…"
            val fetched = withContext(Dispatchers.IO) { fetchWeather(context, preferences.weatherUnit) }
            weatherInfo = fetched
            weatherStatus = if (fetched == null) "Weather unavailable · tap to retry" else ""
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
            .padding(horizontal = 22.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.Top
    ) {
        if (preferences.showClock) {
            Text(
                timeText,
                color = primaryText,
                fontSize = 49.sp * fontScale,
                lineHeight = 52.sp * fontScale,
                letterSpacing = (-1.6).sp,
                fontWeight = FontWeight.Light,
                fontFamily = fontFamily,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.clip(RoundedCornerShape(5.dp))
                    .clickable { openClock(context) }
            )
        }
        if (preferences.showDate) {
            Spacer(Modifier.height(1.dp))
            Text(
                dateText,
                color = secondaryText,
                fontSize = 11.sp * fontScale,
                fontFamily = fontFamily,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.clip(RoundedCornerShape(5.dp)).clickable { openCalendar(context) }
            )
        }
        if (preferences.showYearProgress && preferences.pinYearProgress) {
            Spacer(Modifier.height(6.dp))
            ProgressLine(
                label = "YEAR",
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
                    label = "YEAR",
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
                    label = "DAY",
                    percent = (now.toSecondOfDay() * 100f / 86400f).toInt(),
                    primaryText = primaryText, secondaryText = secondaryText,
                    fill = primaryText,
                    track = secondaryText.copy(alpha = 0.22f),
                    fontScale = fontScale
                )
            }
        }


        if (preferences.showWeather || preferences.showBattery || preferences.showScreenTime) {
            Spacer(Modifier.height(7.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (preferences.showWeather) {
                    val hasLocation = ContextCompat.checkSelfPermission(
                        context, Manifest.permission.ACCESS_COARSE_LOCATION
                    ) == PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(
                        context, Manifest.permission.ACCESS_FINE_LOCATION
                    ) == PackageManager.PERMISSION_GRANTED
                    val weatherText = when {
                        weatherInfo != null -> weatherInfo!!.summary()
                        !hasLocation -> "WEATHER · $weatherStatus"
                        else -> "WEATHER · $weatherStatus"
                    }
                    Text(
                        weatherText,
                        color = secondaryText,
                        fontSize = 9.sp * fontScale,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f).padding(end = 8.dp).clickable(onClick = onOpenWeather)
                    )
                }
                if (preferences.showScreenTime) {
                    val screenText = screenTimeMinutes?.let { formatMinutes(it) } ?: "SCREEN TIME"
                    Text(
                        screenText,
                        color = secondaryText,
                        fontSize = 9.sp * fontScale,
                        maxLines = 1,
                        modifier = Modifier.padding(end = 8.dp).clickable {
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
                if (preferences.showBattery) {
                    Text(
                        batteryPercent?.let { "$it%" } ?: "--%",
                        color = secondaryText,
                        fontSize = 9.sp * fontScale,
                        maxLines = 1
                    )
                }
            }
        }

        val activeMiniWidgets = preferences.showTasks ||
            preferences.showCall || preferences.showMusic || preferences.showCalendarShortcut
        if (activeMiniWidgets) {
            Spacer(Modifier.height(7.dp))
            if (preferences.showTasks) {
                Text(
                    "TASKS",
                    color = primaryText,
                    fontSize = 20.sp * fontScale,
                    lineHeight = 25.sp * fontScale,
                    fontFamily = fontFamily,
                    modifier = Modifier.fillMaxWidth().clickable(onClick = onOpenTasks)
                        .padding(vertical = 3.dp)
                )
            }
            if (preferences.showCall) {
                Text(
                    "CALLS",
                    color = primaryText,
                    fontSize = 20.sp * fontScale,
                    lineHeight = 25.sp * fontScale,
                    fontFamily = fontFamily,
                    modifier = Modifier.fillMaxWidth().clickable {
                        runCatching { context.startActivity(Intent(Intent.ACTION_DIAL)) }
                    }.padding(vertical = 3.dp)
                )
            }
            if (preferences.showMusic) {
                Text(
                    "MUSIC",
                    color = primaryText,
                    fontSize = 20.sp * fontScale,
                    lineHeight = 25.sp * fontScale,
                    fontFamily = fontFamily,
                    modifier = Modifier.fillMaxWidth().clickable { openMusicPlayer(context, apps) }
                        .padding(vertical = 3.dp)
                )
            }
            if (preferences.showCalendarShortcut) {
                Text(
                    "CALENDAR",
                    color = primaryText,
                    fontSize = 20.sp * fontScale,
                    lineHeight = 25.sp * fontScale,
                    fontFamily = fontFamily,
                    modifier = Modifier.fillMaxWidth().clickable { openCalendar(context) }
                        .padding(vertical = 3.dp)
                )
            }
        }

        Spacer(Modifier.height(8.dp))
        Spacer(Modifier.height(4.dp))
        if (visibleFavorites.isEmpty()) {
            Spacer(Modifier.height(1.dp))
        } else {
            visibleFavorites.forEach { app ->
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .alpha(if (draggingFavorite == app.packageName) 0.62f else 1f)
                        .pointerInput(app.packageName) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = {
                                    draggingFavorite = app.packageName
                                    dragDistance = 0f
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                },
                                onDragEnd = {
                                    draggingFavorite = null
                                    dragDistance = 0f
                                },
                                onDragCancel = {
                                    draggingFavorite = null
                                    dragDistance = 0f
                                },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    dragDistance += dragAmount.y
                                    val threshold = 28.dp.toPx()
                                    if (kotlin.math.abs(dragDistance) >= threshold) {
                                        val order = latestVisibleFavorites.value.map { it.packageName }
                                        val current = order.indexOf(app.packageName)
                                        val direction = if (dragDistance > 0f) 1 else -1
                                        val target = order.getOrNull(current + direction)
                                        if (target != null) {
                                            latestMoveFavorite.value(app.packageName, target)
                                            dragDistance -= direction * threshold
                                        } else {
                                            dragDistance = 0f
                                        }
                                    }
                                }
                            )
                        }
                        .clickable { onLaunch(app) }
                        .padding(vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (preferences.showAppIcons) {
                        AppIcon(app, preferences.iconPack, size = 17.dp)
                    }
                    Text(
                        text = app.label,
                        color = primaryText,
                        fontSize = 20.sp * fontScale,
                        lineHeight = 25.sp * fontScale,
                        fontFamily = fontFamily,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f).padding(start = if (preferences.showAppIcons) 8.dp else 0.dp)
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        if (preferences.showQuote) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = "“${inspirationQuotes[quoteIndex]}”",
                color = secondaryText,
                fontSize = 12.sp * fontScale,
                lineHeight = 17.sp * fontScale,
                fontFamily = fontFamily,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                    .clickable { quoteIndex = (quoteIndex + 1) % inspirationQuotes.size }
                    .padding(vertical = 3.dp)
            )
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
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, color = secondaryText, fontSize = 7.sp * fontScale, letterSpacing = 0.2.sp)
            Text("$percent%", color = primaryText, fontSize = 8.sp * fontScale)
        }
        Box(Modifier.fillMaxWidth().height(1.5.dp).clip(RoundedCornerShape(99.dp)).background(track)) {
            Box(
                Modifier.fillMaxWidth(percent.coerceIn(0, 100) / 100f).height(1.5.dp)
                    .clip(RoundedCornerShape(99.dp)).background(fill)
            )
        }
    }
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
