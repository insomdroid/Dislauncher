package com.insomdroid.dislauncher

import androidx.compose.ui.unit.dp

/**
 * Shared icon sizing for launcher UI.
 *
 * App-provided icons and the custom weather illustration keep their own sizing rules.
 * Material Symbols use these role-based sizes throughout the interface.
 */
internal object LauncherIconTokens {
    val status = 16.dp
    val compactSmall = 18.dp
    val compact = 20.dp
    val standard = 24.dp
    val illustration = 40.dp
    val touchTarget = 48.dp
}
