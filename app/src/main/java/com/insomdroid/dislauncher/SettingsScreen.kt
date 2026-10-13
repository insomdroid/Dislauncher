package com.insomdroid.dislauncher

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
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
    onDoubleTapLockChange: (Boolean) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onToggleHidden: (String) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var openDialog by remember { mutableStateOf("") }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            LargeTopAppBar(
                title = {
                    Column {
                        Text("Settings")
                        Text(
                            "Launcher preference",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("‹  Home", fontSize = 13.sp) }
                },
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
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
                    "${favorites.size} pinned apps · long-press apps in the drawer as well",
                    "Manage",
                    { openDialog = "favorites" }
                )
            }

            SettingsGroup("Appearance", "Adjust the launcher without oversized widgets.") {
                Column(Modifier.fillMaxWidth().padding(15.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    ChoiceRow(
                        "Theme",
                        preferences.themeMode,
                        listOf("system" to "Auto", "light" to "Light", "dark" to "Dark")
                    ) { onPreferencesChange(preferences.copy(themeMode = it)) }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    ChoiceRow(
                        "Font",
                        preferences.fontMode,
                        listOf("sans" to "Sans", "serif" to "Serif", "mono" to "Mono")
                    ) { onPreferencesChange(preferences.copy(fontMode = it)) }
                    Text("The selected font applies to Home, the app drawer and settings.", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    ChoiceRow(
                        "Font size",
                        preferences.fontSize,
                        listOf("small" to "Small", "medium" to "Default", "large" to "Large")
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
                SettingToggle("Show calendar shortcut", "Show a compact Calendar action on Home.", preferences.showCalendarShortcut) {
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
                SettingToggle("Show battery level", "Display the current battery percentage.", preferences.showBattery) {
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
                SettingToggle("Pin year in % widget", "Place the year progress directly beneath the clock and date.", preferences.pinYearProgress) {
                    onPreferencesChange(preferences.copy(pinYearProgress = it))
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                SettingToggle("Show inspiring quote", "Tap the quote on Home to cycle through the built-in list.", preferences.showQuote) {
                    onPreferencesChange(preferences.copy(showQuote = it))
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
                SettingToggle("Show music widget", "Opens the system music app or a detected music player.", preferences.showMusic) {
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
                SettingToggle("Show character scroll", "Keep the letter scrubber visible on the right edge of the drawer.", preferences.showCharacterScroll) {
                    onPreferencesChange(preferences.copy(showCharacterScroll = it))
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
                SettingToggle("Swipe down for notifications", "Swipe down from the very top edge. Some Android versions block this for third-party apps.", preferences.swipeDownNotifications) {
                    onPreferencesChange(preferences.copy(swipeDownNotifications = it))
                }
            }

            SettingsGroup("Android", "System-owned settings open outside dislauncher.") {
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
            Spacer(Modifier.height(16.dp))
        }
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
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            options.forEach { (value, label) ->
                FilterChip(
                    selected = selected == value,
                    onClick = { onSelected(value) },
                    label = { Text(label, maxLines = 1) }
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
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.padding(horizontal = 2.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
        ) {
            Column(content = content)
        }
    }
}

@Composable
private fun SettingToggle(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onCheckedChange(!checked) }
            .padding(horizontal = 15.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f).padding(end = 12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 17.sp
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
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
            .padding(horizontal = 15.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f).padding(end = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(description, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 17.sp)
        }
        TextButton(onClick = onClick) { Text(actionLabel, maxLines = 1) }
    }
}
