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
import androidx.compose.foundation.layout.size
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
import androidx.compose.material3.IconButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Switch
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
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
    onApplyWallpaperToSystem: (String, (String) -> Unit) -> Unit,
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
    var newFolderName by remember { mutableStateOf("") }
    var manageFolderName by remember { mutableStateOf<String?>(null) }
    var wallpaperTarget by remember(preferences.wallpaperTarget) { mutableStateOf(preferences.wallpaperTarget) }
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

    var showChangelog by remember { mutableStateOf(false) }

    BackHandler(enabled = showChangelog || selectedCategory != null) {
        if (showChangelog) {
            showChangelog = false
        } else {
            selectedCategory = null
            openDialog = ""
        }
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

    val categoryTitle = if (showChangelog) {
        tr("What's New", "Yang Baru")
    } else when (selectedCategory) {
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
                    IconButton(
                        onClick = {
                            when {
                                showChangelog -> showChangelog = false
                                selectedCategory != null -> {
                                    selectedCategory = null
                                    openDialog = ""
                                }
                                else -> onBack()
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.ArrowBack,
                            contentDescription = tr("Back", "Kembali"),
                            modifier = Modifier.size(24.dp)
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
                .padding(horizontal = when {
                    configuration.screenWidthDp > 888 -> ((configuration.screenWidthDp - 840) / 2).dp
                    configuration.screenWidthDp >= 600 -> 24.dp
                    else -> 20.dp
                }),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (showChangelog) {
                ChangelogScreen()
            } else if (selectedCategory == null) {
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
                        tr("Home information, indicators and formats", "Info Home, indikator, dan format"),
                    Triple(SettingsCategory.APPS, Icons.Outlined.Apps, tr("Apps", "Aplikasi")) to
                        tr("Folders, app visibility and app drawer", "Folder, visibilitas aplikasi, dan laci aplikasi"),
                    Triple(SettingsCategory.GESTURES, Icons.Outlined.Gesture, tr("Gestures", "Gestur")) to
                        tr("Quick actions for the Home screen", "Aksi cepat pada layar utama"),
                    Triple(SettingsCategory.ACCESS, Icons.Outlined.Security, tr("Permissions and access", "Izin dan akses")) to
                        tr("Check access required by features", "Periksa akses yang diperlukan fitur"),
                    Triple(SettingsCategory.ABOUT, Icons.Outlined.Info, tr("About", "Tentang")) to
                        tr("Version and project information", "Versi dan informasi proyek")
                )
                categories.forEach { entry ->
                    val (category, icon, title) = entry.first
                    SettingsCategoryRow(
                        icon = icon,
                        title = title,
                        description = entry.second,
                        onClick = { selectedCategory = category }
                    )
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
                            if (preferences.dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                Column(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        tr("Accent color", "Warna aksen"),
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        tr(
                                            "Managed by Dynamic color while it is enabled.",
                                            "Dikelola oleh Warna dinamis selama fitur ini aktif."
                                        ),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            } else {
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
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        tr("Dim Home wallpaper", "Redupkan wallpaper Home"),
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text("${preferences.homeWallpaperDim}%", style = MaterialTheme.typography.bodyMedium)
                                }
                                Slider(
                                    value = preferences.homeWallpaperDim.toFloat(),
                                    onValueChange = { value ->
                                        onPreferencesChange(preferences.copy(homeWallpaperDim = value.roundToInt()))
                                    },
                                    valueRange = 0f..100f,
                                    steps = 19
                                )
                                Text(
                                    tr(
                                        "0% keeps the wallpaper bright; 100% makes it fully dark.",
                                        "0% menjaga wallpaper tetap terang; 100% membuatnya gelap sepenuhnya."
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                SettingAction(
                                    tr("Apply wallpaper", "Terapkan wallpaper"),
                                    tr(
                                        if (preferences.wallpaperUri != null) {
                                            "Apply the selected image to the Home screen, lock screen, or both."
                                        } else {
                                            "Choose an image before applying it to your device wallpaper."
                                        },
                                        if (preferences.wallpaperUri != null) {
                                            "Terapkan gambar pilihan ke layar utama, layar kunci, atau keduanya."
                                        } else {
                                            "Pilih gambar terlebih dahulu sebelum menerapkannya ke wallpaper perangkat."
                                        }
                                    ),
                                    tr("Apply to screen…", "Terapkan ke layar…"),
                                    {
                                        wallpaperTarget = preferences.wallpaperTarget
                                        openDialog = "wallpaperTarget"
                                    }
                                )
                            } else {
                                SettingAction(
                                    tr("Default wallpaper color", "Warna wallpaper bawaan"),
                                    tr(
                                        "Use the current Light, Dark, System, or AMOLED background color for the selected device screen(s). Dislauncher’s internal background stays separate.",
                                        "Gunakan warna latar Light, Dark, Sistem, atau AMOLED saat ini untuk layar perangkat yang dipilih. Latar internal Dislauncher tetap terpisah."
                                    ),
                                    tr("Apply to screen…", "Terapkan ke layar…"),
                                    {
                                        wallpaperTarget = preferences.wallpaperTarget
                                        openDialog = "wallpaperTarget"
                                    }
                                )
                            }
                        }
                        SettingsGroup(tr("Font and text size", "Font dan ukuran teks")) {
                            ChoiceRow(
                                tr("Built-in font", "Font bawaan"),
                                preferences.fontMode,
                                listOf(
                                    "system" to tr("System", "Sistem"),
                                    "dislauncher" to "Inter Variable",
                                    "syne" to "Syne",
                                    "bricolage_grotesque" to "Bricolage Grotesque",
                                    "doto" to "Doto",
                                    "geist_mono" to "Geist Mono",
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
                        SettingsGroup(tr("Home elements", "Elemen layar utama")) {
                            SettingToggle(tr("Show status bar", "Tampilkan status bar"), tr("Show Android status icons at the top of the screen.", "Tampilkan ikon status Android di bagian atas layar."), !preferences.hideStatusBar) {
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
                        SettingsGroup(tr("Information and indicators", "Informasi dan indikator")) {
                            SettingToggle(
                                tr("Network info", "Info jaringan"),
                                tr("Show the current network connection on the Home status row.", "Tampilkan koneksi jaringan saat ini pada baris info Home."),
                                preferences.showNetworkInfo
                            ) { onPreferencesChange(preferences.copy(showNetworkInfo = it)) }
                            SettingToggle(tr("Calendar agenda", "Agenda kalender"), tr("Show the next calendar event on the Home screen.", "Tampilkan acara kalender berikutnya di layar utama."), preferences.showCalendarShortcut) {
                                onPreferencesChange(preferences.copy(showCalendarShortcut = it))
                            }
                            SettingToggle(
                                tr("Music player", "Pemutar musik"),
                                tr("Shows the active track and media controls.", "Menampilkan lagu aktif dan kontrol media."),
                                preferences.showMusic
                            ) { onPreferencesChange(preferences.copy(showMusic = it)) }
                            SettingToggle(tr("Weather", "Cuaca"), tr("Show current weather and temperature on Home.", "Tampilkan cuaca dan suhu saat ini di Home."), preferences.showWeather) {
                                onPreferencesChange(preferences.copy(showWeather = it))
                            }
                            SettingToggle(tr("Battery percentage", "Persentase baterai"), tr("Show the current battery level as a percentage.", "Tampilkan tingkat baterai saat ini dalam persen."), preferences.showBattery) {
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
                        SettingsGroup(tr("Notification dots", "Titik notifikasi")) {
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
                                    tr("apps selected · Choose which apps show a dot.", "aplikasi dipilih · Pilih aplikasi yang menampilkan titik."),
                                tr("Choose", "Pilih")
                            ) { openDialog = "notificationdots" }
                            ChoiceRow(
                                tr("Dot color", "Warna titik"),
                                preferences.notificationDotColor,
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
                                description = tr(
                                    "Choose the color used for notification dots.",
                                    "Pilih warna untuk titik notifikasi."
                                )
                            ) { color ->
                                onPreferencesChange(preferences.copy(notificationDotColor = color))
                            }
                        }
                        SettingsGroup(tr("Display formats", "Format tampilan")) {
                            ChoiceRow(
                                tr("Time format", "Format waktu"),
                                preferences.timeFormat,
                                listOf(
                                    "system" to tr("System", "Sistem"),
                                    "12" to "12 " + tr("hour", "jam"),
                                    "24" to "24 " + tr("hour", "jam")
                                )
                                ),
                                description = tr(
                                    "Choose how the Home clock displays time.",
                                    "Pilih cara jam Home menampilkan waktu."
                                )
                            ) { onPreferencesChange(preferences.copy(timeFormat = it)) }
                            ChoiceRow(
                                tr("Temperature unit", "Satuan suhu"),
                                preferences.weatherUnit,
                                listOf("C" to "°C", "F" to "°F"),
                                description = tr(
                                    "Choose Celsius or Fahrenheit for weather.",
                                    "Pilih Celsius atau Fahrenheit untuk cuaca."
                                )
                            ) { onPreferencesChange(preferences.copy(weatherUnit = it)) }
                        }
                    }

                    SettingsCategory.APPS -> {
                        SettingsGroup(tr("Manage apps", "Kelola aplikasi")) {
                            SettingAction(
                                tr("Manage folders", "Kelola folder"),
                                preferences.appFolders.size.toString() + " " + tr("folders", "folder"),
                                tr("Manage", "Kelola")
                            ) {
                                newFolderName = ""
                                manageFolderName = null
                                openDialog = "folders"
                            }
                            SettingAction(
                                tr("Manage favourites", "Kelola favorit"),
                                favorites.size.toString() + "/6 " + tr("apps selected", "aplikasi dipilih"),
                                tr("Manage", "Kelola")
                            ) { openDialog = "favorites" }
                            SettingAction(
                                tr("Manage hidden apps", "Kelola aplikasi tersembunyi"),
                                hiddenApps.size.toString() + " " + tr("apps hidden", "aplikasi disembunyikan"),
                                tr("Manage", "Kelola")
                            ) { openDialog = "hidden" }
                        }
                        SettingsGroup(tr("App visibility", "Visibilitas aplikasi")) {
                            SettingToggle(
                                tr("Show built-in apps", "Tampilkan aplikasi bawaan"),
                                tr("Show Calculator, Calendar, Notes and Unit Converter in a dedicated row above the app list.", "Tampilkan Kalkulator, Kalender, Catatan, dan Konverter Satuan dalam baris khusus di atas daftar aplikasi."),
                                preferences.showBuiltInApps
                            ) { onPreferencesChange(preferences.copy(showBuiltInApps = it)) }
                            SettingToggle(
                                tr("Show system apps", "Tampilkan aplikasi sistem"),
                                "",
                                !preferences.hideSystemApps
                            ) { onPreferencesChange(preferences.copy(hideSystemApps = !it)) }
                        }
                        SettingsGroup(tr("App drawer", "Laci aplikasi")) {
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
                                tr("Long press Home to open settings", "Tekan lama Home untuk membuka setelan"),
                                tr("Open Dislauncher Settings when you press and hold an empty area of Home.", "Buka Setelan Dislauncher saat menekan lama area kosong di Home."),
                                preferences.longPressHomeOpensSettings
                            ) { enabled ->
                                onPreferencesChange(preferences.copy(longPressHomeOpensSettings = enabled))
                            }
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
                                tr("Manage", "Kelola")
                            ) {
                                if (!adminActive) {
                                    onDoubleTapLockChange(true)
                                } else {
                                    openSystemPage(Intent(Settings.ACTION_SECURITY_SETTINGS))
                                }
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
                        SettingsGroup(tr("System and launcher", "Sistem dan launcher")) {
                            SettingAction(
                                tr("Default launcher", "Launcher default"),
                                if (defaultLauncher) tr("Dislauncher is the default Home app", "Dislauncher adalah aplikasi Home default")
                                else tr("Another Home app is selected", "Aplikasi Home lain sedang dipilih"),
                                tr("Open settings", "Buka setelan")
                            ) {
                                openSystemPage(Intent(Settings.ACTION_HOME_SETTINGS))
                            }
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
                                tr("What's New", "Yang Baru"),
                                tr("See what's changed in every release.", "Lihat perubahan pada setiap versi."),
                                tr("View", "Lihat")
                            ) { showChangelog = true }
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

    if (openDialog == "wallpaperTarget") {
        AlertDialog(
            onDismissRequest = { openDialog = "" },
            title = { Text(tr("Apply wallpaper", "Terapkan wallpaper")) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        tr(
                            "Choose where to apply this wallpaper.",
                            "Pilih layar tempat wallpaper ini akan diterapkan."
                        ),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    listOf(
                        "home" to tr("Home screen", "Layar utama"),
                        "lock" to tr("Lock screen", "Layar kunci"),
                        "both" to tr("Both screens", "Keduanya")
                    ).forEach { (value, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { wallpaperTarget = value }
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = wallpaperTarget == value,
                                onClick = { wallpaperTarget = value }
                            )
                            Text(label, modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { openDialog = "" }) {
                    Text(tr("Cancel", "Batal"))
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val target = wallpaperTarget
                        openDialog = ""
                        onPreferencesChange(preferences.copy(wallpaperTarget = target))
                        if (preferences.wallpaperMode == "image" && preferences.wallpaperUri == null) {
                            android.widget.Toast.makeText(
                                context,
                                tr("Choose an image first.", "Pilih gambar terlebih dahulu."),
                                android.widget.Toast.LENGTH_LONG
                            ).show()
                            onChooseWallpaper()
                        } else onApplyWallpaperToSystem(target) { result ->
                            val message = when (result) {
                                "success" -> when (target) {
                                    "home" -> tr("Wallpaper applied to the Home screen.", "Wallpaper berhasil diterapkan ke layar utama.")
                                    "lock" -> tr("Wallpaper applied to the lock screen.", "Wallpaper berhasil diterapkan ke layar kunci.")
                                    else -> tr("Wallpaper applied to both screens.", "Wallpaper berhasil diterapkan ke layar utama dan layar kunci.")
                                }
                                "home_success_lock_failure" -> tr(
                                    "Home screen updated; lock screen wallpaper failed.",
                                    "Layar utama berhasil diperbarui. Wallpaper layar kunci gagal diterapkan."
                                )
                                "home_failure_lock_success" -> tr(
                                    "Lock screen updated; Home screen wallpaper failed.",
                                    "Layar kunci berhasil diperbarui. Wallpaper layar utama gagal diterapkan."
                                )
                                else -> tr("Wallpaper could not be applied. Please try again.", "Wallpaper gagal diterapkan. Silakan coba lagi.")
                            }
                            android.widget.Toast.makeText(
                                context,
                                message,
                                android.widget.Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                ) {
                    Text(tr("Apply", "Terapkan"))
                }
            }
        )
    } else if (openDialog == "hidden") {
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
    } else if (openDialog == "folders") {
        AlertDialog(
            onDismissRequest = { openDialog = ""; manageFolderName = null },
            title = {
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(tr("Manage folders", "Kelola folder"))
                    Text(
                        tr("Create folders, manage their apps, or delete a folder without uninstalling its apps.",
                           "Buat folder, kelola aplikasinya, atau hapus folder tanpa menghapus aplikasi."),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 450.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    OutlinedTextField(
                        value = newFolderName,
                        onValueChange = { newFolderName = it.filterNot { ch -> ch == '\t' || ch == '\n' }.take(32) },
                        singleLine = true,
                        label = { Text(tr("New folder name", "Nama folder baru")) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    TextButton(
                        enabled = newFolderName.isNotBlank() &&
                            preferences.appFolders.none { it.equals(newFolderName.trim(), ignoreCase = true) },
                        onClick = {
                            val name = newFolderName.trim()
                            if (name.isNotEmpty()) {
                                onPreferencesChange(preferences.copy(appFolders = preferences.appFolders + name))
                                newFolderName = ""
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(tr("Create folder", "Buat folder")) }
                    preferences.appFolders.sortedBy { it.lowercase() }.forEach { folder ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(folder, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            TextButton(onClick = { manageFolderName = folder }) { Text(tr("Manage", "Kelola")) }
                            TextButton(onClick = {
                                onPreferencesChange(
                                    preferences.copy(
                                        appFolders = preferences.appFolders - folder,
                                        folderAssignments = preferences.folderAssignments.filterNot {
                                            it.substringAfter('\t', "") == folder
                                        }.toSet()
                                    )
                                )
                            }) { Text(tr("Delete", "Hapus")) }
                        }
                    }
                    manageFolderName?.takeIf { it in preferences.appFolders }?.let { folder ->
                        HorizontalDivider()
                        Text(folder, style = MaterialTheme.typography.titleMedium)
                        val assigned = preferences.folderAssignments.mapNotNull { assignment ->
                            val parts = assignment.split('\t', limit = 2)
                            if (parts.size == 2 && parts[1] == folder) parts[0] else null
                        }.toSet()
                        Text(
                            tr(
                                "Turn on an app to add it to this folder. An app can belong to one folder at a time.",
                                "Aktifkan aplikasi untuk memasukkannya ke folder ini. Satu aplikasi hanya bisa berada di satu folder."
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        apps.filter { !it.packageName.startsWith("internal:") }
                            .sortedBy { it.label.lowercase() }
                            .forEach { app ->
                                val assignedToThisFolder = preferences.folderAssignments.any {
                                    it.startsWith(app.packageName + "\t") && it.substringAfter("\t", "") == folder
                                }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                                ) {
                                    Text(
                                        app.label,
                                        modifier = Modifier.weight(1f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Switch(
                                        checked = assignedToThisFolder,
                                        onCheckedChange = { enabled ->
                                            val withoutExisting = preferences.folderAssignments
                                                .filterNot { it.startsWith(app.packageName + "\t") }
                                                .toSet()
                                            val updated = if (enabled) withoutExisting + (app.packageName + "\t" + folder) else withoutExisting
                                            onPreferencesChange(preferences.copy(folderAssignments = updated))
                                        }
                                    )
                                }
                            }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { openDialog = ""; manageFolderName = null }) { Text(tr("Done", "Selesai")) }
            }
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
        modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 8.dp),
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
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
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
        Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
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
