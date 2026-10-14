package com.insomdroid.dislauncher

import android.content.SharedPreferences

internal data class LauncherPreferences(
    val themeMode: String = "system",
    val dynamicColor: Boolean = true,
    val darkAppearance: String = "system",
    val fontMode: String = "system",
    val customFontName: String? = null,
    val fontSize: String = "medium",
    val iconPack: String = "system",
    val showAppIcons: Boolean = false,
    val hideStatusBar: Boolean = false,
    val showSearchBar: Boolean = true,
    val autoLaunchKeyboard: Boolean = false,
    val showBattery: Boolean = true,
    val showDayProgress: Boolean = true,
    val showYearProgress: Boolean = true,
    val showClock: Boolean = true,
    val showDate: Boolean = true,
    val showWeather: Boolean = true,
    val weatherUnit: String = "C",
    val timeFormat: String = "system",
    val showScreenTime: Boolean = false,
    val showTasks: Boolean = true,
    val showCall: Boolean = true,
    val showSettingsButton: Boolean = true,
    val showMusic: Boolean = false,
    val hideSystemApps: Boolean = false,
    val doubleTapToLock: Boolean = false,
    val swipeDownNotifications: Boolean = true,
    val showCalendarShortcut: Boolean = true
) {
    fun save(p: SharedPreferences) {
        p.edit()
            .putString("theme_mode", themeMode)
            .putBoolean("dynamic_color", dynamicColor)
            .putString("dark_appearance", darkAppearance)
            .putString("font_mode", fontMode)
            .putString("custom_font_name", customFontName)
            .putString("font_size", fontSize)
            .putString("icon_pack", iconPack)
            .putBoolean("show_app_icons", showAppIcons)
            .putBoolean("hide_status_bar", hideStatusBar)
            .putBoolean("show_search_bar", showSearchBar)
            .putBoolean("auto_launch_keyboard", autoLaunchKeyboard)
            .putBoolean("show_battery", showBattery)
            .putBoolean("show_day_progress", showDayProgress)
            .putBoolean("show_year_progress", showYearProgress)
            .putBoolean("show_clock", showClock)
            .putBoolean("show_date", showDate)
            .putBoolean("show_weather", showWeather)
            .putString("weather_unit", weatherUnit)
            .putString("time_format", timeFormat)
            .putBoolean("show_screen_time", showScreenTime)
            .putBoolean("show_tasks", showTasks)
            .putBoolean("show_call", showCall)
            .putBoolean("show_settings_button", showSettingsButton)
            .putBoolean("show_music", showMusic)
            .putBoolean("hide_system_apps", hideSystemApps)
            .putBoolean("double_tap_to_lock", doubleTapToLock)
            .putBoolean("swipe_down_notifications", swipeDownNotifications)
            .putBoolean("show_calendar_shortcut", showCalendarShortcut)
            .apply()
    }

    companion object {
        private fun normalizeThemeMode(value: String): String = when (value) {
            "light", "pale-silver", "middle-blue", "parchment", "blue-radiance" -> "light"
            "system", "sun" -> "system"
            else -> "dark"
        }

        private fun normalizeFontMode(value: String): String = when (value) {
            "system", "mono", "custom" -> value
            else -> "system"
        }

        fun load(p: SharedPreferences): LauncherPreferences {
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
                dynamicColor = p.getBoolean("dynamic_color", true),
                darkAppearance = p.getString(
                    "dark_appearance",
                    if (p.getBoolean("pitch_black", false)) "amoled" else "system"
                )?.let { if (it == "amoled") "amoled" else "system" } ?: "system",
                fontMode = normalizeFontMode(p.getString("font_mode", "system") ?: "system"),
                customFontName = p.getString("custom_font_name", null)?.take(80),
                fontSize = p.getString("font_size", "medium") ?: "medium",
                iconPack = p.getString("icon_pack", "system") ?: "system",
                showAppIcons = p.getBoolean("show_app_icons", false),
                hideStatusBar = p.getBoolean("hide_status_bar", false),
                showSearchBar = p.getBoolean("show_search_bar", true),
                autoLaunchKeyboard = p.getBoolean("auto_launch_keyboard", false),
                showBattery = p.getBoolean("show_battery", true),
                showDayProgress = p.getBoolean("show_day_progress", true),
                showYearProgress = p.getBoolean("show_year_progress", true),
                showClock = p.getBoolean("show_clock", true),
                showDate = p.getBoolean("show_date", true),
                showWeather = p.getBoolean("show_weather", true),
                weatherUnit = p.getString("weather_unit", "C") ?: "C",
                timeFormat = p.getString("time_format", "system") ?: "system",
                showScreenTime = p.getBoolean("show_screen_time", false),
                showTasks = p.getBoolean("show_tasks", true),
                showCall = p.getBoolean("show_call", true),
                showSettingsButton = p.getBoolean("show_settings_button", true),
                showMusic = p.getBoolean("show_music", false),
                hideSystemApps = p.getBoolean("hide_system_apps", false),
                doubleTapToLock = p.getBoolean("double_tap_to_lock", false),
                swipeDownNotifications = p.getBoolean("swipe_down_notifications", true),
                showCalendarShortcut = p.getBoolean("show_calendar_shortcut", true)
            )
        }
    }
}
