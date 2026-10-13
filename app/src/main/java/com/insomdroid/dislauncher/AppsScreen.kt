package com.insomdroid.dislauncher

import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

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
    onToggleFavorite: (String) -> Unit
) {
    val background = if (isDark) Color.Black else Color(0xFFFFFCF7)
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
        filtered.mapIndexedNotNull { index, app ->
            val first = app.label.firstOrNull()?.uppercaseChar()?.toString() ?: "#"
            first to index
        }.groupBy({ it.first }, { it.second }).mapValues { it.value.first() }
    }
    val scrubberLetters = remember(firstIndexByLetter) {
        firstIndexByLetter.keys.sortedWith(compareBy<String> { if (it == "#") "" else it })
    }
    var isScrubbing by remember { mutableStateOf(false) }
    var activeLetter by remember { mutableStateOf("") }
    var scrubFraction by remember { mutableFloatStateOf(0.5f) }

    LaunchedEffect(isActive, preferences.autoLaunchKeyboard, preferences.showSearchBar) {
        if (isActive && preferences.autoLaunchKeyboard && preferences.showSearchBar) {
            delay(180L)
            runCatching { focusRequester.requestFocus() }
            keyboard?.show()
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().background(background)
            .windowInsetsPadding(WindowInsets.systemBars)
            .padding(start = 20.dp, end = 10.dp, top = 14.dp, bottom = 8.dp)
    ) {
        Text(
            "All apps",
            color = mainText,
            fontSize = 25.sp * fontScale,
            fontWeight = FontWeight.Light,
            fontFamily = fontFamily,
            maxLines = 1
        )
        Text("${filtered.size} applications", color = secondary, fontSize = 10.sp * fontScale)
        if (preferences.showSearchBar) {
            Spacer(Modifier.height(7.dp))
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                singleLine = true,
                placeholder = { Text("Search apps", fontSize = 13.sp * fontScale) },
                shape = MaterialTheme.shapes.large
            )
        }
        Spacer(Modifier.height(5.dp))
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
                            fontSize = 13.sp * fontScale,
                            modifier = Modifier.padding(vertical = 18.dp)
                        )
                    }
                }
                items(filtered, key = { it.packageName }) { app ->
                    Row(
                        modifier = Modifier.fillMaxWidth().combinedClickable(
                            onClick = { onLaunch(app) },
                            onLongClick = { onToggleFavorite(app.packageName) }
                        ).padding(vertical = 6.dp, horizontal = 1.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (preferences.showAppIcons) {
                            AppIcon(app, preferences.iconPack, size = 19.dp)
                            Spacer(Modifier.width(9.dp))
                        }
                        Text(
                            app.label,
                            color = mainText,
                            fontSize = 15.sp * fontScale,
                            lineHeight = 19.sp * fontScale,
                            fontFamily = fontFamily,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        if (app.packageName in favorites) {
                            Text("•", color = secondary, fontSize = 12.sp, modifier = Modifier.padding(start = 4.dp))
                        }
                    }
                }
            }

            if (preferences.showCharacterScroll && scrubberLetters.isNotEmpty()) {
                BoxWithConstraints(
                    modifier = Modifier.align(Alignment.CenterEnd).width(22.dp).fillMaxHeight()
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
                ) {
                    if (isScrubbing) {
                        Column(
                            modifier = Modifier.align(Alignment.CenterEnd)
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.96f))
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = 3.dp, vertical = 3.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            scrubberLetters.forEach { letter ->
                                val active = letter == activeLetter
                                Text(
                                    letter,
                                    color = if (active) MaterialTheme.colorScheme.onPrimaryContainer
                                        else MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 8.sp * fontScale,
                                    lineHeight = 10.sp * fontScale,
                                    fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                                    modifier = Modifier.clip(RoundedCornerShape(3.dp))
                                        .background(
                                            if (active) MaterialTheme.colorScheme.primaryContainer
                                            else Color.Transparent
                                        )
                                        .padding(vertical = 1.dp, horizontal = 2.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
