package com.insomdroid.dislauncher

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight

/**
 * App-drawer layout and app-name styling helpers.
 *
 * Size preferences use 0 to mean "use the default for the current layout", so existing
 * installs keep the current look until the user changes something.
 */
internal const val DRAWER_ICON_SIZE_MIN = 16
internal const val DRAWER_ICON_SIZE_MAX = 80
internal const val DRAWER_LABEL_SIZE_MIN = 10
internal const val DRAWER_LABEL_SIZE_MAX = 40
internal const val HOME_ICON_SIZE_MIN = 16
internal const val HOME_ICON_SIZE_MAX = 64
internal const val HOME_LABEL_SIZE_MIN = 14
internal const val HOME_LABEL_SIZE_MAX = 44
internal const val DRAWER_COLUMNS_MIN = 2
internal const val DRAWER_COLUMNS_MAX = 8

/** Windows at least this wide (dp) get roomier padding and capped control widths. */
internal const val WIDE_SCREEN_MIN_WIDTH_DP = 600

/** Width reserved on the right for the A–Z index strip. */
private const val ALPHABET_STRIP_DP = 15

internal fun drawerIsGrid(layout: String): Boolean = layout == "grid" || layout == "icons_only"

/** Icon size (dp) a layout uses when the user has not chosen one. "list" matches the original look. */
internal fun drawerDefaultIconSize(layout: String): Int = when (layout) {
    "multi_list" -> 28
    "grid" -> 48
    "icons_only" -> 56
    else -> 18
}

/** App-name size (sp) a layout uses when the user has not chosen one. "list" matches the original look. */
internal fun drawerDefaultLabelSize(layout: String): Int = when (layout) {
    "multi_list" -> 20
    "grid" -> 12
    else -> 25
}

internal fun drawerIconSizeDp(layout: String, override: Int): Int =
    if (override > 0) override else drawerDefaultIconSize(layout)

internal fun drawerLabelSizeSp(layout: String, override: Int): Int =
    if (override > 0) override else drawerDefaultLabelSize(layout)

/** Width (dp) the drawer list can use after side padding and the alphabet strip. */
internal fun drawerAvailableWidthDp(screenWidthDp: Int): Int {
    val sidePadding = if (screenWidthDp >= WIDE_SCREEN_MIN_WIDTH_DP) 32 + 28 else 16 + 14
    return (screenWidthDp - sidePadding - ALPHABET_STRIP_DP).coerceAtLeast(0)
}

/**
 * Number of columns for the installed-apps section.
 * [requested] of 0 means Auto: fit as many columns as the width allows.
 */
internal fun drawerColumnCount(
    layout: String,
    requested: Int,
    iconDp: Int,
    availableWidthDp: Int
): Int = when (layout) {
    "multi_list" ->
        if (requested >= DRAWER_COLUMNS_MIN) requested.coerceAtMost(4)
        else (availableWidthDp / 240).coerceIn(1, 4)
    "grid", "icons_only" ->
        if (requested >= DRAWER_COLUMNS_MIN) requested.coerceAtMost(DRAWER_COLUMNS_MAX)
        else {
            val cell = if (layout == "grid") (iconDp + 32).coerceAtLeast(84) else (iconDp + 28).coerceAtLeast(72)
            (availableWidthDp / cell).coerceIn(DRAWER_COLUMNS_MIN, DRAWER_COLUMNS_MAX)
        }
    else -> 1
}

internal fun labelFontWeight(mode: String): FontWeight = when (mode) {
    "light" -> FontWeight.Light
    "medium" -> FontWeight.Medium
    "bold" -> FontWeight.Bold
    else -> FontWeight.Normal
}

@Composable
internal fun labelTextColor(mode: String, default: Color): Color = when (mode) {
    "accent" -> MaterialTheme.colorScheme.primary
    "muted" -> default.copy(alpha = 0.62f)
    else -> default
}
