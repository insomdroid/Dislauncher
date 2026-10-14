package com.insomdroid.dislauncher

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.os.Process
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import java.util.Locale
import java.time.LocalDate
import java.time.ZoneId

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun AppsScreen(
    apps: List<LauncherApp>,
    favorites: Set<String>,
    query: String,
    onQueryChange: (String) -> Unit,
    preferences: LauncherPreferences,
    isDark: Boolean,
    isActive: Boolean,
    fontFamily: FontFamily,
    fontScale: Float,
    onLaunch: (LauncherApp) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onToggleHidden: (String) -> Unit,
    onRenameApp: (String, String) -> Unit,
    onOpenSettings: () -> Unit
) {
    val background = MaterialTheme.colorScheme.background
    val mainText = MaterialTheme.colorScheme.onBackground
    val secondary = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.62f)
    val filtered = remember(apps, query) {
        apps.filter { it.label.contains(query.trim(), ignoreCase = true) }
            .sortedBy { it.label.lowercase(Locale.getDefault()) }
    }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }
    var searchFocused by remember { mutableStateOf(false) }
    val searchColor = if (searchFocused || query.isNotEmpty()) mainText else secondary.copy(alpha = 0.65f)
    val firstIndexByLetter = remember(filtered) {
        val result = linkedMapOf<String, Int>()
        filtered.forEachIndexed { index, app ->
            val letter = app.label.firstOrNull()?.uppercaseChar()?.toString() ?: "#"
            if (letter !in result) result[letter] = index
        }
        result
    }
    var selectedApp by remember { mutableStateOf<LauncherApp?>(null) }
    var renamingApp by remember { mutableStateOf<LauncherApp?>(null) }
    var renameValue by remember { mutableStateOf("") }
    val context = androidx.compose.ui.platform.LocalContext.current
    var usageMinutesByPackage by remember { mutableStateOf<Map<String, Long>>(emptyMap()) }

    LaunchedEffect(isActive) {
        if (isActive) {
            usageMinutesByPackage = withContext(Dispatchers.IO) { readAppUsageMinutes(context) }
        }
    }

    LaunchedEffect(isActive, preferences.autoLaunchKeyboard, preferences.showSearchBar) {
        if (isActive && preferences.autoLaunchKeyboard && preferences.showSearchBar) {
            delay(180L)
            runCatching { focusRequester.requestFocus() }
            keyboard?.show()
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().background(background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(start = 16.dp, end = 14.dp, top = 4.dp, bottom = 8.dp)
    ) {
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 16.dp, top = 2.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                if (filtered.isEmpty()) {
                    item(key = "empty-app-list") {
                        Text(
                            if (apps.isEmpty()) "Loading apps…" else "No matching apps",
                            color = secondary,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(vertical = 18.dp)
                        )
                    }
                }
                items(filtered, key = { it.packageName }) { app ->
                    Row(
                        modifier = Modifier.fillMaxWidth().combinedClickable(
                            onClick = { onLaunch(app) },
                            onLongClick = { selectedApp = app }
                        ).padding(vertical = 8.dp, horizontal = 20.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (preferences.showAppIcons) {
                            AppIcon(app, preferences.iconPack, size = 24.dp)
                            Spacer(Modifier.width(9.dp))
                        }
                        Text(
                            app.label,
                            color = mainText,
                            fontSize = 25.sp * fontScale,
                            lineHeight = 32.sp * fontScale,
                            fontFamily = fontFamily,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        usageMinutesByPackage[app.packageName]?.takeIf { it > 0 }?.let { minutes ->
                            Text(
                                "${minutes}m",
                                color = secondary,
                                fontSize = 11.sp,
                                lineHeight = 15.sp,
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }
                    }
                }
                item(key = "dislauncher-settings") {
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .clickable(onClick = onOpenSettings)
                            .padding(vertical = 10.dp, horizontal = 20.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Canvas(modifier = Modifier.size(20.dp)) {
                            val lineStart = 1.5.dp.toPx()
                            val lineEnd = size.width - 1.5.dp.toPx()
                            val stroke = 1.4.dp.toPx()
                            val knobRadius = 1.8.dp.toPx()
                            val yPositions = listOf(size.height * 0.24f, size.height * 0.5f, size.height * 0.76f)
                            val knobPositions = listOf(size.width * 0.36f, size.width * 0.68f, size.width * 0.43f)
                            yPositions.forEachIndexed { index, y ->
                                drawLine(
                                    color = mainText,
                                    start = Offset(lineStart, y),
                                    end = Offset(lineEnd, y),
                                    strokeWidth = stroke
                                )
                                drawCircle(
                                    color = mainText,
                                    radius = knobRadius,
                                    center = Offset(knobPositions[index], y)
                                )
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Text(
                            "Dislauncher Settings",
                            color = mainText,
                            fontSize = 20.sp * fontScale,
                            lineHeight = 26.sp * fontScale,
                            fontFamily = fontFamily,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
        if (preferences.showSearchBar) {
            Spacer(Modifier.height(10.dp))
            Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(top = 3.dp)) {
                Box(
                    modifier = Modifier.fillMaxWidth().height(48.dp).padding(start = 12.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (query.isEmpty()) {
                        Text(
                            "Search Apps",
                            color = searchColor,
                            style = MaterialTheme.typography.bodyLarge,
                            fontSize = 15.sp * fontScale
                        )
                    }
                    BasicTextField(
                        value = query,
                        onValueChange = onQueryChange,
                        modifier = Modifier.fillMaxWidth().focusRequester(focusRequester).onFocusChanged { searchFocused = it.isFocused },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                            color = mainText,
                            fontFamily = fontFamily,
                            fontSize = 15.sp * fontScale
                        ),
                        cursorBrush = SolidColor(mainText)
                    )
                }
                HorizontalDivider(
                    modifier = Modifier.fillMaxWidth(),
                    thickness = 1.dp,
                    color = if (searchFocused || query.isNotEmpty()) mainText.copy(alpha = 0.9f) else searchColor.copy(alpha = 0.75f)
                )
            }
        }
    }
    selectedApp?.let { app ->
        AlertDialog(
            onDismissRequest = { selectedApp = null },
            title = { Text(app.label) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    TextButton(
                        onClick = { selectedApp = null; onLaunch(app) },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Open", modifier = Modifier.fillMaxWidth()) }
                    TextButton(
                        onClick = { selectedApp = null; onToggleFavorite(app.packageName) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            if (app.packageName in favorites) "Remove from favourites" else "Add to favourites",
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    TextButton(
                        onClick = {
                            renameValue = app.label
                            renamingApp = app
                            selectedApp = null
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Rename", modifier = Modifier.fillMaxWidth()) }
                    TextButton(
                        onClick = { selectedApp = null; onToggleHidden(app.packageName) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            "Hide from launcher",
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    TextButton(
                        onClick = {
                            selectedApp = null
                            openApplicationInfo(context, app)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("App info", modifier = Modifier.fillMaxWidth()) }
                    if (!app.isSystem) {
                        TextButton(
                            onClick = {
                                selectedApp = null
                                requestUninstall(context, app)
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Uninstall", modifier = Modifier.fillMaxWidth()) }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { selectedApp = null }) { Text("Done") }
            }
        )
    }
    renamingApp?.let { app ->
        AlertDialog(
            onDismissRequest = { renamingApp = null },
            title = { Text("Rename app") },
            text = {
                OutlinedTextField(
                    value = renameValue,
                    onValueChange = { renameValue = it.take(60) },
                    singleLine = true,
                    label = { Text("App name") }
                )
            },
            confirmButton = {
                TextButton(
                    enabled = renameValue.isNotBlank(),
                    onClick = {
                        onRenameApp(app.packageName, renameValue)
                        renamingApp = null
                    }
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { renamingApp = null }) { Text("Cancel") }
            }
        )
    }

}


private fun readAppUsageMinutes(context: android.content.Context): Map<String, Long> {
    val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager
        ?: return emptyMap()
    val allowed = runCatching {
        appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName
        ) == AppOpsManager.MODE_ALLOWED
    }.getOrDefault(false)
    if (!allowed) return emptyMap()

    val manager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
        ?: return emptyMap()
    val start = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    val now = System.currentTimeMillis()
    return runCatching {
        manager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, start, now)
            .groupBy { it.packageName }
            .mapValues { (_, stats) -> stats.sumOf { it.totalTimeInForeground } / 60_000L }
    }.getOrDefault(emptyMap())
}

private fun openApplicationInfo(context: Context, app: LauncherApp) {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${app.packageName}"))
    runCatching { context.startActivity(intent) }
}

private fun requestUninstall(context: Context, app: LauncherApp) {
    val intent = Intent(Intent.ACTION_UNINSTALL_PACKAGE).apply {
        data = Uri.parse("package:${app.packageName}")
        putExtra(Intent.EXTRA_RETURN_RESULT, true)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching { context.startActivity(intent) }.onFailure {
        android.widget.Toast.makeText(
            context,
            "Unable to open the system uninstaller.",
            android.widget.Toast.LENGTH_LONG
        ).show()
    }
}
