package com.insomdroid.dislauncher

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Typeface
import android.net.Uri
import android.content.pm.ApplicationInfo
import android.os.Build
import android.os.Debug
import android.util.Log
import android.provider.Settings
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.positionChangeIgnoreConsumed
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import org.json.JSONObject

internal enum class LauncherScreen { ONBOARDING, HOME, APPS, SETTINGS, TASKS, WEATHER }

internal data class LauncherApp(
    val packageName: String,
    val activityName: String,
    val label: String,
    val isSystem: Boolean
)

private const val MAX_PINNED_APPS = 5

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        setContent { DislauncherRoot() }
    }

    override fun onResume() {
        super.onResume()
        applyStatusBarPreference()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) applyStatusBarPreference()
    }

    private fun applyStatusBarPreference() {
        val hidden = getSharedPreferences("dislauncher_preferences", Context.MODE_PRIVATE)
            .getBoolean("hide_status_bar", false)
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (hidden) controller.hide(WindowInsetsCompat.Type.statusBars())
        else controller.show(WindowInsetsCompat.Type.statusBars())
    }
}

@OptIn(ExperimentalTextApi::class)
@Composable
private fun DislauncherRoot() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
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
    var tasks by remember { mutableStateOf(readLauncherTasks(prefsStore)) }
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
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { 2 })
    val scope = rememberCoroutineScope()
    val iconPacks = remember { findIconPacks(context) }

    fun updatePreferences(next: LauncherPreferences) {
        preferences = next
        next.save(prefsStore)
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
                "You can pin up to 5 apps.",
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

    fun finishOnboarding(selectedPackages: Set<String>?, useDefaultFavorites: Boolean) {
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
            val selected = if (useDefaultFavorites) {
                orderedAvailable.take(MAX_PINNED_APPS).map { it.packageName }
            } else {
                orderedAvailable.filter { it.packageName in selectedPackages.orEmpty() }
                    .take(MAX_PINNED_APPS)
                    .map { it.packageName }
            }
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
        val order = (homeItemOrder + listOf(HOME_ITEM_TASKS, HOME_ITEM_CALLS, HOME_ITEM_MUSIC) + favorites).distinct().toMutableList()
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

    fun updateTasks(next: List<LauncherTask>) {
        tasks = next
        saveLauncherTasks(prefsStore, next)
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
            updatePreferences(preferences.copy(doubleTapToLock = false))
            runCatching {
                if (devicePolicyManager.isAdminActive(adminComponent)) {
                    devicePolicyManager.removeActiveAdmin(adminComponent)
                }
            }
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

    DisposableEffect(lifecycleOwner, context, appNameOverrides) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                scope.launch {
                    val refreshed = withContext(Dispatchers.IO) { loadLauncherApps(context) }.map { app ->
                        appNameOverrides[app.packageName]?.let { app.copy(label = it) } ?: app
                    }
                    apps = refreshed
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
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

    LaunchedEffect(pagerState.settledPage, screen) {
        if ((context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) == 0 ||
            (screen != LauncherScreen.HOME && screen != LauncherScreen.APPS)
        ) {
            return@LaunchedEffect
        }
        repeat(3) { sample ->
            delay(if (sample == 0) 250L else 500L)
            val runtime = Runtime.getRuntime()
            val javaUsedBytes = runtime.totalMemory() - runtime.freeMemory()
            Log.d(
                "DislauncherMemory",
                "page=${if (pagerState.settledPage == 0) "HOME" else "APP_DRAWER"} " +
                    "sample=${sample + 1}/3 javaUsedBytes=$javaUsedBytes " +
                    "javaHeapMaxBytes=${runtime.maxMemory()} " +
                    "nativeHeapAllocatedBytes=${Debug.getNativeHeapAllocatedSize()} " +
                    "processPssKb=${Debug.getPss()}"
            )
        }
    }

    BackHandler(
        enabled = screen == LauncherScreen.SETTINGS || screen == LauncherScreen.TASKS ||
            screen == LauncherScreen.WEATHER || pagerState.settledPage == 1
    ) {
        when (screen) {
            LauncherScreen.SETTINGS, LauncherScreen.TASKS, LauncherScreen.WEATHER ->
                screen = LauncherScreen.HOME
            LauncherScreen.APPS -> scope.launch { pagerState.animateScrollToPage(0) }
            else -> Unit
        }
    }
    BackHandler(enabled = screen == LauncherScreen.ONBOARDING) { }

    val systemDark = androidx.compose.foundation.isSystemInDarkTheme()
    val isDark = when (preferences.themeMode) {
        "light" -> false
        "dark" -> true
        else -> systemDark
    }
    val useDynamicColor = preferences.dynamicColor &&
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
        preferences.themeMode == "system"
    val baseColorScheme = when {
        preferences.themeMode == "dark" -> darkColorScheme()
        useDynamicColor && isDark -> dynamicDarkColorScheme(context)
        useDynamicColor -> dynamicLightColorScheme(context)
        isDark -> darkColorScheme()
        else -> lightColorScheme()
    }
    val textSafeDarkScheme = if (isDark) {
        baseColorScheme.copy(
            primary = Color(0xFF8AB4F8),
            secondary = Color.White,
            tertiary = Color.White,
            onPrimary = Color(0xFF101010),
            onSecondary = Color(0xFF101010),
            onTertiary = Color(0xFF101010),
            onBackground = Color.White,
            onSurface = Color.White,
            onSurfaceVariant = Color.White,
            onPrimaryContainer = Color.White,
            onSecondaryContainer = Color.White,
            onTertiaryContainer = Color.White,
            inverseOnSurface = Color.White,
            error = Color.White,
            onError = Color.White,
            onErrorContainer = Color.White
        )
    } else {
        baseColorScheme
    }
    val colorScheme = if (preferences.themeMode == "dark") {
        textSafeDarkScheme.copy(
            background = Color.Black,
            surface = Color.Black,
            surfaceVariant = Color.Black,
            surfaceContainerLowest = Color.Black,
            surfaceContainerLow = Color.Black,
            surfaceContainer = Color.Black,
            surfaceContainerHigh = Color.Black,
            surfaceContainerHighest = Color.Black,
            surfaceTint = Color.Transparent,
            primary = Color(0xFF8AB4F8),
            secondary = Color.White,
            tertiary = Color.White,
            onPrimary = Color(0xFF101010),
            onSecondary = Color(0xFF101010),
            onTertiary = Color(0xFF101010),
            outline = Color(0xFFBDBDBD),
            outlineVariant = Color(0xFF757575)
        )
    } else {
        textSafeDarkScheme
    }
    val fontFamily = remember(preferences.fontMode, preferences.customFontName) {
        when (preferences.fontMode) {
            "mono" -> FontFamily.Monospace
            "custom" -> {
                val fontFile = listOf("ttf", "otf")
                    .map { File(context.filesDir, "fonts/custom-font.$it") }
                    .firstOrNull { it.isFile && it.length() in 12L..MAX_CUSTOM_FONT_BYTES.toLong() }
                fontFile?.let { file ->
                    runCatching { FontFamily(Typeface.createFromFile(file)) }.getOrNull()
                } ?: FontFamily.Default
            }
            else -> FontFamily.Default
        }
    }
    val fontScale = when (preferences.fontSize) {
        "small" -> 0.88f
        "large" -> 1.22f
        else -> 1.0f
    }
    val baseTypography = Typography()
    fun scaled(style: TextStyle): TextStyle = style.copy(fontFamily = fontFamily)
    val typography = baseTypography.copy(
        displayLarge = scaled(baseTypography.displayLarge),
        displayMedium = scaled(baseTypography.displayMedium),
        displaySmall = scaled(baseTypography.displaySmall),
        headlineLarge = scaled(baseTypography.headlineLarge),
        headlineMedium = scaled(baseTypography.headlineMedium),
        headlineSmall = scaled(baseTypography.headlineSmall),
        titleLarge = scaled(baseTypography.titleLarge),
        titleMedium = scaled(baseTypography.titleMedium),
        titleSmall = scaled(baseTypography.titleSmall),
        bodyLarge = scaled(baseTypography.bodyLarge),
        bodyMedium = scaled(baseTypography.bodyMedium),
        bodySmall = scaled(baseTypography.bodySmall),
        labelLarge = scaled(baseTypography.labelLarge),
        labelMedium = scaled(baseTypography.labelMedium),
        labelSmall = scaled(baseTypography.labelSmall)
    )
    val visibleApps = remember(apps, preferences.hideSystemApps, hiddenApps) {
        apps.filter { (!preferences.hideSystemApps || !it.isSystem) && it.packageName !in hiddenApps }
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

    MaterialTheme(
        colorScheme = colorScheme,
        typography = typography,
        shapes = androidx.compose.material3.Shapes(
            extraSmall = RoundedCornerShape(4.dp),
            small = RoundedCornerShape(8.dp),
            medium = RoundedCornerShape(12.dp),
            large = RoundedCornerShape(16.dp),
            extraLarge = RoundedCornerShape(28.dp)
        )
    ) {
        CompositionLocalProvider(
            LocalDensity provides Density(
                density = baseDensity.density,
                fontScale = baseDensity.fontScale * fontScale
            ),
            LocalTextStyle provides LocalTextStyle.current.copy(fontFamily = fontFamily)
        ) {
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
                onDoubleTapLockChange = ::changeDoubleTapLock,
                onToggleFavorite = ::toggleFavorite,
                onToggleHidden = ::toggleHidden,
                onBack = { screen = LauncherScreen.HOME }
            )
        } else if (screen == LauncherScreen.TASKS) {
            TasksScreen(
                tasks = tasks,
                onTasksChange = ::updateTasks,
                onBack = { screen = LauncherScreen.HOME }
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
                    .background(colorScheme.background)
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
                            onOpenTasks = { screen = LauncherScreen.TASKS },
                            onOpenWeather = { cached -> weatherForScreen = cached ?: solarWeather; screen = LauncherScreen.WEATHER },
                            onOpenSettings = { screen = LauncherScreen.SETTINGS },
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
                            apps = visibleApps,
                            favorites = favorites,
                            query = searchQuery,
                            onQueryChange = { searchQuery = it },
                            preferences = preferences,
                            isDark = isDark,
                            isActive = page == pagerState.currentPage,
                            fontFamily = fontFamily,
                            onLaunch = { launchApp(context, it) },
                            onToggleFavorite = ::toggleFavorite,
                            onToggleHidden = ::toggleHidden,
                            onRenameApp = ::renameApp,
                            onOpenSettings = { screen = LauncherScreen.SETTINGS }
                        )
                    }
                }
            }
        }
        }
    }
}

private const val MAX_CUSTOM_FONT_BYTES = 5 * 1024 * 1024

private fun importUserCustomFont(context: Context, uri: Uri): String {
    val resolver = context.contentResolver
    val displayName = resolver.query(
        uri,
        arrayOf(OpenableColumns.DISPLAY_NAME),
        null,
        null,
        null
    )?.use { cursor ->
        val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
    } ?: uri.lastPathSegment.orEmpty()

    val extension = displayName.substringAfterLast('.', "").lowercase(Locale.ROOT)
    require(extension == "ttf" || extension == "otf") {
        "Only .ttf and .otf font files are supported."
    }
    val fontDirectory = File(context.filesDir, "fonts")
    if (!fontDirectory.isDirectory && !fontDirectory.mkdirs()) {
        throw IOException("Unable to create private font storage.")
    }
    val temporaryFile = File(fontDirectory, ".custom-font.$extension.tmp")
    try {
        val input = resolver.openInputStream(uri) ?: throw IOException("Unable to read selected font.")
        var totalBytes = 0L
        val header = ByteArray(4)
        var headerCount = 0
        input.use { source ->
            FileOutputStream(temporaryFile).use { destination ->
                val buffer = ByteArray(8192)
                while (true) {
                    val count = source.read(buffer)
                    if (count < 0) break
                    if (count == 0) continue
                    if (totalBytes + count > MAX_CUSTOM_FONT_BYTES.toLong()) {
                        throw IllegalArgumentException("Font files must be 5 MB or smaller.")
                    }
                    val headerPart = minOf(count, header.size - headerCount)
                    if (headerPart > 0) {
                        System.arraycopy(buffer, 0, header, headerCount, headerPart)
                        headerCount += headerPart
                    }
                    destination.write(buffer, 0, count)
                    totalBytes += count
                }
                destination.fd.sync()
            }
        }
        require(totalBytes >= 12L) { "The selected font file is too small." }
        val signature = String(header, Charsets.ISO_8859_1)
        val validHeader = when (extension) {
            "ttf" -> header.contentEquals(byteArrayOf(0, 1, 0, 0)) ||
                signature == "true" || signature == "typ1"
            "otf" -> signature == "OTTO"
            else -> false
        }
        require(validHeader) { "The file extension does not match a supported font format." }
        runCatching { Typeface.createFromFile(temporaryFile) }.getOrElse {
            throw IllegalArgumentException("Android could not load this font file.", it)
        }
        val targetFile = File(fontDirectory, "custom-font.$extension")
        Files.move(temporaryFile.toPath(), targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
        val otherExtension = if (extension == "ttf") "otf" else "ttf"
        File(fontDirectory, "custom-font.$otherExtension").delete()
        val safeName = displayName
            .substringAfterLast('/')
            .substringAfterLast('\\')
            .filterNot { Character.isISOControl(it) }
            .trim()
            .take(80)
        return safeName.ifBlank { "Custom font.$extension" }
    } catch (error: Exception) {
        temporaryFile.delete()
        throw error
    }
}

private fun readAppNameOverrides(prefs: android.content.SharedPreferences): Map<String, String> {
    val encoded = prefs.getString("renamed_app_labels_json", null).orEmpty()
    if (encoded.isBlank()) return emptyMap()
    return runCatching {
        val json = JSONObject(encoded)
        json.keys().asSequence().mapNotNull { key ->
            val value = json.optString(key).trim()
            if (key.isBlank() || value.isBlank()) null else key to value
        }.toMap()
    }.getOrDefault(emptyMap())
}

private fun loadLauncherApps(context: Context): List<LauncherApp> {
    val pm = context.packageManager
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    val results = pm.queryIntentActivities(intent, 0)
    val systemFlags = ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP
    return results.mapNotNull { resolveInfo ->
        val activity = resolveInfo.activityInfo ?: return@mapNotNull null
        val pkg = activity.packageName
        if (pkg == context.packageName) return@mapNotNull null
        val info = activity.applicationInfo ?: return@mapNotNull null
        LauncherApp(
            packageName = pkg,
            activityName = activity.name,
            label = resolveInfo.loadLabel(pm).toString().ifBlank { pkg },
            isSystem = (info.flags and systemFlags) != 0
        )
    }.distinctBy { it.packageName }.sortedBy { it.label.lowercase(Locale.getDefault()) }
}

internal fun launchApp(context: Context, app: LauncherApp) {
    val intent = Intent(Intent.ACTION_MAIN)
        .addCategory(Intent.CATEGORY_LAUNCHER)
        .setClassName(app.packageName, app.activityName)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
}

internal fun openClock(context: Context) {
    val packageManager = context.packageManager
    val candidates = listOf(
        Intent(Intent.ACTION_MAIN).addCategory("android.intent.category.APP_CLOCK"),
        Intent(android.provider.AlarmClock.ACTION_SHOW_ALARMS),
        Intent(android.provider.AlarmClock.ACTION_SET_ALARM)
    )
    for (candidate in candidates) {
        val canResolve = runCatching { candidate.resolveActivity(packageManager) != null }.getOrDefault(false)
        if (!canResolve) continue
        val launched = runCatching {
            context.startActivity(candidate.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        }.getOrDefault(false)
        if (launched) return
    }
    val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    val clockApp = runCatching {
        packageManager.queryIntentActivities(launcherIntent, 0).firstOrNull { resolved ->
            val label = resolved.loadLabel(packageManager).toString().lowercase(Locale.getDefault())
            val packageName = resolved.activityInfo?.packageName.orEmpty().lowercase(Locale.getDefault())
            label.contains("clock") || label.contains("alarm") ||
                packageName.contains("deskclock") || packageName.contains("clock")
        }
    }.getOrNull()
    val activityInfo = clockApp?.activityInfo ?: return
    runCatching {
        context.startActivity(
            Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LAUNCHER)
                .setClassName(activityInfo.packageName, activityInfo.name)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}

internal fun openCalendar(context: Context) {
    val appIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_CALENDAR)
    val viewIntent = Intent(Intent.ACTION_VIEW, android.provider.CalendarContract.CONTENT_URI)
    listOf(appIntent, viewIntent).firstOrNull {
        it.resolveActivity(context.packageManager) != null
    }?.let { runCatching { context.startActivity(it) } }
}

internal fun openMusicPlayer(context: Context, apps: List<LauncherApp>) {
    val intent = Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_MUSIC)
    if (intent.resolveActivity(context.packageManager) != null) {
        runCatching { context.startActivity(intent) }
        return
    }
    val keywords = listOf("spotify", "youtube music", "music", "soundcloud", "deezer", "vlc", "poweramp", "podcast")
    val candidate = apps.firstOrNull { app ->
        val label = app.label.lowercase(Locale.getDefault())
        val pkg = app.packageName.lowercase(Locale.getDefault())
        keywords.any { label.contains(it) || pkg.contains(it.replace(" ", "")) }
    }
    if (candidate != null) launchApp(context, candidate)
    else runCatching { context.startActivity(Intent(Settings.ACTION_SOUND_SETTINGS)) }
}

private fun lockDevice(
    context: Context,
    adminComponent: ComponentName,
    manager: DevicePolicyManager
) {
    if (runCatching { manager.isAdminActive(adminComponent) }.getOrDefault(false)) {
        runCatching { manager.lockNow() }
    } else {
        runCatching {
            context.startActivity(
                Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
                    .putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent)
            )
        }
    }
}

@android.annotation.SuppressLint("WrongConstant", "PrivateApi")
private fun expandNotificationPanel(context: Context) {
    try {
        val statusBarService = context.getSystemService("statusbar")
            ?: throw IllegalStateException("Android status bar service is unavailable")
        Class.forName("android.app.StatusBarManager")
            .getMethod("expandNotificationsPanel")
            .invoke(statusBarService)
    } catch (error: Exception) {
        android.util.Log.w(
            "Dislauncher",
            "Cannot expand notification shade on this device",
            error
        )
    }
}

internal fun readBatteryPercent(context: Context): Int? {
    val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return null
    val level = intent.getIntExtra("level", -1)
    val scale = intent.getIntExtra("scale", -1)
    return if (level >= 0 && scale > 0) (level * 100 / scale).coerceIn(0, 100) else null
}
