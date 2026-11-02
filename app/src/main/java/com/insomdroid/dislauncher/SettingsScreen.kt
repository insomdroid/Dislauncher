package com.insomdroid.dislauncher

import androidx.compose.ui.res.painterResource
import android.Manifest
import android.app.admin.DevicePolicyManager
import android.app.role.RoleManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Switch
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.time.LocalDate
import java.time.LocalTime
import kotlin.math.roundToInt

private enum class SettingsCategory {
    APPEARANCE, HOME, APPS, GESTURES, ACCESS, ABOUT
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsScreen(
    preferences: LauncherPreferences,
    apps: List<LauncherApp>,
    favorites: Set<String>,
    hiddenApps: Set<String>,
    iconPacks: List<IconPackInfo>,
    onPreferencesChange: (LauncherPreferences) -> Unit,
    onChooseCustomFont: () -> Unit,
    onChooseWallpaper: () -> Unit,
    onApplyWallpaperToSystem: (String, (String) -> Unit) -> Unit,
    onDoubleTapLockChange: (Boolean) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onToggleHidden: (String) -> Unit,
    onBack: () -> Unit,
    onRestartLauncher: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var selectedCategory by remember { mutableStateOf<SettingsCategory?>(null) }
    var openDialog by remember { mutableStateOf("") }
    var newFolderName by remember { mutableStateOf("") }
    var manageFolderName by remember { mutableStateOf<String?>(null) }
    var wallpaperTarget by remember(preferences.wallpaperTarget) { mutableStateOf(preferences.wallpaperTarget) }
    var showRestartConfirmation by remember { mutableStateOf(false) }
    var detectedIconPacks by remember { mutableStateOf(iconPacks) }
    var refreshVersion by remember { mutableStateOf(0) }
    LaunchedEffect(context, refreshVersion) {
        detectedIconPacks = withContext(Dispatchers.IO) { findIconPacks(context) }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refreshVersion++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    var showChangelog by remember { mutableStateOf(false) }
    var updateDialogState by remember { mutableStateOf<UpdateCheckResult?>(null) }
    var updateCheckRequested by remember { mutableStateOf(false) }

    BackHandler(enabled = showChangelog || selectedCategory != null) {
        if (showChangelog) {
            showChangelog = false
        } else {
            selectedCategory = null
            openDialog = ""
        }
    }

    val adminComponent = remember(context) {
        ComponentName(context, DislauncherAdminReceiver::class.java)
    }
    val devicePolicyManager = remember(context) {
        context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    }
    val notificationListenerComponent = remember(context) {
        ComponentName(context, DislauncherNotificationListener::class.java)
    }
    val notificationAccessEnabled = remember(refreshVersion, notificationListenerComponent) {
        val enabledListeners = Settings.Secure.getString(
            context.contentResolver,
            "enabled_notification_listeners"
        ).orEmpty()
        enabledListeners.split(':').any { listener ->
            ComponentName.unflattenFromString(listener) == notificationListenerComponent
        }
    }
    val adminActive = remember(refreshVersion) {
        runCatching { devicePolicyManager.isAdminActive(adminComponent) }.getOrDefault(false)
    }
    val defaultLauncher = remember(refreshVersion) { isDislauncherDefaultHome(context) }
    val calendarPermission = remember(refreshVersion) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) ==
            PackageManager.PERMISSION_GRANTED
    }
    val locationPermission = remember(refreshVersion) {
        hasWeatherLocationPermission(context)
    }
    val appVersion = remember {
        runCatching {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: "—"
    }

    LaunchedEffect(updateCheckRequested) {
        if (updateCheckRequested) {
            updateDialogState = ReleaseUpdateResult.Checking
            updateDialogState = ReleaseUpdateChecker.check(appVersion)
            updateCheckRequested = false
        }
    }

    fun openSystemPage(intent: Intent) {
        runCatching { context.startActivity(intent) }
    }

    fun openAppPermissionSettings() {
        openSystemPage(
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", context.packageName, null)
            )
        )
    }

    val categoryTitle = if (showChangelog) {
        "What's New"
    } else when (selectedCategory) {
        SettingsCategory.APPEARANCE -> "Appearance"
        SettingsCategory.HOME -> "Home screen"
        SettingsCategory.APPS -> "Apps"
        SettingsCategory.GESTURES -> "Gestures"
        SettingsCategory.ACCESS -> "Permissions and access"
        SettingsCategory.ABOUT -> "About"
        null -> "Settings"
    }

    BuiltInToolScaffold(
        containerColor = MaterialTheme.colorScheme.background,
        header = {
            BuiltInToolHeader(
                title = {
                    Text(
                        categoryTitle,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            when {
                                showChangelog -> showChangelog = false
                                selectedCategory != null -> {
                                    selectedCategory = null
                                    openDialog = ""
                                }
                                else -> onBack()
                            }
                        }
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_symbol_arrow_back),
                            contentDescription = "Back",
                            modifier = Modifier.size(LauncherIconTokens.standard)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(BuiltInToolSpacing.section)
        ) {
            if (showChangelog) {
                ChangelogScreen()
            } else if (selectedCategory == null) {
                Text(
                    "Choose a category",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
                )
                val categories = listOf(
                    Triple(SettingsCategory.APPEARANCE, R.drawable.ic_symbol_palette, "Appearance") to
                        "Theme, wallpaper, fonts and app icons",
                    Triple(SettingsCategory.HOME, R.drawable.ic_symbol_home, "Home screen") to
                        "Home information, indicators and formats",
                    Triple(SettingsCategory.APPS, R.drawable.ic_symbol_apps, "Apps") to
                        "Folders, app visibility and app drawer",
                    Triple(SettingsCategory.GESTURES, R.drawable.ic_symbol_gesture, "Gestures") to
                        "Quick actions for the Home screen",
                    Triple(SettingsCategory.ACCESS, R.drawable.ic_symbol_security, "Permissions and access") to
                        "Check access required by features",
                    Triple(SettingsCategory.ABOUT, R.drawable.ic_symbol_info, "About") to
                        "Version and project information"
                )
                categories.forEach { entry ->
                    val (category, icon, title) = entry.first
                    SettingsCategoryRow(
                        icon = icon,
                        title = title,
                        description = entry.second,
                        onClick = { selectedCategory = category }
                    )
                }
            } else {
                when (selectedCategory) {
                    SettingsCategory.APPEARANCE -> {
                        SettingsGroup("Theme and color") {
                            ChoiceRow(
                                "Theme mode",
                                preferences.themeMode,
                                listOf(
                                    "system" to "System",
                                    "light" to "Light",
                                    "dark" to "Dark",
                                    "black" to "AMOLED"
                                )
                            ) { onPreferencesChange(preferences.copy(themeMode = it)) }
                            SettingToggle(
                                "Dynamic color",
                                "Use colors from your Android wallpaper",
                                preferences.dynamicColor
                            ) {
                                onPreferencesChange(preferences.copy(dynamicColor = it))
                            }
                            if (preferences.dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                Column(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        "Accent color",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        "Managed by Dynamic color while it is enabled.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            } else {
                                ChoiceRow(
                                    "Accent color",
                                    preferences.accentPreset,
                                    listOf(
                                        "default" to "Default",
                                        "red" to "Red",
                                        "yellow" to "Yellow",
                                        "blue" to "Blue",
                                        "green" to "Green",
                                        "purple" to "Purple",
                                        "orange" to "Orange",
                                        "pink" to "Pink"
                                    )
                                ) { onPreferencesChange(preferences.copy(accentPreset = it)) }
                            }
                        }
                        SettingsGroup("Wallpaper") {
                            ChoiceRow(
                                "Wallpaper",
                                preferences.wallpaperMode,
                                listOf(
                                    "default" to "Default",
                                    "image" to "Custom image"
                                )
                            ) { mode ->
                                // Keep the image picker separate from selecting wallpaper mode.
                                onPreferencesChange(preferences.copy(wallpaperMode = mode))
                            }
                            if (preferences.wallpaperMode == "image") {
                                SettingAction(
                                    "Custom wallpaper",
                                    if (preferences.wallpaperUri != null) "Custom image selected"
                                    else "Choose an image for your wallpaper",
                                    "Choose image",
                                    onChooseWallpaper
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        "Dim Home wallpaper",
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text("${preferences.homeWallpaperDim}%", style = MaterialTheme.typography.bodyMedium)
                                }
                                Slider(
                                    value = preferences.homeWallpaperDim.toFloat(),
                                    onValueChange = { value ->
                                        onPreferencesChange(preferences.copy(homeWallpaperDim = value.roundToInt()))
                                    },
                                    valueRange = 0f..100f,
                                    steps = 19
                                )
                                Text(
                                    "0% keeps the wallpaper bright; 100% makes it fully dark.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                SettingAction(
                                    "Apply wallpaper",
                                    if (preferences.wallpaperUri != null) {
                                            "Apply the selected image to the Home screen, lock screen, or both."
                                        } else {
                                            "Choose an image before applying it to your device wallpaper."
                                        },
                                    "Apply to screen…",
                                    {
                                        wallpaperTarget = preferences.wallpaperTarget
                                        openDialog = "wallpaperTarget"
                                    }
                                )
                            } else {
                                SettingAction(
                                    "Default wallpaper color",
                                    "Use the current Light, Dark, System, or AMOLED background color for the selected device screen(s). Dislauncher’s internal background stays separate.",
                                    "Apply to screen…",
                                    {
                                        wallpaperTarget = preferences.wallpaperTarget
                                        openDialog = "wallpaperTarget"
                                    }
                                )
                            }
                        }
                        SettingsGroup("Font and text size") {
                            ChoiceRow(
                                "Built-in font",
                                preferences.fontMode,
                                listOf(
                                    "system" to "System",
                                    "dislauncher" to "Inter Variable",
                                    "syne" to "Syne",
                                    "bricolage_grotesque" to "Bricolage Grotesque",
                                    "doto" to "Doto",
                                    "geist_mono" to "Geist Mono",
                                    "custom" to "Custom"
                                )
                            ) { value ->
                                // Selecting Custom activates the mode without launching
                                // the document picker; the action below opens it explicitly.
                                onPreferencesChange(preferences.copy(fontMode = value))
                            }
                            if (preferences.fontMode == "custom") {
                                SettingAction(
                                    "Custom font file",
                                    "Active: " + (preferences.customFontName
                                        ?: "Custom font"),
                                    "Choose file",
                                    onChooseCustomFont
                                )
                            }
                            ChoiceRow(
                                "Font size",
                                preferences.fontSize,
                                listOf(
                                    "small" to "Small",
                                    "medium" to "Medium",
                                    "large" to "Large"
                                )
                            ) { onPreferencesChange(preferences.copy(fontSize = it)) }
                        }
                        SettingsGroup("App icons") {
                            SettingToggle(
                                "Show app icons",
                                "Display app icons in the app drawer and on Home.",
                                preferences.showAppIcons
                            ) { onPreferencesChange(preferences.copy(showAppIcons = it)) }
                            if (preferences.showAppIcons) {
                                ChoiceRow(
                                    "Built-in icon shape",
                                    preferences.iconShape,
                                    listOf(
                                        "original" to "Original",
                                        "circle" to "Circle",
                                        "filled_square" to "Square"
                                    )
                                ) { onPreferencesChange(preferences.copy(iconShape = it)) }
                                SettingAction(
                                    "Icon pack",
                                    detectedIconPacks.firstOrNull { it.packageName == preferences.iconPack }?.label
                                        ?: if (preferences.iconPack == "system") {
                                            "System default"
                                        } else "Selected pack not installed",
                                    "Choose"
                                ) {
                                    openDialog = "iconpack"
                                }
                            } else {
                                Text(
                                    "Turn on app icons to configure their shape or icon pack.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    SettingsCategory.HOME -> {
                        SettingsGroup("Home elements") {
                            SettingToggle("Show status bar", "Show Android status icons at the top of the screen.", !preferences.hideStatusBar) {
                                onPreferencesChange(preferences.copy(hideStatusBar = !it))
                            }
                            SettingToggle(
                                "Show clock",
                                "Tap the clock to open the clock app.",
                                preferences.showClock
                            ) { onPreferencesChange(preferences.copy(showClock = it)) }
                            SettingToggle(
                                "Show date",
                                "Tap the date to open the calendar.",
                                preferences.showDate
                            ) { onPreferencesChange(preferences.copy(showDate = it)) }
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(LauncherShapeTokens.large))
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
                                    .padding(16.dp)
                            ) {
                                Text(
                                    "Preview",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.height(12.dp))
                                HomeHeader(
                                    preferences = preferences.copy(
                                        showClock = true,
                                        showDate = true,
                                        showYearProgress = false,
                                        showDayProgress = false
                                    ),
                                    timeText = "09:41",
                                    dateText = LocalDate.now().format(
                                        java.time.format.DateTimeFormatter.ofPattern("EEEE, d MMMM", java.util.Locale.getDefault())
                                    ),
                                    today = LocalDate.now(),
                                    now = LocalTime.of(9, 41),
                                    isDark = preferences.themeMode == "dark" || preferences.themeMode == "black",
                                    fontFamily = FontFamily.Default,
                                    primaryText = MaterialTheme.colorScheme.onSurface,
                                    secondaryText = MaterialTheme.colorScheme.onSurfaceVariant,
                                    onOpenClock = {},
                                    onOpenCalendar = {}
                                )
                            }
                            ChoiceRow(
                                "Clock style",
                                preferences.clockStyle,
                                listOf(
                                    "default" to "Default",
                                    "compact" to "Dot Matrix Compact",
                                    "date_focus" to "Date Focus",
                                    "split" to "Split Layout",
                                    "editorial" to "Editorial Date",
                                    "digital" to "Digital Watch",
                                    "vertical_stack" to "Vertical Stack",
                                    "binary_dot_minimal" to "Binary Dot Minimal",
                                    "dual_bar" to "Dual Bar Clock",
                                    "text_clock" to "Text Clock"
                                ),
                                description = "Choose how the clock and date look."
                            ) { onPreferencesChange(preferences.copy(clockStyle = it)) }
                            SettingToggle(
                                "Show Settings button",
                                "When hidden, long-press empty Home space to open Settings.",
                                preferences.showSettingsButton
                            ) { onPreferencesChange(preferences.copy(showSettingsButton = it)) }
                        }
                        SettingsGroup("Information and indicators") {
                            SettingToggle(
                                "Network info",
                                "Show the current network connection on the Home status row.",
                                preferences.showNetworkInfo
                            ) { onPreferencesChange(preferences.copy(showNetworkInfo = it)) }
                            SettingToggle("Calendar agenda", "Show the next calendar event on the Home screen.", preferences.showCalendarShortcut) {
                                onPreferencesChange(preferences.copy(showCalendarShortcut = it))
                            }
                            SettingToggle(
                                "Music player",
                                "Shows the active track and media controls.",
                                preferences.showMusic
                            ) { onPreferencesChange(preferences.copy(showMusic = it)) }
                            SettingToggle("Weather", "Show current weather and temperature on Home.", preferences.showWeather) {
                                onPreferencesChange(preferences.copy(showWeather = it))
                            }
                            SettingToggle("Battery percentage", "Show the current battery level as a percentage.", preferences.showBattery) {
                                onPreferencesChange(preferences.copy(showBattery = it))
                            }
                            SettingToggle(
                                "Day progress",
                                "Shows how much of today has elapsed.",
                                preferences.showDayProgress
                            ) { onPreferencesChange(preferences.copy(showDayProgress = it)) }
                            SettingToggle(
                                "Year progress",
                                "Shows how much of the year has elapsed.",
                                preferences.showYearProgress
                            ) { onPreferencesChange(preferences.copy(showYearProgress = it)) }
                        }
                        SettingsGroup("Notification dots") {
                            SettingToggle(
                                "Show notification dots",
                                "Show a small dot beside selected apps with active notifications.",
                                preferences.notificationDotsEnabled
                            ) { enabled ->
                                onPreferencesChange(preferences.copy(notificationDotsEnabled = enabled))
                                if (enabled && !notificationAccessEnabled) {
                                    openSystemPage(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                                }
                            }
                            SettingAction(
                                "Choose apps",
                                preferences.notificationDotPackages.size.toString() + " " +
                                    "apps selected · Choose which apps show a dot.",
                                "Choose"
                            ) { openDialog = "notificationdots" }
                            ChoiceRow(
                                "Dot color",
                                preferences.notificationDotColor,
                                listOf(
                                    "default" to "Default",
                                    "red" to "Red",
                                    "yellow" to "Yellow",
                                    "blue" to "Blue",
                                    "green" to "Green",
                                    "purple" to "Purple",
                                    "orange" to "Orange",
                                    "pink" to "Pink"
                                ),
                                description = "Choose the color used for notification dots."
                            ) { color ->
                                onPreferencesChange(preferences.copy(notificationDotColor = color))
                            }
                        }
                        SettingsGroup("Display formats") {
                            ChoiceRow(
                                "Time format",
                                preferences.timeFormat,
                                listOf(
                                    "system" to "System",
                                    "12" to "12 " + "hour",
                                    "24" to "24 " + "hour"
                                ),
                                description = "Choose how the Home clock displays time."
                            ) { onPreferencesChange(preferences.copy(timeFormat = it)) }
                            ChoiceRow(
                                "Temperature unit",
                                preferences.weatherUnit,
                                listOf("C" to "°C", "F" to "°F"),
                                description = "Choose Celsius or Fahrenheit for weather."
                            ) { onPreferencesChange(preferences.copy(weatherUnit = it)) }
                        }
                    }

                    SettingsCategory.APPS -> {
                        SettingsGroup("Manage apps") {
                            SettingAction(
                                "Manage folders",
                                preferences.appFolders.size.toString() + " " + "folders",
                                "Manage"
                            ) {
                                newFolderName = ""
                                manageFolderName = null
                                openDialog = "folders"
                            }
                            SettingAction(
                                "Manage favourites",
                                favorites.size.toString() + "/6 " + "apps selected",
                                "Manage"
                            ) { openDialog = "favorites" }
                            SettingAction(
                                "Manage hidden apps",
                                hiddenApps.size.toString() + " " + "apps hidden",
                                "Manage"
                            ) { openDialog = "hidden" }
                        }
                        SettingsGroup("App visibility") {
                            SettingToggle(
                                "Show built-in apps",
                                "Show Calculator, Calendar, Notes, Unit Converter and Books above your installed apps.",
                                preferences.showBuiltInApps
                            ) { onPreferencesChange(preferences.copy(showBuiltInApps = it)) }
                            SettingToggle(
                                "Show system apps",
                                "Include preinstalled Android and manufacturer apps in the app drawer.",
                                !preferences.hideSystemApps
                            ) { onPreferencesChange(preferences.copy(hideSystemApps = !it)) }
                        }
                        SettingsGroup("App drawer") {
                            SettingToggle(
                                "Show search bar",
                                "Display the search field at the bottom of the app drawer.",
                                preferences.showSearchBar
                            ) { onPreferencesChange(preferences.copy(showSearchBar = it)) }
                            SettingToggle(
                                "Alphabet index",
                                "Jump to apps by letter on the right edge. # groups names starting with numbers or symbols.",
                                preferences.showAlphabetIndex
                            ) { onPreferencesChange(preferences.copy(showAlphabetIndex = it)) }
                            SettingToggle(
                                "Auto-launch keyboard",
                                "Focus search when the app drawer opens.",
                                preferences.autoLaunchKeyboard
                            ) { onPreferencesChange(preferences.copy(autoLaunchKeyboard = it)) }
                            if (preferences.wallpaperMode == "image") {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        "App drawer wallpaper transparency",
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        "${preferences.appDrawerWallpaperTransparency}%",
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                                Slider(
                                    value = preferences.appDrawerWallpaperTransparency.toFloat(),
                                    onValueChange = {
                                        onPreferencesChange(
                                            preferences.copy(appDrawerWallpaperTransparency = it.roundToInt())
                                        )
                                    },
                                    valueRange = 0f..100f,
                                    steps = 19
                                )
                                Text(
                                    "0% shows the wallpaper fully; 100% covers it with the app drawer background.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    SettingsCategory.GESTURES -> {
                        SettingsGroup("Home gestures") {
                            SettingToggle(
                                "Double tap to lock",
                                "Requires device-admin activation.",
                                preferences.doubleTapToLock
                            ) { onDoubleTapLockChange(it) }
                            SettingToggle(
                                "Long press Home to open settings",
                                "Open Dislauncher Settings when you press and hold an empty area of Home.",
                                preferences.longPressHomeOpensSettings
                            ) { enabled ->
                                onPreferencesChange(preferences.copy(longPressHomeOpensSettings = enabled))
                            }
                            SettingToggle(
                                "Swipe down for notifications",
                                "Opens the Android notification panel.",
                                preferences.swipeDownNotifications
                            ) { enabled ->
                                onPreferencesChange(preferences.copy(swipeDownNotifications = enabled))
                            }
                        }
                    }

                    SettingsCategory.ACCESS -> {
                        SettingsGroup("Feature access") {
                            SettingAction(
                                "Music and notification access",
                                if (notificationAccessEnabled) "Enabled"
                                else "Not enabled",
                                "Open settings"
                            ) {
                                openSystemPage(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                            }
                            SettingAction(
                                "Device admin",
                                if (adminActive) "Active"
                                else "Not active · needed to lock the screen",
                                "Manage"
                            ) {
                                if (!adminActive) {
                                    onDoubleTapLockChange(true)
                                } else {
                                    openSystemPage(Intent(Settings.ACTION_SECURITY_SETTINGS))
                                }
                            }
                        }
                        SettingsGroup("Android permissions") {
                            SettingAction(
                                "Calendar access",
                                if (calendarPermission) "Allowed"
                                else "Not allowed",
                                "Manage",
                                { openAppPermissionSettings() }
                            )
                            SettingAction(
                                "Location access",
                                if (locationPermission) "Allowed"
                                else "Not allowed",
                                "Manage",
                                ::openAppPermissionSettings
                            )
                        }
                        SettingsGroup("System and launcher") {
                            SettingAction(
                                "Default launcher",
                                if (defaultLauncher) "Dislauncher is the default Home app"
                                else "Another Home app is selected",
                                "Open settings"
                            ) {
                                openSystemPage(Intent(Settings.ACTION_HOME_SETTINGS))
                            }
                            SettingAction(
                                "Android settings",
                                "Open device settings.",
                                "Open"
                            ) {
                                openSystemPage(Intent(Settings.ACTION_SETTINGS))
                            }
                        }
                    }

                    SettingsCategory.ABOUT -> {
                        SettingsGroup("Dislauncher") {
                            Text(
                                "A minimalist, text-first Android launcher.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                "Version $appVersion",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                            )
                            SettingAction(
                                "What's New",
                                "See what's changed in every release.",
                                "View"
                            ) { showChangelog = true }
                            SettingAction(
                                "Restart Launcher",
                                "Reloads the launcher interface.",
                                "Restart"
                            ) { showRestartConfirmation = true }
                        }
                        SettingsGroup("Updates") {
                            SettingAction(
                                "Check for Updates",
                                "Check the latest stable release on GitHub.",
                                "Check"
                            ) {
                                updateDialogState = ReleaseUpdateResult.Checking
                                updateCheckRequested = true
                            }
                        }
                        SettingsGroup("Community & Support") {
                            SettingAction(
                                "Ko-fi",
                                "Support the development of Dislauncher.",
                                "Open"
                            ) {
                                openSystemPage(Intent(Intent.ACTION_VIEW, Uri.parse("https://ko-fi.com/insomdroid")))
                            }
                            SettingAction(
                                "Website",
                                "Project information and documentation.",
                                "Open"
                            ) {
                                openSystemPage(Intent(Intent.ACTION_VIEW, Uri.parse("https://insomdroid.github.io/Dislauncher/")))
                            }
                            SettingAction(
                                "Reddit",
                                "Join the Dislauncher community.",
                                "Open"
                            ) {
                                openSystemPage(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.reddit.com/r/Dislauncher/")))
                            }
                            SettingAction(
                                "GitHub",
                                "Source code and project issues.",
                                "Open"
                            ) {
                                openSystemPage(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/insomdroid/Dislauncher")))
                            }
                        }
                    }

                    null -> Unit
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }

    if (openDialog == "wallpaperTarget") {
        AlertDialog(
            onDismissRequest = { openDialog = "" },
            title = { Text("Apply wallpaper") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "Choose where to apply this wallpaper.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    listOf(
                        "home" to "Home screen",
                        "lock" to "Lock screen",
                        "both" to "Both screens"
                    ).forEach { (value, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { wallpaperTarget = value }
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = wallpaperTarget == value,
                                onClick = { wallpaperTarget = value }
                            )
                            Text(label, modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { openDialog = "" }) {
                    Text("Cancel")
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val target = wallpaperTarget
                        openDialog = ""
                        onPreferencesChange(preferences.copy(wallpaperTarget = target))
                        if (preferences.wallpaperMode == "image" && preferences.wallpaperUri == null) {
                            android.widget.Toast.makeText(
                                context,
                                "Choose an image first.",
                                android.widget.Toast.LENGTH_LONG
                            ).show()
                            onChooseWallpaper()
                        } else onApplyWallpaperToSystem(target) { result ->
                            val message = when (result) {
                                "success" -> when (target) {
                                    "home" -> "Wallpaper applied to the Home screen."
                                    "lock" -> "Wallpaper applied to the lock screen."
                                    else -> "Wallpaper applied to both screens."
                                }
                                "home_success_lock_failure" -> "Home screen updated; lock screen wallpaper failed."
                                "home_failure_lock_success" -> "Lock screen updated; Home screen wallpaper failed."
                                else -> "Wallpaper could not be applied. Please try again."
                            }
                            android.widget.Toast.makeText(
                                context,
                                message,
                                android.widget.Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                ) {
                    Text("Apply")
                }
            }
        )
    } else if (openDialog == "hidden") {
        AppSelectionDialog(
            title = "Manage hidden apps",
            subtitle = "Checked apps are hidden from Home and the drawer.",
            apps = apps,
            checkedPackages = hiddenApps,
            onToggle = onToggleHidden,
            onDismiss = { openDialog = "" }
        )
    } else if (openDialog == "folders") {
        AlertDialog(
            onDismissRequest = { openDialog = ""; manageFolderName = null },
            title = {
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("Manage folders")
                    Text(
                        "Create folders, manage their apps, or delete a folder without uninstalling its apps.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 450.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    OutlinedTextField(
                        value = newFolderName,
                        onValueChange = { newFolderName = it.filterNot { ch -> ch == '\t' || ch == '\n' }.take(32) },
                        singleLine = true,
                        label = { Text("New folder name") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    TextButton(
                        enabled = newFolderName.isNotBlank() &&
                            preferences.appFolders.none { it.equals(newFolderName.trim(), ignoreCase = true) },
                        onClick = {
                            val name = newFolderName.trim()
                            if (name.isNotEmpty()) {
                                onPreferencesChange(preferences.copy(appFolders = preferences.appFolders + name))
                                newFolderName = ""
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Create folder") }
                    preferences.appFolders.sortedBy { it.lowercase() }.forEach { folder ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(folder, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            TextButton(onClick = { manageFolderName = folder }) { Text("Manage") }
                            TextButton(onClick = {
                                onPreferencesChange(
                                    preferences.copy(
                                        appFolders = preferences.appFolders - folder,
                                        folderAssignments = preferences.folderAssignments.filterNot {
                                            it.substringAfter('\t', "") == folder
                                        }.toSet()
                                    )
                                )
                            }) { Text("Delete") }
                        }
                    }
                    manageFolderName?.takeIf { it in preferences.appFolders }?.let { folder ->
                        HorizontalDivider()
                        Text(folder, style = MaterialTheme.typography.titleMedium)
                        val assigned = preferences.folderAssignments.mapNotNull { assignment ->
                            val parts = assignment.split('\t', limit = 2)
                            if (parts.size == 2 && parts[1] == folder) parts[0] else null
                        }.toSet()
                        Text(
                            "Turn on an app to add it to this folder. An app can belong to one folder at a time.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        apps.filter { !it.packageName.startsWith("internal:") }
                            .sortedBy { it.label.lowercase() }
                            .forEach { app ->
                                val assignedToThisFolder = preferences.folderAssignments.any {
                                    it.startsWith(app.packageName + "\t") && it.substringAfter("\t", "") == folder
                                }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                                ) {
                                    Text(
                                        app.label,
                                        modifier = Modifier.weight(1f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Switch(
                                        checked = assignedToThisFolder,
                                        onCheckedChange = { enabled ->
                                            val withoutExisting = preferences.folderAssignments
                                                .filterNot { it.startsWith(app.packageName + "\t") }
                                                .toSet()
                                            val updated = if (enabled) withoutExisting + (app.packageName + "\t" + folder) else withoutExisting
                                            onPreferencesChange(preferences.copy(folderAssignments = updated))
                                        }
                                    )
                                }
                            }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { openDialog = ""; manageFolderName = null }) { Text("Done") }
            }
        )
    } else if (openDialog == "favorites") {
        AppSelectionDialog(
            title = "Manage favourites",
            subtitle = "Checked apps appear in the Home favourites list.",
            apps = apps,
            checkedPackages = favorites,
            onToggle = onToggleFavorite,
            onDismiss = { openDialog = "" }
        )
    } else if (openDialog == "notificationdots") {
        AppSelectionDialog(
            title = "Notification dot apps",
            subtitle = "Only selected apps can show a dot on Home when they have active non-persistent notifications.",
            apps = apps,
            checkedPackages = preferences.notificationDotPackages,
            onToggle = { packageName ->
                val nextPackages = preferences.notificationDotPackages.toMutableSet().apply {
                    if (!add(packageName)) remove(packageName)
                }
                onPreferencesChange(preferences.copy(notificationDotPackages = nextPackages))
            },
            onDismiss = { openDialog = "" }
        )
    } else if (openDialog == "iconpack") {
        AlertDialog(
            onDismissRequest = { openDialog = "" },
            title = { Text("Icon pack") },
            text = {
                Column(
                    Modifier.fillMaxWidth().heightIn(max = 400.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    IconPackChoice(
                        "System default",
                        "system",
                        preferences.iconPack
                    ) {
                        onPreferencesChange(preferences.copy(iconPack = "system"))
                        openDialog = ""
                    }
                    if (detectedIconPacks.isEmpty()) {
                        Text(
                            "No compatible installed icon pack was detected.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                    detectedIconPacks.forEach { pack ->
                        IconPackChoice(pack.label, pack.packageName, preferences.iconPack) {
                            onPreferencesChange(preferences.copy(iconPack = pack.packageName))
                            openDialog = ""
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { openDialog = "" }) { Text("Done") }
            }
        )
    }

    when (val result = updateDialogState) {
        ReleaseUpdateResult.Checking -> {
            AlertDialog(
                onDismissRequest = {
                    updateDialogState = null
                    updateCheckRequested = false
                },
                title = { Text("Checking for updates") },
                text = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        Text("Checking the latest stable release on GitHub.")
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        updateDialogState = null
                        updateCheckRequested = false
                    }) {
                        Text("Cancel")
                    }
                }
            )
        }
        is ReleaseUpdateResult.Success -> {
            val title = when {
                result.updateAvailable -> "Update available"
                result.isDevelopmentBuild -> "Development build"
                else -> "You're up to date"
            }
            val message = when {
                result.updateAvailable -> buildString {
                    append("Dislauncher ")
                    append(result.release.version)
                    append(" is available.")
                    if (result.release.publishedAt.isNotBlank()) {
                        append("\\n\\nReleased: ")
                        append(result.release.publishedAt.take(10))
                    }
                    if (result.release.notes.isNotBlank()) {
                        append("\\n\\n")
                        append(result.release.notes.take(700))
                        if (result.release.notes.length > 700) append("…")
                    }
                }
                result.isDevelopmentBuild -> "Installed: ${result.installedVersion}\\nLatest stable: ${result.release.version}\\n\\nThis development build is newer than the latest stable release. No downgrade is offered."
                else -> "Installed: ${result.installedVersion}\\nLatest stable: ${result.release.version}"
            }
            AlertDialog(
                onDismissRequest = { updateDialogState = null },
                title = { Text(title) },
                text = { Text(message) },
                confirmButton = {
                    if (result.updateAvailable) {
                        TextButton(
                            onClick = {
                                updateDialogState = null
                                openSystemPage(Intent(Intent.ACTION_VIEW, Uri.parse(result.release.releaseUrl)))
                            }
                        ) { Text("View Release") }
                    } else {
                        TextButton(onClick = { updateDialogState = null }) { Text("Done") }
                    }
                },
                dismissButton = {
                    if (result.updateAvailable) {
                        TextButton(onClick = { updateDialogState = null }) { Text("Later") }
                    }
                }
            )
        }
        is ReleaseUpdateResult.Failure -> {
            AlertDialog(
                onDismissRequest = { updateDialogState = null },
                title = { Text("Couldn't check for updates") },
                text = { Text(result.message) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            updateDialogState = ReleaseUpdateResult.Checking
                            updateCheckRequested = true
                        }
                    ) { Text("Retry") }
                },
                dismissButton = {
                    TextButton(onClick = { updateDialogState = null }) { Text("Close") }
                }
            )
        }
        null -> Unit
    }

    if (showRestartConfirmation) {
        AlertDialog(
            onDismissRequest = { showRestartConfirmation = false },
            title = { Text("Restart Launcher?") },
            text = {
                Text(
                    "Dislauncher will reload its interface. Your settings and favourites will be kept."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showRestartConfirmation = false
                        onRestartLauncher()
                    }
                ) {
                    Text("Restart")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRestartConfirmation = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun SettingsCategoryRow(
    icon: Int,
    title: String,
    description: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp)
            .clip(RoundedCornerShape(LauncherShapeTokens.large))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(LauncherShapeTokens.large))
                .padding(horizontal = 13.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(LauncherIconTokens.standard)
            )
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Icon(painterResource(R.drawable.ic_symbol_chevron_right), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(LauncherIconTokens.compact))
    }
}

private fun isDislauncherDefaultHome(context: Context): Boolean {
    return runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = context.getSystemService(Context.ROLE_SERVICE) as RoleManager
            roleManager.isRoleHeld(RoleManager.ROLE_HOME)
        } else {
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            @Suppress("DEPRECATION")
            context.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
                ?.activityInfo?.packageName == context.packageName
        }
    }.getOrDefault(false)
}
