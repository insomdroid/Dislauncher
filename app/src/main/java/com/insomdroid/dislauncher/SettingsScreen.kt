package com.insomdroid.dislauncher

import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    onDoubleTapLockChange: (Boolean) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onToggleHidden: (String) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var detectedIconPacks by remember { mutableStateOf(iconPacks) }
    LaunchedEffect(context) {
        detectedIconPacks = findIconPacks(context)
    }
    var openDialog by remember { mutableStateOf("") }
    var showFontsDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val wallpaperPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            onPreferencesChange(preferences.copy(wallpaperMode = "image", wallpaperUri = uri.toString()))
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Settings",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    TextButton(onClick = onBack) {
                        Text("←", fontSize = 24.sp)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SettingsGroup("Manage Apps", "Choose what appears on the Home screen and in the drawer.") {
                SettingAction(
                    "Manage Hidden Apps",
                    "${hiddenApps.size} hidden · hide apps without uninstalling",
                    "Manage",
                    { openDialog = "hidden" }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                SettingAction(
                    "Manage Favourites",
                    "${favorites.size}/6 pinned apps · long-press apps in the drawer as well",
                    "Manage",
                    { openDialog = "favorites" }
                )
            }

            SettingsGroup("Wallpaper", "Use the current system wallpaper, a photo, or a solid color.") {
                ChoiceRow(
                    "Wallpaper source",
                    preferences.wallpaperMode,
                    listOf("default" to "Theme default", "system" to "System", "image" to "Photo", "solid" to "Solid color")
                ) { onPreferencesChange(preferences.copy(wallpaperMode = it)) }

                if (preferences.wallpaperMode == "image") {
                    SettingAction(
                        "Choose photo",
                        if (preferences.wallpaperUri != null) "Photo selected" else "No photo selected",
                        "Choose",
                        { wallpaperPicker.launch(arrayOf("image/*")) }
                    )
                }
                if (preferences.wallpaperMode == "solid") {
                    ChoiceRow(
                        "Solid color",
                        preferences.wallpaperColor,
                        WALLPAPER_COLOR_OPTIONS.map { it.key to it.label }
                    ) { onPreferencesChange(preferences.copy(wallpaperColor = it)) }
                }
                ChoiceRow(
                    "Apply to",
                    preferences.wallpaperTarget,
                    listOf("home" to "Home screen", "lock" to "Lock screen", "both" to "Both")
                ) { onPreferencesChange(preferences.copy(wallpaperTarget = it)) }
                SettingAction(
                    "Apply wallpaper",
                    when (preferences.wallpaperMode) {
                        "default" -> "Use black in dark mode and white in light mode."
                        "image" -> "Apply the selected photo to the chosen screen."
                        "solid" -> "Apply the selected color to the chosen screen."
                        else -> "Use the current system wallpaper as the Home background."
                    },
                    "Apply",
                    {
                        scope.launch {
                            val applied = if (preferences.wallpaperMode == "system" || preferences.wallpaperMode == "default") true else withContext(Dispatchers.IO) {
                                applySelectedWallpaper(context, preferences.wallpaperMode, preferences.wallpaperUri, preferences.wallpaperColor, preferences.wallpaperTarget)
                            }
                            android.widget.Toast.makeText(
                                context,
                                if (applied) {
                                    if (preferences.wallpaperMode == "default") "Theme-matched wallpaper enabled." else "Wallpaper applied."
                                } else "Choose a photo or solid color first.",
                                android.widget.Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                )
            }

            SettingsGroup("Theme", "Make the launcher feel personal with clear choices and expressive Material surfaces.") {
                Column(Modifier.fillMaxWidth().padding(15.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    ChoiceRow(
                        "Theme",
                        preferences.themeMode,
                        listOf(
                            "system" to "System",
                            "light" to "Light",
                            "dark" to "Dark",
                            "black" to "Black"
                        )
                    ) { onPreferencesChange(preferences.copy(themeMode = it)) }
                    SettingToggle(
                        "Dynamic color",
                        "Use Android wallpaper colors for accents and controls on Android 12+. Dark mode keeps its pure-black background.",
                        preferences.dynamicColor
                    ) { onPreferencesChange(preferences.copy(dynamicColor = it)) }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    SettingAction(
                        "Custom font",
                        when (preferences.fontMode) {
                            "mono" -> "Monospace"
                            "custom" -> preferences.customFontName ?: "Custom font"
                            else -> "System font"
                        },
                        "Choose",
                        { showFontsDialog = true }
                    )
                    ChoiceRow(
                        "Font size",
                        preferences.fontSize,
                        listOf("small" to "Small", "medium" to "Medium", "large" to "Large")
                    ) { onPreferencesChange(preferences.copy(fontSize = it)) }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    SettingToggle(
                        "Show app icons",
                        "Show compact app icons beside names in Home and the drawer.",
                        preferences.showAppIcons
                    ) { onPreferencesChange(preferences.copy(showAppIcons = it)) }
                    if (preferences.showAppIcons) {
                        ChoiceRow(
                            "Built-in icon shape",
                            preferences.iconShape,
                            listOf(
                                "original" to "Original",
                                "circle" to "Circle",
                                "outline_square" to "Outline square",
                                "filled_square" to "Filled square",
                                "filled_rectangle" to "Rectangle"
                            )
                        ) { onPreferencesChange(preferences.copy(iconShape = it)) }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(
                                "Built-in icon shape",
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                "Enable Show app icons to change the shape.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                    if (preferences.showAppIcons) {
                        SettingAction(
                            "Icon pack",
                            detectedIconPacks.firstOrNull { it.packageName == preferences.iconPack }?.label
                                ?: if (preferences.iconPack == "system") "System default" else "Selected pack not installed",
                            "Choose",
                            {
                                detectedIconPacks = findIconPacks(context)
                                openDialog = "iconpack"
                            }
                        )
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(
                                "Icon pack",
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                "Enable Show app icons to choose an icon pack.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }

            SettingsGroup("Home screen", "Turn each lightweight item on or off.") {
                SettingToggle("Show status bar", "Keep Android's status bar visible at the top.", !preferences.hideStatusBar) {
                    onPreferencesChange(preferences.copy(hideStatusBar = !it))
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                SettingToggle("Show clock", "Tap the clock on Home to open the clock app.", preferences.showClock) {
                    onPreferencesChange(preferences.copy(showClock = it))
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                SettingToggle("Show date", "Tap the date to open the calendar app.", preferences.showDate) {
                    onPreferencesChange(preferences.copy(showDate = it))
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                SettingToggle("Show calendar agenda", "Read today's upcoming events on Home; Android asks for calendar access when needed.", preferences.showCalendarShortcut) {
                    onPreferencesChange(preferences.copy(showCalendarShortcut = it))
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                SettingToggle("Show music player", "Show the active track and playback controls on Home. Media access may be requested.", preferences.showMusic) {
                    onPreferencesChange(preferences.copy(showMusic = it))
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                SettingToggle("Show weather", "Uses approximate device location and Open-Meteo weather data.", preferences.showWeather) {
                    onPreferencesChange(preferences.copy(showWeather = it))
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                ChoiceRow("Weather unit", preferences.weatherUnit, listOf("C" to "°C", "F" to "°F")) {
                    onPreferencesChange(preferences.copy(weatherUnit = it))
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                ChoiceRow(
                    "Time format",
                    preferences.timeFormat,
                    listOf("system" to "System", "12" to "12-hour", "24" to "24-hour")
                ) { onPreferencesChange(preferences.copy(timeFormat = it)) }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                SettingToggle("Battery % widget", "Show the current battery percentage on Home.", preferences.showBattery) {
                    onPreferencesChange(preferences.copy(showBattery = it))
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                SettingToggle("Show day in %", "Display a thin progress bar for today's elapsed time.", preferences.showDayProgress) {
                    onPreferencesChange(preferences.copy(showDayProgress = it))
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                SettingToggle("Show year in %", "Display a thin progress bar for the year.", preferences.showYearProgress) {
                    onPreferencesChange(preferences.copy(showYearProgress = it))
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                SettingToggle("Show Settings button", "When disabled, long-press empty space on Home to open Settings.", preferences.showSettingsButton) {
                    onPreferencesChange(preferences.copy(showSettingsButton = it))
                }
            }

            SettingsGroup("App drawer", "Keep the list compact and make navigation easier.") {
                SettingToggle("Show search bar", "Show the app search field.", preferences.showSearchBar) {
                    onPreferencesChange(preferences.copy(showSearchBar = it))
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                SettingToggle("Auto-launch keyboard", "Focus the search field and open the keyboard when the drawer opens.", preferences.autoLaunchKeyboard) {
                    onPreferencesChange(preferences.copy(autoLaunchKeyboard = it))
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                SettingToggle("Hide system apps", "Hide apps marked by Android as system apps.", preferences.hideSystemApps) {
                    onPreferencesChange(preferences.copy(hideSystemApps = it))
                }
            }

            SettingsGroup("Gestures", "Optional shortcuts. Screen lock requires device-admin approval.") {
                SettingToggle("Double tap to lock", "Android asks you to enable dislauncher as a device admin before this works.", preferences.doubleTapToLock) {
                    onDoubleTapLockChange(it)
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                SettingToggle(
                    "Swipe down for notifications",
                    "Swipe down across the Home screen to open the notification shade. Some Android builds may restrict this system action.",
                    preferences.swipeDownNotifications
                ) { enabled ->
                    onPreferencesChange(preferences.copy(swipeDownNotifications = enabled))
                }
            }

            SettingsGroup("Misc", "System-owned settings open outside dislauncher.") {
                SettingAction(
                    "Music controls access",
                    "Allow Dislauncher to read active media sessions so Previous, Play and Next can control your music app.",
                    "Open",
                    {
                        runCatching {
                            context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                        }
                    }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                SettingAction(
                    "Change default launcher",
                    "Choose which Home app Android should use.",
                    "Open",
                    {
                        runCatching { context.startActivity(Intent(Settings.ACTION_HOME_SETTINGS)) }
                    }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                SettingAction(
                    "Device settings",
                    "Open Android Settings.",
                    "Open",
                    { runCatching { context.startActivity(Intent(Settings.ACTION_SETTINGS)) } }
                )
            }

            SettingsGroup("About", "A minimalist, text-first Android launcher.") {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text("Dislauncher", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Version 0.1.0 · Built for a calmer Home screen.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 16.sp,
                        maxLines = 2,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }

    if (showFontsDialog) {
        AlertDialog(
            onDismissRequest = { showFontsDialog = false },
            title = { Text("Custom Fonts") },
            text = {
                Column(Modifier.fillMaxWidth()) {
                    IconPackChoice("System", "system", preferences.fontMode) {
                        onPreferencesChange(preferences.copy(fontMode = "system"))
                        showFontsDialog = false
                    }
                    IconPackChoice("Monospace", "mono", preferences.fontMode) {
                        onPreferencesChange(preferences.copy(fontMode = "mono"))
                        showFontsDialog = false
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .clickable {
                                showFontsDialog = false
                                onChooseCustomFont()
                            }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = preferences.fontMode == "custom",
                            onClick = {
                                showFontsDialog = false
                                onChooseCustomFont()
                            }
                        )
                        Column(Modifier.weight(1f)) {
                            Text("Choose .ttf / .otf file")
                            Text(
                                preferences.customFontName ?: "Maximum 5 MB",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showFontsDialog = false }) { Text("Done") }
            }
        )
    }

    if (openDialog == "hidden") {
        AppSelectionDialog(
            title = "Manage hidden apps",
            subtitle = "Checked apps are hidden from Home and the drawer.",
            apps = apps,
            checkedPackages = hiddenApps,
            onToggle = onToggleHidden,
            onDismiss = { openDialog = "" }
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
    } else if (openDialog == "iconpack") {
        AlertDialog(
            onDismissRequest = { openDialog = "" },
            title = { Text("Icon pack") },
            text = {
                Column(Modifier.fillMaxWidth().heightIn(max = 400.dp).verticalScroll(rememberScrollState())) {
                    IconPackChoice("System default", "system", preferences.iconPack) {
                        onPreferencesChange(preferences.copy(iconPack = "system"))
                        openDialog = ""
                    }
                    if (detectedIconPacks.isEmpty()) {
                        Text(
                            "No compatible installed icon pack was detected. Packs supporting ADW/Nova-style appfilter.xml are used.",
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
            confirmButton = { TextButton(onClick = { openDialog = "" }) { Text("Done") } }
        )
    }
}
