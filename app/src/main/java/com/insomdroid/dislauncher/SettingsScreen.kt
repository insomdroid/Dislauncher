package com.insomdroid.dislauncher

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsScreen(
    themeMode: String,
    fontMode: String,
    showProgress: Boolean,
    showQuote: Boolean,
    hideSystemApps: Boolean,
    onThemeModeChange: (String) -> Unit,
    onFontModeChange: (String) -> Unit,
    onProgressChange: (Boolean) -> Unit,
    onQuoteChange: (Boolean) -> Unit,
    onHideSystemAppsChange: (Boolean) -> Unit,
    onBack: () -> Unit
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            LargeTopAppBar(
                title = {
                    Column {
                        Text("Settings")
                        Text("Make this space yours", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = { TextButton(onClick = onBack) { Text("‹  Home", fontSize = 13.sp) } },
                colors = TopAppBarDefaults.largeTopAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(innerPadding).verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Spacer(Modifier.height(2.dp))
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("PERSONALIZE YOUR SPACE", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary, letterSpacing = 1.1.sp)
                Text("Less noise.\nMore you.", style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Medium, lineHeight = 42.sp)
                Text("Simple by default, flexible when you need it.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            SettingsGroup(title = "Appearance", subtitle = "Choose your look and reading style.") {
                Column(modifier = Modifier.fillMaxWidth().padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text("Theme", style = MaterialTheme.typography.titleMedium)
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        listOf("system" to "Auto", "light" to "Light", "dark" to "Dark")
                            .forEachIndexed { index, option ->
                                SegmentedButton(selected = themeMode == option.first,
                                    onClick = { onThemeModeChange(option.first) },
                                    shape = SegmentedButtonDefaults.itemShape(index = index, count = 3)) {
                                    Text(option.second, maxLines = 1)
                                }
                            }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
                    Text("Home font", style = MaterialTheme.typography.titleMedium)
                    Text("Applies to the clock, quote, and app names.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("sans" to "Sans", "serif" to "Serif", "mono" to "Mono").forEach { option ->
                            FilterChip(selected = fontMode == option.first,
                                onClick = { onFontModeChange(option.first) },
                                label = {
                                    Text(option.second, fontFamily = when (option.first) {
                                        "serif" -> FontFamily.Serif
                                        "mono" -> FontFamily.Monospace
                                        else -> FontFamily.SansSerif
                                    })
                                })
                        }
                    }
                }
            }

            SettingsGroup(title = "Home screen", subtitle = "Keep only the information you want.") {
                SettingToggle("Day and year progress", "Show lightweight progress bars on Home.",
                    showProgress, onProgressChange)
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
                SettingToggle("Inspiring quote", "Show a short quote; tap it on Home to change it.",
                    showQuote, onQuoteChange)
            }

            SettingsGroup(title = "App drawer", subtitle = "Control which apps appear in your list.") {
                SettingToggle("Hide system apps",
                    "Remove system apps from the drawer list. This does not uninstall them.",
                    hideSystemApps, onHideSystemAppsChange)
            }

            Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = MaterialTheme.shapes.extraLarge, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text("All features, no Pro tier.", style = MaterialTheme.typography.titleMedium)
                    Text("dislauncher is being built as a free, customizable launcher. More options will appear as each feature is implemented.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun SettingsGroup(title: String, subtitle: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(3.dp), modifier = Modifier.padding(horizontal = 2.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Card(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.extraLarge,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
            Column(content = content)
        }
    }
}

@Composable
private fun SettingToggle(title: String, description: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().clickable { onCheckedChange(!checked) }
        .padding(horizontal = 18.dp, vertical = 15.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f).padding(end = 14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(description, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 18.sp)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
