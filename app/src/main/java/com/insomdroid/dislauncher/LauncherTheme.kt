package com.insomdroid.dislauncher

import android.graphics.Typeface
import android.os.Build
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LocalTextStyle
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import java.io.File

@Composable
internal fun DislauncherTheme(
    preferences: LauncherPreferences,
    isDark: Boolean,
    baseDensity: Density,
    content: @Composable (FontFamily) -> Unit
) {
    val context = LocalContext.current
    val useDynamicColor = preferences.dynamicColor &&
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val fallbackPrimary = if (isDark) Color(0xFF8AB4F8) else Color(0xFF1967D2)
    val baseColorScheme = when {
        useDynamicColor && isDark -> dynamicDarkColorScheme(context)
        useDynamicColor -> dynamicLightColorScheme(context)
        isDark -> darkColorScheme().copy(
            primary = fallbackPrimary,
            onPrimary = Color(0xFF101010)
        )
        else -> lightColorScheme().copy(
            primary = fallbackPrimary,
            onPrimary = Color.White
        )
    }
    val textSafeDarkScheme = if (isDark) {
        baseColorScheme.copy(
            secondary = Color.White,
            tertiary = Color.White,
            onPrimary = baseColorScheme.onPrimary,
            onSecondary = Color(0xFF101010),
            onTertiary = Color(0xFF101010),
            onBackground = Color.White,
            onSurface = Color.White,
            onSurfaceVariant = Color.White,
            onPrimaryContainer = Color.White,
            onSecondaryContainer = Color.White,
            onTertiaryContainer = Color.White,
            inverseOnSurface = Color.White,
            error = Color.White,
            onError = Color.White,
            onErrorContainer = Color.White
        )
    } else {
        baseColorScheme
    }
    val colorScheme = if (preferences.themeMode == "dark") {
        textSafeDarkScheme.copy(
            background = Color.Black,
            surface = Color.Black,
            surfaceVariant = Color.Black,
            surfaceContainerLowest = Color.Black,
            surfaceContainerLow = Color.Black,
            surfaceContainer = Color.Black,
            surfaceContainerHigh = Color.Black,
            surfaceContainerHighest = Color.Black,
            surfaceTint = Color.Transparent,
            secondary = Color.White,
            tertiary = Color.White,
            onSecondary = Color(0xFF101010),
            onTertiary = Color(0xFF101010),
            outline = Color(0xFFBDBDBD),
            outlineVariant = Color(0xFF757575)
        )
    } else {
        textSafeDarkScheme
    }
    val fontFamily = remember(preferences.fontMode, preferences.customFontName) {
        when (preferences.fontMode) {
            "mono" -> FontFamily.Monospace
            "custom" -> {
                val fontFile = listOf("ttf", "otf")
                    .map { File(context.filesDir, "fonts/custom-font.$it") }
                    .firstOrNull { it.isFile && it.length() in 12L..MAX_CUSTOM_FONT_BYTES.toLong() }
                fontFile?.let { file ->
                    runCatching { FontFamily(Typeface.createFromFile(file)) }.getOrNull()
                } ?: FontFamily.Default
            }
            else -> FontFamily.Default
        }
    }
    val fontScale = when (preferences.fontSize) {
        "small" -> 0.88f
        "large" -> 1.22f
        else -> 1.0f
    }
    val baseTypography = Typography()
    fun scaled(style: TextStyle): TextStyle = style.copy(fontFamily = fontFamily)
    val typography = baseTypography.copy(
        displayLarge = scaled(baseTypography.displayLarge),
        displayMedium = scaled(baseTypography.displayMedium),
        displaySmall = scaled(baseTypography.displaySmall),
        headlineLarge = scaled(baseTypography.headlineLarge),
        headlineMedium = scaled(baseTypography.headlineMedium),
        headlineSmall = scaled(baseTypography.headlineSmall),
        titleLarge = scaled(baseTypography.titleLarge),
        titleMedium = scaled(baseTypography.titleMedium),
        titleSmall = scaled(baseTypography.titleSmall),
        bodyLarge = scaled(baseTypography.bodyLarge),
        bodyMedium = scaled(baseTypography.bodyMedium),
        bodySmall = scaled(baseTypography.bodySmall),
        labelLarge = scaled(baseTypography.labelLarge),
        labelMedium = scaled(baseTypography.labelMedium),
        labelSmall = scaled(baseTypography.labelSmall)
    )
    MaterialTheme(
        colorScheme = colorScheme,
        typography = typography,
        shapes = androidx.compose.material3.Shapes(
            extraSmall = RoundedCornerShape(4.dp),
            small = RoundedCornerShape(8.dp),
            medium = RoundedCornerShape(12.dp),
            large = RoundedCornerShape(16.dp),
            extraLarge = RoundedCornerShape(28.dp)
        )
    ) {
        CompositionLocalProvider(
            LocalDensity provides Density(
                density = baseDensity.density,
                fontScale = baseDensity.fontScale * fontScale
            ),
            LocalTextStyle provides LocalTextStyle.current.copy(fontFamily = fontFamily)
        ) {
            content(fontFamily)
        }
    }
}
