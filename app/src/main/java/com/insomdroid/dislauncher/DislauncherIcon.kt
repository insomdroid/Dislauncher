package com.insomdroid.dislauncher

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContentColor
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.vicart.compose.material.symbols.MaterialSymbol

/**
 * The single UI icon entry point for Dislauncher.
 *
 * Uses Google's Material Symbols Outlined set, keeps icon tint inherited from the
 * current theme by default, and clears glyph text from accessibility output.
 */
@Composable
internal fun DislauncherIcon(
    icon: String,
    contentDescription: String? = null,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
    size: Dp = 24.dp,
    weight: FontWeight = FontWeight.Normal
) {
    MaterialSymbol.Outlined(
        icon = icon,
        weight = weight,
        size = size,
        tint = tint,
        modifier = modifier.clearAndSetSemantics {
            contentDescription?.let { this.contentDescription = it }
        }
    )
}
