package com.insomdroid.dislauncher

private val supportedAccentPresets = setOf(
    "default", "red", "yellow", "blue", "green", "purple", "orange", "pink"
)
private val supportedIconShapes = setOf(
    "original", "circle", "outline_square", "filled_square", "filled_rectangle"
)
private val wallpaperColorPattern = Regex("^#(?:[0-9a-fA-F]{6}|[0-9a-fA-F]{8})$")

/** Normalize persisted values before they reach UI or rendering code. */
internal fun normalizeWallpaperMode(value: String): String =
    if (value == "image") "image" else "default"

internal fun normalizeWallpaperColor(value: String): String =
    value.takeIf(wallpaperColorPattern::matches) ?: "#202124"

internal fun normalizeAccentPreset(value: String): String =
    value.takeIf { it in supportedAccentPresets } ?: "default"

internal fun normalizeWallpaperTarget(value: String): String =
    value.takeIf { it in setOf("home", "lock", "both") } ?: "both"

internal fun normalizeFontSize(value: String): String =
    value.takeIf { it in setOf("small", "medium", "large") } ?: "medium"

internal fun normalizeWeatherUnit(value: String): String =
    value.takeIf { it == "C" || it == "F" } ?: "C"

internal fun normalizeTimeFormat(value: String): String =
    value.takeIf { it in setOf("system", "12", "24") } ?: "system"

internal fun normalizeIconShape(value: String): String =
    value.takeIf { it in supportedIconShapes } ?: "original"

internal fun normalizeNotificationDotColor(value: String): String =
    (if (value == "white") "default" else value)
        .takeIf { it in supportedAccentPresets } ?: "default"
