package com.insomdroid.dislauncher

internal enum class LauncherScreen { ONBOARDING, HOME, APPS, SETTINGS, WEATHER, NOTES, CALCULATOR, CALENDAR }

internal data class LauncherApp(
    val packageName: String,
    val activityName: String,
    val label: String,
    val isSystem: Boolean
)
