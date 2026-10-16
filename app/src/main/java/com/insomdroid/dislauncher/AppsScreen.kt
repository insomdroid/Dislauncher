package com.insomdroid.dislauncher

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search as SearchIcon
import androidx.compose.material.icons.filled.Settings as SettingsIcon
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.activity.compose.BackHandler
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontFamily
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
    onLaunch: (LauncherApp) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onToggleHidden: (String) -> Unit,
    onRenameApp: (String, String) -> Unit,
    onOpenSettings: () -> Unit
) {
    val background = MaterialTheme.colorScheme.background
    val mainText = MaterialTheme.colorScheme.onBackground
    val secondary = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.62f)
    val searching = query.isNotBlank()
    val filtered = remember(apps, query) {
        apps.filter { it.label.contains(query.trim(), ignoreCase = true) }
            .sortedBy { it.label.lowercase(Locale.getDefault()) }
    }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }
    var searchFocused by remember { mutableStateOf(false) }
    val searchColor = if (searchFocused || query.isNotEmpty()) mainText else secondary.copy(alpha = 0.65f)
    val alphabetLetters = remember { ('A'..'Z').map(Char::toString) + "#" }
    var scrubberProgress by remember { mutableStateOf<Float?>(null) }
    val firstIndexByLetter = remember(filtered) {
        val result = linkedMapOf<String, Int>()
        filtered.forEachIndexed { index, app ->
            val letter = app.label.firstOrNull()?.uppercaseChar()?.toString()
                ?.takeIf { it in alphabetLetters } ?: "#"
            if (letter !in result) result[letter] = index
        }
        result
    }
    val scrollToLetter: (String) -> Unit = { letter ->
        firstIndexByLetter[letter]?.let { index ->
            scope.launch { listState.animateScrollToItem(index) }
        }
    }
    BackHandler(enabled = searchFocused || searching) {
        keyboard?.hide()
        focusManager.clearFocus(force = true)
        searchFocused = false
        if (query.isNotEmpty()) onQueryChange("")
    }
    LaunchedEffect(query, filtered) {
        if (searching) listState.scrollToItem(0)
    }
    var selectedApp by remember { mutableStateOf<LauncherApp?>(null) }
    var renamingApp by remember { mutableStateOf<LauncherApp?>(null) }
    var renameValue by remember { mutableStateOf("") }
    val context = androidx.compose.ui.platform.LocalContext.current
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
                modifier = Modifier.fillMaxSize().padding(
                    end = if (preferences.showAlphabetIndex && !searching) 15.dp else 0.dp
                ),
                contentPadding = PaddingValues(bottom = 16.dp, top = 2.dp),
                verticalArrangement = if (searching) Arrangement.Bottom else Arrangement.Top
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
                        ).padding(vertical = 8.dp, horizontal = 0.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (preferences.showAppIcons) {
                            AppIcon(app, preferences.iconPack, size = 18.dp, iconShape = preferences.iconShape)
                            Spacer(Modifier.width(9.dp))
                        }
                        Text(
                            app.label,
                            color = mainText,
                            fontSize = 25.sp,
                            lineHeight = 32.sp,
                            fontFamily = fontFamily,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                                .padding(start = if (preferences.showAppIcons) 0.dp else 20.dp)
                        )
                    }
                }
                if (!searching) item(key = "dislauncher-settings") {
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .clickable(onClick = onOpenSettings)
                            .padding(vertical = 10.dp, horizontal = 0.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.SettingsIcon,
                            contentDescription = "Settings",
                            tint = mainText,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(9.dp))
                        Text(
                            "Dislauncher Settings",
                            color = mainText,
                            fontSize = 20.sp,
                            lineHeight = 26.sp,
                            fontFamily = fontFamily,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
            if (preferences.showAlphabetIndex && !searching && filtered.isNotEmpty()) {
                BoxWithConstraints(
                    modifier = Modifier.align(Alignment.BottomEnd)
                        .padding(bottom = 48.dp)
                        .fillMaxHeight(0.56f)
                        .width(20.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize()
                            .pointerInput(alphabetLetters, firstIndexByLetter) {
                                fun letterAt(y: Float): String {
                                    val height = size.height.coerceAtLeast(1)
                                    val index = ((y / height) * alphabetLetters.size)
                                        .toInt().coerceIn(0, alphabetLetters.lastIndex)
                                    return alphabetLetters[index]
                                }
                                detectVerticalDragGestures(
                                    onDragStart = { offset ->
                                        scrubberProgress = (offset.y / size.height.coerceAtLeast(1)).coerceIn(0f, 1f)
                                        scrollToLetter(letterAt(offset.y))
                                    },
                                    onDragEnd = { scrubberProgress = null },
                                    onDragCancel = { scrubberProgress = null }
                                ) { change, _ ->
                                    change.consume()
                                    scrubberProgress = (change.position.y / size.height.coerceAtLeast(1)).coerceIn(0f, 1f)
                                    scrollToLetter(letterAt(change.position.y))
                                }
                            },
                        verticalArrangement = Arrangement.SpaceEvenly,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        alphabetLetters.forEach { letter ->
                            Box(
                                modifier = Modifier.size(18.dp)
                                    .clickable(enabled = letter in firstIndexByLetter) {
                                        scrollToLetter(letter)
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    letter,
                                    color = if (letter in firstIndexByLetter) {
                                        secondary.copy(alpha = 0.42f)
                                    } else {
                                        secondary.copy(alpha = 0.18f)
                                    },
                                    fontSize = 8.sp,
                                    lineHeight = 9.sp
                                )
                            }
                        }
                    }
                    androidx.compose.animation.AnimatedVisibility(
                        visible = scrubberProgress != null,
                        modifier = Modifier.align(Alignment.TopStart).offset(
                            x = (-48).dp,
                            y = (maxHeight * (scrubberProgress ?: 0f) - 20.dp)
                                .coerceIn(0.dp, (maxHeight - 40.dp).coerceAtLeast(0.dp))
                        ),
                        enter = androidx.compose.animation.fadeIn() +
                            androidx.compose.animation.slideInHorizontally { it / 2 },
                        exit = androidx.compose.animation.fadeOut() +
                            androidx.compose.animation.slideOutHorizontally { it / 2 }
                    ) {
                        Box(
                            modifier = Modifier.size(40.dp)
                                .background(MaterialTheme.colorScheme.primary, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                scrubberProgress?.let {
                                    alphabetLetters[((it * alphabetLetters.size).toInt())
                                        .coerceIn(0, alphabetLetters.lastIndex)]
                                } ?: "",
                                color = MaterialTheme.colorScheme.onPrimary,
                                fontSize = 18.sp,
                                lineHeight = 20.sp
                            )
                        }
                    }
                }
            }
        }
        if (preferences.showSearchBar) {
            Spacer(Modifier.height(2.dp))
            val searchShape = RoundedCornerShape(24.dp)
            Row(
                modifier = Modifier.fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .height(46.dp)
                    .border(
                        width = 1.dp,
                        color = if (searchFocused || query.isNotEmpty()) {
                            mainText.copy(alpha = 0.62f)
                        } else {
                            secondary.copy(alpha = 0.38f)
                        },
                        shape = searchShape
                    )
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Filled.SearchIcon,
                    contentDescription = "Search apps",
                    tint = searchColor,
                    modifier = Modifier.size(19.dp)
                )
                Spacer(Modifier.width(10.dp))
                Box(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (query.isEmpty()) {
                        Text(
                            "Search Apps",
                            color = searchColor,
                            style = MaterialTheme.typography.bodyLarge,
                            fontSize = 15.sp
                        )
                    }
                    BasicTextField(
                        value = query,
                        onValueChange = onQueryChange,
                        modifier = Modifier.fillMaxWidth()
                            .focusRequester(focusRequester)
                            .onFocusChanged { searchFocused = it.isFocused },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                            color = mainText,
                            fontFamily = fontFamily,
                            fontSize = 15.sp
                        ),
                        cursorBrush = SolidColor(mainText)
                    )
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


private fun openApplicationInfo(context: Context, app: LauncherApp) {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${app.packageName}"))
    runCatching { context.startActivity(intent) }
}

private fun requestUninstall(context: Context, app: LauncherApp) {
    val intent = Intent(Intent.ACTION_UNINSTALL_PACKAGE).apply {
        data = Uri.parse("package:${app.packageName}")
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
