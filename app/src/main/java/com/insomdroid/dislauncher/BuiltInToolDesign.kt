package com.insomdroid.dislauncher

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
    val cardCornerRadius = LauncherShapeTokens.medium
    val controlCornerRadius = LauncherShapeTokens.small
    val calculatorKeyCornerRadius = LauncherShapeTokens.medium
    val fabEndPadding = 8.dp
    val fabBottomPadding = 24.dp
    val fabSize = 56.dp
    val contentHorizontalInset = 16.dp
    val contentVerticalInset = 10.dp
    val filterHorizontalInset = 4.dp
}

/** Shared placement and touch size for floating actions in built-in tools. */
internal fun BoxScope.builtInToolFab(): Modifier =
    Modifier.align(Alignment.BottomEnd)
        .padding(end = BuiltInToolDesign.fabEndPadding, bottom = BuiltInToolDesign.fabBottomPadding)
        .size(BuiltInToolDesign.fabSize)

/** Follow the device language while keeping English as the fallback. */
internal fun builtInToolText(english: String, indonesian: String): String =
    if (Locale.getDefault().language == "id") indonesian else english
