package com.insomdroid.dislauncher

import android.content.SharedPreferences

internal data class LauncherPreferences(
    val themeMode: String = "system",
    val wallpaperMode: String = "default",
    val wallpaperColor: String = "#202124",
    val accentPreset: String = "default",
    val dynamicColor: Boolean = false,
    val wallpaperUri: String? = null,
    val wallpaperTarget: String = "both",
    val appDrawerWallpaperTransparency: Int = 0,
    val fontMode: String = "system",
    val customFontName: String? = null,
    val fontSize: String = "medium",
    val iconPack: String = "system",
    val iconShape: String = "original",
    val showAppIcons: Boolean = false,
    val showBuiltInApps: Boolean = true,
    val hideStatusBar: Boolean = false,
    val showSearchBar: Boolean = true,
    val showAlphabetIndex: Boolean = true,
    val autoLaunchKeyboard: Boolean = false,
    val showBattery: Boolean = true,
    val showDayProgress: Boolean = true,
    val showYearProgress: Boolean = true,
    val showClock: Boolean = true,
    val showDate: Boolean = true,
    val showWeather: Boolean = true,
    val weatherUnit: String = "C",
    val timeFormat: String = "system",
    val showSettingsButton: Boolean = true,
    val showMusic: Boolean = true,
    val hideSystemApps: Boolean = false,
    val doubleTapToLock: Boolean = false,
    val swipeDownNotifications: Boolean = true,
    val showCalendarShortcut: Boolean = true,
    val notificationDotsEnabled: Boolean = false,
    val notificationDotColor: String = "white",
    val notificationDotPackages: Set<String> = emptySet()
) {
    fun save(p: SharedPreferences) {
        p.edit()
            .putString("theme_mode", themeMode)
            .putString("wallpaper_mode", wallpaperMode)
            .putString("wallpaper_color", wallpaperColor)
            .putString("accent_preset", accentPreset)
            .putBoolean("dynamic_color", dynamicColor)
            .putString("wallpaper_uri", wallpaperUri)
            .putString("wallpaper_target", wallpaperTarget)
            .putInt("app_drawer_wallpaper_transparency", appDrawerWallpaperTransparency.coerceIn(0, 100))
            .putString("font_mode", fontMode)
            .putString("custom_font_name", customFontName)
            .putString("font_size", fontSize)
            .putString("icon_pack", iconPack)
            .putString("icon_shape", iconShape)
            .putBoolean("show_app_icons", showAppIcons)
            .putBoolean("show_built_in_apps", showBuiltInApps)
            .putBoolean("hide_status_bar", hideStatusBar)
            .putBoolean("show_search_bar", showSearchBar)
            .putBoolean("show_alphabet_index", showAlphabetIndex)
            .putBoolean("auto_launch_keyboard", autoLaunchKeyboard)
            .putBoolean("show_battery", showBattery)
            .putBoolean("show_day_progress", showDayProgress)
            .putBoolean("show_year_progress", showYearProgress)
            .putBoolean("show_clock", showClock)
            .putBoolean("show_date", showDate)
            .putBoolean("show_weather", showWeather)
            .putString("weather_unit", weatherUnit)
            .putString("time_format", timeFormat)
            .putBoolean("show_settings_button", showSettingsButton)
            .putBoolean("show_music", showMusic)
            .putBoolean("hide_system_apps", hideSystemApps)
            .putBoolean("double_tap_to_lock", doubleTapToLock)
            .putBoolean("swipe_down_notifications", swipeDownNotifications)
            .putBoolean("show_calendar_shortcut", showCalendarShortcut)
            .putBoolean("notification_dots_enabled", notificationDotsEnabled)
            .putString("notification_dot_color", notificationDotColor)
            .putStringSet("notification_dot_packages", notificationDotPackages.toMutableSet())
            .apply()
    }

    companion object {
        private fun normalizeThemeMode(value: String): String = when (value) {
            "light", "pale-silver", "middle-blue", "parchment", "blue-radiance" -> "light"
            "system", "sun" -> "system"
            "dark" -> "dark"
            "black" -> "black"
            else -> "system"
        }

        private fun normalizeFontMode(value: String): String = when (value) {
            "system", "custom" -> value
            "dislauncher", "mono" -> "dislauncher"
            else -> "system"
        }

        fun load(p: SharedPreferences): LauncherPreferences {
            if (p.getInt("theme_mode_version", 0) < 1) {
                val previousTheme = p.getString("theme_mode", "system") ?: "system"
                p.edit()
                    .putString("theme_mode", if (previousTheme == "dark") "black" else previousTheme)
                    .putInt("theme_mode_version", 1)
                    .apply()
            }

            if (p.getInt("icon_pack_migration_version", 0) < 1) {
                val savedIconPack = p.getString("icon_pack", "system") ?: "system"
                p.edit()
                    .putString("icon_pack", if (savedIconPack.startsWith("builtin:")) "system" else savedIconPack)
                    .putInt("icon_pack_migration_version", 1)
                    .apply()
            }

            if (p.getInt("music_default_migration_version", 0) < 1) {
                p.edit()
                    .putBoolean("show_music", true)
                    .putInt("music_default_migration_version", 1)
                    .apply()
            }

            // Default to both day and year progress after merging the old pin option.
            if (p.getInt("home_layout_version", 0) < 2) {
                p.edit()
                    .putBoolean("show_year_progress", true)
                    .remove("pin_year_progress")
                    .putInt("home_layout_version", 2)
                    .apply()
            }
            return LauncherPreferences(
                themeMode = normalizeThemeMode(p.getString("theme_mode", "system") ?: "system"),
                wallpaperMode = (p.getString("wallpaper_mode", "default") ?: "default").let { if (it == "image") "image" else "default" },
                wallpaperColor = (p.getString("wallpaper_color", "#202124") ?: "#202124").take(9),
                accentPreset = (p.getString("accent_preset", "default") ?: "default")
                    .takeIf { it in setOf("default", "red", "yellow", "blue", "green", "purple", "orange", "pink") } ?: "default",
                dynamicColor = p.getBoolean("dynamic_color", false),
                wallpaperUri = p.getString("wallpaper_uri", null)?.take(2048),
                wallpaperTarget = (p.getString("wallpaper_target", "both") ?: "both")
                    .takeIf { it in setOf("home", "lock", "both") } ?: "both",
                appDrawerWallpaperTransparency = p.getInt("app_drawer_wallpaper_transparency", 0).coerceIn(0, 100),
                fontMode = normalizeFontMode(p.getString("font_mode", "system") ?: "system"),
                customFontName = p.getString("custom_font_name", null)?.take(80),
                fontSize = p.getString("font_size", "medium") ?: "medium",
                iconPack = p.getString("icon_pack", "system") ?: "system",
                iconShape = p.getString("icon_shape", "original")?.takeIf { it in setOf("original", "circle", "outline_square", "filled_square", "filled_rectangle") } ?: "original",
                showAppIcons = p.getBoolean("show_app_icons", false),
                showBuiltInApps = p.getBoolean("show_built_in_apps", true),
                hideStatusBar = p.getBoolean("hide_status_bar", false),
                showSearchBar = p.getBoolean("show_search_bar", true),
                showAlphabetIndex = p.getBoolean("show_alphabet_index", true),
                autoLaunchKeyboard = p.getBoolean("auto_launch_keyboard", false),
                showBattery = p.getBoolean("show_battery", true),
                showDayProgress = p.getBoolean("show_day_progress", true),
                showYearProgress = p.getBoolean("show_year_progress", true),
                showClock = p.getBoolean("show_clock", true),
                showDate = p.getBoolean("show_date", true),
                showWeather = p.getBoolean("show_weather", true),
                weatherUnit = p.getString("weather_unit", "C") ?: "C",
                timeFormat = p.getString("time_format", "system") ?: "system",
                showSettingsButton = p.getBoolean("show_settings_button", true),
                showMusic = p.getBoolean("show_music", true),
                hideSystemApps = p.getBoolean("hide_system_apps", false),
                doubleTapToLock = p.getBoolean("double_tap_to_lock", false),
                swipeDownNotifications = p.getBoolean("swipe_down_notifications", true),
                showCalendarShortcut = p.getBoolean("show_calendar_shortcut", true),
                notificationDotsEnabled = p.getBoolean("notification_dots_enabled", false),
                notificationDotColor = (p.getString("notification_dot_color", "white") ?: "white")
                    .takeIf { it in setOf("white", "red", "orange", "blue", "purple") } ?: "white",
                notificationDotPackages = p.getStringSet("notification_dot_packages", emptySet()).orEmpty().toSet()
            )
        }
    }
}
