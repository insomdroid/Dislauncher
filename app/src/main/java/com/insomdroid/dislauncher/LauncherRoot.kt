package com.insomdroid.dislauncher

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.app.WallpaperManager
import android.os.Build
import android.content.ComponentName
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.positionChangeIgnoreConsumed
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

private const val MAX_PINNED_APPS = 6


private const val MAX_CUSTOM_WALLPAPER_BYTES = 10L * 1024L * 1024L
private const val MAX_CUSTOM_WALLPAPER_SOURCE_PIXELS = 250_000_000L
private const val MAX_CUSTOM_WALLPAPER_DECODED_PIXELS = 6_000_000L

/**
 * Decode custom wallpapers at a bounded size. Compressed file size is not a reliable
 * estimate of bitmap memory use, so inspect dimensions before decoding the pixels.
 */
private fun decodeWallpaperForScreen(
    context: Context,
    uri: Uri
): androidx.compose.ui.graphics.ImageBitmap? {
    val fileLength = runCatching {
        context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length }
    }.getOrNull()
    if (fileLength != null && fileLength > MAX_CUSTOM_WALLPAPER_BYTES) return null

    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    val boundsStream = context.contentResolver.openInputStream(uri) ?: return null
    boundsStream.use { input -> BitmapFactory.decodeStream(input, null, bounds) }

    val width = bounds.outWidth
    val height = bounds.outHeight
    if (width <= 0 || height <= 0) return null
    if (width.toLong() * height.toLong() > MAX_CUSTOM_WALLPAPER_SOURCE_PIXELS) return null

    val metrics = context.resources.displayMetrics
    val targetWidth = metrics.widthPixels.coerceAtLeast(1)
    val targetHeight = metrics.heightPixels.coerceAtLeast(1)
    var sampleSize = 1
    while (
        (width.toLong() / sampleSize) * (height.toLong() / sampleSize) >
            MAX_CUSTOM_WALLPAPER_DECODED_PIXELS ||
        width / sampleSize > targetWidth * 2 ||
        height / sampleSize > targetHeight * 2
    ) {
        sampleSize *= 2
    }

    val options = BitmapFactory.Options().apply {
        inSampleSize = sampleSize
        inPreferredConfig = android.graphics.Bitmap.Config.ARGB_8888
    }
    val bitmap = context.contentResolver.openInputStream(uri)?.use { input ->
        BitmapFactory.decodeStream(input, null, options)
    } ?: return null
    return bitmap.asImageBitmap()
}


@OptIn(ExperimentalTextApi::class)
@Composable
internal fun DislauncherRoot() {
    val context = LocalContext.current
    val view = LocalView.current
    val baseDensity = LocalDensity.current
    val prefsStore = remember {
        context.getSharedPreferences("dislauncher_preferences", Context.MODE_PRIVATE)
    }
    var preferences by remember { mutableStateOf(LauncherPreferences.load(prefsStore)) }
    var favorites by remember {
        mutableStateOf(
            prefsStore.getStringSet("favorite_packages", emptySet())
                .orEmpty()
                .sorted()
                .take(MAX_PINNED_APPS)
                .toSet()
        )
    }
    LaunchedEffect(Unit) {
        val savedFavorites = prefsStore.getStringSet("favorite_packages", emptySet()).orEmpty()
        if (savedFavorites.size > MAX_PINNED_APPS) {
            prefsStore.edit().putStringSet("favorite_packages", favorites.toMutableSet()).apply()
        }
    }
    var appNameOverrides by remember { mutableStateOf(readAppNameOverrides(prefsStore)) }
    val initialWeatherCache = remember { loadWeatherCache(context, preferences.weatherUnit) }
    var solarWeather by remember { mutableStateOf<WeatherInfo?>(initialWeatherCache) }
    var weatherForScreen by remember { mutableStateOf<WeatherInfo?>(initialWeatherCache) }
    var homeItemOrder by remember { mutableStateOf(readHomeItemOrder(prefsStore, favorites)) }
    var hiddenApps by remember {
        mutableStateOf(prefsStore.getStringSet("hidden_packages", emptySet())?.toSet().orEmpty())
    }
    var favoriteOrder by remember { mutableStateOf(readFavoriteOrder(prefsStore, favorites)) }
    var apps by remember { mutableStateOf<List<LauncherApp>>(emptyList()) }
    var searchQuery by remember { mutableStateOf("") }
    var screen by remember {
        mutableStateOf(
            if (prefsStore.getBoolean("onboarding_completed", false)) {
                LauncherScreen.HOME
            } else {
                LauncherScreen.ONBOARDING
            }
        )
    }
    var settingsOpenedFromApps by remember { mutableStateOf(false) }
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { 2 })
    val scope = rememberCoroutineScope()
    val iconPacks = remember { findIconPacks(context) }

    fun updatePreferences(next: LauncherPreferences) {
        val notificationDotSettingsChanged =
            preferences.notificationDotsEnabled != next.notificationDotsEnabled ||
                preferences.notificationDotPackages != next.notificationDotPackages
        preferences = next
        next.save(prefsStore)
        if (notificationDotSettingsChanged) NotificationDotState.refresh()
    }

    val wallpaperPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val isWithinLimit = withContext(Dispatchers.IO) {
                    runCatching {
                        val knownLength = context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length }
                        if (knownLength != null && knownLength > MAX_CUSTOM_WALLPAPER_BYTES) {
                            false
                        } else {
                            context.contentResolver.openInputStream(uri)?.use { input ->
                                val buffer = ByteArray(8192)
                                var total = 0L
                                while (total <= MAX_CUSTOM_WALLPAPER_BYTES) {
                                    val read = input.read(buffer, 0, minOf(buffer.size.toLong(), MAX_CUSTOM_WALLPAPER_BYTES + 1L - total).toInt())
                                    if (read < 0) break
                                    total += read
                                }
                                total <= MAX_CUSTOM_WALLPAPER_BYTES
                            } ?: false
                        }
                    }.getOrDefault(false)
                }
                if (!isWithinLimit) {
                    android.widget.Toast.makeText(
                        context,
                        "Wallpaper must be an image no larger than 10 MB.",
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                    return@launch
                }
                runCatching {
                    context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                updatePreferences(preferences.copy(wallpaperMode = "image", wallpaperUri = uri.toString()))
            }
        }
    }

    val customFontPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val selectedFontName = withContext(Dispatchers.IO) {
                    runCatching { importUserCustomFont(context, uri) }.getOrNull()
                }
                if (selectedFontName == null) {
                    android.widget.Toast.makeText(
                        context,
                        "Invalid font. Choose a valid .ttf or .otf file up to 5 MB.",
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                } else {
                    updatePreferences(
                        preferences.copy(fontMode = "custom", customFontName = selectedFontName)
                    )
                }
            }
        }
    }

    fun toggleFavorite(packageName: String) {
        if (packageName !in favorites && favorites.size >= MAX_PINNED_APPS) {
            android.widget.Toast.makeText(
                context,
                "You can pin up to 6 apps.",
                android.widget.Toast.LENGTH_SHORT
            ).show()
            return
        }
        val next = if (packageName in favorites) favorites - packageName else favorites + packageName
        favorites = next
        val nextOrder = if (packageName in next) {
            (favoriteOrder.filter { it in next } + packageName).distinct()
        } else {
            favoriteOrder.filter { it in next }
        }
        favoriteOrder = nextOrder
        homeItemOrder = if (packageName in next) {
            (homeItemOrder + packageName).distinct()
        } else {
            homeItemOrder.filterNot { it == packageName }
        }
        prefsStore.edit().putStringSet("favorite_packages", next.toMutableSet()).apply()
        saveFavoriteOrder(prefsStore, nextOrder)
        saveHomeItemOrder(prefsStore, homeItemOrder)
    }

    fun finishOnboarding(selectedPackages: Set<String>) {
        scope.launch {
            val allApps = if (apps.isNotEmpty()) {
                apps
            } else {
                withContext(Dispatchers.IO) { loadLauncherApps(context) }.map { app ->
                    appNameOverrides[app.packageName]?.let { app.copy(label = it) } ?: app
                }.also { apps = it }
            }
            val orderedAvailable = (allApps.filterNot { it.isSystem } + allApps.filter { it.isSystem })
                .distinctBy { it.packageName }
            val selected = orderedAvailable.filter { it.packageName in selectedPackages }
                .take(MAX_PINNED_APPS)
                .map { it.packageName }
            val selectedSet = selected.toSet()
            favorites = selectedSet
            favoriteOrder = selected
            val allAppPackages = allApps.map { it.packageName }.toSet()
            homeItemOrder = (homeItemOrder.filterNot { it in allAppPackages } + selected).distinct()
            saveFavoriteOrder(prefsStore, selected)
            saveHomeItemOrder(prefsStore, homeItemOrder)
            prefsStore.edit()
                .putStringSet("favorite_packages", selectedSet.toMutableSet())
                .putBoolean("onboarding_completed", true)
                .apply()
            screen = LauncherScreen.HOME
        }
    }

    fun moveHomeItem(sourceId: String, targetId: String) {
        if (sourceId == targetId) return
        val order = (homeItemOrder + favorites).distinct().toMutableList()
        val sourceIndex = order.indexOf(sourceId)
        val targetIndex = order.indexOf(targetId)
        if (sourceIndex < 0 || targetIndex < 0) return
        val moving = order[sourceIndex]
        order[sourceIndex] = order[targetIndex]
        order[targetIndex] = moving
        homeItemOrder = order
        saveHomeItemOrder(prefsStore, order)
    }

    fun renameApp(packageName: String, requestedName: String) {
        val newName = requestedName.trim().take(60)
        if (newName.isBlank()) return
        appNameOverrides = appNameOverrides + (packageName to newName)
        prefsStore.edit().putString("renamed_app_labels_json", JSONObject(appNameOverrides).toString()).apply()
        apps = apps.map { if (it.packageName == packageName) it.copy(label = newName) else it }
    }

    fun moveFavorite(sourcePackage: String, targetPackage: String) {
        val order = readFavoriteOrder(prefsStore, favorites).toMutableList()
        val sourceIndex = order.indexOf(sourcePackage)
        val targetIndex = order.indexOf(targetPackage)
        if (sourceIndex < 0 || targetIndex < 0 || sourceIndex == targetIndex) return
        val moving = order[sourceIndex]
        order[sourceIndex] = order[targetIndex]
        order[targetIndex] = moving
        favoriteOrder = order
        saveFavoriteOrder(prefsStore, order)
    }

    fun toggleHidden(packageName: String) {
        val next = if (packageName in hiddenApps) hiddenApps - packageName else hiddenApps + packageName
        hiddenApps = next
        prefsStore.edit().putStringSet("hidden_packages", next.toMutableSet()).apply()
    }

    val adminComponent = remember { ComponentName(context, DislauncherAdminReceiver::class.java) }
    val devicePolicyManager = remember {
        context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    }
    val adminRequest = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        val active = runCatching { devicePolicyManager.isAdminActive(adminComponent) }.getOrDefault(false)
        updatePreferences(preferences.copy(doubleTapToLock = active))
    }

    fun changeDoubleTapLock(enabled: Boolean) {
        if (!enabled) {
            // Disabling the gesture should not revoke device-admin access automatically.
            updatePreferences(preferences.copy(doubleTapToLock = false))
            return
        }
        if (devicePolicyManager.isAdminActive(adminComponent)) {
            updatePreferences(preferences.copy(doubleTapToLock = true))
        } else {
            val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
                .putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent)
                .putExtra(
                    DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                    "Allow dislauncher to lock the screen when you double-tap an empty area of Home."
                )
            adminRequest.launch(intent)
        }
    }

    LaunchedEffect(Unit) {
        apps = withContext(Dispatchers.IO) { loadLauncherApps(context) }.map { app ->
            appNameOverrides[app.packageName]?.let { app.copy(label = it) } ?: app
        }
    }

    DisposableEffect(context, appNameOverrides) {
        // Refresh only when the installed launcher-app set changes. Re-scanning all
        // launcher activities on every ON_RESUME caused unnecessary work on app return.
        var refreshJob: Job? = null
        val packageFilter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        val packageReceiver = object : BroadcastReceiver() {
            override fun onReceive(receiverContext: Context?, intent: Intent?) {
                refreshJob?.cancel()
                refreshJob = scope.launch {
                    // Package installs can emit multiple related broadcasts in quick succession.
                    delay(150L)
                    val refreshed = withContext(Dispatchers.IO) {
                        loadLauncherApps(context)
                    }.map { app ->
                        appNameOverrides[app.packageName]?.let { app.copy(label = it) } ?: app
                    }
                    // Preserve the current list instance when the visible app data is unchanged.
                    if (apps != refreshed) apps = refreshed
                }
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(packageReceiver, packageFilter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            context.registerReceiver(packageReceiver, packageFilter)
        }
        onDispose {
            refreshJob?.cancel()
            context.unregisterReceiver(packageReceiver)
        }
    }

    LaunchedEffect(pagerState.settledPage) {
        if (screen == LauncherScreen.HOME || screen == LauncherScreen.APPS) {
            screen = if (pagerState.settledPage == 0) LauncherScreen.HOME else LauncherScreen.APPS
        }
    }
    LaunchedEffect(screen) {
        if (screen == LauncherScreen.HOME || screen == LauncherScreen.APPS) {
            val wantedPage = if (screen == LauncherScreen.HOME) 0 else 1
            if (pagerState.currentPage != wantedPage) pagerState.animateScrollToPage(wantedPage)
        }
    }

    BackHandler(
        enabled = screen == LauncherScreen.SETTINGS || screen == LauncherScreen.WEATHER ||
            pagerState.settledPage == 1
    ) {
        when (screen) {
            LauncherScreen.SETTINGS ->
                screen = if (settingsOpenedFromApps) LauncherScreen.APPS else LauncherScreen.HOME
            LauncherScreen.WEATHER -> screen = LauncherScreen.HOME
            LauncherScreen.APPS -> scope.launch { pagerState.animateScrollToPage(0) }
            else -> Unit
        }
    }
    BackHandler(enabled = screen == LauncherScreen.ONBOARDING) { }

    val systemDark = androidx.compose.foundation.isSystemInDarkTheme()
    val isDark = when (preferences.themeMode) {
        "light" -> false
        "dark", "black" -> true
        else -> systemDark
    }
    val visibleApps = remember(apps, preferences.hideSystemApps, hiddenApps) {
        apps.filter { (!preferences.hideSystemApps || !it.isSystem) && it.packageName !in hiddenApps }
    }
    val drawerApps = remember(visibleApps) {
        visibleApps + listOf(
            LauncherApp("internal:calculator", "", "Calculator", true),
            LauncherApp("internal:calendar", "", "Calendar", true),
            LauncherApp("internal:notes", "", "Notes", true)
        )
    }
    val wallpaperImage = produceState<androidx.compose.ui.graphics.ImageBitmap?>(
        initialValue = null,
        key1 = preferences.wallpaperUri
    ) {
        val savedUri = preferences.wallpaperUri?.let(Uri::parse)
        val decoded = withContext(Dispatchers.IO) {
            savedUri?.let { uri -> runCatching { decodeWallpaperForScreen(context, uri) }.getOrNull() }
        }
        value = decoded
        // Never leave a bad wallpaper URI saved: otherwise every launcher restart could
        // decode the same problematic image again and repeatedly crash or exhaust memory.
        if (savedUri != null && decoded == null && preferences.wallpaperUri == savedUri.toString()) {
            updatePreferences(LauncherPreferences.load(prefsStore).copy(wallpaperMode = "default", wallpaperUri = null))
            android.widget.Toast.makeText(
                context,
                "Wallpaper couldn't be loaded safely. The default wallpaper has been restored.",
                android.widget.Toast.LENGTH_LONG
            ).show()
        }
    }

    SideEffect {
        val activity = context as? Activity
        if (activity != null) {
            val controller: WindowInsetsControllerCompat =
                WindowCompat.getInsetsController(activity.window, view)
            controller.isAppearanceLightStatusBars = !isDark
            controller.isAppearanceLightNavigationBars = !isDark
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            if (preferences.hideStatusBar) controller.hide(WindowInsetsCompat.Type.statusBars())
            else controller.show(WindowInsetsCompat.Type.statusBars())
            controller.show(WindowInsetsCompat.Type.navigationBars())
        }
    }

    DislauncherTheme(
        preferences = preferences,
        isDark = isDark,
        baseDensity = baseDensity
    ) { fontFamily ->
        if (screen == LauncherScreen.ONBOARDING) {
            WelcomeOnboardingScreen(
                preferences = preferences,
                onPreferencesChange = ::updatePreferences,
                apps = apps,
                favorites = favorites,
                onFinish = ::finishOnboarding
            )
        } else if (screen == LauncherScreen.SETTINGS) {
            SettingsScreen(
                preferences = preferences,
                apps = apps,
                favorites = favorites,
                hiddenApps = hiddenApps,
                iconPacks = iconPacks,
                onPreferencesChange = ::updatePreferences,
                onChooseCustomFont = { customFontPicker.launch(arrayOf("*/*")) },
                onChooseWallpaper = { wallpaperPicker.launch(arrayOf("image/*")) },
                onApplyWallpaperToSystem = {
                    val selectedWallpaper = preferences.wallpaperUri?.let(Uri::parse)
                    if (selectedWallpaper == null) {
                        android.widget.Toast.makeText(
                            context,
                            "Choose a custom wallpaper first.",
                            android.widget.Toast.LENGTH_LONG
                        ).show()
                    } else {
                        scope.launch {
                            val applied = withContext(Dispatchers.IO) {
                                runCatching {
                                    val stream = context.contentResolver.openInputStream(selectedWallpaper)
                                        ?: error("Unable to open the selected wallpaper.")
                                    stream.use {
                                        WallpaperManager.getInstance(context).setStream(
                                            it,
                                            null,
                                            true,
                                            WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK
                                        )
                                    }
                                }.isSuccess
                            }
                            android.widget.Toast.makeText(
                                context,
                                if (applied) {
                                    "Wallpaper applied to the device Home screen and lock screen."
                                } else {
                                    "Unable to apply the wallpaper. Please try another image."
                                },
                                android.widget.Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                },
                onDoubleTapLockChange = ::changeDoubleTapLock,
                onToggleFavorite = ::toggleFavorite,
                onToggleHidden = ::toggleHidden,
                onBack = { screen = if (settingsOpenedFromApps) LauncherScreen.APPS else LauncherScreen.HOME }
            )
        } else if (screen == LauncherScreen.WEATHER) {
            WeatherScreen(
                weatherUnit = preferences.weatherUnit,
                cachedWeather = weatherForScreen ?: solarWeather,
                onWeatherChange = { updated ->
                    if (updated != null) {
                        val normalized = convertWeatherUnit(updated, preferences.weatherUnit)
                        solarWeather = normalized
                        weatherForScreen = normalized
                        saveWeatherCache(context, normalized)
                    }
                },
                onBack = { screen = LauncherScreen.HOME }
            )
        } else {
            val notificationsGestureEnabled =
                screen == LauncherScreen.HOME && pagerState.currentPage == 0 && preferences.swipeDownNotifications
            Box(
                modifier = Modifier.fillMaxSize()
                    .background(
                        if (preferences.wallpaperMode == "image" && wallpaperImage.value != null) Color.Transparent
                        else MaterialTheme.colorScheme.background
                    )
                    .pointerInput(notificationsGestureEnabled) {
                        if (!notificationsGestureEnabled) return@pointerInput
                        val triggerDistance = 100.dp.toPx()
                        val horizontalSlop = 24.dp.toPx()
                        awaitEachGesture {
                            val down = awaitFirstDown(
                                pass = PointerEventPass.Initial,
                                requireUnconsumed = false
                            )
                            var totalX = 0f
                            var totalY = 0f
                            var horizontalGesture = false
                            var triggered = false
                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) break

                                val delta = change.positionChangeIgnoreConsumed()
                                totalX += delta.x
                                totalY += delta.y

                                if (
                                    kotlin.math.abs(totalX) > horizontalSlop &&
                                    kotlin.math.abs(totalX) > kotlin.math.abs(totalY) * 1.15f
                                ) {
                                    horizontalGesture = true
                                }

                                if (!horizontalGesture && !triggered && totalY >= triggerDistance) {
                                    triggered = true
                                    expandNotificationPanel(context)
                                }
                                if (triggered) change.consume()
                            }
                        }
                    }
            ) {
                if (preferences.wallpaperMode == "image") {
                    wallpaperImage.value?.let { bitmap ->
                        Image(
                            bitmap = bitmap,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                    beyondViewportPageCount = 1,
                    pageSpacing = 0.dp
                ) { page ->
                    if (page == 0) {
                        HomeScreen(
                            apps = visibleApps,
                            favorites = favorites,
                            favoriteOrder = favoriteOrder,
                            preferences = preferences,
                            cachedWeather = solarWeather,
                            isActive = page == pagerState.currentPage,
                            isDark = isDark,
                            fontFamily = fontFamily,
                            onLaunch = { launchApp(context, it) },
                            onMoveFavorite = ::moveFavorite,
                            onOpenWeather = { cached -> weatherForScreen = cached ?: solarWeather; screen = LauncherScreen.WEATHER },
                            onOpenSettings = { settingsOpenedFromApps = false; screen = LauncherScreen.SETTINGS },
                            onDoubleTapToLock = { lockDevice(context, adminComponent, devicePolicyManager) },
                            homeItemOrder = homeItemOrder,
                            onMoveHomeItem = ::moveHomeItem,
                            onSolarInfoChange = { updated ->
                                if (updated != null) {
                                    val normalized = convertWeatherUnit(updated, preferences.weatherUnit)
                                    solarWeather = normalized
                                    saveWeatherCache(context, normalized)
                                }
                            }
                        )
                    } else {
                        AppsScreen(
                            apps = drawerApps,
                            favorites = favorites,
                            query = searchQuery,
                            onQueryChange = { searchQuery = it },
                            preferences = preferences,
                            isDark = isDark,
                            isActive = page == pagerState.currentPage,
                            fontFamily = fontFamily,
                            onLaunch = { app ->
                                when (app.packageName) {
                                    "internal:notes" -> launchBuiltInTool(context, BuiltInTool.NOTES)
                                    "internal:calculator" -> launchBuiltInTool(context, BuiltInTool.CALCULATOR)
                                    "internal:calendar" -> launchBuiltInTool(context, BuiltInTool.CALENDAR)
                                    else -> launchApp(context, app)
                                }
                            },
                            onToggleFavorite = ::toggleFavorite,
                            onToggleHidden = ::toggleHidden,
                            onRenameApp = ::renameApp,
                            onOpenSettings = { settingsOpenedFromApps = true; screen = LauncherScreen.SETTINGS }
                        )
                    }
                }
            }
        }
    }
}
