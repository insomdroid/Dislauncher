package com.insomdroid.dislauncher

import android.content.SharedPreferences

internal data class LauncherPreferences(
    val themeMode: String = "dark",
    val fontMode: String = "sans",
    val fontSize: String = "small",
    val iconPack: String = "system",
    val showAppIcons: Boolean = false,
    val hideStatusBar: Boolean = false,
    val showSearchBar: Boolean = true,
    val showCharacterScroll: Boolean = true,
    val autoLaunchKeyboard: Boolean = false,
    val showBattery: Boolean = true,
    val showDayProgress: Boolean = true,
    val showYearProgress: Boolean = true,
    val pinYearProgress: Boolean = true,
    val showClock: Boolean = true,
    val showDate: Boolean = true,
    val showWeather: Boolean = true,
    val weatherUnit: String = "C",
    val timeFormat: String = "system",
    val showScreenTime: Boolean = false,
    val showTasks: Boolean = false,
    val showCall: Boolean = false,
    val showSettingsButton: Boolean = false,
    val showMusic: Boolean = false,
    val showQuote: Boolean = true,
    val hideSystemApps: Boolean = false,
    val doubleTapToLock: Boolean = false,
    val swipeDownNotifications: Boolean = false,
    val showCalendarShortcut: Boolean = false
) {
    fun save(p: SharedPreferences) {
        p.edit()
            .putString("theme_mode", themeMode)
            .putString("font_mode", fontMode)
            .putString("font_size", fontSize)
            .putString("icon_pack", iconPack)
            .putBoolean("show_app_icons", showAppIcons)
            .putBoolean("hide_status_bar", hideStatusBar)
            .putBoolean("show_search_bar", showSearchBar)
            .putBoolean("show_character_scroll", showCharacterScroll)
            .putBoolean("auto_launch_keyboard", autoLaunchKeyboard)
            .putBoolean("show_battery", showBattery)
            .putBoolean("show_day_progress", showDayProgress)
            .putBoolean("show_year_progress", showYearProgress)
            .putBoolean("pin_year_progress", pinYearProgress)
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
            .putBoolean("show_quote", showQuote)
            .putBoolean("hide_system_apps", hideSystemApps)
            .putBoolean("double_tap_to_lock", doubleTapToLock)
            .putBoolean("swipe_down_notifications", swipeDownNotifications)
            .putBoolean("show_calendar_shortcut", showCalendarShortcut)
            .apply()
    }

    companion object {
        private fun normalizeThemeMode(value: String): String = when (value) {
            "system", "sun", "dark", "light" -> value
            "pale-silver", "middle-blue", "parchment", "blue-radiance" -> "light"
            else -> "dark"
        }

        private fun normalizeFontMode(value: String): String = when (value) {
            "writer", "ubuntu" -> "condensed"
            "quicksand", "nunito" -> "rounded"
            "opensans-light", "nunito-light" -> "light"
            "writer-mono" -> "mono"
            "opensans", "menlo", "raleway-dots", "sans", "serif", "mono", "condensed", "rounded", "light", "casual", "cursive" -> value
            else -> "sans"
        }

        fun load(p: SharedPreferences): LauncherPreferences {
            val oldProgress = p.getBoolean("show_progress", true)
            return LauncherPreferences(
                themeMode = normalizeThemeMode(p.getString("theme_mode", "dark") ?: "dark"),
                fontMode = normalizeFontMode(p.getString("font_mode", "sans") ?: "sans"),
                fontSize = p.getString("font_size", "small") ?: "small",
                iconPack = p.getString("icon_pack", "system") ?: "system",
                showAppIcons = p.getBoolean("show_app_icons", false),
                hideStatusBar = p.getBoolean("hide_status_bar", false),
                showSearchBar = p.getBoolean("show_search_bar", true),
                showCharacterScroll = p.getBoolean("show_character_scroll", true),
                autoLaunchKeyboard = p.getBoolean("auto_launch_keyboard", false),
                showBattery = p.getBoolean("show_battery", true),
                showDayProgress = p.getBoolean("show_day_progress", oldProgress),
                showYearProgress = p.getBoolean("show_year_progress", oldProgress),
                pinYearProgress = p.getBoolean("pin_year_progress", true),
                showClock = p.getBoolean("show_clock", true),
                showDate = p.getBoolean("show_date", true),
                showWeather = p.getBoolean("show_weather", true),
                weatherUnit = p.getString("weather_unit", "C") ?: "C",
                timeFormat = p.getString("time_format", "system") ?: "system",
                showScreenTime = p.getBoolean("show_screen_time", false),
                showTasks = p.getBoolean("show_tasks", false),
                showCall = p.getBoolean("show_call", false),
                showSettingsButton = p.getBoolean("show_settings_button", false),
                showMusic = p.getBoolean("show_music", false),
                showQuote = p.getBoolean("show_quote", true),
                hideSystemApps = p.getBoolean("hide_system_apps", false),
                doubleTapToLock = p.getBoolean("double_tap_to_lock", false),
                swipeDownNotifications = p.getBoolean("swipe_down_notifications", false),
                showCalendarShortcut = p.getBoolean("show_calendar_shortcut", false)
            )
        }
    }
}
