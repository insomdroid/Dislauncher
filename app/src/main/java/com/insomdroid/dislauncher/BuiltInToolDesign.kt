package com.insomdroid.dislauncher

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.util.Locale

/** Shared spacing rhythm for the built-in tools. */
internal object BuiltInToolSpacing {
    val section = 8.dp
    val compact = 4.dp
    val card = 12.dp
    val cardHorizontal = 16.dp
    val listRowHorizontal = 8.dp
    val listRowVertical = 12.dp
    val agendaRowHorizontal = 4.dp
    val agendaRowVertical = 8.dp
    val gridInset = 8.dp
}

/**
 * Shared shape language for built-in tools: restrained corners, no decorative pill cards.
 * Pills remain appropriate for compact filters and status labels.
 */
internal object BuiltInToolDesign {
    val cardCornerRadius = LauncherShapeTokens.medium
    val controlCornerRadius = LauncherShapeTokens.small
    val calculatorKeyCornerRadius = LauncherShapeTokens.medium
    /** The shared shell adds [contentVerticalInset]; with that, 6 dp here gives a 16 dp bottom margin. */
    val fabBottomPadding = 6.dp
    val fabSize = 56.dp
    val contentHorizontalInset = 16.dp
    val filterHorizontalInset = 4.dp
    val contentHorizontalInsetWide = 24.dp
    /** Vertical inset of tool content inside the shared shell. */
    val contentVerticalInset = 10.dp
    /** Maximum width of tool content on wide screens; the Books reader shares it. */
    val contentMaxWidth = 720.dp
    val calculatorMaxWidth = 560.dp
    val emptyStateHorizontalInset = 24.dp
}

/** Horizontal margin for tool content: 16 dp on phones, 24 dp from 600 dp wide. */
internal fun builtInToolContentMargin(availableWidth: Dp): Dp =
    if (availableWidth >= 600.dp) BuiltInToolDesign.contentHorizontalInsetWide else BuiltInToolDesign.contentHorizontalInset

/** Follow the device language while keeping English as the fallback. */
internal fun builtInToolText(english: String, indonesian: String): String =
    if (Locale.getDefault().language == "id") indonesian else english
