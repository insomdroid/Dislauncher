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
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import java.io.File

@OptIn(ExperimentalTextApi::class)
@Composable
internal fun DislauncherTheme(
    preferences: LauncherPreferences,
    isDark: Boolean,
    baseDensity: Density,
    content: @Composable (FontFamily) -> Unit
) {
    val context = LocalContext.current
    // Dynamic Color is an explicit preference, independent from theme mode and accent presets.
    val useDynamicColor = preferences.dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val monochromeScheme = if (isDark) {
        val pureBlack = preferences.themeMode == "black"
        darkColorScheme().copy(
            primary = Color(0xFFF2F2F2),
            onPrimary = Color(0xFF111111),
            primaryContainer = Color(0xFF262626),
            onPrimaryContainer = Color(0xFFF2F2F2),
            secondary = Color(0xFFD9D9D9),
            onSecondary = Color(0xFF111111),
            secondaryContainer = Color(0xFF262626),
            onSecondaryContainer = Color(0xFFF2F2F2),
            tertiary = Color(0xFFA6A6A6),
            onTertiary = Color(0xFF111111),
            tertiaryContainer = Color(0xFF333333),
            onTertiaryContainer = Color(0xFFF2F2F2),
            background = if (pureBlack) Color.Black else Color(0xFF111111),
            onBackground = Color(0xFFF2F2F2),
            surface = if (pureBlack) Color.Black else Color(0xFF111111),
            onSurface = Color(0xFFF2F2F2),
            surfaceVariant = Color(0xFF262626),
            onSurfaceVariant = Color(0xFFD9D9D9),
            inverseSurface = Color(0xFFF2F2F2),
            inverseOnSurface = Color(0xFF111111),
            inversePrimary = Color(0xFF404040),
            outline = Color(0xFF737373),
            outlineVariant = Color(0xFF404040),
            surfaceTint = Color.Transparent,
            surfaceContainerLowest = if (pureBlack) Color.Black else Color(0xFF0A0A0A),
            surfaceContainerLow = if (pureBlack) Color.Black else Color(0xFF111111),
            surfaceContainer = Color(0xFF1B1B1B),
            surfaceContainerHigh = Color(0xFF262626),
            surfaceContainerHighest = Color(0xFF333333)
        )
    } else {
        lightColorScheme().copy(
            primary = Color(0xFF111111),
            onPrimary = Color.White,
            primaryContainer = Color(0xFFE6E6E6),
            onPrimaryContainer = Color(0xFF111111),
            secondary = Color(0xFF404040),
            onSecondary = Color.White,
            secondaryContainer = Color(0xFFF2F2F2),
            onSecondaryContainer = Color(0xFF111111),
            tertiary = Color(0xFF737373),
            onTertiary = Color.White,
            tertiaryContainer = Color(0xFFE6E6E6),
            onTertiaryContainer = Color(0xFF111111),
            background = Color(0xFFF2F2F2),
            onBackground = Color(0xFF111111),
            surface = Color.White,
            onSurface = Color(0xFF111111),
            surfaceVariant = Color(0xFFE6E6E6),
            onSurfaceVariant = Color(0xFF404040),
            inverseSurface = Color(0xFF262626),
            inverseOnSurface = Color(0xFFF2F2F2),
            inversePrimary = Color(0xFFD9D9D9),
            outline = Color(0xFF737373),
            outlineVariant = Color(0xFFD9D9D9),
            surfaceTint = Color.Transparent,
            surfaceContainerLowest = Color.White,
            surfaceContainerLow = Color(0xFFF7F7F7),
            surfaceContainer = Color(0xFFF2F2F2),
            surfaceContainerHigh = Color(0xFFE6E6E6),
            surfaceContainerHighest = Color(0xFFD9D9D9)
        )
    }
    val baseColorScheme = when {
        useDynamicColor && isDark -> dynamicDarkColorScheme(context)
        useDynamicColor -> dynamicLightColorScheme(context)
        else -> monochromeScheme
    }
    val baseThemeColorScheme = if (preferences.themeMode == "black") {
        baseColorScheme.copy(
            background = Color.Black,
            surface = Color.Black,
            surfaceVariant = Color(0xFF111111),
            surfaceContainerLowest = Color.Black,
            surfaceContainerLow = Color.Black,
            surfaceContainer = Color(0xFF111111),
            surfaceContainerHigh = Color(0xFF1B1B1B),
            surfaceContainerHighest = Color(0xFF262626),
            surfaceTint = Color.Transparent,
            secondary = Color(0xFFD9D9D9),
            tertiary = Color(0xFFA6A6A6),
            onSecondary = Color(0xFF111111),
            onTertiary = Color(0xFF111111),
            outline = Color(0xFF737373),
            outlineVariant = Color(0xFF404040)
        )
    } else {
        baseColorScheme
    }
    val accentColors = when (preferences.accentPreset) {
        "red" -> if (isDark) listOf(Color(0xFFFF6B6B), Color(0xFFFFDAD6), Color(0xFF5B2024)) else listOf(Color(0xFFC62828), Color(0xFF410005), Color(0xFFFFCDD2))
        "yellow" -> if (isDark) listOf(Color(0xFFFFD54F), Color(0xFF211B00), Color(0xFF554500)) else listOf(Color(0xFF805B00), Color(0xFF261900), Color(0xFFFFE082))
        "blue" -> if (isDark) listOf(Color(0xFF82B1FF), Color(0xFFD1E4FF), Color(0xFF174A82)) else listOf(Color(0xFF1565C0), Color(0xFF001B3D), Color(0xFFD1E4FF))
        "green" -> if (isDark) listOf(Color(0xFF81C784), Color(0xFFC8E6C9), Color(0xFF1B5E20)) else listOf(Color(0xFF2E7D32), Color(0xFF002204), Color(0xFFC8E6C9))
        "purple" -> if (isDark) listOf(Color(0xFFCE93D8), Color(0xFFE1BEE7), Color(0xFF5A1765)) else listOf(Color(0xFF7B1FA2), Color(0xFF2B0036), Color(0xFFE1BEE7))
        "orange" -> if (isDark) listOf(Color(0xFFFFB74D), Color(0xFFFFE0B2), Color(0xFF663C00)) else listOf(Color(0xFFB45309), Color(0xFF2D1600), Color(0xFFFFE0B2))
        "pink" -> if (isDark) listOf(Color(0xFFFF80AB), Color(0xFFF8BBD0), Color(0xFF801447)) else listOf(Color(0xFFAD1457), Color(0xFF3E001D), Color(0xFFF8BBD0))
        else -> null
    }
    val colorScheme = if (useDynamicColor) baseThemeColorScheme else accentColors?.let { colors ->
        val primary = colors[0]
        val onPrimary = if (isDark) Color(0xFF24131A) else Color.White
        baseThemeColorScheme.copy(
            primary = primary,
            onPrimary = onPrimary,
            primaryContainer = colors[2],
            onPrimaryContainer = colors[1],
            secondary = primary,
            onSecondary = onPrimary,
            secondaryContainer = colors[2],
            onSecondaryContainer = colors[1],
            tertiary = primary,
            onTertiary = onPrimary,
            tertiaryContainer = colors[2],
            onTertiaryContainer = colors[1],
            inversePrimary = primary,
            surfaceTint = primary
        )
    } ?: baseThemeColorScheme
    val fontFamily = remember(preferences.fontMode, preferences.customFontName) {
        when (preferences.fontMode) {
            "dislauncher" -> FontFamily(
                Font(
                    R.font.inter_variable,
                    weight = FontWeight.Normal,
                    variationSettings = FontVariation.Settings(FontVariation.weight(400))
                ),
                Font(
                    R.font.inter_variable,
                    weight = FontWeight.Medium,
                    variationSettings = FontVariation.Settings(FontVariation.weight(500))
                ),
                Font(
                    R.font.inter_variable,
                    weight = FontWeight.SemiBold,
                    variationSettings = FontVariation.Settings(FontVariation.weight(600))
                )
            )
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
