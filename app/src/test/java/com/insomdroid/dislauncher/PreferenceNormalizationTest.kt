package com.insomdroid.dislauncher

import org.junit.Assert.assertEquals
import org.junit.Test

class PreferenceNormalizationTest {
    @Test
    fun normalizesWallpaperValues() {
        assertEquals("image", normalizeWallpaperMode("image"))
        assertEquals("default", normalizeWallpaperMode("unknown"))
        assertEquals("#AABBCC", normalizeWallpaperColor("#AABBCC"))
        assertEquals("#202124", normalizeWallpaperColor("not-a-color"))
        assertEquals("#202124", normalizeWallpaperColor("#12345"))
    }

    @Test
    fun normalizesAccentAndWallpaperTarget() {
        assertEquals("purple", normalizeAccentPreset("purple"))
        assertEquals("default", normalizeAccentPreset("neon"))
        assertEquals("home", normalizeWallpaperTarget("home"))
        assertEquals("lock", normalizeWallpaperTarget("lock"))
        assertEquals("both", normalizeWallpaperTarget("invalid"))
    }

    @Test
    fun normalizesFontSizeWeatherUnitAndTimeFormat() {
        assertEquals("small", normalizeFontSize("small"))
        assertEquals("medium", normalizeFontSize("extra-large"))
        assertEquals("F", normalizeWeatherUnit("F"))
        assertEquals("C", normalizeWeatherUnit("Kelvin"))
        assertEquals("12", normalizeTimeFormat("12"))
        assertEquals("system", normalizeTimeFormat("automatic"))
    }

    @Test
    fun preservesSupportedIconShapesAndMigratesLegacyValues() {
        assertEquals("circle", normalizeIconShape("circle"))
        assertEquals("original", normalizeIconShape("unknown"))
        assertEquals("filled_square", normalizeIconShape("outline_square"))
        assertEquals("filled_square", normalizeIconShape("filled_rectangle"))
    }

    @Test
    fun normalizesNotificationDotColor() {
        assertEquals("blue", normalizeNotificationDotColor("blue"))
        assertEquals("default", normalizeNotificationDotColor("white"))
        assertEquals("default", normalizeNotificationDotColor("invalid"))
    }
}
