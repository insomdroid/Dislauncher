package com.insomdroid.dislauncher

import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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
    onLaunch: (LauncherApp) -> Unit,
    onMoveFavorite: (String, String) -> Unit,
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
    var hostResumed by remember(lifecycleOwner) {
        mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
    }
    var now by remember { mutableStateOf(LocalTime.now()) }
    var batteryPercent by remember { mutableStateOf(readBatteryPercent(context)) }
    var batteryCharging by remember { mutableStateOf(readBatteryCharging(context)) }
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
    val homeWallpaperMode = if (
        preferences.wallpaperMode in setOf("image", "solid") && preferences.wallpaperTarget == "lock"
    ) {
        "default"
    } else {
        preferences.wallpaperMode
    }
    val homeWallpaperUri = if (homeWallpaperMode == "image") preferences.wallpaperUri else null
    val wallpaperBitmap by produceState<androidx.compose.ui.graphics.ImageBitmap?>(
        initialValue = null, context, homeWallpaperMode, homeWallpaperUri
    ) {
        value = if (homeWallpaperMode == "solid") null else withContext(Dispatchers.IO) {
            loadWallpaperBitmap(context, homeWallpaperMode, homeWallpaperUri)?.asImageBitmap()
        }
    }
    val primaryText = MaterialTheme.colorScheme.onBackground
    val secondaryText = MaterialTheme.colorScheme.onSurfaceVariant
    val visibleFavorites = remember(apps, favorites) {
        apps.filter { it.packageName in favorites }
    }
    val favoriteAppsByPackage = remember(visibleFavorites) { visibleFavorites.associateBy { it.packageName } }
    var draggingMainItem by remember { mutableStateOf<String?>(null) }
    var dragDistance by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(isActive, hostResumed) {
        if (!isActive || !hostResumed) return@LaunchedEffect

        now = LocalTime.now()
        batteryPercent = readBatteryPercent(context)
        batteryCharging = readBatteryCharging(context)

        while (true) {
            delay(60_000L)
            now = LocalTime.now()
            batteryPercent = readBatteryPercent(context)
            batteryCharging = readBatteryCharging(context)
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
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasLocation) {
            weatherInfo = usableCache
            weatherStatus = if (usableCache == null && preferences.showWeather) "Tap to allow location" else ""
            currentSolarInfoChange(usableCache)
        } else {
            val matchingCache = usableCache?.takeIf {
                it.temperature.endsWith("°${preferences.weatherUnit}") && isWeatherCacheFresh(context)
            }
            if (matchingCache != null) {
                weatherInfo = matchingCache
                weatherStatus = ""
                currentSolarInfoChange(matchingCache)
                return@LaunchedEffect
            }
            weatherInfo = usableCache
            weatherStatus = if (preferences.showWeather) "Loading weather…" else ""
            val fetched = withContext(Dispatchers.IO) { fetchWeather(context, preferences.weatherUnit) }
            weatherInfo = fetched ?: usableCache
            currentSolarInfoChange(fetched)
            weatherStatus = if (fetched == null && preferences.showWeather) "Weather unavailable · tap to retry" else ""
        }
    }

    Column(
        modifier = Modifier.fillMaxSize()
            .drawBehind {
                when {
                    homeWallpaperMode == "default" -> drawRect(if (isDark) Color.Black else Color.White)
                    homeWallpaperMode == "solid" -> drawRect(Color(wallpaperColorInt(preferences.wallpaperColor)))
                    wallpaperBitmap != null -> {
                        drawImage(wallpaperBitmap!!, dstSize = IntSize(size.width.toInt(), size.height.toInt()))
                        drawRect(background.copy(alpha = 0.48f))
                    }
                    else -> drawRect(background)
                }
            }
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
            .padding(horizontal = 26.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.Top
    ) {
        HomeHeader(
            preferences = preferences,
            timeText = timeText,
            dateText = dateText,
            today = today,
            now = now,
            isDark = isDark,
            fontFamily = fontFamily,
            primaryText = primaryText,
            secondaryText = secondaryText,
            onOpenClock = { openClock(context) },
            onOpenCalendar = { openCalendar(context) }
        )

        if (preferences.showWeather || preferences.showBattery || preferences.showCalendarShortcut) {
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
                if (preferences.showWeather || preferences.showCalendarShortcut) {
                    Row(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (preferences.showWeather) {
                            val hasLocation = ContextCompat.checkSelfPermission(
                                context, Manifest.permission.ACCESS_COARSE_LOCATION
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
                        MusicPlayerWidget(
                modifier = Modifier.fillMaxWidth(),
                onOpenMusicApp = { openMusicPlayer(context, apps) }
            )
        }

        val activeMainItemIds = visibleFavorites.map { it.packageName }
        val orderedMainItemIds = remember(activeMainItemIds, homeItemOrder) {
            val existing = homeItemOrder.filter { it in activeMainItemIds }
            (existing + activeMainItemIds.filterNot { it in existing }).distinct()
        }
        val latestOrderedMainItemIds = rememberUpdatedState(orderedMainItemIds)
        val latestMoveHomeItem = rememberUpdatedState(onMoveHomeItem)
        Spacer(Modifier.height(220.dp))
        orderedMainItemIds.forEach { itemId ->
            val app = favoriteAppsByPackage[itemId]
            val label = app?.label.orEmpty()
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
                        .clickable { app?.let(onLaunch) }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (app != null && preferences.showAppIcons) {
                        AppIcon(app, preferences.iconPack, size = 19.dp, iconShape = preferences.iconShape)
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
                            .padding(start = if (app != null && preferences.showAppIcons) 9.dp else 0.dp)
                    )
                }
            }
        }

        if (preferences.showSettingsButton) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onOpenSettings)
                    .padding(7.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Settings,
                    contentDescription = "Settings",
                    tint = secondaryText,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
        Spacer(Modifier.height(14.dp))
    }


}

