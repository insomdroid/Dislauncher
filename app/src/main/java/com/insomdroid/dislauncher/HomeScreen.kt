package com.insomdroid.dislauncher

import android.Manifest
import android.content.pm.PackageManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.DragIndicator
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
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
    preferences: LauncherPreferences,
    cachedWeather: WeatherInfo?,
    isActive: Boolean,
    isDark: Boolean,
    fontFamily: FontFamily,
    onLaunch: (LauncherApp) -> Unit,
    onOpenWeather: (WeatherInfo?) -> Unit,
    onOpenSettings: () -> Unit,
    onDoubleTapToLock: () -> Unit,
    homeItemOrder: List<String>,
    onMoveHomeItem: (String, String) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onRenameApp: (String, String) -> Unit,
    onSolarInfoChange: (WeatherInfo?) -> Unit
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val homeVerticalGap = (configuration.screenHeightDp.dp * 0.28f).coerceIn(120.dp, 225.dp)
    val lifecycleOwner = LocalLifecycleOwner.current
    val haptic = LocalHapticFeedback.current
    val currentOpenSettings by rememberUpdatedState(onOpenSettings)
    val currentLockScreen by rememberUpdatedState(onDoubleTapToLock)
    val currentSolarInfoChange by rememberUpdatedState(onSolarInfoChange)
    var hostResumed by remember(lifecycleOwner) {
        mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
    }
    var now by remember { mutableStateOf(LocalTime.now()) }
    var batteryPercent by remember { mutableStateOf<Int?>(null) }
    var batteryCharging by remember { mutableStateOf(false) }
    var networkLabel by remember { mutableStateOf(readNetworkLabel(context)) }
    var weatherInfo by remember { mutableStateOf(cachedWeather) }
    var weatherStatus by remember { mutableStateOf("Tap to allow location") }
    val today = LocalDate.now()
    val systemTimeFormat = android.text.format.DateFormat.is24HourFormat(context)
    val timePattern = when (preferences.timeFormat) {
        "12" -> "h:mm a"
        "24" -> "HH:mm"
        else -> if (systemTimeFormat) "HH:mm" else "h:mm a"
    }
    val timeText = now.format(DateTimeFormatter.ofPattern(timePattern, Locale.getDefault()))
    val dateText = formatHomeDate(today)
    val background = if (preferences.wallpaperMode == "image") androidx.compose.ui.graphics.Color.Transparent else MaterialTheme.colorScheme.background
    val primaryText = MaterialTheme.colorScheme.onBackground
    val secondaryText = MaterialTheme.colorScheme.onSurfaceVariant
    val activeNotificationPackages = NotificationDotState.activePackages
    val visibleFavorites = remember(apps, favorites) {
        apps.filter { it.packageName in favorites }
    }
    val favoriteAppsByPackage = remember(visibleFavorites) { visibleFavorites.associateBy { it.packageName } }
    var draggingMainItem by remember { mutableStateOf<String?>(null) }
    var dragDistance by remember { mutableFloatStateOf(0f) }
    var selectedFavoriteMenu by remember { mutableStateOf<String?>(null) }
    var movingFavoriteId by remember { mutableStateOf<String?>(null) }
    var renamingFavorite by remember { mutableStateOf<LauncherApp?>(null) }
    var favoriteRenameValue by remember { mutableStateOf("") }

    LaunchedEffect(isActive, hostResumed, preferences.showBattery) {
        if (!isActive || !hostResumed) return@LaunchedEffect

        now = LocalTime.now()
        networkLabel = readNetworkLabel(context)
        if (preferences.showBattery) {
            val batteryState = readBatteryState(context)
            batteryPercent = batteryState.percent
            batteryCharging = batteryState.isCharging
        } else {
            batteryPercent = null
            batteryCharging = false
        }

        while (true) {
            val delayUntilNextMinute = 60_000L - (System.currentTimeMillis() % 60_000L)
            delay(delayUntilNextMinute.coerceAtLeast(1L))
            now = LocalTime.now()
            networkLabel = readNetworkLabel(context)
            if (preferences.showBattery) {
                val batteryState = readBatteryState(context)
                batteryPercent = batteryState.percent
                batteryCharging = batteryState.isCharging
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
            if (shouldBackoffWeatherFetch(context)) {
                weatherInfo = usableCache
                weatherStatus = if (usableCache == null) {
                    "Weather unavailable · tap to retry"
                } else {
                    ""
                }
                return@LaunchedEffect
            }
            weatherInfo = usableCache
            weatherStatus = if (preferences.showWeather) "Loading weather…" else ""
            val fetched = withContext(Dispatchers.IO) { fetchWeather(context, preferences.weatherUnit) }
            recordWeatherFetchResult(context, succeeded = fetched != null)
            weatherInfo = fetched ?: usableCache
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
                    if (preferences.longPressHomeOpensSettings) {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        currentOpenSettings()
                    }
                },
                onDoubleClick = {
                    if (preferences.doubleTapToLock) currentLockScreen()
                }
            )
            .padding(start = HomeDesign.horizontalInset, top = HomeDesign.topInset, end = HomeDesign.horizontalInset, bottom = HomeDesign.bottomInset),
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

        val showStatusWidgets = preferences.showWeather || preferences.showBattery || preferences.showNetworkInfo || preferences.showCalendarShortcut
        if (showStatusWidgets) {
            Spacer(Modifier.height(HomeDesign.headerToStatus))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val statusParts = buildList<Pair<String, Boolean>> {
                    if (preferences.showBattery) {
                        add(((if (batteryCharging) "+" else "") + (batteryPercent?.let { "$it%" } ?: "--%")) to false)
                    }
                    if (preferences.showNetworkInfo) add(networkLabel to false)
                    if (preferences.showWeather) {
                        val hasLocation = ContextCompat.checkSelfPermission(
                            context, Manifest.permission.ACCESS_COARSE_LOCATION
                        ) == PackageManager.PERMISSION_GRANTED
                        val weatherText = weatherInfo?.let {
                            "${it.temperature} ${it.condition.lowercase(Locale.getDefault())}"
                        } ?: weatherStatus.ifBlank {
                            if (!hasLocation) "Tap to allow location" else ""
                        }
                        if (weatherText.isNotBlank()) add(weatherText to true)
                    }
                }
                statusParts.forEach { part ->
                    Text(
                        part.first,
                        color = primaryText,
                        fontSize = 12.sp,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                        modifier = if (part.second) {
                            Modifier.widthIn(max = 100.dp).clickable { onOpenWeather(weatherInfo ?: cachedWeather) }
                        } else {
                            Modifier.widthIn(max = 64.dp)
                        }
                    )
                }
                if (preferences.showCalendarShortcut) {
                    CalendarAgenda(
                        preferences.timeFormat,
                        compact = true,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        if (preferences.showMusic) {
            if (showStatusWidgets) Spacer(Modifier.height(HomeDesign.statusToMusic))
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
        Spacer(Modifier.height(homeVerticalGap))
        orderedMainItemIds.forEach { itemId ->
            val app = favoriteAppsByPackage[itemId]
            val label = app?.label.orEmpty()
            key(itemId) {
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .alpha(if (draggingMainItem == itemId) 0.62f else 1f)
                        .combinedClickable(
                            onClick = { app?.let(onLaunch) },
                            onLongClick = {
                                if (app != null) {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    movingFavoriteId = null
                                    selectedFavoriteMenu = itemId
                                }
                            }
                        )
                        .padding(vertical = HomeDesign.favoriteVerticalPadding),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (app != null && preferences.showAppIcons) {
                        AppIcon(app, preferences.iconPack, size = HomeDesign.appIconSize, iconShape = preferences.iconShape)
                    }
                    Text(
                        text = label,
                        color = primaryText,
                        fontSize = HomeDesign.favoriteSize,
                        lineHeight = HomeDesign.favoriteLineHeight,
                        fontFamily = fontFamily,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                            .padding(start = if (app != null && preferences.showAppIcons) HomeDesign.appIconLabelGap else 0.dp)
                    )
                    if (app != null && preferences.notificationDotsEnabled &&
                        app.packageName in preferences.notificationDotPackages &&
                        app.packageName in activeNotificationPackages
                    ) {
                        val notificationDotColor =
                            accentColorForPreset(preferences.notificationDotColor)
                                ?: MaterialTheme.colorScheme.primary
                        val notificationCount = NotificationDotState.countFor(app.packageName)
                        if (notificationCount > 1) {
                            Box(
                                modifier = Modifier.padding(start = 6.dp)
                                    .widthIn(min = HomeDesign.notificationBadgeHeight).height(HomeDesign.notificationBadgeHeight)
                                    .background(notificationDotColor, RoundedCornerShape(9.dp))
                                    .padding(horizontal = 4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (notificationCount > 99) "99+" else notificationCount.toString(),
                                    color = if (preferences.notificationDotColor == "yellow" || preferences.notificationDotColor == "orange") androidx.compose.ui.graphics.Color(0xFF111111) else MaterialTheme.colorScheme.onPrimary,
                                    fontSize = 10.sp,
                                    lineHeight = 12.sp,
                                    maxLines = 1
                                )
                            }
                        } else {
                            Box(
                                modifier = Modifier.padding(start = 6.dp)
                                    .size(HomeDesign.notificationDotSize)
                                    .background(notificationDotColor, CircleShape)
                            )
                        }
                    }
                    if (movingFavoriteId == itemId) {
                        Icon(
                            imageVector = Icons.Outlined.DragIndicator,
                            contentDescription = "Reorder favourite",
                            tint = secondaryText.copy(alpha = 0.82f),
                            modifier = Modifier.padding(start = 8.dp).size(20.dp)
                                .pointerInput(itemId) {
                                    detectDragGestures(
                                        onDragStart = {
                                            draggingMainItem = itemId
                                            dragDistance = 0f
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        },
                                        onDragEnd = {
                                            draggingMainItem = null
                                            dragDistance = 0f
                                            movingFavoriteId = null
                                        },
                                        onDragCancel = {
                                            draggingMainItem = null
                                            dragDistance = 0f
                                            movingFavoriteId = null
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
                        )
                    }
                    Box {
                        DropdownMenu(
                            expanded = selectedFavoriteMenu == itemId,
                            onDismissRequest = { selectedFavoriteMenu = null }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Open") },
                                onClick = { selectedFavoriteMenu = null; app?.let(onLaunch) }
                            )
                            if (app != null) {
                                DropdownMenuItem(
                                    text = { Text("Rename") },
                                    onClick = {
                                        favoriteRenameValue = app.label
                                        renamingFavorite = app
                                        selectedFavoriteMenu = null
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Remove from Favourites") },
                                    onClick = {
                                        selectedFavoriteMenu = null
                                        movingFavoriteId = null
                                        onToggleFavorite(app.packageName)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Move") },
                                    onClick = {
                                        selectedFavoriteMenu = null
                                        movingFavoriteId = itemId
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("App info") },
                                    onClick = {
                                        selectedFavoriteMenu = null
                                        movingFavoriteId = null
                                        openApplicationInfo(context, app)
                                    }
                                )
                                if (!app.isSystem) {
                                    DropdownMenuItem(
                                        text = { Text("Uninstall") },
                                        onClick = {
                                            selectedFavoriteMenu = null
                                            movingFavoriteId = null
                                            requestUninstall(context, app)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        renamingFavorite?.let { appToRename ->
            AlertDialog(
                onDismissRequest = { renamingFavorite = null },
                title = { Text("Rename app") },
                text = {
                    OutlinedTextField(
                        value = favoriteRenameValue,
                        onValueChange = { favoriteRenameValue = it },
                        singleLine = true,
                        label = { Text("App name") }
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            onRenameApp(appToRename.packageName, favoriteRenameValue)
                            renamingFavorite = null
                        }
                    ) { Text("Save") }
                },
                dismissButton = {
                    TextButton(onClick = { renamingFavorite = null }) { Text("Cancel") }
                }
            )
        }

        if (preferences.showSettingsButton) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onOpenSettings)
                    .padding(horizontal = 7.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Outlined.Settings,
                    contentDescription = "Dislauncher Settings",
                    tint = secondaryText,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = "Dislauncher Settings",
                    color = secondaryText,
                    fontSize = 14.sp,
                    fontFamily = fontFamily,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Spacer(Modifier.height(14.dp))
    }
}


private fun readNetworkLabel(context: Context): String {
    val connectivity = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        ?: return "Mobile"
    val network = connectivity.activeNetwork ?: return "Offline"
    val capabilities = connectivity.getNetworkCapabilities(network) ?: return "Offline"
    if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return "Wi-Fi"
    if (!capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) return "Offline"

    // Avoid READ_PHONE_STATE: the launcher only needs a privacy-friendly
    // indication of the active transport, not the cellular radio generation.
    return "Mobile"
}
