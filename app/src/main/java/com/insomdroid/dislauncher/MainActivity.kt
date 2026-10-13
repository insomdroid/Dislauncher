package com.insomdroid.dislauncher

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

internal enum class LauncherScreen { HOME, APPS, SETTINGS }

internal data class LauncherApp(
    val packageName: String,
    val activityName: String,
    val label: String,
    val isSystem: Boolean
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
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
    val prefs = remember {
        context.getSharedPreferences("dislauncher_preferences", Context.MODE_PRIVATE)
    }
    var screen by remember { mutableStateOf(LauncherScreen.HOME) }
    var themeMode by remember { mutableStateOf(prefs.getString("theme_mode", "dark") ?: "dark") }
    var fontMode by remember { mutableStateOf(prefs.getString("font_mode", "sans") ?: "sans") }
    var showProgress by remember { mutableStateOf(prefs.getBoolean("show_progress", true)) }
    var showQuote by remember { mutableStateOf(prefs.getBoolean("show_quote", true)) }
    var hideSystemApps by remember { mutableStateOf(prefs.getBoolean("hide_system_apps", false)) }
    var favorites by remember {
        mutableStateOf(prefs.getStringSet("favorite_packages", emptySet())?.toSet().orEmpty())
    }
    var searchQuery by remember { mutableStateOf("") }
    var apps by remember { mutableStateOf<List<LauncherApp>>(emptyList()) }

    val systemDark = androidx.compose.foundation.isSystemInDarkTheme()
    val isDark = when (themeMode) {
        "light" -> false
        "dark" -> true
        else -> systemDark
    }
    val colorScheme = when {
        themeMode == "system" && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (systemDark) androidx.compose.material3.dynamicDarkColorScheme(context)
            else androidx.compose.material3.dynamicLightColorScheme(context)
        }
        isDark -> darkColorScheme(
            primary = Color(0xFFC4C8FF),
            onPrimary = Color(0xFF171A35),
            secondary = Color(0xFFD8C2FF),
            tertiary = Color(0xFFFFB8AA),
            background = Color(0xFF101014),
            surface = Color(0xFF19191F),
            surfaceVariant = Color(0xFF292932)
        )
        else -> lightColorScheme(
            primary = Color(0xFF4A4F8F),
            secondary = Color(0xFF625B71),
            tertiary = Color(0xFF805347),
            background = Color(0xFFFFF8FC),
            surface = Color(0xFFFFF8FC),
            surfaceVariant = Color(0xFFECE7F1)
        )
    }
    val fontFamily = when (fontMode) {
        "serif" -> androidx.compose.ui.text.font.FontFamily.Serif
        "mono" -> androidx.compose.ui.text.font.FontFamily.Monospace
        else -> androidx.compose.ui.text.font.FontFamily.SansSerif
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
        }
    }

    LaunchedEffect(hideSystemApps) {
        apps = withContext(Dispatchers.IO) { loadLauncherApps(context, hideSystemApps) }
    }

    fun saveTheme(value: String) {
        themeMode = value
        prefs.edit().putString("theme_mode", value).apply()
    }
    fun saveFont(value: String) {
        fontMode = value
        prefs.edit().putString("font_mode", value).apply()
    }
    fun saveProgress(value: Boolean) {
        showProgress = value
        prefs.edit().putBoolean("show_progress", value).apply()
    }
    fun saveQuote(value: Boolean) {
        showQuote = value
        prefs.edit().putBoolean("show_quote", value).apply()
    }
    fun saveHideSystemApps(value: Boolean) {
        hideSystemApps = value
        prefs.edit().putBoolean("hide_system_apps", value).apply()
    }
    fun toggleFavorite(packageName: String) {
        val updated = if (packageName in favorites) favorites - packageName else favorites + packageName
        favorites = updated
        prefs.edit().putStringSet("favorite_packages", updated.toMutableSet()).apply()
    }

    BackHandler(enabled = screen != LauncherScreen.HOME) {
        screen = LauncherScreen.HOME
    }

    MaterialTheme(
        colorScheme = colorScheme,
        shapes = androidx.compose.material3.Shapes(
            extraSmall = RoundedCornerShape(8.dp),
            small = RoundedCornerShape(14.dp),
            medium = RoundedCornerShape(20.dp),
            large = RoundedCornerShape(28.dp),
            extraLarge = RoundedCornerShape(36.dp)
        )
    ) {
        Box(
            modifier = Modifier.fillMaxSize().background(colorScheme.background)
                .pointerInputScreen(screen) { screen = it }
        ) {
            when (screen) {
                LauncherScreen.HOME -> HomeScreen(
                    apps = apps,
                    favorites = favorites,
                    showProgress = showProgress,
                    showQuote = showQuote,
                    isDark = isDark,
                    fontFamily = fontFamily,
                    onLaunch = { launchApp(context, it) },
                    onToggleFavorite = { toggleFavorite(it) },
                    onOpenApps = {
                        searchQuery = ""
                        screen = LauncherScreen.APPS
                    },
                    onOpenSettings = { screen = LauncherScreen.SETTINGS }
                )
                LauncherScreen.APPS -> AppsScreen(
                    apps = apps,
                    favorites = favorites,
                    query = searchQuery,
                    onQueryChange = { searchQuery = it },
                    isDark = isDark,
                    fontFamily = fontFamily,
                    onLaunch = { launchApp(context, it) },
                    onToggleFavorite = { toggleFavorite(it) },
                    onBack = { screen = LauncherScreen.HOME }
                )
                LauncherScreen.SETTINGS -> SettingsScreen(
                    themeMode = themeMode,
                    fontMode = fontMode,
                    showProgress = showProgress,
                    showQuote = showQuote,
                    hideSystemApps = hideSystemApps,
                    onThemeModeChange = { saveTheme(it) },
                    onFontModeChange = { saveFont(it) },
                    onProgressChange = { saveProgress(it) },
                    onQuoteChange = { saveQuote(it) },
                    onHideSystemAppsChange = { saveHideSystemApps(it) },
                    onBack = { screen = LauncherScreen.HOME }
                )
            }
        }
    }
}

private fun Modifier.pointerInputScreen(
    screen: LauncherScreen,
    onNavigate: (LauncherScreen) -> Unit
): Modifier = pointerInput(screen) {
    if (screen == LauncherScreen.HOME || screen == LauncherScreen.APPS) {
        var totalDrag = 0f
        var handled = false
        val threshold = 76.dp.toPx()
        detectHorizontalDragGestures(
            onHorizontalDrag = { _, dragAmount ->
                if (!handled) {
                    totalDrag += dragAmount
                    if (screen == LauncherScreen.HOME && totalDrag <= -threshold) {
                        handled = true
                        onNavigate(LauncherScreen.APPS)
                    } else if (screen == LauncherScreen.APPS && totalDrag >= threshold) {
                        handled = true
                        onNavigate(LauncherScreen.HOME)
                    }
                }
            },
            onDragEnd = { totalDrag = 0f; handled = false },
            onDragCancel = { totalDrag = 0f; handled = false }
        )
    }
}

private fun loadLauncherApps(context: Context, hideSystemApps: Boolean): List<LauncherApp> {
    val pm = context.packageManager
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    val results = pm.queryIntentActivities(intent, 0)
    val systemFlags = ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP
    return results.mapNotNull { resolveInfo ->
        val activity = resolveInfo.activityInfo ?: return@mapNotNull null
        val pkg = activity.packageName
        if (pkg == context.packageName) return@mapNotNull null
        val info = activity.applicationInfo ?: return@mapNotNull null
        val system = (info.flags and systemFlags) != 0
        if (hideSystemApps && system) return@mapNotNull null
        LauncherApp(
            packageName = pkg,
            activityName = activity.name,
            label = resolveInfo.loadLabel(pm).toString().ifBlank { pkg },
            isSystem = system
        )
    }.distinctBy { it.packageName }.sortedBy { it.label.lowercase(Locale.getDefault()) }
}

private fun launchApp(context: Context, app: LauncherApp) {
    val intent = Intent(Intent.ACTION_MAIN)
        .addCategory(Intent.CATEGORY_LAUNCHER)
        .setClassName(app.packageName, app.activityName)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
}

internal fun readBatteryPercent(context: Context): Int? {
    val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return null
    val level = intent.getIntExtra("level", -1)
    val scale = intent.getIntExtra("scale", -1)
    return if (level >= 0 && scale > 0) (level * 100 / scale).coerceIn(0, 100) else null
}
