package com.insomdroid.dislauncher

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.os.Build
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import org.json.JSONObject

internal enum class LauncherScreen { HOME, APPS, SETTINGS, TASKS, WEATHER }

internal data class LauncherApp(
    val packageName: String,
    val activityName: String,
    val label: String,
    val isSystem: Boolean
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        setContent { DislauncherRoot() }
    }
}

@Composable
private fun DislauncherRoot() {
    val context = LocalContext.current
    val view = LocalView.current
    val baseDensity = LocalDensity.current
    val prefsStore = remember {
        context.getSharedPreferences("dislauncher_preferences", Context.MODE_PRIVATE)
    }
    var preferences by remember { mutableStateOf(LauncherPreferences.load(prefsStore)) }
    var favorites by remember {
        mutableStateOf(prefsStore.getStringSet("favorite_packages", emptySet())?.toSet().orEmpty())
    }
    var appNameOverrides by remember { mutableStateOf(readAppNameOverrides(prefsStore)) }
    var solarWeather by remember { mutableStateOf<WeatherInfo?>(null) }
    var weatherForScreen by remember { mutableStateOf<WeatherInfo?>(null) }
    var solarTick by remember { mutableStateOf(0L) }
    var homeItemOrder by remember { mutableStateOf(readHomeItemOrder(prefsStore, favorites)) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000L)
            solarTick++
        }
    }
    var hiddenApps by remember {
        mutableStateOf(prefsStore.getStringSet("hidden_packages", emptySet())?.toSet().orEmpty())
    }
    var tasks by remember { mutableStateOf(readLauncherTasks(prefsStore)) }
    var favoriteOrder by remember { mutableStateOf(readFavoriteOrder(prefsStore, favorites)) }
    var apps by remember { mutableStateOf<List<LauncherApp>>(emptyList()) }
    var searchQuery by remember { mutableStateOf("") }
    var screen by remember { mutableStateOf(LauncherScreen.HOME) }
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { 2 })
    val scope = rememberCoroutineScope()
    val iconPacks = remember { findIconPacks(context) }

    fun updatePreferences(next: LauncherPreferences) {
        preferences = next
        next.save(prefsStore)
    }

    fun toggleFavorite(packageName: String) {
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

    val systemDark = androidx.compose.foundation.isSystemInDarkTheme()
    val solarDark = remember(solarWeather, solarTick) {
        val sunrise = solarWeather?.sunrise
        val sunset = solarWeather?.sunset
        if (sunrise == null || sunset == null) null else {
            val solarTime = runCatching {
                solarWeather?.timezone?.let { ZonedDateTime.now(ZoneId.of(it)).toLocalTime() } ?: LocalTime.now()
            }.getOrElse { LocalTime.now() }
            solarTime.isBefore(sunrise) || !solarTime.isBefore(sunset)
        }
    }
    val isDark = when (preferences.themeMode) {
        "system" -> systemDark
        "sun" -> solarDark ?: systemDark
        "light", "pale-silver", "middle-blue", "parchment", "blue-radiance" -> false
        else -> true
    }
    val backgroundColor = when (preferences.themeMode) {
        "system", "sun" -> if (isDark) Color.Black else Color.White
        "light" -> Color.White
        "dim" -> Color(0xFF151E29)
        "twitter-dim" -> Color(0xFF16202A)
        "midnight-blue" -> Color(0xFF191970)
        "pale-silver" -> Color(0xFFD7CEC9)
        "middle-blue" -> Color(0xFF90E0EF)
        "parchment" -> Color(0xFFF3ECD8)
        "blue-radiance" -> Color(0xFF57CAD5)
        "dark-indigo" -> Color(0xFF171D4B)
        "carbon" -> Color(0xFF333333)
        else -> Color.Black
    }
    val foregroundColor = when (preferences.themeMode) {
        "system", "sun" -> if (isDark) Color.White else Color.Black
        "light", "pale-silver", "middle-blue", "parchment" -> Color.Black
        else -> Color.White
    }
    val secondaryColor = when (preferences.themeMode) {
        "dark" -> Color(0xFFBDBDBD)
        "light" -> Color(0xFF666666)
        "pale-silver", "middle-blue", "parchment" -> Color.Black.copy(alpha = 0.72f)
        else -> foregroundColor.copy(alpha = 0.78f)
    }
    val surfaceVariantColor = when (preferences.themeMode) {
        "system", "sun" -> if (isDark) Color(0xFF1D1D1F) else Color(0xFFF2F2F7)
        "dark" -> Color(0xFF121212)
        "light" -> Color(0xFFF2F2F7)
        "dim" -> Color(0xFF1D2A39)
        "twitter-dim" -> Color(0xFF1B2935)
        "midnight-blue" -> Color(0xFF22227D)
        "dark-indigo" -> Color(0xFF202858)
        "carbon" -> Color(0xFF424242)
        "pale-silver" -> Color(0xFFC8BEB9)
        "middle-blue" -> Color(0xFF7ACBD9)
        "parchment" -> Color(0xFFE8DFC9)
        else -> backgroundColor
    }
    val colorScheme = if (isDark) {
        darkColorScheme(
            primary = foregroundColor, onPrimary = backgroundColor,
            secondary = secondaryColor, tertiary = foregroundColor,
            background = backgroundColor, onBackground = foregroundColor,
            surface = backgroundColor, onSurface = foregroundColor,
            surfaceVariant = surfaceVariantColor, onSurfaceVariant = secondaryColor
        )
    } else {
        lightColorScheme(
            primary = foregroundColor, onPrimary = backgroundColor,
            secondary = secondaryColor, tertiary = foregroundColor,
            background = backgroundColor, onBackground = foregroundColor,
            surface = backgroundColor, onSurface = foregroundColor,
            surfaceVariant = surfaceVariantColor, onSurfaceVariant = secondaryColor
        )
    }
    val fontFamily = when (preferences.fontMode) {
        "serif" -> FontFamily.Serif
        "opensans" -> FontFamily(androidx.compose.ui.text.font.Font(R.font.open_sans_regular, androidx.compose.ui.text.font.FontWeight.Normal))
        "raleway-dots" -> FontFamily(androidx.compose.ui.text.font.Font(R.font.raleway_dots_regular, androidx.compose.ui.text.font.FontWeight.Normal))
        "menlo" -> FontFamily(android.graphics.Typeface.create("monospace", android.graphics.Typeface.NORMAL))
        "mono" -> FontFamily.Monospace
        "condensed" -> FontFamily(android.graphics.Typeface.create("sans-serif-condensed", android.graphics.Typeface.NORMAL))
        "rounded" -> FontFamily(android.graphics.Typeface.create("sans-serif-rounded", android.graphics.Typeface.NORMAL))
        "light" -> FontFamily(android.graphics.Typeface.create("sans-serif-light", android.graphics.Typeface.NORMAL))
        "casual" -> FontFamily(android.graphics.Typeface.create("casual", android.graphics.Typeface.NORMAL))
        "cursive" -> FontFamily(android.graphics.Typeface.create("cursive", android.graphics.Typeface.NORMAL))
        else -> FontFamily.SansSerif
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
            activity.window.statusBarColor = android.graphics.Color.TRANSPARENT
            activity.window.navigationBarColor = android.graphics.Color.TRANSPARENT
            val controller: WindowInsetsControllerCompat =
                WindowCompat.getInsetsController(activity.window, view)
            controller.isAppearanceLightStatusBars = !isDark
            controller.isAppearanceLightNavigationBars = !isDark
            if (preferences.hideStatusBar) controller.hide(WindowInsetsCompat.Type.statusBars())
            else controller.show(WindowInsetsCompat.Type.statusBars())
            controller.show(WindowInsetsCompat.Type.navigationBars())
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = typography,
        shapes = androidx.compose.material3.Shapes(
            extraSmall = RoundedCornerShape(8.dp),
            small = RoundedCornerShape(14.dp),
            medium = RoundedCornerShape(20.dp),
            large = RoundedCornerShape(28.dp),
            extraLarge = RoundedCornerShape(36.dp)
        )
    ) {
        CompositionLocalProvider(
            LocalDensity provides Density(
                density = baseDensity.density,
                fontScale = baseDensity.fontScale * fontScale
            ),
            LocalTextStyle provides LocalTextStyle.current.copy(fontFamily = fontFamily)
        ) {
        if (screen == LauncherScreen.SETTINGS) {
            SettingsScreen(
                preferences = preferences,
                apps = apps,
                favorites = favorites,
                hiddenApps = hiddenApps,
                iconPacks = iconPacks,
                onPreferencesChange = ::updatePreferences,
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
                cachedWeather = weatherForScreen,
                onBack = { screen = LauncherScreen.HOME }
            )
        } else {
            Box(Modifier.fillMaxSize().background(colorScheme.background)) {
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
                            isDark = isDark,
                            fontFamily = fontFamily,
                            fontScale = fontScale,
                            onLaunch = { launchApp(context, it) },
                            onMoveFavorite = ::moveFavorite,
                            onOpenTasks = { screen = LauncherScreen.TASKS },
                            onOpenWeather = { cached -> weatherForScreen = cached; screen = LauncherScreen.WEATHER },
                            onOpenSettings = { screen = LauncherScreen.SETTINGS },
                            onDoubleTapToLock = { lockDevice(context, adminComponent, devicePolicyManager) },
                            homeItemOrder = homeItemOrder,
                            onMoveHomeItem = ::moveHomeItem,
                            onSolarInfoChange = { solarWeather = it }
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
                            fontScale = fontScale,
                            onLaunch = { launchApp(context, it) },
                            onToggleFavorite = ::toggleFavorite,
                            onToggleHidden = ::toggleHidden,
                            onRenameApp = ::renameApp
                        )
                    }
                }
                if (screen == LauncherScreen.HOME && pagerState.currentPage == 0 && preferences.swipeDownNotifications) {
                    Box(
                        modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth()
                            .windowInsetsPadding(WindowInsets.statusBars)
                            .height(48.dp)
                            .pointerInput(preferences.swipeDownNotifications) {
                                var totalDrag = 0f
                                detectVerticalDragGestures(
                                    onVerticalDrag = { _, dragAmount ->
                                        totalDrag += dragAmount
                                    },
                                    onDragEnd = {
                                        if (totalDrag > 32.dp.toPx()) expandNotificationPanel(context)
                                        totalDrag = 0f
                                    },
                                    onDragCancel = { totalDrag = 0f }
                                )
                            }
                    )
                }
            }
        }
        }
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
    val candidates = listOf(
        Intent(android.provider.AlarmClock.ACTION_SHOW_ALARMS),
        Intent(android.provider.AlarmClock.ACTION_SET_ALARM)
    )
    candidates.firstOrNull { it.resolveActivity(context.packageManager) != null }?.let {
        runCatching { context.startActivity(it) }
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

private fun expandNotificationPanel(context: Context) {
    runCatching {
        val manager = context.getSystemService("statusbar") ?: return
        manager.javaClass.getMethod("expandNotificationsPanel").invoke(manager)
    }
}

internal fun readBatteryPercent(context: Context): Int? {
    val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return null
    val level = intent.getIntExtra("level", -1)
    val scale = intent.getIntExtra("scale", -1)
    return if (level >= 0 && scale > 0) (level * 100 / scale).coerceIn(0, 100) else null
}
