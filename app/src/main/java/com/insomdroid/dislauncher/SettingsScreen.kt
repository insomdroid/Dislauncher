package com.insomdroid.dislauncher

import android.Manifest
import android.app.admin.DevicePolicyManager
import android.app.role.RoleManager
import android.content.ComponentName
import android.content.res.Configuration
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Gesture
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlin.math.roundToInt

private enum class SettingsCategory {
    APPEARANCE, HOME, APPS, GESTURES, ACCESS, ABOUT
}

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
    onChooseWallpaper: () -> Unit,
    onApplyWallpaperToSystem: (String, (Boolean) -> Unit) -> Unit,
    onDoubleTapLockChange: (Boolean) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onToggleHidden: (String) -> Unit,
    onBack: () -> Unit,
    onRestartLauncher: () -> Unit
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val lifecycleOwner = LocalLifecycleOwner.current
    fun tr(english: String, indonesian: String): String =
        settingsLocalizedText(context, english, indonesian)

    var selectedCategory by remember { mutableStateOf<SettingsCategory?>(null) }
    var openDialog by remember { mutableStateOf("") }
    var showRestartConfirmation by remember { mutableStateOf(false) }
    var detectedIconPacks by remember { mutableStateOf(iconPacks) }
    var refreshVersion by remember { mutableStateOf(0) }
    LaunchedEffect(context, refreshVersion) {
        detectedIconPacks = withContext(Dispatchers.IO) { findIconPacks(context) }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refreshVersion++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    BackHandler(enabled = selectedCategory != null) {
        selectedCategory = null
        openDialog = ""
    }

    val adminComponent = remember(context) {
        ComponentName(context, DislauncherAdminReceiver::class.java)
    }
    val devicePolicyManager = remember(context) {
        context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    }
    val notificationAccessEnabled = remember(refreshVersion) {
        hasMusicNotificationAccess(context)
    }
    val adminActive = remember(refreshVersion) {
        runCatching { devicePolicyManager.isAdminActive(adminComponent) }.getOrDefault(false)
    }
    val defaultLauncher = remember(refreshVersion) { isDislauncherDefaultHome(context) }
    val calendarPermission = remember(refreshVersion) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) ==
            PackageManager.PERMISSION_GRANTED
    }
    val locationPermission = remember(refreshVersion) {
        hasWeatherLocationPermission(context)
    }
    val appVersion = remember {
        runCatching {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: "—"
    }

    fun openSystemPage(intent: Intent) {
        runCatching { context.startActivity(intent) }
    }

    fun openAppPermissionSettings() {
        openSystemPage(
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", context.packageName, null)
            )
        )
    }

    val categoryTitle = when (selectedCategory) {
        SettingsCategory.APPEARANCE -> tr("Appearance", "Tampilan")
        SettingsCategory.HOME -> tr("Home screen", "Layar utama")
        SettingsCategory.APPS -> tr("Apps", "Aplikasi")
        SettingsCategory.GESTURES -> tr("Gestures", "Gestur")
        SettingsCategory.ACCESS -> tr("Permissions and access", "Izin dan akses")
        SettingsCategory.ABOUT -> tr("About", "Tentang")
        null -> tr("Settings", "Setelan")
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        categoryTitle,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    TextButton(
                        onClick = {
                            if (selectedCategory != null) {
                                selectedCategory = null
                                openDialog = ""
                            } else onBack()
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.ArrowBack,
                            contentDescription = tr("Back", "Kembali")
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier.fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (selectedCategory == null) {
                Text(
                    tr("Choose a category", "Pilih kategori"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
                )
                val categories = listOf(
                    Triple(SettingsCategory.APPEARANCE, Icons.Outlined.Palette, tr("Appearance", "Tampilan")) to
                        tr("Theme, wallpaper, fonts and app icons", "Tema, wallpaper, font, dan ikon aplikasi"),
                    Triple(SettingsCategory.HOME, Icons.Outlined.Home, tr("Home screen", "Layar utama")) to
                        tr("Home elements, widgets and formats", "Elemen Home, widget, dan format"),
                    Triple(SettingsCategory.APPS, Icons.Outlined.Apps, tr("Apps", "Aplikasi")) to
                        tr("Favourites, hidden apps and app drawer", "Favorit, aplikasi tersembunyi, dan app drawer"),
                    Triple(SettingsCategory.GESTURES, Icons.Outlined.Gesture, tr("Gestures", "Gestur")) to
                        tr("Quick actions for the Home screen", "Aksi cepat pada layar utama"),
                    Triple(SettingsCategory.ACCESS, Icons.Outlined.Security, tr("Permissions and access", "Izin dan akses")) to
                        tr("Check access required by features", "Periksa akses yang diperlukan fitur"),
                    Triple(SettingsCategory.ABOUT, Icons.Outlined.Info, tr("About", "Tentang")) to
                        tr("Version and project information", "Versi dan informasi proyek")
                )
                categories.forEachIndexed { index, entry ->
                    val (category, icon, title) = entry.first
                    SettingsCategoryRow(
                        icon = icon,
                        title = title,
                        description = entry.second,
                        onClick = { selectedCategory = category }
                    )
                    if (index < categories.lastIndex) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
                    }
                }
            } else {
                when (selectedCategory) {
                    SettingsCategory.APPEARANCE -> {
                        SettingsGroup(tr("Theme and color", "Tema dan warna")) {
                            ChoiceRow(
                                tr("Theme mode", "Mode tampilan"),
                                preferences.themeMode,
                                listOf(
                                    "system" to tr("System", "Sistem"),
                                    "light" to tr("Light", "Terang"),
                                    "dark" to tr("Dark", "Gelap"),
                                    "black" to "AMOLED"
                                )
                            ) { onPreferencesChange(preferences.copy(themeMode = it)) }
                            SettingToggle(
                                tr("Dynamic color", "Warna dinamis"),
                                tr("Use colors from your Android wallpaper", "Gunakan warna dari wallpaper Android"),
                                preferences.dynamicColor
                            ) {
                                onPreferencesChange(preferences.copy(dynamicColor = it))
                            }
                            ChoiceRow(
                                tr("Accent color", "Warna aksen"),
                                preferences.accentPreset,
                                listOf(
                                    "default" to tr("Default", "Bawaan"),
                                    "red" to tr("Red", "Merah"),
                                    "yellow" to tr("Yellow", "Kuning"),
                                    "blue" to tr("Blue", "Biru"),
                                    "green" to tr("Green", "Hijau"),
                                    "purple" to tr("Purple", "Ungu"),
                                    "orange" to tr("Orange", "Oranye"),
                                    "pink" to tr("Pink", "Merah muda")
                                )
                            ) { onPreferencesChange(preferences.copy(accentPreset = it)) }
                        }
                        SettingsGroup(tr("Wallpaper", "Wallpaper")) {
                            ChoiceRow(
                                tr("Wallpaper", "Wallpaper"),
                                preferences.wallpaperMode,
                                listOf(
                                    "default" to tr("Default", "Bawaan"),
                                    "image" to tr("Custom image", "Gambar kustom")
                                )
                            ) { mode ->
                                // Keep the image picker separate from selecting wallpaper mode.
                                onPreferencesChange(preferences.copy(wallpaperMode = mode))
                            }
                            if (preferences.wallpaperMode == "image") {
                                SettingAction(
                                    tr("Custom wallpaper", "Wallpaper kustom"),
                                    if (preferences.wallpaperUri != null) tr("Custom image selected", "Gambar kustom dipilih")
                                    else tr("Choose an image for your wallpaper", "Pilih gambar untuk wallpaper"),
                                    tr("Choose image", "Pilih gambar"),
                                    onChooseWallpaper
                                )
                                if (preferences.wallpaperUri != null) {
                                    SettingAction(
                                        tr("Apply to device Home and lock screen", "Terapkan ke Home dan lockscreen perangkat"),
                                        tr(
                                            "Apply this image to the device wallpaper. Dislauncher keeps its internal wallpaper separate.",
                                            "Terapkan gambar ini ke wallpaper perangkat. Wallpaper internal Dislauncher tetap terpisah."
                                        ),
                                        tr("Apply", "Terapkan"),
                                        { onApplyWallpaperToSystem("both") { applied ->
                                            android.widget.Toast.makeText(
                                                context,
                                                if (applied) tr("Wallpaper applied", "Wallpaper diterapkan")
                                                else tr("Couldn't apply wallpaper", "Wallpaper gagal diterapkan"),
                                                android.widget.Toast.LENGTH_SHORT
                                            ).show()
                                        } }
                                    )
                                }

                            }
                        }
                        SettingsGroup(tr("Font and text size", "Font dan ukuran teks")) {
                            ChoiceRow(
                                tr("Built-in font", "Font bawaan"),
                                preferences.fontMode,
                                listOf(
                                    "system" to tr("System", "Sistem"),
                                    "dislauncher" to "Inter Variable",
                                    "custom" to tr("Custom", "Kustom")
                                )
                            ) { value ->
                                // Selecting Custom activates the mode without launching
                                // the document picker; the action below opens it explicitly.
                                onPreferencesChange(preferences.copy(fontMode = value))
                            }
                            if (preferences.fontMode == "custom") {
                                SettingAction(
                                    tr("Custom font file", "File font kustom"),
                                    tr("Active: ", "Aktif: ") + (preferences.customFontName
                                        ?: tr("Custom font", "Font kustom")),
                                    tr("Choose file", "Pilih file"),
                                    onChooseCustomFont
                                )
                            }
                            ChoiceRow(
                                tr("Font size", "Ukuran font"),
                                preferences.fontSize,
                                listOf(
                                    "small" to tr("Small", "Kecil"),
                                    "medium" to tr("Medium", "Sedang"),
                                    "large" to tr("Large", "Besar")
                                )
                            ) { onPreferencesChange(preferences.copy(fontSize = it)) }
                        }
                        SettingsGroup(tr("App icons", "Ikon aplikasi")) {
                            SettingToggle(
                                tr("Show app icons", "Tampilkan ikon aplikasi"),
                                "",
                                preferences.showAppIcons
                            ) { onPreferencesChange(preferences.copy(showAppIcons = it)) }
                            if (preferences.showAppIcons) {
                                ChoiceRow(
                                    tr("Built-in icon shape", "Bentuk ikon bawaan"),
                                    preferences.iconShape,
                                    listOf(
                                        "original" to tr("Original", "Asli"),
                                        "circle" to tr("Circle", "Lingkaran"),
                                        "outline_square" to tr("Outline square", "Kotak outline"),
                                        "filled_square" to tr("Filled square", "Kotak penuh"),
                                        "filled_rectangle" to tr("Rectangle", "Persegi panjang")
                                    )
                                ) { onPreferencesChange(preferences.copy(iconShape = it)) }
                                SettingAction(
                                    tr("Icon pack", "Paket ikon"),
                                    detectedIconPacks.firstOrNull { it.packageName == preferences.iconPack }?.label
                                        ?: if (preferences.iconPack == "system") {
                                            tr("System default", "Bawaan sistem")
                                        } else tr("Selected pack not installed", "Paket terpilih tidak terpasang"),
                                    tr("Choose", "Pilih")
                                ) {
                                    openDialog = "iconpack"
                                }
                            } else {
                                Text(
                                    tr(
                                        "Turn on app icons to configure their shape or icon pack.",
                                        "Aktifkan ikon aplikasi untuk mengatur bentuk atau paket ikon."
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    SettingsCategory.HOME -> {
                        SettingsGroup(tr("Home elements", "Elemen Home")) {
                            SettingToggle(tr("Show status bar", "Tampilkan status bar"), "", !preferences.hideStatusBar) {
                                onPreferencesChange(preferences.copy(hideStatusBar = !it))
                            }
                            SettingToggle(
                                tr("Show clock", "Tampilkan jam"),
                                tr("Tap the clock to open the clock app.", "Ketuk jam untuk membuka aplikasi jam."),
                                preferences.showClock
                            ) { onPreferencesChange(preferences.copy(showClock = it)) }
                            SettingToggle(
                                tr("Show date", "Tampilkan tanggal"),
                                tr("Tap the date to open the calendar.", "Ketuk tanggal untuk membuka kalender."),
                                preferences.showDate
                            ) { onPreferencesChange(preferences.copy(showDate = it)) }
                            SettingToggle(
                                tr("Show Settings button", "Tampilkan tombol Setelan"),
                                tr("When hidden, long-press empty Home space to open Settings.", "Jika disembunyikan, tekan lama area kosong Home untuk membuka Setelan."),
                                preferences.showSettingsButton
                            ) { onPreferencesChange(preferences.copy(showSettingsButton = it)) }
                        }
                        SettingsGroup(tr("Widgets and indicators", "Widget dan indikator")) {
                            SettingToggle(tr("Calendar agenda", "Agenda kalender"), "", preferences.showCalendarShortcut) {
                                onPreferencesChange(preferences.copy(showCalendarShortcut = it))
                            }
                            SettingToggle(
                                tr("Music player", "Pemutar musik"),
                                tr("Shows the active track and media controls.", "Menampilkan lagu aktif dan kontrol media."),
                                preferences.showMusic
                            ) { onPreferencesChange(preferences.copy(showMusic = it)) }
                            SettingToggle(tr("Weather", "Cuaca"), "", preferences.showWeather) {
                                onPreferencesChange(preferences.copy(showWeather = it))
                            }
                            SettingToggle(tr("Battery percentage", "Persentase baterai"), "", preferences.showBattery) {
                                onPreferencesChange(preferences.copy(showBattery = it))
                            }
                            SettingToggle(
                                tr("Day progress", "Progres hari"),
                                tr("Shows how much of today has elapsed.", "Menunjukkan bagian hari yang telah berlalu."),
                                preferences.showDayProgress
                            ) { onPreferencesChange(preferences.copy(showDayProgress = it)) }
                            SettingToggle(
                                tr("Year progress", "Progres tahun"),
                                tr("Shows how much of the year has elapsed.", "Menunjukkan bagian tahun yang telah berlalu."),
                                preferences.showYearProgress
                            ) { onPreferencesChange(preferences.copy(showYearProgress = it)) }
                        }
                        SettingsGroup(tr("Notification dots", "Notification dot")) {
                            SettingToggle(
                                tr("Show notification dots", "Tampilkan notification dot"),
                                tr(
                                    "Show a small dot beside selected apps with active notifications.",
                                    "Tampilkan titik kecil di sebelah aplikasi pilihan yang memiliki notifikasi aktif."
                                ),
                                preferences.notificationDotsEnabled
                            ) { enabled ->
                                onPreferencesChange(preferences.copy(notificationDotsEnabled = enabled))
                                if (enabled && !notificationAccessEnabled) {
                                    openSystemPage(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                                }
                            }
                            SettingAction(
                                tr("Choose apps", "Pilih aplikasi"),
                                preferences.notificationDotPackages.size.toString() + " " +
                                    tr("apps selected", "aplikasi dipilih"),
                                tr("Choose", "Pilih")
                            ) { openDialog = "notificationdots" }
                            ChoiceRow(
                                tr("Dot color", "Warna titik"),
                                preferences.notificationDotColor,
                                listOf(
                                    "white" to tr("White", "Putih"),
                                    "red" to tr("Red", "Merah"),
                                    "orange" to tr("Orange", "Oranye"),
                                    "blue" to tr("Blue", "Biru"),
                                    "purple" to tr("Purple", "Ungu")
                                )
                            ) { color ->
                                onPreferencesChange(preferences.copy(notificationDotColor = color))
                            }
                        }
                        SettingsGroup(tr("Format", "Format")) {
                            ChoiceRow(
                                tr("Time format", "Format waktu"),
                                preferences.timeFormat,
                                listOf(
                                    "system" to tr("System", "Sistem"),
                                    "12" to "12 " + tr("hour", "jam"),
                                    "24" to "24 " + tr("hour", "jam")
                                )
                            ) { onPreferencesChange(preferences.copy(timeFormat = it)) }
                            ChoiceRow(
                                tr("Temperature unit", "Satuan suhu"),
                                preferences.weatherUnit,
                                listOf("C" to "°C", "F" to "°F")
                            ) { onPreferencesChange(preferences.copy(weatherUnit = it)) }
                        }
                    }

                    SettingsCategory.APPS -> {
                        SettingsGroup(tr("Favourites", "Favorit")) {
                            SettingAction(
                                tr("Manage favourites", "Kelola favorit"),
                                favorites.size.toString() + "/6 " + tr("apps selected", "aplikasi dipilih"),
                                tr("Manage", "Kelola")
                            ) { openDialog = "favorites" }
                        }
                        SettingsGroup(tr("Hidden apps", "Aplikasi tersembunyi")) {
                            SettingAction(
                                tr("Manage hidden apps", "Kelola aplikasi tersembunyi"),
                                hiddenApps.size.toString() + " " + tr("apps hidden", "aplikasi disembunyikan"),
                                tr("Manage", "Kelola")
                            ) { openDialog = "hidden" }
                        }
                        SettingsGroup(tr("Built-in apps", "Aplikasi bawaan")) {
                            SettingToggle(
                                tr("Show built-in apps", "Tampilkan aplikasi bawaan"),
                                tr("Show Calculator, Calendar and Notes in the app drawer.", "Tampilkan Kalkulator, Kalender, dan Catatan di app drawer."),
                                preferences.showBuiltInApps
                            ) { onPreferencesChange(preferences.copy(showBuiltInApps = it)) }
                        }
                        SettingsGroup(tr("App drawer", "App drawer")) {
                            SettingToggle(
                                tr("Show search bar", "Tampilkan kolom pencarian"),
                                "",
                                preferences.showSearchBar
                            ) { onPreferencesChange(preferences.copy(showSearchBar = it)) }
                            SettingToggle(
                                tr("Alphabet index", "Indeks alfabet"),
                                tr("Show a scrollable A–Z index on the right edge of the app drawer.", "Tampilkan indeks A–Z yang bisa digeser di tepi kanan app drawer."),
                                preferences.showAlphabetIndex
                            ) { onPreferencesChange(preferences.copy(showAlphabetIndex = it)) }
                            SettingToggle(
                                tr("Auto-launch keyboard", "Buka keyboard otomatis"),
                                tr("Focus search when the app drawer opens.", "Fokus ke pencarian saat app drawer dibuka."),
                                preferences.autoLaunchKeyboard
                            ) { onPreferencesChange(preferences.copy(autoLaunchKeyboard = it)) }
                            SettingToggle(
                                tr("Show system apps", "Tampilkan aplikasi sistem"),
                                "",
                                !preferences.hideSystemApps
                            ) { onPreferencesChange(preferences.copy(hideSystemApps = !it)) }
                            if (preferences.wallpaperMode == "image") {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        tr("App drawer wallpaper transparency", "Transparansi wallpaper app drawer"),
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        "${preferences.appDrawerWallpaperTransparency}%",
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                                Slider(
                                    value = preferences.appDrawerWallpaperTransparency.toFloat(),
                                    onValueChange = {
                                        onPreferencesChange(
                                            preferences.copy(appDrawerWallpaperTransparency = it.roundToInt())
                                        )
                                    },
                                    valueRange = 0f..100f,
                                    steps = 19
                                )
                                Text(
                                    tr(
                                        "0% shows the wallpaper fully; 100% covers it with the app drawer background.",
                                        "0% menampilkan wallpaper sepenuhnya; 100% menutupinya dengan latar app drawer."
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    SettingsCategory.GESTURES -> {
                        SettingsGroup(tr("Home gestures", "Gestur Home")) {
                            SettingToggle(
                                tr("Double tap to lock", "Ketuk dua kali untuk mengunci layar"),
                                tr("Requires device-admin activation.", "Memerlukan aktivasi admin perangkat."),
                                preferences.doubleTapToLock
                            ) { onDoubleTapLockChange(it) }
                            SettingToggle(
                                tr("Swipe down for notifications", "Usap ke bawah untuk notifikasi"),
                                tr("Opens the Android notification panel.", "Membuka panel notifikasi Android."),
                                preferences.swipeDownNotifications
                            ) { enabled ->
                                onPreferencesChange(preferences.copy(swipeDownNotifications = enabled))
                            }
                        }
                    }

                    SettingsCategory.ACCESS -> {
                        SettingsGroup(tr("Feature access", "Akses fitur")) {
                            SettingAction(
                                tr("Music and notification access", "Akses musik dan notifikasi"),
                                if (notificationAccessEnabled) tr("Enabled", "Aktif")
                                else tr("Not enabled", "Belum aktif"),
                                tr("Open settings", "Buka setelan")
                            ) {
                                openSystemPage(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                            }
                            SettingAction(
                                tr("Device admin", "Admin perangkat"),
                                if (adminActive) tr("Active", "Aktif")
                                else tr("Not active · needed to lock the screen", "Belum aktif · diperlukan untuk mengunci layar"),
                                if (adminActive) tr("Active", "Aktif") else tr("Activate", "Aktifkan")
                            ) {
                                if (!adminActive) onDoubleTapLockChange(true)
                            }
                            SettingAction(
                                tr("Default launcher", "Launcher default"),
                                if (defaultLauncher) tr("Dislauncher is the default Home app", "Dislauncher adalah aplikasi Home default")
                                else tr("Another Home app is selected", "Aplikasi Home lain sedang dipilih"),
                                tr("Open settings", "Buka setelan")
                            ) {
                                openSystemPage(Intent(Settings.ACTION_HOME_SETTINGS))
                            }
                        }
                        SettingsGroup(tr("Android permissions", "Izin Android")) {
                            SettingAction(
                                tr("Calendar access", "Akses kalender"),
                                if (calendarPermission) tr("Allowed", "Diizinkan")
                                else tr("Not allowed", "Belum diizinkan"),
                                tr("Manage", "Kelola"),
                                { openAppPermissionSettings() }
                            )
                            SettingAction(
                                tr("Location access", "Akses lokasi"),
                                if (locationPermission) tr("Allowed", "Diizinkan")
                                else tr("Not allowed", "Belum diizinkan"),
                                tr("Manage", "Kelola"),
                                ::openAppPermissionSettings
                            )
                        }
                        SettingsGroup(tr("System", "Sistem")) {
                            SettingAction(
                                tr("Android settings", "Setelan Android"),
                                tr("Open device settings.", "Buka setelan perangkat."),
                                tr("Open", "Buka")
                            ) {
                                openSystemPage(Intent(Settings.ACTION_SETTINGS))
                            }
                        }
                    }

                    SettingsCategory.ABOUT -> {
                        SettingsGroup(tr("Dislauncher", "Dislauncher")) {
                            Text(
                                tr("A minimalist, text-first Android launcher.", "Launcher Android minimalis yang mengutamakan teks."),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    tr("Version", "Versi") + " " + appVersion,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            SettingAction(
                                tr("Restart Launcher", "Mulai ulang launcher"),
                                tr("Reloads the launcher interface.", "Memuat ulang antarmuka launcher."),
                                tr("Restart", "Mulai ulang")
                            ) { showRestartConfirmation = true }
                            SettingAction(
                                tr("Project on GitHub", "Proyek di GitHub"),
                                "github.com/insomdroid/dislauncher",
                                tr("Open", "Buka")
                            ) {
                                openSystemPage(
                                    Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/insomdroid/dislauncher"))
                                )
                            }
                        }
                    }

                    null -> Unit
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }

    if (openDialog == "hidden") {
        AppSelectionDialog(
            title = tr("Manage hidden apps", "Kelola aplikasi tersembunyi"),
            subtitle = tr(
                "Checked apps are hidden from Home and the drawer.",
                "Aplikasi yang dicentang disembunyikan dari Home dan app drawer."
            ),
            apps = apps,
            checkedPackages = hiddenApps,
            onToggle = onToggleHidden,
            onDismiss = { openDialog = "" }
        )
    } else if (openDialog == "favorites") {
        AppSelectionDialog(
            title = tr("Manage favourites", "Kelola favorit"),
            subtitle = tr(
                "Checked apps appear in the Home favourites list.",
                "Aplikasi yang dicentang muncul di daftar favorit Home."
            ),
            apps = apps,
            checkedPackages = favorites,
            onToggle = onToggleFavorite,
            onDismiss = { openDialog = "" }
        )
    } else if (openDialog == "notificationdots") {
        AppSelectionDialog(
            title = tr("Notification dot apps", "Aplikasi notification dot"),
            subtitle = tr(
                "Only selected apps can show a dot on Home when they have active non-persistent notifications.",
                "Hanya aplikasi terpilih yang menampilkan titik di Home saat memiliki notifikasi aktif nonpersisten."
            ),
            apps = apps,
            checkedPackages = preferences.notificationDotPackages,
            onToggle = { packageName ->
                val nextPackages = preferences.notificationDotPackages.toMutableSet().apply {
                    if (!add(packageName)) remove(packageName)
                }
                onPreferencesChange(preferences.copy(notificationDotPackages = nextPackages))
            },
            onDismiss = { openDialog = "" }
        )
    } else if (openDialog == "iconpack") {
        AlertDialog(
            onDismissRequest = { openDialog = "" },
            title = { Text(tr("Icon pack", "Paket ikon")) },
            text = {
                Column(
                    Modifier.fillMaxWidth().heightIn(max = 400.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    IconPackChoice(
                        tr("System default", "Bawaan sistem"),
                        "system",
                        preferences.iconPack
                    ) {
                        onPreferencesChange(preferences.copy(iconPack = "system"))
                        openDialog = ""
                    }
                    if (detectedIconPacks.isEmpty()) {
                        Text(
                            tr(
                                "No compatible installed icon pack was detected.",
                                "Tidak ditemukan paket ikon terpasang yang kompatibel."
                            ),
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
            confirmButton = {
                TextButton(onClick = { openDialog = "" }) { Text(tr("Done", "Selesai")) }
            }
        )
    }

    if (showRestartConfirmation) {
        AlertDialog(
            onDismissRequest = { showRestartConfirmation = false },
            title = { Text(tr("Restart Launcher?", "Mulai ulang launcher?")) },
            text = {
                Text(
                    tr(
                        "Dislauncher will reload its interface. Your settings and favourites will be kept.",
                        "Dislauncher akan memuat ulang antarmukanya. Pengaturan dan favorit tetap tersimpan."
                    )
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showRestartConfirmation = false
                        onRestartLauncher()
                    }
                ) {
                    Text(tr("Restart", "Mulai ulang"))
                }
            },
            dismissButton = {
                TextButton(onClick = { showRestartConfirmation = false }) {
                    Text(tr("Cancel", "Batal"))
                }
            }
        )
    }
}

@Composable
private fun SettingsCategoryRow(
    icon: ImageVector,
    title: String,
    description: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
            .padding(vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(14.dp))
                .padding(horizontal = 13.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun isDislauncherDefaultHome(context: Context): Boolean {
    return runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = context.getSystemService(Context.ROLE_SERVICE) as RoleManager
            roleManager.isRoleHeld(RoleManager.ROLE_HOME)
        } else {
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            @Suppress("DEPRECATION")
            context.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
                ?.activityInfo?.packageName == context.packageName
        }
    }.getOrDefault(false)
}
