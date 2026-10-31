package com.insomdroid.dislauncher

import androidx.compose.ui.unit.dp
import java.util.Locale

/** Shared spacing rhythm for the built-in tools. */
internal object BuiltInToolSpacing {
    val section = 8.dp
    val compact = 4.dp
}

/**
 * Shared shape language for built-in tools: restrained corners, no decorative pill cards.
 * Pills remain appropriate for compact filters and status labels.
 */
internal object BuiltInToolDesign {
    val cardCornerRadius = 8.dp
    val controlCornerRadius = 6.dp
    val calculatorKeyCornerRadius = 8.dp
    val fabEndPadding = 8.dp
    val fabBottomPadding = 24.dp
}

/** Follow the device language while keeping English as the fallback. */
internal fun builtInToolText(english: String, indonesian: String): String =
    if (Locale.getDefault().language == "id") indonesian else english
