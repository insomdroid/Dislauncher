package com.insomdroid.dislauncher

private val supportedAccentPresets = setOf(
    "default", "red", "yellow", "blue", "green", "purple", "orange", "pink"
)
private val supportedIconShapes = setOf(
    "original", "circle", "filled_square"
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
    when (value) {
        "outline_square", "filled_rectangle" -> "filled_square"
        else -> value.takeIf { it in supportedIconShapes } ?: "original"
    }

internal fun normalizeNotificationDotColor(value: String): String =
    (if (value == "white") "default" else value)
        .takeIf { it in supportedAccentPresets } ?: "default"

private val supportedDrawerLayouts = setOf("list", "multi_list", "grid", "icons_only")
private val supportedLabelWeights = setOf("light", "normal", "medium", "bold")
private val supportedLabelColors = setOf("default", "accent", "muted")

internal fun normalizeDrawerLayout(value: String): String =
    value.takeIf { it in supportedDrawerLayouts } ?: "list"

/** 0 = Auto. */
internal fun normalizeDrawerColumns(value: Int): Int =
    if (value <= 0) 0 else value.coerceIn(DRAWER_COLUMNS_MIN, DRAWER_COLUMNS_MAX)

/** 0 = layout default. */
internal fun normalizeDrawerIconSize(value: Int): Int =
    if (value <= 0) 0 else value.coerceIn(DRAWER_ICON_SIZE_MIN, DRAWER_ICON_SIZE_MAX)

/** 0 = layout default. */
internal fun normalizeDrawerLabelSize(value: Int): Int =
    if (value <= 0) 0 else value.coerceIn(DRAWER_LABEL_SIZE_MIN, DRAWER_LABEL_SIZE_MAX)

internal fun normalizeLabelWeight(value: String): String =
    value.takeIf { it in supportedLabelWeights } ?: "normal"

internal fun normalizeLabelColor(value: String): String =
    value.takeIf { it in supportedLabelColors } ?: "default"

/** 0 = default Home favourite icon size. */
internal fun normalizeHomeIconSize(value: Int): Int =
    if (value <= 0) 0 else value.coerceIn(HOME_ICON_SIZE_MIN, HOME_ICON_SIZE_MAX)

/** 0 = default Home favourite name size. */
internal fun normalizeHomeLabelSize(value: Int): Int =
    if (value <= 0) 0 else value.coerceIn(HOME_LABEL_SIZE_MIN, HOME_LABEL_SIZE_MAX)
