package com.insomdroid.dislauncher

import androidx.compose.ui.unit.dp
import java.util.Locale

/** Shared spacing rhythm for the built-in tools; layouts may still adapt to their purpose. */
internal object BuiltInToolSpacing {
    val section = 12.dp
    val compact = 6.dp
}

/** Follow the device language while keeping English as the fallback. */
internal fun builtInToolText(english: String, indonesian: String): String =
    if (Locale.getDefault().language == "id") indonesian else english
