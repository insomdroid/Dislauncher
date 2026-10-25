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


/**
 * Build a complete set of Material 3 accent roles from a tonal ramp.
 * The neutral background/surface roles are retained from the selected theme.
 */
private fun createTonalAccentScheme(
    neutral: androidx.compose.material3.ColorScheme,
    seed: Color,
    isDark: Boolean
): androidx.compose.material3.ColorScheme {
    fun tone(lightTone: Int, darkTone: Int) = tonalColor(seed, if (isDark) darkTone else lightTone)
    val primary = tone(40, 80)
    val onPrimary = tone(100, 20)
    val primaryContainer = tone(90, 30)
    val onPrimaryContainer = tone(10, 90)
    val secondary = tone(35, 80)
    val onSecondary = tone(100, 20)
    val secondaryContainer = tone(92, 30)
    val onSecondaryContainer = tone(10, 90)
    val tertiary = tone(30, 70)
    val onTertiary = tone(100, 10)
    val tertiaryContainer = tone(88, 25)
    val onTertiaryContainer = tone(10, 90)
    return neutral.copy(
        primary = primary,
        onPrimary = onPrimary,
        primaryContainer = primaryContainer,
        onPrimaryContainer = onPrimaryContainer,
        inversePrimary = tone(80, 40),
        secondary = secondary,
        onSecondary = onSecondary,
        secondaryContainer = secondaryContainer,
        onSecondaryContainer = onSecondaryContainer,
        tertiary = tertiary,
        onTertiary = onTertiary,
        tertiaryContainer = tertiaryContainer,
        onTertiaryContainer = onTertiaryContainer,
        surfaceTint = primary
    )
}

private fun tonalColor(seed: Color, tone: Int): Color {
    val t = tone.coerceIn(0, 100) / 100f
    fun channel(value: Float): Float = if (t < 0.5f) {
        value * (t * 2f)
    } else {
        value + (1f - value) * ((t - 0.5f) * 2f)
    }
    return Color(
        red = channel(seed.red),
        green = channel(seed.green),
        blue = channel(seed.blue),
        alpha = 1f
    )
}

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
    val neutralScheme = monochromeScheme
    val dynamicScheme = when {
        useDynamicColor && isDark -> dynamicDarkColorScheme(context)
        useDynamicColor -> dynamicLightColorScheme(context)
        else -> neutralScheme
    }
    // Dynamic Color controls accent roles only. Keep the launcher background and
    // neutral surfaces independent so wallpaper/theme behavior remains predictable.
    val dynamicAccentScheme = dynamicScheme.copy(
        background = neutralScheme.background,
        onBackground = neutralScheme.onBackground,
        surface = neutralScheme.surface,
        onSurface = neutralScheme.onSurface,
        surfaceVariant = neutralScheme.surfaceVariant,
        onSurfaceVariant = neutralScheme.onSurfaceVariant,
        inverseSurface = neutralScheme.inverseSurface,
        inverseOnSurface = neutralScheme.inverseOnSurface,
        outline = neutralScheme.outline,
        outlineVariant = neutralScheme.outlineVariant,
        surfaceContainerLowest = neutralScheme.surfaceContainerLowest,
        surfaceContainerLow = neutralScheme.surfaceContainerLow,
        surfaceContainer = neutralScheme.surfaceContainer,
        surfaceContainerHigh = neutralScheme.surfaceContainerHigh,
        surfaceContainerHighest = neutralScheme.surfaceContainerHighest
    )
    val accentSeed = when (preferences.accentPreset) {
        "red" -> Color(0xFFBA1A1A)
        "yellow" -> Color(0xFF795900)
        "blue" -> Color(0xFF005AC1)
        "green" -> Color(0xFF386A20)
        "purple" -> Color(0xFF6750A4)
        "orange" -> Color(0xFF8B5000)
        "pink" -> Color(0xFF9C4168)
        else -> null
    }
    val manualAccentScheme = accentSeed?.let { createTonalAccentScheme(neutralScheme, it, isDark) }
    val colorScheme = when {
        useDynamicColor -> dynamicAccentScheme
        manualAccentScheme != null -> manualAccentScheme
        else -> neutralScheme
    }
    val themedColorScheme = if (preferences.themeMode == "black") {
        colorScheme.copy(
            background = Color.Black,
            surface = Color.Black,
            surfaceVariant = Color(0xFF111111),
            surfaceContainerLowest = Color.Black,
            surfaceContainerLow = Color.Black,
            surfaceContainer = Color(0xFF080808),
            surfaceContainerHigh = Color(0xFF111111),
            surfaceContainerHighest = Color(0xFF1B1B1B),
            surfaceTint = Color.Transparent
        )
    } else colorScheme
    val fontFamily = remember(preferences.fontMode, preferences.customFontName) {
        fun variableFont(resourceId: Int) = FontFamily(
            Font(
                resourceId,
                weight = FontWeight.Normal,
                variationSettings = FontVariation.Settings(FontVariation.weight(400))
            ),
            Font(
                resourceId,
                weight = FontWeight.Medium,
                variationSettings = FontVariation.Settings(FontVariation.weight(500))
            ),
            Font(
                resourceId,
                weight = FontWeight.SemiBold,
                variationSettings = FontVariation.Settings(FontVariation.weight(600))
            )
        )
        when (preferences.fontMode) {
            "dislauncher", "mono" -> variableFont(R.font.inter_variable)
            "syne" -> variableFont(R.font.syne_variable)
            "bricolage_grotesque" -> variableFont(R.font.bricolage_grotesque_variable)
            "doto" -> variableFont(R.font.doto_variable)
            "geist_mono" -> variableFont(R.font.geist_mono_variable)
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
        colorScheme = themedColorScheme,
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
