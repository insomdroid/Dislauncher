package com.insomdroid.dislauncher

import android.app.AppOpsManager
import android.content.Intent
import android.os.Process
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    var usageAccessAllowed by remember { mutableStateOf(hasUsageStatsPermission(context)) }
    var openDialog by remember { mutableStateOf("") }
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                usageAccessAllowed = hasUsageStatsPermission(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    var showFontsDialog by remember { mutableStateOf(false) }

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
            SettingsGroup("Manage apps", "Choose what appears on the Home screen and in the drawer.") {
                SettingAction(
                    "Manage hidden apps",
                    "${hiddenApps.size} hidden · hide apps without uninstalling",
                    "Manage",
                    { openDialog = "hidden" }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                SettingAction(
                    "Manage favourites",
                    "${favorites.size}/5 pinned apps · long-press apps in the drawer as well",
                    "Manage",
                    { openDialog = "favorites" }
                )
            }

            SettingsGroup("Appearance", "Make the launcher feel personal with clear choices and expressive Material surfaces.") {
                Column(Modifier.fillMaxWidth().padding(15.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    ChoiceRow(
                        "Theme",
                        preferences.themeMode,
                        listOf(
                            "system" to "System",
                            "light" to "Light",
                            "dark" to "Dark"
                        )
                    ) { onPreferencesChange(preferences.copy(themeMode = it)) }
                    Text(
                        "System follows Android appearance. Light uses a light palette. Dark always uses a pure-black background.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    SettingToggle(
                        "Dynamic color",
                        "Use Android wallpaper colors for accents and controls on Android 12+. Dark mode keeps its pure-black background.",
                        preferences.dynamicColor
                    ) { onPreferencesChange(preferences.copy(dynamicColor = it)) }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    SettingAction(
                        "Custom Fonts",
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
                    SettingAction(
                        "Icon pack",
                        iconPacks.firstOrNull { it.packageName == preferences.iconPack }?.label
                            ?: if (preferences.iconPack == "system") "System default" else "Selected pack not installed",
                        "Choose",
                        { openDialog = "iconpack" }
                    )
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
                SettingToggle("Show screen time", "Requires Android Usage Access. Tap its Home row to open access settings.", preferences.showScreenTime) {
                    onPreferencesChange(preferences.copy(showScreenTime = it))
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                SettingToggle("Show default tasks", "A small task list stored locally on this device.", preferences.showTasks) {
                    onPreferencesChange(preferences.copy(showTasks = it))
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                SettingToggle("Show default call", "A compact shortcut that opens the Android dialer.", preferences.showCall) {
                    onPreferencesChange(preferences.copy(showCall = it))
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                SettingToggle("Show music player", "Show the active track and playback controls on Home. Media access may be requested.", preferences.showMusic) {
                    onPreferencesChange(preferences.copy(showMusic = it))
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

            SettingsGroup("Android", "System-owned settings open outside dislauncher.") {
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
                    "Usage access",
                    if (usageAccessAllowed) {
                        "Screen time access is enabled for Dislauncher."
                    } else {
                        "Grant Dislauncher access to app usage so Screen time can show today's total."
                    },
                    if (usageAccessAllowed) "Allowed" else "Grant",
                    {
                        runCatching {
                            context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
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
                    if (iconPacks.isEmpty()) {
                        Text(
                            "No compatible installed icon pack was detected. Packs supporting ADW/Nova-style appfilter.xml are used.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                    iconPacks.forEach { pack ->
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

@Composable
private fun AppSelectionDialog(
    title: String,
    subtitle: String,
    apps: List<LauncherApp>,
    checkedPackages: Set<String>,
    onToggle: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(title)
                Text(subtitle, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        text = {
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 450.dp)) {
                items(apps, key = { it.packageName }) { app ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { onToggle(app.packageName) }
                            .padding(vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = app.packageName in checkedPackages,
                            onCheckedChange = { onToggle(app.packageName) }
                        )
                        Text(app.label, modifier = Modifier.weight(1f), maxLines = 1)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } }
    )
}

@Composable
private fun IconPackChoice(
    label: String,
    value: String,
    selected: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected == value, onClick = onClick)
        Text(label, modifier = Modifier.weight(1f), maxLines = 2)
    }
}

@Composable
private fun ChoiceRow(
    title: String,
    selected: String,
    options: List<Pair<String, String>>,
    onSelected: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            maxItemsInEachRow = 3
        ) {
            options.forEach { (value, label) ->
                FilterChip(
                    selected = selected == value,
                    onClick = { onSelected(value) },
                    label = { Text(label, style = MaterialTheme.typography.labelLarge) }
                )
            }
        }
    }
}

@Composable
private fun SettingsGroup(
    title: String,
    subtitle: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(2.dp))
            Column(content = content)
        }
    }
}

private fun hasUsageStatsPermission(context: android.content.Context): Boolean {
    val appOps = context.getSystemService(android.content.Context.APP_OPS_SERVICE) as? AppOpsManager
        ?: return false
    return runCatching {
        appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName
        ) == AppOpsManager.MODE_ALLOWED
    }.getOrDefault(false)
}

@Composable
private fun SettingToggle(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f)
                .clip(RoundedCornerShape(18.dp))
                .clickable { onCheckedChange(!checked) }
                .padding(start = 0.dp, top = 9.dp, end = 14.dp, bottom = 9.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 16.sp,
                maxLines = 2,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                checkedTrackColor = MaterialTheme.colorScheme.primary,
                checkedBorderColor = MaterialTheme.colorScheme.primary,
                uncheckedThumbColor = Color(0xFFBDBDBD),
                uncheckedTrackColor = Color(0xFF424242),
                uncheckedBorderColor = Color(0xFF808080)
            )
        )
    }
}

@Composable
private fun SettingAction(
    title: String,
    description: String,
    actionLabel: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f).padding(end = 10.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 16.sp,
                maxLines = 2,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
        }
        TextButton(onClick = onClick) { Text(actionLabel) }
    }
}
