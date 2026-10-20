package com.insomdroid.dislauncher

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.app.WallpaperManager
import android.os.Build
import android.content.ComponentName
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.positionChangeIgnoreConsumed
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

private const val MAX_PINNED_APPS = 6


private const val MAX_CUSTOM_WALLPAPER_BYTES = 10L * 1024L * 1024L
private const val MAX_CUSTOM_WALLPAPER_SOURCE_PIXELS = 250_000_000L
private const val MAX_CUSTOM_WALLPAPER_DECODED_PIXELS = 3_000_000L

/**
 * Estimate the brightness at the edge covered by a transparent system bar.
 * Sampling is deliberately bounded so large wallpapers do not add noticeable work.
 */
private fun wallpaperEdgeUsesDarkIcons(
    image: androidx.compose.ui.graphics.ImageBitmap,
    topEdge: Boolean
): Boolean? = runCatching {
    val bitmap = image.asAndroidBitmap()
    if (bitmap.width <= 0 || bitmap.height <= 0) return@runCatching null
    val y = if (topEdge) (bitmap.height * 0.03f).toInt() else (bitmap.height * 0.97f).toInt()
    val step = (bitmap.width / 32).coerceAtLeast(1)
    var luminanceTotal = 0.0
    var samples = 0
    for (x in 0 until bitmap.width step step) {
        luminanceTotal += android.graphics.Color.luminance(bitmap.getPixel(x, y))
        samples++
    }
    samples > 0 && luminanceTotal / samples > 0.45
}.getOrNull()

/**
 * Estimate overall wallpaper brightness with a bounded sample for automatic foreground contrast.
 * Returns true for a generally light image, so the launcher can use dark text.
 */
private fun wallpaperAverageIsLight(
    image: androidx.compose.ui.graphics.ImageBitmap
): Boolean? = runCatching {
    val bitmap = image.asAndroidBitmap()
    if (bitmap.width <= 0 || bitmap.height <= 0) return@runCatching null
    val step = maxOf((bitmap.width / 32).coerceAtLeast(1), (bitmap.height / 32).coerceAtLeast(1))
    var luminanceTotal = 0.0
    var samples = 0
    for (y in 0 until bitmap.height step step) {
        for (x in 0 until bitmap.width step step) {
            luminanceTotal += android.graphics.Color.luminance(bitmap.getPixel(x, y))
            samples++
        }
    }
    if (samples > 0) luminanceTotal / samples > 0.45 else null
}.getOrNull()

/**
 * Keep a private copy of a selected wallpaper so it remains readable after reboot,
 * even when the document provider does not retain its URI permission.
 */
private fun copyWallpaperToInternalStorage(context: Context, sourceUri: Uri): Uri? {
    val directory = File(context.filesDir, "wallpapers")
    if (!directory.exists() && !directory.mkdirs()) return null
    val temporaryFile = runCatching { File.createTempFile("wallpaper-", ".tmp", directory) }
        .getOrNull() ?: return null

    try {
        val input = context.contentResolver.openInputStream(sourceUri) ?: return null
        var totalBytes = 0L
        input.use { source ->
            temporaryFile.outputStream().buffered().use { destination ->
                val buffer = ByteArray(8192)
                while (true) {
                    val count = source.read(buffer)
                    if (count < 0) break
                    totalBytes += count
                    if (totalBytes > MAX_CUSTOM_WALLPAPER_BYTES) return null
                    destination.write(buffer, 0, count)
                }
            }
        }
        if (totalBytes <= 0L) return null

        // Validate that this is a decodable image before replacing the active selection.
        if (decodeWallpaperForScreen(context, Uri.fromFile(temporaryFile)) == null) return null
        val target = File(directory, "custom-wallpaper-${System.currentTimeMillis()}.img")
        if (!temporaryFile.renameTo(target)) return null
        return Uri.fromFile(target)
    } catch (_: Exception) {
        return null
    } finally {
        runCatching { temporaryFile.delete() }
    }
}

private fun deleteOldInternalWallpapers(context: Context, keepUri: Uri?) {
    val directory = File(context.filesDir, "wallpapers")
    val keepFile = keepUri?.takeIf { it.scheme == "file" }?.path?.let(::File)
    directory.listFiles()
        ?.filter { it.name.startsWith("custom-wallpaper-") && it != keepFile }
        ?.forEach { runCatching { it.delete() } }
}

internal fun decodeWallpaperForScreen(
    context: Context,
    uri: Uri
): androidx.compose.ui.graphics.ImageBitmap? {
    val fileLength = runCatching {
        context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length }
    }.getOrNull()
    if (fileLength != null && fileLength > MAX_CUSTOM_WALLPAPER_BYTES) return null

    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    val boundsStream = context.contentResolver.openInputStream(uri) ?: return null
    boundsStream.use { input -> BitmapFactory.decodeStream(input, null, bounds) }

    val width = bounds.outWidth
    val height = bounds.outHeight
    if (width <= 0 || height <= 0) return null
    if (width.toLong() * height.toLong() > MAX_CUSTOM_WALLPAPER_SOURCE_PIXELS) return null

    val metrics = context.resources.displayMetrics
    val targetWidth = metrics.widthPixels.coerceAtLeast(1)
    val targetHeight = metrics.heightPixels.coerceAtLeast(1)
    var sampleSize = 1
    while (
        (width.toLong() / sampleSize) * (height.toLong() / sampleSize) >
            MAX_CUSTOM_WALLPAPER_DECODED_PIXELS ||
        width / sampleSize > targetWidth * 2 ||
        height / sampleSize > targetHeight * 2
    ) {
        sampleSize *= 2
    }

    val options = BitmapFactory.Options().apply {
        inSampleSize = sampleSize
        inPreferredConfig = android.graphics.Bitmap.Config.ARGB_8888
    }
    val bitmap = context.contentResolver.openInputStream(uri)?.use { input ->
        BitmapFactory.decodeStream(input, null, options)
    } ?: return null
    return bitmap.asImageBitmap()
}


@Composable
internal fun DislauncherRoot() {
    val context = LocalContext.current
    val view = LocalView.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val baseDensity = LocalDensity.current
    val prefsStore = remember {
        context.getSharedPreferences("dislauncher_preferences", Context.MODE_PRIVATE)
    }
    var preferences by remember { mutableStateOf(LauncherPreferences.load(prefsStore)) }
    var favorites by remember {
        mutableStateOf(
            prefsStore.getStringSet("favorite_packages", emptySet())
                .orEmpty()
                .sorted()
                .take(MAX_PINNED_APPS)
                .toSet()
        )
    }
    LaunchedEffect(Unit) {
        val savedFavorites = prefsStore.getStringSet("favorite_packages", emptySet()).orEmpty()
        if (savedFavorites.size > MAX_PINNED_APPS) {
            prefsStore.edit().putStringSet("favorite_packages", favorites.toMutableSet()).apply()
        }
    }
    var appNameOverrides by remember { mutableStateOf(readAppNameOverrides(prefsStore)) }
    val initialWeatherCache = remember { loadWeatherCache(context, preferences.weatherUnit) }
    var solarWeather by remember { mutableStateOf<WeatherInfo?>(initialWeatherCache) }
    var weatherForScreen by remember { mutableStateOf<WeatherInfo?>(initialWeatherCache) }
    var homeItemOrder by remember { mutableStateOf(readHomeItemOrder(prefsStore, favorites)) }
    var hiddenApps by remember {
        mutableStateOf(prefsStore.getStringSet("hidden_packages", emptySet())?.toSet().orEmpty())
    }
    var apps by remember { mutableStateOf<List<LauncherApp>>(emptyList()) }
    var searchQuery by remember { mutableStateOf("") }
    var screen by remember {
        mutableStateOf(
            if (prefsStore.getBoolean("onboarding_completed", false)) {
                LauncherScreen.HOME
            } else {
                LauncherScreen.ONBOARDING
            }
        )
    }
    var settingsOpenedFromApps by remember { mutableStateOf(false) }
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { 2 })
    val scope = rememberCoroutineScope()
    val homeBackBounce = remember { Animatable(0f) }
    var iconPacks by remember { mutableStateOf<List<IconPackInfo>>(emptyList()) }
    var launcherStarted by remember {
        mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> launcherStarted = true
                Lifecycle.Event.ON_STOP -> launcherStarted = false
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(context) {
        iconPacks = withContext(Dispatchers.IO) { findIconPacks(context) }
    }

    fun updatePreferences(next: LauncherPreferences) {
        val notificationDotSettingsChanged =
            preferences.notificationDotsEnabled != next.notificationDotsEnabled ||
                preferences.notificationDotPackages != next.notificationDotPackages
        preferences = next
        next.save(prefsStore)
        if (notificationDotSettingsChanged) NotificationDotState.refresh()
    }

    val wallpaperPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val internalUri = withContext(Dispatchers.IO) {
                    copyWallpaperToInternalStorage(context, uri)
                }
                if (internalUri == null) {
                    android.widget.Toast.makeText(
                        context,
                        "Wallpaper couldn't be read. Choose a valid image no larger than 10 MB.",
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                    return@launch
                }

                val previousUri = preferences.wallpaperUri?.let(Uri::parse)
                updatePreferences(
                    preferences.copy(wallpaperMode = "image", wallpaperUri = internalUri.toString())
                )
                deleteOldInternalWallpapers(context, internalUri)
                if (previousUri != null && previousUri.scheme == "content") {
                    runCatching {
                        context.contentResolver.releasePersistableUriPermission(
                            previousUri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION
                        )
                    }
                }
            }
        }
    }

    val customFontPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val selectedFontName = withContext(Dispatchers.IO) {
                    runCatching { importUserCustomFont(context, uri) }.getOrNull()
                }
                if (selectedFontName == null) {
                    android.widget.Toast.makeText(
                        context,
                        "Invalid font. Choose a valid .ttf or .otf file up to 5 MB.",
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                } else {
                    updatePreferences(
                        preferences.copy(fontMode = "custom", customFontName = selectedFontName)
                    )
                }
            }
        }
    }

    fun toggleFavorite(packageName: String) {
        if (packageName !in favorites && favorites.size >= MAX_PINNED_APPS) {
            android.widget.Toast.makeText(
                context,
                "You can pin up to 6 apps.",
                android.widget.Toast.LENGTH_SHORT
            ).show()
            return
        }
        val next = if (packageName in favorites) favorites - packageName else favorites + packageName
        favorites = next
        homeItemOrder = if (packageName in next) {
            (homeItemOrder + packageName).distinct()
        } else {
            homeItemOrder.filterNot { it == packageName }
        }
        prefsStore.edit().putStringSet("favorite_packages", next.toMutableSet()).apply()
        saveHomeItemOrder(prefsStore, homeItemOrder)
    }

    fun finishOnboarding(selectedPackages: Set<String>) {
        scope.launch {
            val allApps = if (apps.isNotEmpty()) {
                apps
            } else {
                withContext(Dispatchers.IO) { loadLauncherApps(context) }.map { app ->
                    appNameOverrides[app.packageName]?.let { app.copy(label = it) } ?: app
                }.also { apps = it }
            }
            val orderedAvailable = prioritizeNonSystemApps(allApps).distinctBy { it.packageName }
            val selected = orderedAvailable.filter { it.packageName in selectedPackages }
                .take(MAX_PINNED_APPS)
                .map { it.packageName }
            val selectedSet = selected.toSet()
            favorites = selectedSet
            val allAppPackages = allApps.map { it.packageName }.toSet()
            homeItemOrder = (homeItemOrder.filterNot { it in allAppPackages } + selected).distinct()
            saveHomeItemOrder(prefsStore, homeItemOrder)
            prefsStore.edit()
                .putStringSet("favorite_packages", selectedSet.toMutableSet())
                .putBoolean("onboarding_completed", true)
                .apply()
            screen = LauncherScreen.HOME
        }
    }

    fun moveHomeItem(sourceId: String, targetId: String) {
        if (sourceId == targetId) return
        val order = (homeItemOrder + favorites).distinct().toMutableList()
        val sourceIndex = order.indexOf(sourceId)
        val targetIndex = order.indexOf(targetId)
        if (sourceIndex < 0 || targetIndex < 0) return
        val moving = order[sourceIndex]
        order[sourceIndex] = order[targetIndex]
        order[targetIndex] = moving
        homeItemOrder = order
        saveHomeItemOrder(prefsStore, order)
    }

    fun renameApp(packageName: String, requestedName: String) {
        val newName = requestedName.trim().take(60)
        if (newName.isBlank()) return
        appNameOverrides = appNameOverrides + (packageName to newName)
        prefsStore.edit().putString("renamed_app_labels_json", JSONObject(appNameOverrides).toString()).apply()
        apps = apps.map { if (it.packageName == packageName) it.copy(label = newName) else it }
    }


    fun toggleHidden(packageName: String) {
        val next = if (packageName in hiddenApps) hiddenApps - packageName else hiddenApps + packageName
        hiddenApps = next
        prefsStore.edit().putStringSet("hidden_packages", next.toMutableSet()).apply()
    }

    val adminComponent = remember { ComponentName(context, DislauncherAdminReceiver::class.java) }
    val devicePolicyManager = remember {
        context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    }
    val adminRequest = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        val active = runCatching { devicePolicyManager.isAdminActive(adminComponent) }.getOrDefault(false)
        updatePreferences(preferences.copy(doubleTapToLock = active))
    }

    fun changeDoubleTapLock(enabled: Boolean) {
        if (!enabled) {
            // Disabling the gesture should not revoke device-admin access automatically.
            updatePreferences(preferences.copy(doubleTapToLock = false))
            return
        }
        if (devicePolicyManager.isAdminActive(adminComponent)) {
            updatePreferences(preferences.copy(doubleTapToLock = true))
        } else {
            val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
                .putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent)
                .putExtra(
                    DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                    "Allow dislauncher to lock the screen when you double-tap an empty area of Home."
                )
            adminRequest.launch(intent)
        }
    }

    LaunchedEffect(Unit) {
        apps = withContext(Dispatchers.IO) { loadLauncherApps(context) }.map { app ->
            appNameOverrides[app.packageName]?.let { app.copy(label = it) } ?: app
        }
    }

    DisposableEffect(context, appNameOverrides) {
        // Refresh only when the installed launcher-app set changes. Re-scanning all
        // launcher activities on every ON_RESUME caused unnecessary work on app return.
        var refreshJob: Job? = null
        val packageFilter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        val packageReceiver = object : BroadcastReceiver() {
            override fun onReceive(receiverContext: Context?, intent: Intent?) {
                val changedPackage = intent?.data?.schemeSpecificPart
                invalidateAppIconCaches(changedPackage)
                invalidateIconPackDetectionCache()
                refreshJob?.cancel()
                refreshJob = scope.launch {
                    // Package installs can emit multiple related broadcasts in quick succession.
                    delay(150L)
                    val refreshed = withContext(Dispatchers.IO) {
                        loadLauncherApps(context)
                    }.map { app ->
                        appNameOverrides[app.packageName]?.let { app.copy(label = it) } ?: app
                    }
                    // Preserve the current list instance when the visible app data is unchanged.
                    if (apps != refreshed) apps = refreshed
                    iconPacks = withContext(Dispatchers.IO) { findIconPacks(context) }
                }
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(packageReceiver, packageFilter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            context.registerReceiver(packageReceiver, packageFilter)
        }
        onDispose {
            refreshJob?.cancel()
            context.unregisterReceiver(packageReceiver)
        }
    }

    LaunchedEffect(pagerState.settledPage) {
        if (screen == LauncherScreen.HOME || screen == LauncherScreen.APPS) {
            screen = if (pagerState.settledPage == 0) LauncherScreen.HOME else LauncherScreen.APPS
        }
    }
    LaunchedEffect(screen) {
        if (screen == LauncherScreen.HOME || screen == LauncherScreen.APPS) {
            val wantedPage = if (screen == LauncherScreen.HOME) 0 else 1
            if (pagerState.currentPage != wantedPage) pagerState.animateScrollToPage(wantedPage)
        }
    }

    BackHandler(
        enabled = screen == LauncherScreen.SETTINGS || screen == LauncherScreen.WEATHER ||
            pagerState.settledPage == 1
    ) {
        when (screen) {
            LauncherScreen.SETTINGS ->
                screen = if (settingsOpenedFromApps) LauncherScreen.APPS else LauncherScreen.HOME
            LauncherScreen.WEATHER -> screen = LauncherScreen.HOME
            LauncherScreen.APPS -> scope.launch { pagerState.animateScrollToPage(0) }
            else -> Unit
        }
    }
    BackHandler(enabled = screen == LauncherScreen.ONBOARDING) { }
    BackHandler(enabled = screen == LauncherScreen.HOME && pagerState.settledPage == 0) {
        scope.launch {
            homeBackBounce.snapTo(0f)
            homeBackBounce.animateTo(6f, animationSpec = tween(durationMillis = 70))
            homeBackBounce.animateTo(-3f, animationSpec = tween(durationMillis = 100))
            homeBackBounce.animateTo(0f, animationSpec = tween(durationMillis = 100))
        }
    }

    val systemDark = androidx.compose.foundation.isSystemInDarkTheme()
    val isDark = when (preferences.themeMode) {
        "light" -> false
        "dark", "black" -> true
        else -> systemDark
    }
    val visibleApps = remember(apps, preferences.hideSystemApps, hiddenApps) {
        apps.filter { (!preferences.hideSystemApps || !it.isSystem) && it.packageName !in hiddenApps }
    }
    val drawerApps = remember(visibleApps) {
        visibleApps + listOf(
            LauncherApp("internal:calculator", "", "Calculator", true),
            LauncherApp("internal:calendar", "", "Calendar", true),
            LauncherApp("internal:converter", "", "Converter", true),
            LauncherApp("internal:notes", "", "Notes", true)
        )
    }
    val wallpaperImage = produceState<androidx.compose.ui.graphics.ImageBitmap?>(
        initialValue = null,
        key1 = preferences.wallpaperUri,
        key2 = launcherStarted
    ) {
        // Keep the last decoded bitmap visible while a lifecycle restart reloads the same URI.
        val savedUri = preferences.wallpaperUri?.let(Uri::parse)
        if (!launcherStarted || savedUri == null) return@produceState

        val decoded = withContext(Dispatchers.IO) {
            runCatching { decodeWallpaperForScreen(context, savedUri) }.getOrNull()
        }
        value = decoded

        if (decoded == null && preferences.wallpaperUri == savedUri.toString()) {
            // A failed legacy/provider URI must not leave the launcher stuck on a blank image.
            updatePreferences(
                LauncherPreferences.load(prefsStore).copy(wallpaperMode = "default", wallpaperUri = null)
            )
            if (savedUri.scheme == "content") {
                runCatching {
                    context.contentResolver.releasePersistableUriPermission(
                        savedUri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                }
            }
            android.widget.Toast.makeText(
                context,
                "Wallpaper couldn't be loaded. The default background has been restored.",
                android.widget.Toast.LENGTH_LONG
            ).show()
        } else if (
            decoded != null &&
            savedUri.scheme != "file" &&
            preferences.wallpaperUri == savedUri.toString()
        ) {
            // Migrate wallpapers selected by older app versions to private storage.
            val internalUri = withContext(Dispatchers.IO) {
                copyWallpaperToInternalStorage(context, savedUri)
            }
            if (internalUri != null && preferences.wallpaperUri == savedUri.toString()) {
                updatePreferences(
                    LauncherPreferences.load(prefsStore).copy(
                        wallpaperMode = "image",
                        wallpaperUri = internalUri.toString()
                    )
                )
                deleteOldInternalWallpapers(context, internalUri)
                if (savedUri.scheme == "content") {
                    runCatching {
                        context.contentResolver.releasePersistableUriPermission(
                            savedUri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION
                        )
                    }
                }
            } else if (preferences.wallpaperUri == savedUri.toString()) {
                // Do not keep a provider-only URI that could fail again after reboot.
                updatePreferences(
                    LauncherPreferences.load(prefsStore).copy(
                        wallpaperMode = "default",
                        wallpaperUri = null
                    )
                )
                android.widget.Toast.makeText(
                    context,
                    "Wallpaper couldn't be saved for reliable loading. The default background has been restored.",
                    android.widget.Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    val wallpaperIsLight = remember(wallpaperImage.value, preferences.wallpaperMode) {
        if (preferences.wallpaperMode == "image") {
            wallpaperImage.value?.let(::wallpaperAverageIsLight)
        } else null
    }

    LaunchedEffect(preferences.hideStatusBar) {
        val activity = context as? Activity ?: return@LaunchedEffect
        val controller = WindowCompat.getInsetsController(activity.window, view)
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (preferences.hideStatusBar) {
            controller.hide(WindowInsetsCompat.Type.statusBars())
        } else {
            controller.show(WindowInsetsCompat.Type.statusBars())
        }
        controller.show(WindowInsetsCompat.Type.navigationBars())
    }

    SideEffect {
        val activity = context as? Activity
        if (activity != null) {
            val controller = WindowCompat.getInsetsController(activity.window, view)
            // Solid wallpapers can intentionally differ from the selected theme.
            // Choose system-bar icon contrast from the actual solid background when available.
            val solidWallpaperColor = if (preferences.wallpaperMode == "solid") {
                runCatching {
                    android.graphics.Color.parseColor(preferences.wallpaperColor)
                }.getOrNull()
            } else null
            val image = if (preferences.wallpaperMode == "image") wallpaperImage.value else null
            val useDarkStatusBarIcons = solidWallpaperColor?.let {
                android.graphics.Color.luminance(it) > 0.45f
            } ?: image?.let { wallpaperEdgeUsesDarkIcons(it, topEdge = true) } ?: !isDark
            val useDarkNavigationBarIcons = solidWallpaperColor?.let {
                android.graphics.Color.luminance(it) > 0.45f
            } ?: image?.let { wallpaperEdgeUsesDarkIcons(it, topEdge = false) } ?: !isDark
            controller.isAppearanceLightStatusBars = useDarkStatusBarIcons
            controller.isAppearanceLightNavigationBars = useDarkNavigationBarIcons
        }
    }

    DislauncherTheme(
        preferences = preferences,
        isDark = isDark,
        baseDensity = baseDensity
    ) { fontFamily ->
        if (screen == LauncherScreen.ONBOARDING) {
            WelcomeOnboardingScreen(
                preferences = preferences,
                onPreferencesChange = ::updatePreferences,
                apps = apps,
                favorites = favorites,
                onFinish = ::finishOnboarding
            )
        } else if (screen == LauncherScreen.SETTINGS) {
            SettingsScreen(
                preferences = preferences,
                apps = apps,
                favorites = favorites,
                hiddenApps = hiddenApps,
                iconPacks = iconPacks,
                onPreferencesChange = ::updatePreferences,
                onChooseCustomFont = { customFontPicker.launch(arrayOf("*/*")) },
                onChooseWallpaper = { wallpaperPicker.launch(arrayOf("image/*")) },
                onApplyWallpaperToSystem = { target, onResult ->
                    val selectedWallpaper = preferences.wallpaperUri?.let(Uri::parse)
                    val isSolidWallpaper = preferences.wallpaperMode != "image"
                    if (!isSolidWallpaper && selectedWallpaper == null) {
                        onResult("failure")
                    } else {
                        scope.launch {
                            val result = withContext(Dispatchers.IO) {
                                runCatching {
                                    val solidWallpaperBytes = if (isSolidWallpaper) {
                                        // Default wallpaper mirrors Dislauncher's neutral theme background.
                                        // Dynamic Color and accent presets never tint this solid wallpaper.
                                        val color = when {
                                            preferences.wallpaperMode == "default" && preferences.themeMode == "black" ->
                                                android.graphics.Color.BLACK
                                            preferences.wallpaperMode == "default" && isDark ->
                                                android.graphics.Color.rgb(17, 17, 17)
                                            preferences.wallpaperMode == "default" ->
                                                android.graphics.Color.rgb(242, 242, 242)
                                            else -> android.graphics.Color.parseColor(preferences.wallpaperColor)
                                        }
                                        val bitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
                                        bitmap.eraseColor(color)
                                        ByteArrayOutputStream().use { output ->
                                            bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
                                            bitmap.recycle()
                                            output.toByteArray()
                                        }
                                    } else null

                                    val wallpaperManager = WallpaperManager.getInstance(context)
                                    fun applyToScreen(flag: Int): Boolean = runCatching {
                                        val stream = if (solidWallpaperBytes != null) {
                                            ByteArrayInputStream(solidWallpaperBytes)
                                        } else {
                                            context.contentResolver.openInputStream(selectedWallpaper!!)
                                                ?: error("Unable to open the selected wallpaper.")
                                        }
                                        stream.use {
                                            wallpaperManager.setStream(it, null, true, flag)
                                        }
                                        true
                                    }.getOrDefault(false)

                                    when (target) {
                                        "home" -> if (applyToScreen(WallpaperManager.FLAG_SYSTEM)) "success" else "failure"
                                        "lock" -> if (applyToScreen(WallpaperManager.FLAG_LOCK)) "success" else "failure"
                                        else -> {
                                            // Apply separately so a failure on one screen does not hide
                                            // a successful update on the other screen.
                                            val homeApplied = applyToScreen(WallpaperManager.FLAG_SYSTEM)
                                            val lockApplied = applyToScreen(WallpaperManager.FLAG_LOCK)
                                            when {
                                                homeApplied && lockApplied -> "success"
                                                homeApplied -> "home_success_lock_failure"
                                                lockApplied -> "home_failure_lock_success"
                                                else -> "failure"
                                            }
                                        }
                                    }
                                }.getOrDefault("failure")
                            }
                            onResult(result)
                        }
                    }
                },
                onDoubleTapLockChange = ::changeDoubleTapLock,
                onToggleFavorite = ::toggleFavorite,
                onToggleHidden = ::toggleHidden,
                onBack = { screen = if (settingsOpenedFromApps) LauncherScreen.APPS else LauncherScreen.HOME },
                onRestartLauncher = { (context as? Activity)?.recreate() }
            )
        } else if (screen == LauncherScreen.WEATHER) {
            WeatherScreen(
                weatherUnit = preferences.weatherUnit,
                cachedWeather = weatherForScreen ?: solarWeather,
                onWeatherChange = { updated ->
                    if (updated != null) {
                        val normalized = convertWeatherUnit(updated, preferences.weatherUnit)
                        solarWeather = normalized
                        weatherForScreen = normalized
                        saveWeatherCache(context, normalized)
                    }
                },
                onBack = { screen = LauncherScreen.HOME }
            )
        } else {
            val notificationsGestureEnabled =
                screen == LauncherScreen.HOME && pagerState.currentPage == 0 && preferences.swipeDownNotifications
            Box(
                modifier = Modifier.fillMaxSize()
                    .background(
                        if (preferences.wallpaperMode == "image" && wallpaperImage.value != null) Color.Transparent
                        else MaterialTheme.colorScheme.background
                    )
                    .pointerInput(notificationsGestureEnabled) {
                        if (!notificationsGestureEnabled) return@pointerInput
                        val triggerDistance = 100.dp.toPx()
                        val horizontalSlop = 24.dp.toPx()
                        awaitEachGesture {
                            val down = awaitFirstDown(
                                pass = PointerEventPass.Initial,
                                requireUnconsumed = false
                            )
                            var totalX = 0f
                            var totalY = 0f
                            var horizontalGesture = false
                            var triggered = false
                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) break

                                val delta = change.positionChangeIgnoreConsumed()
                                totalX += delta.x
                                totalY += delta.y

                                if (
                                    kotlin.math.abs(totalX) > horizontalSlop &&
                                    kotlin.math.abs(totalX) > kotlin.math.abs(totalY) * 1.15f
                                ) {
                                    horizontalGesture = true
                                }

                                if (!horizontalGesture && !triggered && totalY >= triggerDistance) {
                                    triggered = true
                                    expandNotificationPanel(context)
                                }
                                if (triggered) change.consume()
                            }
                        }
                    }
            ) {
                if (preferences.wallpaperMode == "image") {
                    wallpaperImage.value?.let { bitmap ->
                        Image(
                            bitmap = bitmap,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    wallpaperIsLight?.let { isLight ->
                        Box(
                            Modifier.fillMaxSize().background(
                                if (isLight) Color.White.copy(alpha = 0.14f)
                                else Color.Black.copy(alpha = 0.18f)
                            )
                        )
                    }
                }
                val wallpaperForeground = when (wallpaperIsLight) {
                    true -> Color(0xFF111111)
                    false -> Color(0xFFF2F2F2)
                    null -> null
                }
                val pageColorScheme = wallpaperForeground?.let { foreground ->
                    MaterialTheme.colorScheme.copy(
                        onBackground = foreground,
                        onSurface = foreground,
                        onSurfaceVariant = foreground.copy(alpha = 0.78f)
                    )
                } ?: MaterialTheme.colorScheme
                MaterialTheme(colorScheme = pageColorScheme) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize().offset(x = homeBackBounce.value.dp),
                    beyondViewportPageCount = 1,
                    pageSpacing = 0.dp
                ) { page ->
                    if (page == 0) {
                        HomeScreen(
                            apps = visibleApps,
                            favorites = favorites,
                                        preferences = preferences,
                            cachedWeather = solarWeather,
                            isActive = page == pagerState.currentPage,
                            isDark = isDark,
                            fontFamily = fontFamily,
                            onLaunch = { launchApp(context, it) },
                            onOpenWeather = { cached -> weatherForScreen = cached ?: solarWeather; screen = LauncherScreen.WEATHER },
                            onOpenSettings = { settingsOpenedFromApps = false; screen = LauncherScreen.SETTINGS },
                            onDoubleTapToLock = { lockDevice(context, adminComponent, devicePolicyManager) },
                            homeItemOrder = homeItemOrder,
                            onMoveHomeItem = ::moveHomeItem,
                            onSolarInfoChange = { updated ->
                                if (updated != null) {
                                    val normalized = convertWeatherUnit(updated, preferences.weatherUnit)
                                    solarWeather = normalized
                                    saveWeatherCache(context, normalized)
                                }
                            }
                        )
                    } else {
                        AppsScreen(
                            apps = drawerApps,
                            favorites = favorites,
                            query = searchQuery,
                            onQueryChange = { searchQuery = it },
                            preferences = preferences,
                            isDark = isDark,
                            isActive = page == pagerState.currentPage,
                            fontFamily = fontFamily,
                            onLaunch = { app ->
                                when (app.packageName) {
                                    "internal:notes" -> launchBuiltInTool(context, BuiltInTool.NOTES)
                                    "internal:calculator" -> launchBuiltInTool(context, BuiltInTool.CALCULATOR)
                                    "internal:calendar" -> launchBuiltInTool(context, BuiltInTool.CALENDAR)
                                    "internal:converter" -> launchBuiltInTool(context, BuiltInTool.UNIT_CONVERTER)
                                    else -> launchApp(context, app)
                                }
                            },
                            onToggleFavorite = ::toggleFavorite,
                            onToggleHidden = ::toggleHidden,
                            onRenameApp = ::renameApp,
                            onOpenSettings = { settingsOpenedFromApps = true; screen = LauncherScreen.SETTINGS }
                        )
                    }
                }
                }
            }
        }
    }
}
