package com.insomdroid.dislauncher

import org.junit.Assert.assertEquals
import org.junit.Test

class LauncherPreferencesNormalizationTest {
    @Test
    fun invalidEnumValuesFallBackToSafeDefaults() {
        assertEquals("default", normalizeWallpaperMode("video"))
        assertEquals("both", normalizeWallpaperTarget("unknown"))
        assertEquals("medium", normalizeFontSize("huge"))
        assertEquals("C", normalizeWeatherUnit("K"))
        assertEquals("system", normalizeTimeFormat("25"))
        assertEquals("original", normalizeIconShape("circle_crop"))
        assertEquals("default", normalizeAccentPreset("ultraviolet"))
        assertEquals("default", normalizeNotificationDotColor("white"))
        assertEquals("default", normalizeNotificationDotColor("invalid"))
    }

    @Test
    fun supportedPreferenceValuesArePreserved() {
        assertEquals("image", normalizeWallpaperMode("image"))
        assertEquals("home", normalizeWallpaperTarget("home"))
        assertEquals("small", normalizeFontSize("small"))
        assertEquals("F", normalizeWeatherUnit("F"))
        assertEquals("24", normalizeTimeFormat("24"))
        assertEquals("filled_rectangle", normalizeIconShape("filled_rectangle"))
        assertEquals("purple", normalizeAccentPreset("purple"))
        assertEquals("red", normalizeNotificationDotColor("red"))
    }

    @Test
    fun wallpaperColorAcceptsOnlySixOrEightDigitHexColors() {
        assertEquals("#202124", normalizeWallpaperColor("#202124"))
        assertEquals("#FF202124", normalizeWallpaperColor("#FF202124"))
        assertEquals("#202124", normalizeWallpaperColor("not-a-color"))
        assertEquals("#202124", normalizeWallpaperColor("#12"))
    }
}
