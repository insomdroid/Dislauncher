package com.insomdroid.dislauncher

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun AppsScreen(
    apps: List<LauncherApp>,
    favorites: Set<String>,
    query: String,
    onQueryChange: (String) -> Unit,
    isDark: Boolean,
    fontFamily: FontFamily,
    onLaunch: (LauncherApp) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onBack: () -> Unit
) {
    val background = if (isDark) Color.Black else Color(0xFFFFFCF7)
    val mainText = MaterialTheme.colorScheme.onBackground
    val secondary = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.62f)
    val filtered = remember(apps, query) { apps.filter { it.label.contains(query.trim(), ignoreCase = true) } }
    val groups = remember(filtered) {
        filtered.groupBy { it.label.firstOrNull()?.uppercaseChar()?.toString() ?: "#" }.toSortedMap()
    }

    Column(
        modifier = Modifier.fillMaxSize().background(background)
            .windowInsetsPadding(WindowInsets.systemBars).padding(horizontal = 22.dp)
    ) {
        Spacer(Modifier.height(22.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom) {
            Column(modifier = Modifier.weight(1f)) {
                Text("All apps", color = mainText, fontSize = 32.sp, fontWeight = FontWeight.Light)
                Text("${filtered.size} apps · hold a name to pin", color = secondary, fontSize = 11.sp)
            }
            TextButton(onClick = onBack) { Text("Home") }
        }
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(value = query, onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth(), singleLine = true,
            label = { Text("Search apps") }, shape = MaterialTheme.shapes.extraLarge)
        Spacer(Modifier.height(8.dp))
        LazyColumn(modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 22.dp, top = 4.dp),
            verticalArrangement = Arrangement.spacedBy(1.dp)) {
            if (filtered.isEmpty()) {
                item(key = "empty-app-search") {
                    Text(if (apps.isEmpty()) "Loading apps…" else "No matching apps",
                        color = secondary, fontSize = 14.sp, modifier = Modifier.padding(vertical = 20.dp))
                }
            }
            groups.forEach { (letter, groupApps) ->
                item(key = "letter-$letter") {
                    Text(letter, color = secondary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 13.dp, bottom = 4.dp))
                }
                items(groupApps, key = { it.packageName }) { app ->
                    Row(modifier = Modifier.fillMaxWidth().combinedClickable(
                        onClick = { onLaunch(app) },
                        onLongClick = { onToggleFavorite(app.packageName) }
                    ).padding(vertical = 11.dp, horizontal = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(app.label, color = mainText, fontSize = 17.sp, lineHeight = 22.sp,
                            fontFamily = fontFamily, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f))
                        if (app.packageName in favorites) {
                            Text("HOME", color = secondary, fontSize = 8.sp, letterSpacing = 0.9.sp)
                        }
                    }
                }
            }
        }
    }
}
