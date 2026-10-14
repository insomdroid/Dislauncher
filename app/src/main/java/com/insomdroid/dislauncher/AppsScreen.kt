package com.insomdroid.dislauncher

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.os.Process
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
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
    onRenameApp: (String, String) -> Unit
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
    val firstIndexByLetter = remember(filtered) {
        val result = linkedMapOf<String, Int>()
        filtered.forEachIndexed { index, app ->
            val letter = app.label.firstOrNull()?.uppercaseChar()?.toString() ?: "#"
            if (letter !in result) result[letter] = index
        }
        result
    }
    val scrubberLetters = remember(firstIndexByLetter) { firstIndexByLetter.keys.toList() }
    var isScrubbing by remember { mutableStateOf(false) }
    var activeLetter by remember { mutableStateOf("") }
    var scrubFraction by remember { mutableFloatStateOf(0.5f) }
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
            .padding(start = 16.dp, end = 14.dp, top = 14.dp, bottom = 8.dp)
    ) {
        if (preferences.showSearchBar) {
            Column(Modifier.fillMaxWidth().padding(top = 3.dp)) {
                Box(
                    modifier = Modifier.fillMaxWidth().height(48.dp).padding(start = 12.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (query.isEmpty()) {
                        Text(
                            "Search Apps",
                            color = mainText,
                            style = MaterialTheme.typography.bodyLarge,
                            fontSize = 16.sp
                        )
                    }
                    BasicTextField(
                        value = query,
                        onValueChange = onQueryChange,
                        modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                            color = mainText,
                            fontFamily = fontFamily,
                            fontSize = 16.sp
                        ),
                        cursorBrush = SolidColor(mainText)
                    )
                }
                HorizontalDivider(
                    modifier = Modifier.fillMaxWidth(),
                    thickness = 1.dp,
                    color = mainText.copy(alpha = 0.9f)
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().padding(end = if (preferences.showCharacterScroll) 20.dp else 0.dp),
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
                        ).padding(vertical = 12.dp, horizontal = 28.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (preferences.showAppIcons) {
                            AppIcon(app, preferences.iconPack, size = 19.dp)
                            Spacer(Modifier.width(9.dp))
                        }
                        Text(
                            app.label,
                            color = mainText,
                            fontSize = 25.sp,
                            lineHeight = 33.sp,
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
                        if (app.packageName in favorites) {
                            Text(
                                "★",
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 16.sp,
                                lineHeight = 20.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }
                    }
                }
            }

            if (preferences.showCharacterScroll && scrubberLetters.isNotEmpty()) {
                BoxWithConstraints(
                    modifier = Modifier.align(Alignment.CenterEnd).width(22.dp).fillMaxHeight()
                ) {
                    Column(
                        modifier = Modifier.width(22.dp)
                            .align(Alignment.CenterEnd)
                            .offset(y = maxHeight * 0.23f)
                            .pointerInput(scrubberLetters, filtered) {
                                awaitEachGesture {
                                    val down = awaitFirstDown(requireUnconsumed = false)
                                    isScrubbing = true
                                    var previousIndex = -1
                                    fun updateFromY(y: Float) {
                                        val fraction = (y / size.height.toFloat()).coerceIn(0f, 0.9999f)
                                        scrubFraction = fraction
                                        val index = (fraction * scrubberLetters.size).toInt()
                                            .coerceIn(0, scrubberLetters.lastIndex)
                                        if (index != previousIndex) {
                                            previousIndex = index
                                            activeLetter = scrubberLetters[index]
                                            val row = firstIndexByLetter[activeLetter] ?: 0
                                            scope.launch { listState.scrollToItem(row) }
                                        }
                                    }
                                    try {
                                        updateFromY(down.position.y)
                                        var pressed = true
                                        while (pressed) {
                                            val event = awaitPointerEvent()
                                            val change = event.changes.firstOrNull() ?: break
                                            if (change.pressed) {
                                                updateFromY(change.position.y)
                                                change.consume()
                                            } else {
                                                pressed = false
                                            }
                                        }
                                    } finally {
                                        isScrubbing = false
                                    }
                                }
                            }
                            .padding(horizontal = 1.dp, vertical = 2.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(1.dp)
                    ) {
                        scrubberLetters.forEach { letter ->
                            Text(
                                letter,
                                color = if (isScrubbing && letter == activeLetter) mainText else secondary,
                                fontSize = 10.sp,
                                lineHeight = 15.sp,
                                fontWeight = if (isScrubbing && letter == activeLetter) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                    Crossfade(
                        targetState = isScrubbing && activeLetter.isNotBlank(),
                        modifier = Modifier.align(Alignment.CenterEnd).offset(x = (-30).dp),
                        animationSpec = tween(110),
                        label = "scrubber-letter-visibility"
                    ) { visible ->
                        if (visible) {
                            Crossfade(
                                targetState = activeLetter,
                                animationSpec = tween(85),
                                label = "scrubber-active-letter"
                            ) { letter ->
                                Text(
                                    letter,
                                    color = mainText,
                                    fontSize = 34.sp,
                                    lineHeight = 38.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.width(40.dp)
                                )
                            }
                        }
                    }
                }
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
